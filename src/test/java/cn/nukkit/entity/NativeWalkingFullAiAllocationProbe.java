package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockStone;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.passive.EntityCow;
import cn.nukkit.entity.passive.EntityWalkingAnimal;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.PlayerRangeMap;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sun.management.ThreadMXBean;
import sun.misc.Unsafe;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Exact EntityCow.onUpdate, including native entityBaseTick, target search,
 * movement and lookAt/scheduleUpdate. No entity or nearby-query overrides.
 * Repeated ordinary age1->2 episodes; excludes periodic love particles/breeding,
 * effects, damage, pathfinding and actual clients. Constructor lifecycle and
 * block materialization are supplied by an Unsafe/prebuilt-world fixture.
 * Overlay/provider-null uses CollisionHelper's fallback, not the LevelDB fast path.
 * Reset and cold-cache invalidation are outside each per-call allocation counter.
 * Caffeine background maintenance allocations are outside this thread's metric.
 */
public final class NativeWalkingFullAiAllocationProbe {
    private static volatile double stateSink;
    private static volatile boolean resultSink;
    private static Unsafe unsafe;
    private static long loveOffset;
    private static final double X = 5.5, Y = 64, Z = 5.5;

    private static final class Overlay extends Level {
        PlayerRangeMap players;
        Chunk stored;
        Block[] blocks;
        long playerReads, blockReads, idReads, chunkReads, missingChunkReads;
        private Overlay() { super(null, "full-ai-probe", "full-ai-probe", null); }
        static int index(int x, int y, int z, int layer) {
            if (x < 0 || x >= 16 || z < 0 || z >= 16 || y < 60 || y >= 76 || layer < 0 || layer > 1)
                throw new AssertionError("world boundary");
            return layer * 4096 + ((y - 60) * 16 + z) * 16 + x;
        }
        @Override public Map<Long, Player> getPlayers() { playerReads++; return players; }
        @Override public Block getBlock(FullChunk hint, int x, int y, int z, int layer, boolean load) {
            blockReads++;
            if (hint != null && hint != stored) throw new AssertionError("block hint");
            Block b = blocks[index(x, y, z, layer)];
            if (b.getId() != stored.getBlockId(x, y, z, layer)) throw new AssertionError("storage mismatch");
            return b;
        }
        @Override public int getBlockIdAt(FullChunk hint, int x, int y, int z, int layer) {
            idReads++;
            if (hint != null && hint != stored) throw new AssertionError("id hint");
            index(x, y, z, layer);
            return stored.getBlockId(x, y, z, layer);
        }
        @Override public BaseFullChunk getChunkIfLoaded(int x, int z) {
            chunkReads++;
            if (x != 0 || z != 0) { missingChunkReads++; return null; }
            return stored;
        }
        @Override public BaseFullChunk getChunk(int x, int z, boolean create) {
            if (x != 0 || z != 0) throw new AssertionError("unexpected loading request");
            return getChunkIfLoaded(x, z);
        }
        @Override public int getMinBlockY() { return 0; }
        @Override public int getMaxBlockY() { return 255; }
    }

    private static final class Fixture {
        final String name;
        final boolean acquire;
        final int candidates;
        final Overlay world;
        final EntityCow mob, mate;
        final SimpleAxisAlignedBB inputBox;
        final Cache<Long, Entity[]> nearbyCache;
        final Method endTick;
        final double radius, height, friction, expectedX, expectedZ, expectedYaw;
        long calls, selected, correctSteps, expectedMissingReads, expectedChunkReads;

