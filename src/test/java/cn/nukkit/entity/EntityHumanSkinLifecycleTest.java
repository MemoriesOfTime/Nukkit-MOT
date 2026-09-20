package cn.nukkit.entity;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.scheduler.ServerScheduler;
import cn.nukkit.scheduler.TaskHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntityHumanSkinLifecycleTest {

    private Server server;
    private Level level;
    private final List<DelayedTask> pendingDelayedTasks = new ArrayList<>();

    private record DelayedTask(int delay, Runnable task) { }

    @BeforeEach
    void setUp() {
        MockServer.reset();
        this.server = MockServer.get();
        this.level = this.server.getDefaultLevel();
        this.pendingDelayedTasks.clear();

        lenient().when(this.level.getServer()).thenReturn(this.server);
        lenient().when(this.server.getViewDistance()).thenReturn(2);
        markLevelAsBeingConverted(this.level);
        doCallRealMethod().when(this.server).updatePlayerListData(
                any(UUID.class), anyLong(), anyString(), any(Skin.class), any(Player[].class));
        doCallRealMethod().when(this.server).updatePlayerListData(
                any(PlayerListPacket.Entry.class), any(Player[].class));
        doCallRealMethod().when(this.server).removePlayerListData(any(UUID.class), any(Player.class));
        doCallRealMethod().when(this.server).removePlayerListData(any(UUID.class), any(Player[].class));

        // 捕获式 fake scheduler：默认不执行延迟任务，runPendingDelayedTasks() 可手动触发。
        // Capturing fake: delayed tasks don't run until runPendingDelayedTasks() flushes them.
        ServerScheduler fakeScheduler = mock(ServerScheduler.class);
        lenient().when(this.server.getScheduler()).thenReturn(fakeScheduler);
        lenient().when(fakeScheduler.scheduleDelayedTask(eq(InternalPlugin.INSTANCE), any(Runnable.class), anyInt()))
                .thenAnswer(invocation -> {
                    this.pendingDelayedTasks.add(new DelayedTask(
                            invocation.getArgument(2), invocation.getArgument(1)));
                    return mock(TaskHandler.class);
                });
    }

    /**
     * 执行所有已注册的延迟任务，模拟延迟到期。
     * <p>
     * Runs all registered delayed tasks, simulating the delay elapsing.
     */
    private void runPendingDelayedTasks() {
        runPendingDelayedTasks(Integer.MAX_VALUE);
    }

    private void runPendingDelayedTasks(int throughTick) {
        for (DelayedTask scheduled : new ArrayList<>(this.pendingDelayedTasks)) {
            if (scheduled.delay() <= throughTick) {
                this.pendingDelayedTasks.remove(scheduled);
                scheduled.task().run();
            }
        }
    }

    @Test
    void v860NpcSendsSkinHandshakeBeforeDelayedRemove() {
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124_NETEASE);

        npc.spawnTo(viewer);

        assertSpawnSkinHandshake(npc, viewer);
    }

    @Test
    void v860NpcPlaceholderSkinIsVisible() {
        // 回归：占位皮肤必须不透明——93 被客户端限流队列丢弃时，降级为可见占位而非永久隐形
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124_NETEASE);

        npc.spawnTo(viewer);

        PlayerListPacket add = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .findFirst().orElseThrow();
        assertEquals(PlayerListPacket.TYPE_ADD, add.type);
        assertTrue(add.entries[0].skin.isValid());
        assertFalse(add.entries[0].skin.isFullyTransparent(),
                "placeholder skin must be opaque so a dropped skin packet degrades to a visible fallback");
    }

    @Test
    void v860NpcResendUsesLiveSkinAfterChange() {
        // 回归：补发必须取实体当前皮肤——补发 spawn 时的快照会把窗口内的新皮肤永久顶掉
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124_NETEASE);
        npc.spawnTo(viewer);

        Skin changed = new Skin();
        changed.setSkinId("test-npc-skin-2");
        changed.setSkinData(new byte[Skin.SINGLE_SKIN_SIZE]);
        npc.setSkin(changed);

        runPendingDelayedTasks(); // +2t 补发与 +5t REMOVE

        PlayerSkinPacket last = viewer.sentPackets.stream()
                .filter(PlayerSkinPacket.class::isInstance)
                .map(PlayerSkinPacket.class::cast)
                .reduce((first, second) -> second).orElseThrow();
        assertEquals("test-npc-skin-2", last.skin.getSkinId());
    }

    @Test
    void v860NpcRemovesPlayerListEntryAfterDelayedRemove() {
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124_NETEASE);
        npc.spawnTo(viewer);
        viewer.sentPackets.clear();

        // 触发延迟 REMOVE，清理 Tab 条目。
        runPendingDelayedTasks();

        List<PlayerListPacket> removes = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertEquals(1, removes.size());
        assertEquals(PlayerListPacket.TYPE_REMOVE, removes.get(0).type);
        assertEquals(npc.getUniqueId(), removes.get(0).entries[0].uuid);
        assertFalse(viewer.sentSkins.contains(npc.getUniqueId()));
    }

    @Test
    void v860NpcRemovesPlayerListRegistrationOnDespawn() {
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124_NETEASE);
        npc.spawnTo(viewer);
        viewer.sentPackets.clear();

        npc.despawnFrom(viewer);

        List<DataPacket> lifecyclePackets = viewer.sentPackets.stream()
                .filter(packet -> packet instanceof RemoveEntityPacket || packet instanceof PlayerListPacket)
                .toList();
        assertEquals(2, lifecyclePackets.size());
        assertInstanceOf(RemoveEntityPacket.class, lifecyclePackets.get(0));
        PlayerListPacket remove = assertInstanceOf(PlayerListPacket.class, lifecyclePackets.get(1));
        assertEquals(PlayerListPacket.TYPE_REMOVE, remove.type);
        assertFalse(viewer.sentSkins.contains(npc.getUniqueId()));
    }

    @Test
    void v860NpcRollsBackPlayerListWhenSkinUpdateIsRejected() {
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124_NETEASE);
        viewer.reject(PlayerSkinPacket.class);

        npc.spawnTo(viewer);

        List<DataPacket> lifecyclePackets = viewer.sentPackets.stream()
                .filter(packet -> packet instanceof PlayerListPacket
                        || packet instanceof PlayerSkinPacket
                        || packet instanceof AddPlayerPacket)
                .toList();
        assertEquals(3, lifecyclePackets.size());
        PlayerListPacket add = assertInstanceOf(PlayerListPacket.class, lifecyclePackets.get(0));
        assertEquals(PlayerListPacket.TYPE_ADD, add.type);
        assertInstanceOf(PlayerSkinPacket.class, lifecyclePackets.get(1));
        PlayerListPacket remove = assertInstanceOf(PlayerListPacket.class, lifecyclePackets.get(2));
        assertEquals(PlayerListPacket.TYPE_REMOVE, remove.type);
        assertFalse(viewer.sentSkins.contains(npc.getUniqueId()));
        assertTrue(this.pendingDelayedTasks.isEmpty());

        viewer.reject(null);
        viewer.sentPackets.clear();
        npc.spawnTo(viewer);

        assertSpawnSkinHandshake(npc, viewer);
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {
            "V1_21_124", "V1_21_93_NETEASE", "V1_21_124_NETEASE", "V1_26_45"
    })
    void everyClientReceivesPostSpawnSkinAndDelayedListRemoval(GameVersion version) {
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(version);

        npc.spawnTo(viewer);

        assertSpawnSkinHandshake(npc, viewer);
        assertEquals(List.of(2, 5), this.pendingDelayedTasks.stream().map(DelayedTask::delay).toList());
        viewer.sentPackets.clear();

        runPendingDelayedTasks(1);
        assertTrue(viewer.sentPackets.isEmpty());
        assertTrue(viewer.sentSkins.contains(npc.getUniqueId()));

        runPendingDelayedTasks(2);
        assertEquals(1, viewer.sentPackets.size());
        assertRealSkin(npc, viewer.sentPackets.get(0));
        assertTrue(viewer.sentSkins.contains(npc.getUniqueId()));
        viewer.sentPackets.clear();

        runPendingDelayedTasks(4);
        assertTrue(viewer.sentPackets.isEmpty());
        assertTrue(viewer.sentSkins.contains(npc.getUniqueId()));

        runPendingDelayedTasks(5);
        assertEquals(1, viewer.sentPackets.size());
        PlayerListPacket remove = assertInstanceOf(PlayerListPacket.class, viewer.sentPackets.get(0));
        assertEquals(PlayerListPacket.TYPE_REMOVE, remove.type);
        assertEquals(1, remove.entries.length);
        assertEquals(npc.getUniqueId(), remove.entries[0].uuid);
        assertFalse(viewer.sentSkins.contains(npc.getUniqueId()));
        assertTrue(this.pendingDelayedTasks.isEmpty());
    }

    @Test
    void v860NpcRetriesAfterPlayerListAddIsRejected() {
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124_NETEASE);
        viewer.reject(PlayerListPacket.class);

        npc.spawnTo(viewer);

        assertEquals(1, viewer.sentPackets.size());
        PlayerListPacket rejectedAdd = assertInstanceOf(PlayerListPacket.class, viewer.sentPackets.get(0));
        assertEquals(PlayerListPacket.TYPE_ADD, rejectedAdd.type);
        assertFalse(viewer.sentSkins.contains(npc.getUniqueId()));

        viewer.reject(null);
        viewer.sentPackets.clear();
        npc.spawnTo(viewer);

        assertSpawnSkinHandshake(npc, viewer);
    }

    @Test
    void v860NpcSuppressesDuplicateHandshakeWhileSpawned() {
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124_NETEASE);
        npc.spawnTo(viewer);
        viewer.sentPackets.clear();

        npc.spawnTo(viewer);

        assertTrue(viewer.sentPackets.isEmpty());
        assertTrue(viewer.sentSkins.contains(npc.getUniqueId()));
    }

    @Test
    void v860NpcRemovesPlayerListRegistrationOnClose() {
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124_NETEASE);
        npc.spawnTo(viewer);
        viewer.sentPackets.clear();

        npc.close();

        List<DataPacket> lifecyclePackets = viewer.sentPackets.stream()
                .filter(packet -> packet instanceof RemoveEntityPacket || packet instanceof PlayerListPacket)
                .toList();
        assertEquals(2, lifecyclePackets.size());
        assertInstanceOf(RemoveEntityPacket.class, lifecyclePackets.get(0));
        PlayerListPacket remove = assertInstanceOf(PlayerListPacket.class, lifecyclePackets.get(1));
        assertEquals(PlayerListPacket.TYPE_REMOVE, remove.type);
        assertFalse(viewer.sentSkins.contains(npc.getUniqueId()));
    }

    @Test
    void v860NpcRegistersSkinAgainAfterDespawn() {
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124_NETEASE);
        npc.spawnTo(viewer);
        npc.despawnFrom(viewer);
        viewer.sentPackets.clear();

        npc.spawnTo(viewer);

        assertSpawnSkinHandshake(npc, viewer);
    }

    @Test
    void v860NpcDelayedRemoveFromPriorSpawnDoesNotClobberReRegistration() {
        // 回归：despawn → re-spawn 后，首次 spawn 的旧延迟任务不得误删二次注册的条目。
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124_NETEASE);
        npc.spawnTo(viewer);
        npc.despawnFrom(viewer);
        viewer.sentPackets.clear();
        npc.spawnTo(viewer);
        viewer.sentPackets.clear();
        assertTrue(viewer.sentSkins.contains(npc.getUniqueId()));

        // 触发所有延迟任务（含首次 spawn 的旧任务），二次注册条目应恰好被移除一次。
        runPendingDelayedTasks();

        List<PlayerListPacket> removes = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .filter(packet -> packet.type == PlayerListPacket.TYPE_REMOVE)
                .toList();
        assertEquals(1, removes.size());
        assertEquals(npc.getUniqueId(), removes.get(0).entries[0].uuid);
        assertFalse(viewer.sentSkins.contains(npc.getUniqueId()));
    }

    private static void assertSpawnSkinHandshake(TestHuman npc, RecordingPlayer viewer) {
        List<DataPacket> packets = viewer.sentPackets.stream()
                .filter(packet -> packet instanceof PlayerListPacket
                        || packet instanceof PlayerSkinPacket || packet instanceof AddPlayerPacket)
                .toList();
        // 0010 retains every NPC's entry; 0020 paints the real skin again after AddPlayer.
        assertEquals(4, packets.size());
        PlayerListPacket add = assertInstanceOf(PlayerListPacket.class, packets.get(0));
        assertEquals(PlayerListPacket.TYPE_ADD, add.type);
        assertEquals(1, add.entries.length);
        assertEquals(npc.getUniqueId(), add.entries[0].uuid);
        assertEquals(npc.getId(), add.entries[0].entityId);
        assertTrue(add.entries[0].skin.isValid());
        assertFalse(add.entries[0].skin.isFullyTransparent());
        assertNotEquals(npc.getSkin().getSkinId(), add.entries[0].skin.getSkinId());
        assertRealSkin(npc, packets.get(1));
        AddPlayerPacket spawn = assertInstanceOf(AddPlayerPacket.class, packets.get(2));
        assertEquals(npc.getUniqueId(), spawn.uuid);
        assertEquals(npc.getId(), spawn.entityUniqueId);
        assertEquals(npc.getId(), spawn.entityRuntimeId);
        assertRealSkin(npc, packets.get(3));
        assertTrue(viewer.sentSkins.contains(npc.getUniqueId()));
    }

    private static void assertRealSkin(TestHuman npc, DataPacket packet) {
        PlayerSkinPacket skin = assertInstanceOf(PlayerSkinPacket.class, packet);
        assertEquals(npc.getUniqueId(), skin.uuid);
        assertEquals(npc.getSkin().getSkinId(), skin.skin.getSkinId());
        assertEquals(npc.getSkin().getSkinId(), skin.newSkinName);
        assertArrayEquals(npc.getSkin().getSkinData().data, skin.skin.getSkinData().data);
    }

    private RecordingPlayer newViewer(GameVersion gameVersion) {
        SourceInterface source = mock(SourceInterface.class);
        when(source.getSession(any(InetSocketAddress.class))).thenReturn(mock(NetworkPlayerSession.class));
        RecordingPlayer player = new RecordingPlayer(source);
        player.useGameVersion(gameVersion);
        return player;
    }

    private static void markLevelAsBeingConverted(Level level) {
        try {
            Field field = Level.class.getDeclaredField("isBeingConverted");
            field.setAccessible(true);
            field.setBoolean(level, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to configure test level", e);
        }
    }

    private FullChunk newMockChunk() {
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(this.level);
        return chunk;
    }

    private static CompoundTag npcNbt() {
        return Entity.getDefaultNBT(new Vector3(0.5, 64, 0.5))
                .putString("NameTag", "Test NPC")
                .putCompound("Skin", new CompoundTag()
                        .putString("ModelId", "test-npc-skin")
                        .putByteArray("Data", new byte[Skin.SINGLE_SKIN_SIZE]));
    }

    private static final class TestHuman extends EntityHuman {

        private TestHuman(FullChunk chunk, CompoundTag nbt) {
            super(chunk, nbt);
        }
    }

    private static final class RecordingPlayer extends Player {

        private final List<DataPacket> sentPackets = new ArrayList<>();
        private Class<? extends DataPacket> rejectedPacketType;

        private RecordingPlayer(SourceInterface source) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19132));
        }

        private void useGameVersion(GameVersion gameVersion) {
            this.gameVersion = gameVersion;
            this.protocol = gameVersion.getProtocol();
        }

        private void reject(Class<? extends DataPacket> packetType) {
            this.rejectedPacketType = packetType;
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            this.sentPackets.add(packet);
            return this.rejectedPacketType == null || !this.rejectedPacketType.isInstance(packet);
        }
    }
}
