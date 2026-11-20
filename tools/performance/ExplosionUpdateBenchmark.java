package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.event.block.BlockUpdateEvent;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.WeakExplosion;
import cn.nukkit.level.StrongExplosion;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.Sound;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.level.vibration.VibrationEvent;
import cn.nukkit.level.vibration.VibrationListener;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import com.sun.management.ThreadMXBean;
import org.objenesis.ObjenesisStd;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Complete explodeB with concrete world/event callbacks; no server TPS claim. */
public final class ExplosionUpdateBenchmark {
    private enum Route { ORDINARY, WEAK, STRONG }
    private static final Entity[] NO_ENTITIES = new Entity[0];
    private static volatile Object escaped;
    private static volatile long checksum;

    public static void main(String[] args) {
        // Registry bootstrap only. No Mockito mock participates in timed callbacks.
        MockServer.init();
        boolean checkOnly = args.length == 1 && args[0].equals("--check");
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        System.out.println("route,affectedBlocks,iterations,ns/op,bytes/op,checksum");
        for (Route route : Route.values()) {
        for (int count : new int[]{0, 64, 512, 2048}) {
            Fixture fixture = fixture(route, count);
            int warmups = checkOnly ? 0 : count < 512 ? 200 : count == 512 ? 40 : 10;
            int iterations = checkOnly ? 1 : count == 0 ? 10_000 : count < 512 ? 100 : count == 512 ? 20 : 6;
            for (int i = 0; i < warmups; i++) {
                if (!fixture.explosion.explodeB()) throw new AssertionError("explosion refused");
            }
            for (int sample = 0; sample < (checkOnly ? 1 : 7); sample++) {
                fixture.world.reset();
                long beforeBytes = bean.getThreadAllocatedBytes(thread);
                long beforeNanos = System.nanoTime();
                for (int i = 0; i < iterations; i++) {
                    if (!fixture.explosion.explodeB()) throw new AssertionError("explosion refused");
                }
                long nanos = System.nanoTime() - beforeNanos;
                long bytes = bean.getThreadAllocatedBytes(thread) - beforeBytes;
                fixture.world.verify(route, count, iterations);
                System.out.printf(Locale.ROOT, "%s,%d,%d,%.1f,%.1f,%d%n",
                        route, count, iterations, (double) nanos / iterations,
                        (double) bytes / iterations, checksum);
            }
        }
        }
        if (escaped == null || checksum == 0) throw new AssertionError("unconsumed effects");
    }

    private static Fixture fixture(Route route, int count) {
        ObjenesisStd allocator = new ObjenesisStd();
        FixtureWorld world = allocator.newInstance(FixtureWorld.class);
        FixtureServer server = allocator.newInstance(FixtureServer.class);
        FixtureEvents events = new FixtureEvents(world);
        world.fixtureServer = server;
        server.events = events;
        world.vibrations = new FixtureVibrations(world);
        events.affected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.affected.add(Block.get(Block.OBSIDIAN, 0, world, i * 3, 64, 0));
        }
        Position center = new Position(0, 64, 0, world);
        Explosion explosion = switch (route) {
            case ORDINARY -> new Explosion(center, 4, (Entity) null);
            case WEAK -> new WeakExplosion(center, 4, null);
            case STRONG -> new StrongExplosion(center, 4, null);
        };
        return new Fixture(world, explosion);
    }

    private record Fixture(FixtureWorld world, Explosion explosion) {}

    private static final class FixtureServer extends Server {
        private FixtureEvents events;
        private FixtureServer() { super(null, null, null, false, false); }
        @Override public PluginManager getPluginManager() { return events; }
    }

    private static final class FixtureWorld extends Level {
        private FixtureServer fixtureServer;
        private FixtureVibrations vibrations;
        private long clears, updates, effects, antiXray;
        private FixtureWorld() { super(null, "benchmark", "", null); }
        @Override public Server getServer() { return fixtureServer; }
        @Override public Entity[] getNearbyEntities(AxisAlignedBB box, Entity except) { return NO_ENTITIES; }
        @Override public BlockEntity getBlockEntity(Vector3 at) { return null; }
        @Override public Block getBlock(int x, int y, int z, int layer) {
            return Block.get(Block.OBSIDIAN, 0, this, x, y, z, layer);
        }
        @Override public Block getBlock(Vector3 at) {
            return Block.get(Block.AIR, 0, this, at.getFloorX(), at.getFloorY(), at.getFloorZ());
        }
        @Override public Block getBlock(Vector3 at, int layer) {
            return Block.get(Block.AIR, 0, this, at.getFloorX(), at.getFloorY(), at.getFloorZ(), layer);
        }
        @Override public boolean setBlock(Vector3 at, int layer, Block block) {
            clears++;
            checksum += at.getFloorX() + at.getFloorY() + layer + block.getId();
            return true;
        }
        @Override public void setBlockAt(int x, int y, int z, int id) {
            clears++;
            checksum += x + y + id;
        }
        @Override public void antiXrayOnBlockChange(Player player, Vector3 at, int type) {
            antiXray++;
        }
        @Override public void addLevelSoundEvent(Vector3 at, int type) { consume(type); }
        @Override public void addParticle(Particle particle) { consume(particle); }
        @Override public void addSound(Vector3 at, Sound sound) { consume(sound); }
        @Override public VibrationManager getVibrationManager() { return vibrations; }
        private void consume(Object effect) { effects++; escaped = effect; checksum++; }
        private void reset() { clears = updates = effects = antiXray = 0; }
        private void verify(Route route, int count, int iterations) {
            if (clears != (long) count * iterations || updates != (long) count * 6 * iterations
                    || effects != (long) iterations * (route == Route.ORDINARY ? 3 : 2)
                    || antiXray != (route == Route.ORDINARY ? 0 : (long) count * 6 * iterations)) {
                throw new AssertionError("fixture effects differ: " + clears + "/" + updates + "/" + effects);
            }
        }
    }

    private static final class FixtureEvents extends PluginManager {
        private final FixtureWorld world;
        private List<Block> affected;
        private FixtureEvents(FixtureWorld world) { super(null, null); this.world = world; }
        @Override public void callEvent(Event event) {
            if (event instanceof EntityExplodeEvent explosion) {
                explosion.setBlockList(new ArrayList<>(affected));
                explosion.setYield(0);
            } else if (event instanceof BlockUpdateEvent update) {
                world.updates++;
                checksum += update.getBlock().getFloorX() + update.getBlock().getFloorY() + 1;
            } else {
                throw new AssertionError("unexpected fixture event: " + event.getClass());
            }
        }
    }

    private static final class FixtureVibrations implements VibrationManager {
        private final FixtureWorld world;
        private FixtureVibrations(FixtureWorld world) { this.world = world; }
        @Override public void callVibrationEvent(VibrationEvent event) { world.consume(event); }
        @Override public void addListener(VibrationListener listener) {}
        @Override public void removeListener(VibrationListener listener) {}
    }
}