        Fixture(boolean acquire, int candidates) throws Exception {
            this.acquire = acquire;
            this.candidates = candidates;
            name = (acquire ? "full-tick-acquire-cold-" : "full-tick-retained-") + candidates;
            Server server = (Server) unsafe.allocateInstance(Server.class);
            server.mobAiEnabled = true;
            server.entityActivationRangeSquared = 6400;
            world = (Overlay) unsafe.allocateInstance(Overlay.class);
            world.gameRules = GameRules.getDefault();
            world.stored = Chunk.getEmptyChunk(0, 0);
            world.blocks = new Block[8192];
            for (int layer = 0; layer < 2; layer++) for (int y = 60; y < 76; y++)
                for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    boolean solid = layer == 0 && y == 63;
                    Block b = solid ? new BlockStone() : new BlockAir();
                    b.x = x; b.y = y; b.z = z; b.level = world; b.layer = layer;
                    world.blocks[Overlay.index(x, y, z, layer)] = b;
                    if (solid) world.stored.setBlockId(x, y, z, Block.STONE);
                }
            nearbyCache = Caffeine.newBuilder().maximumSize(4096)
                    .expireAfterWrite(700, TimeUnit.MILLISECONDS).build();
            Cache<Long, Boolean> dirty = Caffeine.newBuilder().maximumSize(512)
                    .expireAfterWrite(1, TimeUnit.SECONDS).build();
            setField(Level.class, world, "nearbyEntitiesCache", nearbyCache);
            setField(Level.class, world, "entityNearbyCacheDirty", dirty);
            setField(Level.class, world, "updateEntities", new Long2ObjectNonBlockingMap<Entity>());
            world.players = new PlayerRangeMap();
            Player observer = (Player) unsafe.allocateInstance(Player.class);
            observer.x = 8; observer.y = 64; observer.z = 8;
            world.players.put(1000L, observer);
            Method begin = PlayerRangeMap.class.getDeclaredMethod("beginTick");
            begin.setAccessible(true);
            endTick = PlayerRangeMap.class.getDeclaredMethod("endTick");
            endTick.setAccessible(true);
            begin.invoke(world.players);
            check(world.players.tickSnapshot().length == 1, "active-range player snapshot");
            mob = cow(server, 1, X, Z, true);
            mate = cow(server, 2, 9.5, 7.5, true);
            for (int i = 1; i < candidates; i++) cow(server, i + 2, 9.5, 7.5, false);
            check(world.stored.getEntities().size() == candidates + 1, "candidate population");
            mob.setLookupForTarget(true);
            radius = mob.getWidth() / 2d;
            height = mob.getHeight();
            inputBox = (SimpleAxisAlignedBB) mob.boundingBox;
            friction = (1 - mob.getDrag()) * new BlockStone().getFrictionFactor();
            expectedX = X + 0.15 * (4d / 6d) * friction;
            expectedZ = Z + 0.15 * (2d / 6d) * friction;
            double lookX = expectedX - mate.x, lookZ = expectedZ - mate.z;
            expectedYaw = Math.asin(lookX / Math.sqrt(lookX * lookX + lookZ * lookZ)) / Math.PI * 180d;
            check(mob.getClass() == EntityCow.class, "exact native entity");
            check(mob.getClass().getMethod("onUpdate", int.class).getDeclaringClass() == EntityWalkingAnimal.class,
                    "native full onUpdate");
            check(mob.getClass().getMethod("entityBaseTick", int.class).getDeclaringClass() == BaseEntity.class,
                    "native base tick chain");
            check(world.getClass().getMethod("getNearbyEntities", cn.nukkit.math.AxisAlignedBB.class,
                    Entity.class, boolean.class, boolean.class).getDeclaringClass() == Level.class,
                    "native target spatial query");
            world.updateEntities.put(mob.getId(), mob);
            reset();
        }

