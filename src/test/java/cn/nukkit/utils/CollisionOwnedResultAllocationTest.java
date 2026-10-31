package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.utils.serverconfig.ServerConfig;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objenesis.ObjenesisStd;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CollisionOwnedResultAllocationTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 9})
    void nativeMixedResultsKeepOrderFreshnessAndVersionChanges(int size) throws Exception {
        CollisionGrowFixture fixture = new CollisionGrowFixture(false);
        fixture.entity.box(new SimpleAxisAlignedBB(1, 68, 1, 1 + size, 69, 2));
        for (int x = 1; x <= size; x++) {
            fixture.write(x, 68, 0, Block.STONE); // Scanned but outside the entity's collision box.
            fixture.write(x, 68, 1, (x & 1) == 0 ? Block.NETHER_PORTAL : Block.STONE);
        }
        Block[] first = fixture.helper.getCollisionBlocks();
        Block[] second = fixture.helper.getCollisionBlocks();
        assertEquals(size, first.length);
        assertEquals(size, second.length);
        assertNotSame(first, second);
        for (int i = 0; i < size; i++) {
            assertEquals(i + 1, first[i].x);
            assertEquals(68, first[i].y);
            assertEquals(1, first[i].z);
            assertEquals(first[i].getId(), second[i].getId());
            assertNotSame(first[i], second[i]);
        }
        first[0].x = 99;
        first[0] = null;
        Block[] third = fixture.helper.getCollisionBlocks();
        assertEquals(1, third[0].x);
        assertEquals(1, second[0].x);
        for (int x = 1; x <= size; x++) fixture.write(x, 68, 1, Block.AIR);
        assertSame(Block.EMPTY_ARRAY, fixture.helper.getCollisionBlocks());
        fixture.write(1, 68, 1, Block.NETHER_PORTAL);
        Block[] replacement = fixture.helper.getCollisionBlocks();
        assertEquals(1, replacement.length);
        assertEquals(Block.NETHER_PORTAL, replacement[0].getId());
        assertEquals(Block.STONE, second[0].getId());
    }

    @Test
    void nativePortalQueryDoesNotAllocateASecondResultArray() throws Exception {
        Path output = temporary.resolve("allocation.txt");
        Path classes = temporary.resolve("class-load.txt");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-XX:-DoEscapeAnalysis", "-Xms128m", "-Xmx512m",
                "-Xlog:class+load=info:file=" + classes,
                "-Dnukkit.collision.owned.data=" + temporary,
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                AllocationChild.class.getName()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "native allocation child timeout");
            String text = Files.readString(output);
            assertEquals(0, process.exitValue(), text);
            assertTrue(text.contains("PORTAL_NATIVE_ALLOCATION"), text);
            String loaded = Files.readString(classes);
            assertFalse(loaded.contains("org.mockito."), "Mockito must not instrument the native child");
            assertFalse(loaded.contains("net.bytebuddy."), "ByteBuddy must not instrument the native child");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "allocation child was not reaped");
            }
        }
    }

    public static final class AllocationChild {
        private static void require(boolean value, String message) {
            if (!value) throw new AssertionError(message);
        }
        private static double run(CollisionGrowFixture fixture, ThreadMXBean bean, int count) {
            long bytes = 0;
            Block[] previous = null;
            for (int i = 0; i < count; i++) {
                long before = bean.getCurrentThreadAllocatedBytes();
                Block[] result = fixture.helper.getCollisionBlocks();
                bytes += bean.getCurrentThreadAllocatedBytes() - before;
                require(result.length == 1 && result[0].getId() == Block.NETHER_PORTAL,
                        "one real portal must be returned");
                require(result[0].x == 5 && result[0].y == 68 && result[0].z == 5,
                        "native portal coordinates");
                if (previous != null) {
                    require(result != previous && result[0] != previous[0], "fresh array and block");
                    previous[0].x = 99;
                    previous[0] = null;
                }
                previous = result;
            }
            return bytes / (double) count;
        }
        public static void main(String[] args) throws Exception {
            Server server = new ObjenesisStd().newInstance(Server.class);
            CollisionGrowFixture.set(Server.class, null, "instance", server);
            CollisionGrowFixture.set(Server.class, server, "currentThread", Thread.currentThread());
            CollisionGrowFixture.set(Server.class, server, "dataPath", System.getProperty("nukkit.collision.owned.data"));
            ServerConfig config = new ServerConfig();
            config.customBlockSettings().autoDownloadVanillaPalette(false);
            CollisionGrowFixture.set(Server.class, server, "serverConfig", config);
            CollisionGrowFixture fixture = new CollisionGrowFixture(false);
            fixture.entity.box(new SimpleAxisAlignedBB(5, 68, 5, 6, 69, 6));
            fixture.write(5, 68, 5, Block.NETHER_PORTAL);
            ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            require(bean.isThreadAllocatedMemorySupported(), "allocation accounting is required");
            bean.setThreadAllocatedMemoryEnabled(true);
            run(fixture, bean, 100_000);
            double[] windows = new double[7];
            for (int i = 0; i < windows.length; i++) windows[i] = run(fixture, bean, 20_000);
            Arrays.sort(windows);
            System.out.println("PORTAL_NATIVE_ALLOCATION=" + windows[3]);
            require(windows[3] <= 152, "owned native result must avoid the second array: " + windows[3]);
        }
    }
}
