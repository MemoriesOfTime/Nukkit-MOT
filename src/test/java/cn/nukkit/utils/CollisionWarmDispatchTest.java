package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.utils.serverconfig.ServerConfig;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objenesis.ObjenesisStd;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CollisionWarmDispatchTest {
    @TempDir Path temporary;

    @Test
    void movingNativeBoxesAndMotionKeepUncachedCollisionResults() throws Exception {
        CollisionGrowFixture fixture = new CollisionGrowFixture(false);
        CollisionHelper uncached = new CollisionHelper(fixture.entity, null);
        for (int x = 3; x <= 9; x++) {
            fixture.write(x, 64, 4, Block.FENCE_GATE);
            fixture.write(x, 64, 5, switch (x % 4) {
                case 0 -> Block.STONE;
                case 1 -> Block.FIRE;
                case 2 -> Block.NETHER_PORTAL;
                default -> Block.WATER;
            });
        }
        int comparisons = 0;
        for (double y : new double[]{63.9, 64, 64.25, 65.1}) {
            for (double x : new double[]{4, 4.25, 5, 5.75, 6, 7.25}) {
                for (double motionX : new double[]{0, .1, .8}) {
                    for (double motionY : new double[]{-.8, 0, .8}) {
                        fixture.entity.box(new SimpleAxisAlignedBB(x, y, 4.8, x + 1, y + 1.6, 5.8));
                        fixture.entity.motionX = motionX;
                        fixture.entity.motionY = motionY;
                        for (int repeat = 0; repeat < 2; repeat++) {
                            assertArrayEquals(signature(uncached.getCollisionBlocks()),
                                    signature(fixture.helper.getCollisionBlocks()));
                            comparisons++;
                        }
                    }
                }
            }
        }
        assertEquals(432, comparisons);
    }

    @Test
    void movedNativeBoxStillCapturesScanBeforeLiveContextMutation() throws Exception {
        CollisionGrowFixture cachedFixture = new CollisionGrowFixture(false, CollisionGrowTest.RecordingEntity.class);
        CollisionGrowFixture uncachedFixture = new CollisionGrowFixture(false, CollisionGrowTest.RecordingEntity.class);
        CollisionHelper uncached = new CollisionHelper(uncachedFixture.entity, null);
        for (CollisionGrowFixture fixture : new CollisionGrowFixture[]{cachedFixture, uncachedFixture}) {
            fixture.write(5, 64, 5, Block.STONE);
            fixture.write(8, 64, 5, Block.STONE);
            fixture.helper.getCollisionBlocks();
            CollisionGrowTest.RecordingEntity entity = (CollisionGrowTest.RecordingEntity) fixture.entity;
            entity.box(new SimpleAxisAlignedBB(5.25, 64, 5, 6.25, 65.6, 6));
            entity.reset();
            entity.onSecondLevel = () -> entity.moveBox(3);
        }
        assertArrayEquals(signature(uncached.getCollisionBlocks()), signature(cachedFixture.helper.getCollisionBlocks()));
        assertEquals(((CollisionGrowTest.RecordingEntity) uncachedFixture.entity).events,
                ((CollisionGrowTest.RecordingEntity) cachedFixture.entity).events);
    }

    @Test
    void outsideWorldNativeBoxRetainsLiveValidationWhenReturning() throws Exception {
        CollisionGrowFixture fixture = new CollisionGrowFixture(false);
        fixture.write(5, 64, 5, Block.NETHER_PORTAL);
        assertEquals(Block.NETHER_PORTAL, fixture.helper.getCollisionBlocks()[0].getId());
        fixture.entity.box(new SimpleAxisAlignedBB(5, 257, 5, 6, 258.6, 6));
        assertSame(Block.EMPTY_ARRAY, fixture.helper.getCollisionBlocks());
        fixture.write(5, 64, 5, Block.STONE);
        fixture.entity.box(new SimpleAxisAlignedBB(5, 64, 5, 6, 65.6, 6));
        assertEquals(Block.STONE, fixture.helper.getCollisionBlocks()[0].getId());
    }

    @Test
    void warmEmptyNativeQueryKeepsZeroAllocationWithoutMockitoInstrumentation() throws Exception {
        Path output = temporary.resolve("warm-dispatch.txt");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-Xms128m", "-Xmx512m", "-Dcollision.dispatch.data=" + temporary,
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                BenchmarkChild.class.getName(), "verify").redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "native collision benchmark child timeout");
            String text = Files.readString(output);
            assertEquals(0, process.exitValue(), text);
            assertTrue(text.contains("COLLISION_DISPATCH warm-empty"), text);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "native collision child was not reaped");
            }
        }
    }

    private static String[] signature(Block[] blocks) {
        return Arrays.stream(blocks).map(block -> block.getClass().getName() + ":" + block.getId() + ":"
                + block.getDamage() + ":" + block.x + ":" + block.y + ":" + block.z + ":" + block.layer).toArray(String[]::new);
    }

    /** Run with the baseline class directory first on the classpath for reproducible paired measurements. */
    public static final class BenchmarkChild {
        private static volatile Block[] sink;

        public static void main(String[] args) throws Exception {
            Server server = new ObjenesisStd().newInstance(Server.class);
            CollisionGrowFixture.set(Server.class, null, "instance", server);
            CollisionGrowFixture.set(Server.class, server, "currentThread", Thread.currentThread());
            CollisionGrowFixture.set(Server.class, server, "dataPath", System.getProperty("collision.dispatch.data"));
            ServerConfig config = new ServerConfig();
            config.customBlockSettings().autoDownloadVanillaPalette(false);
            CollisionGrowFixture.set(Server.class, server, "serverConfig", config);
            CollisionGrowFixture fixture = new CollisionGrowFixture(false);
            ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            if (!bean.isThreadAllocatedMemorySupported()) throw new AssertionError("allocation accounting required");
            bean.setThreadAllocatedMemoryEnabled(true);
            boolean verifyOnly = args.length > 0 && args[0].equals("verify");
            measure("warm-empty", fixture, bean, false, verifyOnly);
            if (verifyOnly) {
                measure("above-world", fixture, bean, false, true);
                return;
            }
            fixture.write(5, 64, 5, Block.STONE);
            measure("warm-solid", fixture, bean, false, false);
            fixture.write(5, 64, 5, Block.NETHER_PORTAL);
            measure("warm-portal", fixture, bean, false, false);
            measure("moving-portal", fixture, bean, true, false);
            measure("above-world", fixture, bean, false, false);
        }

        private static void measure(String name, CollisionGrowFixture fixture, ThreadMXBean bean,
                                    boolean moving, boolean verify) {
            SimpleAxisAlignedBB box = new SimpleAxisAlignedBB(5, 64, 5, 6, 65.6, 6);
            if (name.equals("above-world")) box.setBounds(5, 257, 5, 6, 258.6, 6);
            fixture.entity.box(box);
            for (int i = 0; i < 200_000; i++) query(fixture, box, moving, i);
            double[] nanos = new double[9];
            double[] bytes = new double[9];
            int iterations = verify ? 20_000 : 300_000;
            for (int window = 0; window < nanos.length; window++) {
                long allocated = bean.getCurrentThreadAllocatedBytes();
                long start = System.nanoTime();
                for (int i = 0; i < iterations; i++) query(fixture, box, moving, i);
                nanos[window] = (System.nanoTime() - start) / (double) iterations;
                bytes[window] = (bean.getCurrentThreadAllocatedBytes() - allocated) / (double) iterations;
            }
            Arrays.sort(nanos);
            Arrays.sort(bytes);
            System.out.printf(java.util.Locale.ROOT, "COLLISION_DISPATCH %s ns/op=%.3f bytes/op=%.3f%n",
                    name, nanos[4], bytes[4]);
            if (verify && bytes[4] > .1) throw new AssertionError("warm empty query allocated " + bytes[4]);
        }

        private static void query(CollisionGrowFixture fixture, SimpleAxisAlignedBB box, boolean moving, int i) {
            if (moving) {
                double offset = (i & 1) == 0 ? 0 : .125;
                box.setBounds(5 + offset, 64, 5, 6 + offset, 65.6, 6);
            }
            sink = fixture.helper.getCollisionBlocks();
        }
    }
}