        EntityCow cow(Server server, long id, double x, double z, boolean love) throws Exception {
            EntityCow cow = (EntityCow) unsafe.allocateInstance(EntityCow.class);
            cow.id = id; cow.server = server; cow.level = world; cow.chunk = world.stored;
            cow.health = 10; cow.maxHealth = 10; cow.x = x; cow.y = Y; cow.z = z;
            cow.age = 1; cow.temporalVector = new Vector3(); cow.setAirTicks(400);
            cow.boundingBox = new SimpleAxisAlignedBB(x - cow.getWidth() / 2d, Y, z - cow.getWidth() / 2d,
                    x + cow.getWidth() / 2d, Y + cow.getHeight(), z + cow.getWidth() / 2d);
            setField(Entity.class, cow, "hasSpawned", new ConcurrentHashMap<Integer, Player>());
            setField(Entity.class, cow, "effects", new ConcurrentHashMap<>());
            setField(Entity.class, cow, "passengers", new ArrayList<Entity>());
            setField(Entity.class, cow, "dataProperties", new EntityMetadata().putLong(Entity.DATA_FLAGS, 0)
                    .putByte(Entity.DATA_COLOR, 0).putShort(Entity.DATA_AIR, 400).putShort(Entity.DATA_MAX_AIR, 400)
                    .putString(Entity.DATA_NAMETAG, "").putLong(Entity.DATA_LEAD_HOLDER_EID, -1).putFloat(Entity.DATA_SCALE, 1f));
            setField(BaseEntity.class, cow, "leadHolder", -1L);
            setField(Entity.class, cow, "published", true);
            setField(Entity.class, cow, "scale", 1f);
            unsafe.putShort(cow, loveOffset, (short) (love ? 100 : 0));
            world.stored.addEntity(cow);
            return cow;
        }

        void reset() {
            mob.x = mob.lastX = X; mob.y = mob.lastY = Y; mob.z = mob.lastZ = Z;
            mob.yaw = mob.lastYaw = -90; mob.headYaw = mob.lastHeadYaw = -90;
            mob.pitch = mob.lastPitch = 0;
            mob.motionX = mob.lastMotionX = 0.4;
            mob.motionY = mob.lastMotionY = -mob.getGravity();
            mob.motionZ = mob.lastMotionZ = 0.2;
            mob.age = 1; mob.ticksLived = 1; mob.lastUpdate = 1;
            mob.onGround = true;
            mob.isCollided = mob.isCollidedHorizontally = mob.isCollidedVertically = false;
            mob.positionChanged = false; mob.moveTime = 37; mob.stayTime = 0;
            mob.moveMultiplier = 1; mob.highestPosition = Y; mob.fallDistance = 0;
            mob.blocksAround = null; mob.collisionBlocks = null;
            mob.followTarget = acquire ? null : mate;
            mob.target = acquire ? null : mate;
            unsafe.putShort(mob, loveOffset, (short) 100);
            inputBox.setBounds(X - radius, Y, Z - radius, X + radius, Y + height, Z + radius);
            nearbyCache.invalidate(mob.getId());
        }

