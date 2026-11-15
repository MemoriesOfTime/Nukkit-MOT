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
import cn.nukkit.network.Network;
import cn.nukkit.network.PacketPool;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.v113.ProtocolInfo_v113;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.scheduler.ServerScheduler;
import cn.nukkit.scheduler.TaskHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityHumanSkinTest {

    private Server server;
    private Level level;
    private final List<Runnable> pendingDelayedTasks = new ArrayList<>();

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
                    this.pendingDelayedTasks.add(invocation.getArgument(1));
                    return mock(TaskHandler.class);
                });
    }

    /**
     * 执行所有已注册的延迟任务，模拟延迟到期。
     * <p>
     * Runs all registered delayed tasks, simulating the delay elapsing.
     */
    private void runPendingDelayedTasks() {
        for (Runnable task : new ArrayList<>(this.pendingDelayedTasks)) {
            task.run();
        }
        this.pendingDelayedTasks.clear();
    }

    @Test
    void v860NpcSendsSkinHandshakeBeforeDelayedRemove() {
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124_NETEASE);

        npc.spawnTo(viewer);

        // 延迟 REMOVE 未触发，仅见握手三件套（ADD → 皮肤包 → AddPlayer）。
        List<DataPacket> lifecyclePackets = viewer.sentPackets.stream()
                .filter(packet -> packet instanceof PlayerListPacket
                        || packet instanceof PlayerSkinPacket
                        || packet instanceof AddPlayerPacket)
                .toList();
        assertEquals(3, lifecyclePackets.size());
        PlayerListPacket add = assertInstanceOf(PlayerListPacket.class, lifecyclePackets.get(0));
        assertEquals(PlayerListPacket.TYPE_ADD, add.type);
        assertNotEquals(npc.getSkin().getSkinId(), add.entries[0].skin.getSkinId());
        PlayerSkinPacket update = assertInstanceOf(PlayerSkinPacket.class, lifecyclePackets.get(1));
        assertEquals(npc.getSkin().getSkinId(), update.skin.getSkinId());
        assertInstanceOf(AddPlayerPacket.class, lifecyclePackets.get(2));
        assertTrue(viewer.sentSkins.contains(npc.getUniqueId()));
    }

    @Test
    void npcProfileHashSurvivesNbtRoundTrip() {
        CompoundTag nbt = npcNbt();
        nbt.getCompound("Skin").putString("ProfileHash", "persona-profile-hash");

        TestHuman loaded = new TestHuman(newMockChunk(), nbt);
        assertEquals("persona-profile-hash", loaded.getSkin().getProfileHash());

        TestHuman reloaded = new TestHuman(newMockChunk(), loaded.saveAndGetNbt());
        assertEquals("persona-profile-hash", reloaded.getSkin().getProfileHash());
    }

    @Test
    void v126PersonaNpcWithoutProfileHashAppliesSkinAfterSpawn() {
        CompoundTag nbt = npcNbt();
        nbt.getCompound("Skin").putBoolean("PersonaSkin", true);
        TestHuman npc = new TestHuman(newMockChunk(), nbt);
        RecordingPlayer viewer = newViewer(GameVersion.V1_26_45);

        npc.spawnTo(viewer);

        List<DataPacket> packets = viewer.sentPackets.stream()
                .filter(packet -> packet instanceof PlayerListPacket
                        || packet instanceof AddPlayerPacket
                        || packet instanceof PlayerSkinPacket)
                .toList();
        assertEquals(3, packets.size());
        PlayerListPacket add = assertInstanceOf(PlayerListPacket.class, packets.get(0));
        assertEquals(PlayerListPacket.TYPE_ADD, add.type);
        assertNotEquals(npc.getSkin().getSkinId(), add.entries[0].skin.getSkinId());
        assertInstanceOf(AddPlayerPacket.class, packets.get(1));
        PlayerSkinPacket skin = assertInstanceOf(PlayerSkinPacket.class, packets.get(2));
        assertEquals(npc.getSkin().getSkinId(), skin.skin.getSkinId());
        assertTrue(viewer.sentSkins.contains(npc.getUniqueId()));
    }

    @Test
    void completedNpcSkinHandshakeReleasesGenerationState() {
        CompoundTag nbt = npcNbt();
        nbt.getCompound("Skin").putBoolean("PersonaSkin", true);
        TestHuman npc = new TestHuman(newMockChunk(), nbt);
        RecordingPlayer viewer = newViewer(GameVersion.V1_26_45);

        npc.spawnTo(viewer);
        assertEquals(1, trackedGenerationCount(viewer));

        runPendingDelayedTasks();

        assertEquals(0, trackedGenerationCount(viewer),
                "completed handshakes must not retain every NPC UUID for the viewer lifetime");
    }

    @Test
    void v126PersonaNpcWithProfileHashKeepsDirectListSkinPath() {
        CompoundTag nbt = npcNbt();
        nbt.getCompound("Skin")
                .putBoolean("PersonaSkin", true)
                .putString("ProfileHash", "persona-profile-hash");
        TestHuman npc = new TestHuman(newMockChunk(), nbt);
        RecordingPlayer viewer = newViewer(GameVersion.V1_26_45);

        npc.spawnTo(viewer);

        assertTrue(viewer.sentPackets.stream().noneMatch(PlayerSkinPacket.class::isInstance));
        List<PlayerListPacket> playerListPackets = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertEquals(1, playerListPackets.size());
        assertEquals(PlayerListPacket.TYPE_REMOVE, playerListPackets.get(0).type);
        assertFalse(viewer.sentSkins.contains(npc.getUniqueId()));
        assertTrue(this.pendingDelayedTasks.isEmpty());
    }

    @Test
    void pre126PersonaNpcKeepsDirectListSkinPath() {
        CompoundTag nbt = npcNbt();
        nbt.getCompound("Skin").putBoolean("PersonaSkin", true);
        TestHuman npc = new TestHuman(newMockChunk(), nbt);
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124);

        npc.spawnTo(viewer);

        assertTrue(viewer.sentPackets.stream().noneMatch(PlayerSkinPacket.class::isInstance));
        assertFalse(viewer.sentSkins.contains(npc.getUniqueId()));
        assertTrue(this.pendingDelayedTasks.isEmpty());
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

        viewer.reject(null);
        viewer.sentPackets.clear();
        npc.spawnTo(viewer);

        assertEquals(3, viewer.sentPackets.stream()
                .filter(packet -> packet instanceof PlayerListPacket
                        || packet instanceof PlayerSkinPacket
                        || packet instanceof AddPlayerPacket)
                .count());
        assertTrue(viewer.sentSkins.contains(npc.getUniqueId()));
    }

    @Test
    void standardV860NpcKeepsLegacyImmediateRemovalBehavior() {
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124);

        npc.spawnTo(viewer);

        assertTrue(viewer.sentPackets.stream().noneMatch(PlayerSkinPacket.class::isInstance));
        List<PlayerListPacket> playerListPackets = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertEquals(1, playerListPackets.size());
        assertEquals(PlayerListPacket.TYPE_REMOVE, playerListPackets.get(0).type);
        assertFalse(viewer.sentSkins.contains(npc.getUniqueId()));
    }

    @Test
    void olderNeteaseNpcKeepsLegacyImmediateRemovalBehavior() {
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_93_NETEASE);

        npc.spawnTo(viewer);

        assertTrue(viewer.sentPackets.stream().noneMatch(PlayerSkinPacket.class::isInstance));
        List<PlayerListPacket> playerListPackets = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertEquals(1, playerListPackets.size());
        assertEquals(PlayerListPacket.TYPE_REMOVE, playerListPackets.get(0).type);
        assertFalse(viewer.sentSkins.contains(npc.getUniqueId()));
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

        List<DataPacket> lifecyclePackets = viewer.sentPackets.stream()
                .filter(packet -> packet instanceof PlayerListPacket
                        || packet instanceof PlayerSkinPacket
                        || packet instanceof AddPlayerPacket)
                .toList();
        assertEquals(3, lifecyclePackets.size());
        assertEquals(PlayerListPacket.TYPE_ADD,
                assertInstanceOf(PlayerListPacket.class, lifecyclePackets.get(0)).type);
        assertInstanceOf(PlayerSkinPacket.class, lifecyclePackets.get(1));
        assertInstanceOf(AddPlayerPacket.class, lifecyclePackets.get(2));
        assertTrue(viewer.sentSkins.contains(npc.getUniqueId()));
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

        List<DataPacket> lifecyclePackets = viewer.sentPackets.stream()
                .filter(packet -> packet instanceof PlayerListPacket
                        || packet instanceof PlayerSkinPacket
                        || packet instanceof AddPlayerPacket)
                .toList();
        assertEquals(3, lifecyclePackets.size());
        PlayerListPacket add = assertInstanceOf(PlayerListPacket.class, lifecyclePackets.get(0));
        assertEquals(PlayerListPacket.TYPE_ADD, add.type);
        assertInstanceOf(PlayerSkinPacket.class, lifecyclePackets.get(1));
        assertInstanceOf(AddPlayerPacket.class, lifecyclePackets.get(2));
        assertTrue(viewer.sentSkins.contains(npc.getUniqueId()));
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

    /** Uses the frozen native fixture; reflection is test setup only, never a timed loop. */
    private static final UUID UUID_FIXED = new UUID(0x123456789abcdef0L, 0x123456789abcdef0L);
    private static final String PIXELS_SHA = "2de9f1efb4cf85f020c68465acf41f8211e2402e7425ffc902537b0e7046e4f0";

    @Test
    void postSpawnRegistrationPreservesPlaceholderFields() throws Exception {
        assertEquivalent(register(GameVersion.V1_26_50, false).entries[0].skin);
    }

    @Test
    void initialRegistrationPreservesPlaceholderFields() throws Exception {
        assertEquivalent(register(GameVersion.V1_21_124_NETEASE, true).entries[0].skin);
    }

    @Test
    void allVersionAddPacketsEqualFrozenOracle() throws Exception {
        // Only v113 reset() consults the registry. Use its exact native packet ID;
        // a full Network constructor would inspect host interfaces unnecessarily.
        Network network = mock(Network.class);
        PacketPool pool = PacketPool.builder().protocolVersion(ProtocolInfo.v1_1_0)
                .minecraftVersion("1.1.0")
                .registerPacket(ProtocolInfo_v113.PLAYER_LIST_PACKET, PlayerListPacket.class).build();
        when(network.getPacketPool(GameVersion.V1_1_0)).thenReturn(pool);
        when(MockServer.get().getNetwork()).thenReturn(network);
        int count = 0;
        for (GameVersion version : GameVersion.values()) {
            PlayerListPacket actual = register(version, false);
            PlayerListPacket expected = new PlayerListPacket();
            expected.type = PlayerListPacket.TYPE_ADD;
            expected.entries = new PlayerListPacket.Entry[]{
                    new PlayerListPacket.Entry(UUID_FIXED, 1007L, "Probe NPC", oracle(), "fixed-xuid")};
            for (PlayerListPacket packet : new PlayerListPacket[]{actual, expected}) {
                packet.gameVersion = version;
                packet.protocol = version.getProtocol();
                // Native fullSkinId is randomized lazily; compare payloads under
                // identical explicit identity inputs, without changing production.
                packet.entries[0].skin.setFullSkinId("probe-placeholder-full-id");
                packet.entries[0].skin.setPlayFabId("probe-playfab");
                packet.encode();
            }
            assertArrayEquals(expected.getBuffer(), actual.getBuffer(), version.name());
            System.out.println("PACKET\t" + version.name() + "\t" + version.getProtocol()
                    + "\t" + actual.getBuffer().length + "\t" + digest(actual.getBuffer()));
            count++;
        }
        assertEquals(GameVersion.values().length, count);
        System.out.println("VERSIONS\t" + count);
    }

    @Test
    void pixelsAndFieldsNeverAliasOtherResultsOrPublicDefault() throws Exception {
        Skin first = register(GameVersion.V1_26_50, false).entries[0].skin;
        Skin second = register(GameVersion.V1_21_124_NETEASE, true).entries[0].skin;
        assertNotSame(first, second);
        assertNotSame(first.getSkinData(), second.getSkinData());
        assertNotSame(first.getSkinData().data, second.getSkinData().data);
        Skin savedDefault = Skin.NO_PERSONA_SKIN;
        byte[] savedPixels = savedDefault.getSkinData().data.clone();
        String savedId = savedDefault.getSkinId();
        String savedPatch = savedDefault.getSkinResourcePatch();
        boolean savedSlim = savedDefault.isLegacySlim;
        Field fingerprint = Skin.class.getDeclaredField("cachedFingerprint");
        fingerprint.setAccessible(true);
        Object savedFingerprint = fingerprint.get(savedDefault);
        try {
            first.getSkinData().data[3] ^= (byte) 0xff;
            first.setSkinId("mutated-first");
            first.setGeometryName("geometry.humanoid.customSlim");
            savedDefault.getSkinData().data[7] ^= (byte) 0xff;
            savedDefault.setSkinId("mutated-public-default");
            savedDefault.setGeometryName("geometry.humanoid.customSlim");
            assertEquivalent(second);
            Skin third = register(GameVersion.V1_26_50, false).entries[0].skin;
            assertEquivalent(third);
            assertNotSame(first.getSkinData().data, third.getSkinData().data);
            assertNotSame(second.getSkinData().data, third.getSkinData().data);
            Skin.NO_PERSONA_SKIN = first;
            Skin fourth = register(GameVersion.V1_21_124_NETEASE, true).entries[0].skin;
            assertEquivalent(fourth);
            assertNotSame(first.getSkinData().data, fourth.getSkinData().data);
        } finally {
            Skin.NO_PERSONA_SKIN = savedDefault;
            System.arraycopy(savedPixels, 0, savedDefault.getSkinData().data, 0, savedPixels.length);
            savedDefault.setSkinId(savedId);
            savedDefault.setSkinResourcePatch(savedPatch);
            savedDefault.isLegacySlim = savedSlim;
            fingerprint.set(savedDefault, savedFingerprint);
        }
    }

    private static Skin oracle() {
        Skin skin = new Skin();
        skin.setSkinId("nukkit.empty-player-entity-skin");
        skin.setSkinData(Base64.getDecoder().decode(Skin.STEVE_SKIN));
        skin.setGeometryName("geometry.humanoid.custom");
        assertTrue(skin.isValid());
        return skin;
    }

    private PlayerListPacket register(GameVersion version, boolean initial) throws Exception {
        RecordingPlayer viewer = newViewer(version);
        Skin real = oracle();
        real.setSkinId("probe-real-npc");
        boolean accepted = initial
                ? PlayerEntitySkinSender.sendInitialSkinIfAbsent(viewer, UUID_FIXED, 1007L,
                    "Probe NPC", real, "fixed-xuid", () -> real)
                : PlayerEntitySkinSender.registerPostSpawnSkinIfAbsent(viewer, UUID_FIXED, 1007L,
                    "Probe NPC", "fixed-xuid", () -> real);
        assertTrue(accepted);
        List<DataPacket> packets = viewer.sentPackets;
        PlayerListPacket add = packets.stream().filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast).findFirst().orElseThrow();
        assertEquals(PlayerListPacket.TYPE_ADD, add.type);
        assertEquals(1, add.entries.length);
        assertEquals(UUID_FIXED, add.entries[0].uuid);
        assertEquals(1007L, add.entries[0].entityId);
        assertEquals("Probe NPC", add.entries[0].name);
        assertEquals("fixed-xuid", add.entries[0].xboxUserId);
        return add;
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void assertEquivalent(Skin actual) throws Exception {
        Skin expected = oracle();
        assertNotSame(Skin.NO_PERSONA_SKIN, actual);
        assertEquals(64, actual.getSkinData().width);
        assertEquals(64, actual.getSkinData().height);
        assertEquals(16384, actual.getSkinData().data.length);
        assertEquals(PIXELS_SHA, digest(actual.getSkinData().data));
        assertArrayEquals(expected.getSkinData().data, actual.getSkinData().data);
        assertEquals(expected.getSkinId(), actual.getSkinId());
        assertEquals(expected.getGeometryData(), actual.getGeometryData());
        assertEquals(expected.getSkinResourcePatch(), actual.getSkinResourcePatch());
        assertTrue(actual.isValid());
        assertFalse(actual.isFullyTransparent());
        assertFalse(actual.isPremium());
        assertFalse(actual.isPersona());
        assertFalse(actual.isCapeOnClassic());
        assertFalse(actual.isLegacySlim);
        assertTrue(actual.isPrimaryUser());
        assertTrue(actual.isTrusted());
        assertTrue(actual.isOverridingPlayerAppearance());
        assertEquals("", actual.getProfileHash());
        assertEquals("#0", actual.getSkinColor());
        assertEquals("wide", actual.getArmSize());
        assertEquals("0.0.0", actual.getGeometryDataEngineVersion());
        assertTrue(actual.getAnimations().isEmpty());
        assertTrue(actual.getPersonaPieces().isEmpty());
        assertTrue(actual.getTintColors().isEmpty());
        assertEquals(0, actual.getCapeData().data.length);
        // Do not call randomized/lazy identity getters before this characterization.
        Field fullId = Skin.class.getDeclaredField("fullSkinId");
        fullId.setAccessible(true);
        assertNull(fullId.get(actual));
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

    @SuppressWarnings("unchecked")
    private static int trackedGenerationCount(Player viewer) {
        try {
            Field field = PlayerEntitySkinSender.class.getDeclaredField("REMOVE_GENERATIONS");
            field.setAccessible(true);
            Map<Player, Map<UUID, ?>> generations = (Map<Player, Map<UUID, ?>>) field.get(null);
            synchronized (generations) {
                Map<UUID, ?> viewerGenerations = generations.get(viewer);
                return viewerGenerations == null ? 0 : viewerGenerations.size();
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to inspect pending NPC skin registrations", e);
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

        private CompoundTag saveAndGetNbt() {
            this.saveNBT();
            return this.namedTag;
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
