package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 观察者同步行为：{@code despawnFromAll} 的快照遍历语义与 {@code checkChunks} 的跨区块迁移语义。
 * <p>
 * 只断言对外可观察的结果——哪些观察者收到了 despawn/spawn、实体最终注册在哪个区块、异常是否传播。
 * 不断言内部调用的顺序或次数：合并一次多余的 {@code getChunkPlayers} 或调整回调顺序不应让本测试变红。
 * <p>
 * Behavioural contract for viewer synchronisation. Assertions describe which viewers were notified
 * and where the entity ended up, never the internal call sequence.
 */
class EntityViewerSyncTest {

    // ---------------------------------------------------------------- despawnFromAll

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 8})
    void despawnNotifiesEveryRegisteredViewerExactlyOnce(int viewerCount) {
        Fixture f = new Fixture(viewerCount);

        f.actor.despawnFromAll();

        assertEquals(viewerCount, f.actor.despawns);
        assertEquals(viewerCount, f.events("despawn:").size(), "每个观察者必须恰好收到一次 despawn");
        assertEquals(expectedViewerNumbers(viewerCount), f.events("despawn:"));
    }

    @Test
    void despawnKeepsVisitingSnapshotMembersWhenCallbackClearsRegistry() {
        Fixture f = new Fixture(3);
        f.actor.onDespawn = ignored -> f.actor.hasSpawned.clear();

        assertDoesNotThrow(f.actor::despawnFromAll, "回调修改注册表不得导致并发修改异常");

        assertEquals(3, f.actor.despawns, "快照内的成员都必须被访问，即使注册表已被清空");
    }

    @Test
    void despawnDoesNotVisitViewersAddedDuringCallback() {
        Fixture f = new Fixture(2);
        Viewer latecomer = f.viewer(3);
        f.actor.onDespawn = ignored -> f.actor.hasSpawned.put(3, latecomer);

        f.actor.despawnFromAll();

        assertEquals(Set.of("despawn:1", "despawn:2"), f.events("despawn:"));
        assertSame(latecomer, f.actor.hasSpawned.get(3), "本轮新增的观察者必须留在注册表中");
    }

    @Test
    void nestedDespawnDoesNotInterruptOuterTraversal() {
        Fixture f = new Fixture(2);
        Viewer first = f.viewers[0];
        Viewer latecomer = f.viewer(3);
        f.actor.onDespawn = player -> {
            if (player == first) {
                f.actor.onDespawn = null;
                f.actor.hasSpawned.clear();
                f.actor.hasSpawned.put(3, latecomer);
                f.actor.despawnFromAll();
            }
        };

        f.actor.despawnFromAll();

        assertEquals(Set.of("despawn:1", "despawn:2", "despawn:3"), f.events("despawn:"),
                "外层快照的 1、2 与内层快照的 3 都应被访问");
        assertEquals(3, f.actor.despawns);
    }

    @Test
    void despawnPropagatesCallbackFailureAndStopsVisitingLaterViewers() {
        Fixture f = new Fixture(3);
        RuntimeException failure = new IllegalStateException("despawn callback");
        f.actor.onDespawn = ignored -> {
            throw failure;
        };

        assertSame(failure, assertThrows(RuntimeException.class, f.actor::despawnFromAll));
        assertEquals(1, f.actor.despawns, "第一个回调抛出后不得继续访问后续观察者");
    }

    // ---------------------------------------------------------------- checkChunks

    @Test
    void unchangedChunkDoesNotTouchRegistryOrViewers() {
        Fixture f = new Fixture(2);
        f.actor.x = 2;                       // 仍在区块 0

        f.actor.crossChunks();

        assertTrue(f.recorded.isEmpty(), "区块未变化时不应有任何注册或通知动作");
        assertSame(f.chunks[0], f.actor.chunk);
        assertSame(f.actor, f.chunks[0].registered);
    }

    @Test
    void crossingReRegistersEntityAndResynchronisesViewers() {
        Fixture f = new Fixture(2);                       // 当前观察者 1、2
        Viewer shared = f.viewers[1];                     // 2 在新区块仍然可见
        Viewer newlyVisible = f.viewer(3);
        f.destinationViewers.put(2, shared);
        f.destinationViewers.put(3, newlyVisible);

        f.actor.x = 17;                                   // 进入区块 1
        f.actor.crossChunks();

        assertEquals(Set.of("despawn:1"), f.events("despawn:"), "新区块看不到的观察者必须被 despawn");
        assertEquals(Set.of("spawn:3"), f.events("spawn:"), "只有新出现的观察者才需要 spawn");
        assertSame(f.chunks[1], f.actor.chunk);
        assertSame(f.actor, f.chunks[1].registered, "实体必须注册到新区块");
        assertNull(f.chunks[0].registered, "旧区块必须解除注册");
    }

    @Test
    void crossingWhileJustCreatedSkipsViewerSyncButRegistersNewChunk() {
        Fixture f = new Fixture(2);
        f.actor.justCreated = true;

        f.actor.x = 17;
        f.actor.crossChunks();

        assertTrue(f.events("despawn:").isEmpty(), "新建实体不做观察者同步");
        assertTrue(f.events("spawn:").isEmpty());
        assertSame(f.chunks[1], f.actor.chunk);
        assertSame(f.actor, f.chunks[1].registered);
    }

    @Test
    void crossingWithoutCurrentChunkStillResynchronisesViewers() {
        Fixture f = new Fixture(2);
        f.actor.chunk = null;                              // 实体尚未注册到任何区块
        f.chunks[0].registered = null;
        f.destinationViewers.put(2, f.viewers[1]);         // 新区块仍能看到观察者 2

        f.actor.x = 17;
        f.actor.crossChunks();

        assertEquals(Set.of("despawn:1"), f.events("despawn:"), "新区块看不到的观察者仍必须被 despawn");
        assertTrue(f.events("spawn:").isEmpty(), "新区块已包含的观察者不应重复 spawn");
        assertSame(f.chunks[1], f.actor.chunk);
        assertSame(f.actor, f.chunks[1].registered);
    }

    @Test
    void despawnFailureDuringCrossingStopsLaterCallbacksAndDestinationRegistration() {
        Fixture f = new Fixture(2);
        RuntimeException failure = new IllegalStateException("crossing callback");
        f.actor.onDespawn = ignored -> {
            throw failure;
        };
        f.actor.x = 17;

        assertSame(failure, assertThrows(RuntimeException.class, f.actor::crossChunks));
        assertEquals(1, f.actor.despawns, "第一个 despawn 回调抛出后不得继续访问后续观察者");
        assertNull(f.chunks[0].registered, "旧区块在迁移开始时已解除注册");
        assertNull(f.chunks[1].registered, "异常中断后不得注册到新区块");
        assertTrue(f.events("spawn:").isEmpty());
    }

    @Test
    void unavailableDestinationLeavesEntityDetachedWithoutRegistering() {
        Fixture f = new Fixture(2);
        f.destinationViewers.put(1, f.viewers[0]);
        f.destinationViewers.put(2, f.viewers[1]);
        f.world.destinationUnavailable = true;             // getChunk 返回 null

        f.actor.x = 17;
        f.actor.crossChunks();

        assertNull(f.actor.chunk, "目标区块不可用时实体应保持游离状态");
        assertNull(f.chunks[0].registered);
        assertNull(f.chunks[1].registered);
        assertTrue(f.events("spawn:").isEmpty());
    }

    @Test
    void viewerLookupFailurePropagatesAndLeavesBothChunksUnregistered() {
        Fixture f = new Fixture(2);
        f.world.viewerLookupFailure = true;

        f.actor.x = 17;

        IllegalStateException thrown = assertThrows(IllegalStateException.class, f.actor::crossChunks);
        assertEquals("viewers unavailable", thrown.getMessage());
        assertNull(f.chunks[0].registered, "旧区块在迁移开始时已解除注册");
        assertNull(f.chunks[1].registered, "异常中断后不得留下注册痕迹");
        assertTrue(f.events("spawn:").isEmpty());
    }

    // ---------------------------------------------------------------- fixture

    private static Set<String> expectedViewerNumbers(int count) {
        Set<String> expected = new HashSet<>();
        for (int i = 1; i <= count; i++) {
            expected.add("despawn:" + i);
        }
        return expected;
    }

    /** 包级可见以便 {@link EntityViewerSnapshotBenchmark} 复用同一 fixture。 */
    static final class Fixture {
        final List<String> recorded = new ArrayList<>();
        final Actor actor;
        final World world;
        final Chunk[] chunks = new Chunk[3];
        final Viewer[] viewers;
        final Map<Integer, Player> destinationViewers = new HashMap<>();
        /** 基准复用时关闭记录，避免无界增长。 */
        boolean recording = true;
        /** 生产 despawnFrom 会移除注册；基准需要重复调用，显式保留。 */
        boolean keepRegistered;

        Fixture(int viewerCount) {
            actor = allocate(Actor.class);
            actor.fixture = this;
            attachHasSpawned(actor);
            actor.boundingBox = new SimpleAxisAlignedBB(0, 64, 0, 1, 65, 1);
            actor.scale = 1;
            actor.x = 1;
            actor.y = 64;
            actor.z = 1;

            for (int i = 0; i < chunks.length; i++) {
                chunks[i] = allocate(Chunk.class);
                chunks[i].coordinate = i;
                chunks[i].fixture = this;
                chunks[i].setPosition(i, 0);
            }
            actor.chunk = chunks[0];
            chunks[0].registered = actor;

            world = allocate(World.class);
            world.chunks = chunks;
            world.viewers = destinationViewers;
            actor.level = world;

            viewers = new Viewer[viewerCount];
            for (int i = 0; i < viewerCount; i++) {
                viewers[i] = viewer(i + 1);
                actor.hasSpawned.put(i + 1, viewers[i]);
            }
        }

        /**
         * 仅在真正记录时拼接字符串。基准会关闭 {@code recording}，若先拼接再判断，
         * 每次调用仍会分配 StringBuilder/String，测量结果会被无关分配污染。
         */
        void record(String prefix, int number) {
            if (recording) {
                recorded.add(prefix + number);
            }
        }

        Viewer viewer(int number) {
            Viewer player = allocate(Viewer.class);
            player.number = number;
            return player;
        }

        Set<String> events(String prefix) {
            return recorded.stream().filter(e -> e.startsWith(prefix)).collect(Collectors.toSet());
        }
    }

    /**
     * 反射只用于把 {@code Entity.hasSpawned} 这个 <em>public final</em> 字段挂到无构造器实例上：
     * Objenesis 跳过字段初始化，而该字段在 {@code Entity} 中是 final，子类无法覆盖。
     * <p>
     * Reflection here only attaches the <em>public final</em> {@code hasSpawned} field to an
     * instance created without a constructor; no private state is inspected.
     */
    private static void attachHasSpawned(Entity entity) {
        try {
            Field field = Entity.class.getField("hasSpawned");
            field.setAccessible(true);
            field.set(entity, new ConcurrentHashMap<Integer, Player>());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static <T> T allocate(Class<T> type) {
        return new ObjenesisStd().newInstance(type);
    }

    static final class Actor extends Entity {
        Fixture fixture;
        int despawns;
        int spawns;
        Consumer<Player> onDespawn;

        Actor() {
            super((FullChunk) null, new CompoundTag());
        }

        @Override
        public int getNetworkId() {
            return -1;
        }

        @Override
        public void despawnFrom(Player player) {
            despawns++;
            fixture.record("despawn:", ((Viewer) player).number);
            if (!fixture.keepRegistered) {
                // 与生产实现一致：despawnFrom 成功后从 hasSpawned 移除。
                fixture.actor.hasSpawned.remove(((Viewer) player).number);
            }
            if (onDespawn != null) {
                onDespawn.accept(player);
            }
        }

        @Override
        public void spawnTo(Player player) {
            spawns++;
            fixture.record("spawn:", ((Viewer) player).number);
        }

        void crossChunks() {
            super.checkChunks();
        }
    }

    static final class Viewer extends Player {
        int number;

        Viewer() {
            super(null, 0L, null);
        }

        @Override
        public int getLoaderId() {
            return number;
        }
    }

    static final class Chunk extends LevelDBChunk {
        int coordinate;
        Fixture fixture;
        Entity registered;

        Chunk() {
            super(null, 0, 0);
        }

        @Override
        public void addEntity(Entity entity) {
            fixture.record("addEntity:", coordinate);
            registered = entity;
        }

        @Override
        public void removeEntity(Entity entity) {
            fixture.record("removeEntity:", coordinate);
            registered = null;
        }
    }

    static final class World extends Level {
        Chunk[] chunks;
        Map<Integer, Player> viewers;
        boolean destinationUnavailable;
        boolean viewerLookupFailure;

        World() {
            super(null, "viewer-sync-fixture", "", null);
        }

        @Override
        public BaseFullChunk getChunk(int x, int z, boolean create) {
            return destinationUnavailable ? null : chunks[x];
        }

        @Override
        public Map<Integer, Player> getChunkPlayers(int x, int z) {
            if (viewerLookupFailure) {
                throw new IllegalStateException("viewers unavailable");
            }
            // 与 Level#getChunkPlayers 一致：每次返回副本，调用方消费副本不影响源数据。
            return new HashMap<>(viewers);
        }
    }
}