        void verify() {
            check(calls == selected && calls == correctSteps, "every episode selected target and preserved tick state");
            check(mob.followTarget == mate && mob.target == mate, "selected actual mate");
            check(unsafe.getShort(mob, loveOffset) == 99 && mob.age == 2 && mob.ticksLived == 2 && mob.lastUpdate == 2,
                    "full native base tick advanced");
            check(world.updateEntities.get(mob.getId()) == mob, "native update scheduling remains registered");
            check(world.missingChunkReads == calls * expectedMissingReads
                    && world.chunkReads == calls * expectedChunkReads, "native query counts stable");
            check(acquire ? expectedMissingReads > 0 : expectedMissingReads == 0, "cold spatial traversal witness");
            if (acquire) {
                Entity[] actual = nearbyCache.getIfPresent(mob.getId());
                check(actual != null && actual.length == candidates, "native nearby result size");
            }
        }
        void close() throws Exception { endTick.invoke(world.players); }
    }

    private static void setField(Class<?> type, Object object, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static long run(Fixture f, int iterations, boolean counterOnly, ThreadMXBean mx) {
        long bytes = 0;
        for (int i = 0; i < iterations; i++) {
            f.reset();
            long start = mx.getCurrentThreadAllocatedBytes();
            boolean updated = counterOnly ? f.mob.isAlive() : f.mob.onUpdate(2);
            bytes += mx.getCurrentThreadAllocatedBytes() - start;
            resultSink = updated;
            if (!counterOnly) {
                f.calls++;
                if (f.mob.followTarget == f.mate && f.mob.target == f.mate) f.selected++;
                if (updated && f.mob.x == f.expectedX && f.mob.y == Y && f.mob.z == f.expectedZ
                        && f.mob.age == 2 && f.mob.ticksLived == 2 && f.mob.lastUpdate == 2
                        && unsafe.getShort(f.mob, loveOffset) == 99 && f.mob.health == 10 && !f.mob.closed
                        && f.mob.onGround && f.mob.isCollidedVertically && !f.mob.isCollidedHorizontally
                        && f.mob.boundingBox == f.inputBox && f.mob.motionY == 0
                        && f.mob.yaw == f.expectedYaw && f.mob.headYaw == f.expectedYaw
                        && f.mob.pitch == 0 && f.mob.lastYaw != f.mob.yaw
                        && f.mob.moveTime == (f.acquire ? 0 : 36)) f.correctSteps++;
            }
            stateSink = f.mob.x + f.mob.z + f.mob.yaw + f.mob.headYaw;
        }
        return bytes;
    }

    public static void main(String[] args) throws Exception {
        String label = args.length > 0 ? args[0] : "baseline";
        int warmup = args.length > 1 ? Integer.parseInt(args[1]) : 50000;
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 20000;
        int rounds = args.length > 3 ? Integer.parseInt(args[3]) : 7;
        double retainedBudget = args.length > 4 ? Double.parseDouble(args[4]) : Double.POSITIVE_INFINITY;
        check(warmup > 0 && iterations > 0 && rounds > 0 && rounds % 2 == 1, "counts");
        check(retainedBudget >= 0, "retained allocation budget");
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
        loveOffset = unsafe.objectFieldOffset(BaseEntity.class.getDeclaredField("inLoveTicks"));
        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        check(mx.isThreadAllocatedMemorySupported(), "allocation accounting");
        mx.setThreadAllocatedMemoryEnabled(true);
        Fixture[] fixtures = {new Fixture(false, 1), new Fixture(true, 1), new Fixture(true, 32), new Fixture(true, 128)};
        try {
            System.out.printf(Locale.ROOT, "runtime,%s,%s,%s%n", System.getProperty("java.version"),
                    System.getProperty("os.arch"), EntityWalking.class.getProtectionDomain().getCodeSource().getLocation());
            System.out.printf(Locale.ROOT, "animal-class,%s%n",
                    EntityWalkingAnimal.class.getProtectionDomain().getCodeSource().getLocation());
            for (Fixture f : fixtures) {
                // Independent scheduleUpdate witness, only during initial smoke.
                // Measured episodes keep the usual already-registered entity.
                f.world.updateEntities.remove(f.mob.getId());
                run(f, 1, false, mx);
                f.expectedMissingReads = f.world.missingChunkReads;
                f.expectedChunkReads = f.world.chunkReads;
                f.verify();
                System.out.printf(Locale.ROOT, "smoke,%s,x=%.17g,z=%.17g,age=%d,love=%d,chunkReads=%d,missingReads=%d%n",
                        f.name, f.mob.x, f.mob.z, f.mob.age, unsafe.getShort(f.mob, loveOffset), f.expectedChunkReads, f.expectedMissingReads);
                for (boolean counterOnly : new boolean[]{true, false}) {
                    run(f, warmup, counterOnly, mx); run(f, warmup, counterOnly, mx);
                    double[] bytes = new double[rounds];
                    for (int round = 0; round < rounds; round++) {
                        bytes[round] = run(f, iterations, counterOnly, mx) / (double) iterations;
                        if (!counterOnly) f.verify();
                        System.out.printf(Locale.ROOT, "%s,%s%s,%d,%.6f%n", label, f.name,
                                counterOnly ? "-counter-only" : "", round, bytes[round]);
                    }
                    double[] sorted = bytes.clone(); Arrays.sort(sorted);
                    System.out.printf(Locale.ROOT, "summary,%s%s,median=%.6f,calls=%d,selected=%d,valid=%d%n",
                            f.name, counterOnly ? "-counter-only" : "", sorted[rounds / 2], f.calls, f.selected, f.correctSteps);
                    if (!counterOnly && !f.acquire) {
                        check(sorted[rounds / 2] <= retainedBudget,
                                "retained native allocation " + sorted[rounds / 2] + " exceeds " + retainedBudget);
                    }
                }
            }
        } finally { for (Fixture f : fixtures) f.close(); }
    }
}
