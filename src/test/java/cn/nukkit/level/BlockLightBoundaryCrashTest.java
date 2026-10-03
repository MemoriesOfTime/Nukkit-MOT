package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.format.ChunkReadTicket;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.generator.Flat;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Kill an owned child at each light/save cut; recover real LevelDB with B mounted first. */
class BlockLightBoundaryCrashTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"before-A-read", "mid-repair", "B-saved-first", "A-saved-first", "both-saved"})
    void processKillReconstructsLightWithoutAQueueOrCrossChunkSaveAtomicity(String phase) throws Exception {
        Process child = start(phase);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
            Path ready = directory.resolve("ready");
            while (!Files.exists(ready) && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(Files.exists(ready), () -> readLog(phase));
            child.destroyForcibly();
            assertTrue(child.waitFor(10, TimeUnit.SECONDS));
            assertNotEquals(0, child.exitValue());
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(10, TimeUnit.SECONDS); }
        }
        Process recovery = start("recover");
        try {
            assertTrue(recovery.waitFor(40, TimeUnit.SECONDS));
            assertEquals(0, recovery.exitValue(), () -> readLog("recover"));
        } finally {
            if (recovery.isAlive()) { recovery.destroyForcibly(); recovery.waitFor(10, TimeUnit.SECONDS); }
        }
    }

    private Process start(String phase) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xmx256m");
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            if (entry.contains("mockito-core") && entry.endsWith(".jar")) {
                command.add("-javaagent:" + entry); break;
            }
        }
        command.addAll(List.of("-cp", classpath, Worker.class.getName(), phase, directory.toString()));
        return new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(directory.resolve(phase + ".log").toFile()).start();
    }

    private String readLog(String phase) {
        try { return Files.readString(directory.resolve(phase + ".log")); }
        catch (Exception failure) { return failure.toString(); }
    }

    public static final class Worker {
        public static void main(String[] args) throws Exception {
            String phase = args[0]; Path directory = Path.of(args[1]), world = directory.resolve("world");
            MockServer.init();
            Server server = Server.getInstance();
            server.useNativeLevelDB = false; server.levelDbCache = 8;
            server.asyncChunkSending = true; server.asyncChunkLoadCompletion = true;
            Level level = mock(Level.class);
            when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
            when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
            when(level.getMinBlockY()).thenReturn(-64); when(level.getMaxBlockY()).thenReturn(319);
            when(level.isYInRange(anyInt())).thenCallRealMethod();
            when(level.requestChunkLoadAsyncResult(anyInt(), anyInt())).thenAnswer(ignored -> new CompletableFuture<ChunkLoadResult>());
            if (!phase.equals("recover")) LevelDBProvider.generate(world.toString(), "light-crash", 404L, Flat.class);
            LevelDBProvider provider = new LevelDBProvider(level, world.toString());
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(inv ->
                    provider.getLoadedChunk(Level.chunkHash(inv.getArgument(0), inv.getArgument(1))));
            if (!phase.equals("recover")) {
                for (int x = 0; x <= 2; x++) for (int z = -1; z <= 1; z++) {
                    BaseFullChunk chunk = provider.getEmptyChunk(x, z);
                    chunk.setGenerated(true); chunk.setPopulated(true); chunk.setLightPopulated(true);
                    // The emitter block was durably removed, lighting may still be stale.
                    for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) for (int y = 50; y <= 78; y++) {
                        int v = Math.max(0, 15 - Math.abs((x << 4) + lx - 14) - Math.abs(y - 64) - Math.abs((z << 4) + lz - 8));
                        if (v != 0) chunk.setBlockLight(lx, y, lz, v);
                    }
                    // Durable block content unrelated to relighting must survive every cut.
                    chunk.setBlock(8, 10, 8, 1);
                    provider.setChunk(x, z, chunk);
                    provider.saveChunkFuture(x, z, chunk).get(10, TimeUnit.SECONDS);
                    provider.unloadChunk(x, z, false);
                }
            }
            BlockLightBoundary boundary = new BlockLightBoundary(level);
            BaseFullChunk b = load(provider, 1, 0);
            boundary.mounted(b, true); // No pre-crash queue, B first and A still cold.
            if (!phase.equals("before-A-read")) {
                BaseFullChunk a = load(provider, 0, 0);
                boundary.mounted(a, true);
                for (int x = 0; x <= 2; x++) for (int z = -1; z <= 1; z++) {
                    if (provider.getLoadedChunk(Level.chunkHash(x, z)) == null) {
                        boundary.mounted(load(provider, x, z), true);
                    }
                }
                boundary.tick();
                if (!phase.equals("mid-repair")) {
                    for (int i = 0; i < 2000 && boundary.hasWork(); i++) boundary.tick();
                    if (boundary.hasWork() || b.getBlockLight(0, 64, 8) != 0) throw new AssertionError("Ghost light did not converge");
                    if (phase.equals("recover")) {
                        for (int x = 0; x <= 2; x++) for (int z = -1; z <= 1; z++) {
                            BaseFullChunk chunk = provider.getLoadedChunk(Level.chunkHash(x, z));
                            if (chunk.getBlockId(8, 10, 8) != 1) throw new AssertionError("Block content lost");
                        }
                        provider.close(); System.exit(0);
                    }
                    if (phase.equals("B-saved-first") || phase.equals("both-saved"))
                        provider.saveChunkFuture(1, 0, b).get(10, TimeUnit.SECONDS);
                    if (phase.equals("A-saved-first") || phase.equals("both-saved"))
                        provider.saveChunkFuture(0, 0, a).get(10, TimeUnit.SECONDS);
                }
            }
            Files.writeString(directory.resolve("ready"), phase);
            if (!new CountDownLatch(1).await(60, TimeUnit.SECONDS)) throw new AssertionError("Expected process kill");
        }

        private static BaseFullChunk load(LevelDBProvider provider, int x, int z) throws Exception {
            try (ChunkReadTicket ticket = provider.openChunkRead(x, z)) {
                BaseFullChunk decoded = CompletableFuture.supplyAsync(ticket::read).get(10, TimeUnit.SECONDS);
                BaseFullChunk mounted = ticket.tryMount(decoded);
                if (mounted == null) throw new AssertionError("Read result was stale");
                return mounted;
            }
        }
    }
}
