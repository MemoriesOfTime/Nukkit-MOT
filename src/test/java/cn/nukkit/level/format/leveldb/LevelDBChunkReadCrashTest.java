package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkReadTicket;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import org.iq80.leveldb.DB;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Process-crash contract, not a claim about OS power loss or cross-store atomicity. */
class LevelDBChunkReadCrashTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"before-write", "after-write", "after-ack", "after-read", "after-mount", "second-ack"})
    void processKillNeverLosesAnAcknowledgedSnapshot(String phase) throws Exception {
        Process child = start(phase);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            Path ready = this.directory.resolve("ready");
            while (!Files.exists(ready) && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(Files.exists(ready), () -> "Child did not reach " + phase + ": " + log(phase));
            assertEquals(phase, Files.readString(ready));
            child.destroyForcibly(); // Only this test's owned child; SIGKILL on Unix.
            assertTrue(child.waitFor(10, TimeUnit.SECONDS));
            assertNotEquals(0, child.exitValue());
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(10, TimeUnit.SECONDS);
            }
        }
        Process recovery = start("recover");
        try {
            assertTrue(recovery.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, recovery.exitValue(), () -> log("recover"));
            int expected = phase.equals("before-write") ? 1 : phase.equals("second-ack") ? 4 : 3;
            assertEquals(expected, Integer.parseInt(Files.readString(this.directory.resolve("recovered"))));
        } finally {
            if (recovery.isAlive()) {
                recovery.destroyForcibly();
                recovery.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private Process start(String mode) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xmx256m");
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            if (entry.contains("mockito-core") && entry.endsWith(".jar")) {
                command.add("-javaagent:" + entry);
                break;
            }
        }
        command.addAll(List.of("-cp", classpath, Worker.class.getName(), mode, this.directory.toString()));
        return new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(this.directory.resolve(mode + ".log").toFile()).start();
    }

    private String log(String mode) {
        try { return Files.readString(this.directory.resolve(mode + ".log")); }
        catch (Exception failure) { return failure.toString(); }
    }

    public static final class Worker {
        public static void main(String[] args) throws Exception {
            String phase = args[0];
            Path root = Path.of(args[1]);
            Path world = root.resolve("world");
            MockServer.init();
            Server server = Server.getInstance();
            server.useNativeLevelDB = false;
            server.levelDbCache = 8;
            server.asyncChunkSending = true;
            server.asyncChunkLoadCompletion = true;
            Level level = Mockito.mock(Level.class);
            Mockito.when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
            Mockito.when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
            if (!phase.equals("recover")) LevelDBProvider.generate(world.toString(), "crash-fixture", 404L, Flat.class);
            LevelDBProvider provider = new LevelDBProvider(level, world.toString());
            if (phase.equals("recover")) {
                try (ChunkReadTicket ticket = provider.openChunkRead(41, 42)) {
                    BaseFullChunk chunk = ticket.read();
                    if (chunk == null) throw new AssertionError("Acknowledged chunk is missing");
                    Files.writeString(root.resolve("recovered"), Integer.toString(chunk.getBlockId(0, 64, 0)));
                }
                provider.close();
                System.exit(0);
            }

            LevelDBChunk chunk = provider.getEmptyChunk(41, 42);
            chunk.setGenerated(true);
            chunk.setBlock(0, 64, 0, 1);
            provider.setChunk(41, 42, chunk);
            provider.saveChunkFuture(41, 42, chunk).get(10, TimeUnit.SECONDS);
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            if (phase.equals("before-write") || phase.equals("after-write")) {
                Field field = LevelDBProvider.class.getDeclaredField("db");
                field.setAccessible(true);
                DB real = (DB) field.get(provider);
                DB controlled = (DB) Proxy.newProxyInstance(DB.class.getClassLoader(), new Class<?>[]{DB.class},
                        (proxy, method, values) -> {
                            boolean write = method.getName().equals("write");
                            if (write && phase.equals("before-write")) {
                                entered.countDown();
                                if (!release.await(60, TimeUnit.SECONDS)) throw new AssertionError("Parent did not kill child");
                            }
                            Object result;
                            try { result = method.invoke(real, values); }
                            catch (InvocationTargetException failure) { throw failure.getCause(); }
                            if (write && phase.equals("after-write")) {
                                entered.countDown();
                                if (!release.await(60, TimeUnit.SECONDS)) throw new AssertionError("Parent did not kill child");
                            }
                            return result;
                        });
                field.set(provider, controlled);
            }
            chunk.setBlock(0, 64, 0, 3);
            CompletableFuture<Void> ack = provider.saveChunkFuture(41, 42, chunk);
            if (phase.equals("before-write") || phase.equals("after-write")) {
                if (!entered.await(10, TimeUnit.SECONDS) || ack.isDone()) throw new AssertionError("Expected unacknowledged write");
            } else {
                ack.get(10, TimeUnit.SECONDS);
                if (phase.equals("after-read") || phase.equals("after-mount") || phase.equals("second-ack")) {
                    if (!provider.unloadChunk(41, 42, false)) throw new AssertionError("Unload refused");
                    try (ChunkReadTicket ticket = provider.openChunkRead(41, 42)) {
                        BaseFullChunk decoded = ticket.read();
                        if (decoded.getBlockId(0, 64, 0) != 3) throw new AssertionError("Stale read");
                        if (!phase.equals("after-read")) {
                            if (ticket.tryMount(decoded) != decoded) throw new AssertionError("Wrong canonical chunk");
                            if (phase.equals("second-ack")) {
                                decoded.setBlock(0, 64, 0, 4);
                                provider.saveChunkFuture(41, 42, decoded).get(10, TimeUnit.SECONDS);
                            }
                        }
                    }
                }
            }
            Files.writeString(root.resolve("ready"), phase);
            // No close/shutdown hook. The parent kills only this isolated process.
            if (!release.await(60, TimeUnit.SECONDS)) throw new AssertionError("Expected process kill");
        }
    }
}
