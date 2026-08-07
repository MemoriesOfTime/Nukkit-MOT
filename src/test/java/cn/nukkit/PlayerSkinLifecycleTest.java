package cn.nukkit;

import cn.nukkit.entity.data.Skin;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.utils.LoginChainData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlayerSkinLifecycleTest {

    private Server server;
    private Map<UUID, Player> playerList;

    @BeforeEach
    void setUp() {
        MockServer.reset();
        this.server = MockServer.get();
        this.playerList = installPlayerList(this.server);
        doCallRealMethod().when(this.server).addOnlinePlayer(any(Player.class));
        doCallRealMethod().when(this.server).updatePlayerListData(
                any(PlayerListPacket.Entry.class), any(Player[].class));
        doCallRealMethod().when(this.server).removePlayerListData(
                any(UUID.class), any(Player.class));
        doCallRealMethod().when(this.server).removePlayerListData(
                any(UUID.class), any(Player[].class));
    }

    @Test
    void v860SkinChangeUsesPlayerSkinWithoutRepeatingPlayerListAdd() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124_NETEASE, "old-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124_NETEASE, "viewer-skin");
        registerViewer(subject, viewer);

        subject.setSkin(validSkin("new-skin"));

        List<DataPacket> skinPackets = viewer.sentPackets.stream()
                .filter(packet -> packet instanceof PlayerListPacket || packet instanceof PlayerSkinPacket)
                .toList();
        PlayerSkinPacket update = assertInstanceOf(PlayerSkinPacket.class, skinPackets.get(0));
        assertEquals(1, skinPackets.size());
        assertEquals(subject.getUniqueId(), update.uuid);
        assertEquals("old-skin", update.oldSkinName);
        assertEquals("new-skin", update.newSkinName);
    }

    @Test
    void v860DisplayNameChangeReplacesPlayerListEntryBeforeAdd() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124_NETEASE, "subject-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124_NETEASE, "viewer-skin");
        registerViewer(subject, viewer);

        subject.setDisplayName("Updated Name");

        List<PlayerListPacket> packets = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertEquals(2, packets.size());
        assertEquals(PlayerListPacket.TYPE_REMOVE, packets.get(0).type);
        assertEquals(PlayerListPacket.TYPE_ADD, packets.get(1).type);
        assertEquals("Updated Name", packets.get(1).entries[0].name);
        assertTrue(viewer.sentSkins.contains(subject.getUniqueId()));
    }

    /**
     * displayName 变更会 REMOVE→ADD 重建条目（客户端确认状态随之失效），观察者的确认指纹必须作废，
     * 否则下次确认会因指纹相同被误抑制，皮肤在重建后无法重新确认。
     * <p>
     * A displayName change rebuilds the entry (REMOVE → ADD), invalidating the client's confirmation
     * state; the viewer's fingerprint must be cleared or the next confirmation is suppressed as a
     * duplicate and the rebuilt entry never gets re-confirmed.
     */
    @Test
    void v860DisplayNameChangeInvalidatesConfirmedFingerprint() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124_NETEASE, "subject-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124_NETEASE, "viewer-skin");
        registerViewer(subject, viewer);
        viewer.confirmedSkins.put(subject.getUniqueId(), subject.getSkin().getContentFingerprint());

        subject.setDisplayName("Updated Name");

        assertFalse(viewer.confirmedSkins.containsKey(subject.getUniqueId()),
                "displayName rebuild must invalidate the confirmation fingerprint");
    }

    @Test
    void standardClientSkinChangeKeepsPlayerListUpdate() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124, "old-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124, "viewer-skin");
        registerViewer(subject, viewer);

        subject.setSkin(validSkin("new-skin"));

        PlayerListPacket update = assertInstanceOf(PlayerListPacket.class, viewer.sentPackets.get(0));
        assertEquals(1, viewer.sentPackets.size());
        assertEquals(PlayerListPacket.TYPE_ADD, update.type);
        assertEquals("new-skin", update.entries[0].skin.getSkinId());
    }

    @Test
    void v860SkinChangeAlsoUpdatesTheOwningClientWithoutPlayerListAdd() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124_NETEASE, "old-skin");
        this.playerList.put(subject.getUniqueId(), subject);
        subject.sentSkins.add(subject.getUniqueId());

        subject.setSkin(validSkin("new-skin"));

        PlayerSkinPacket update = assertInstanceOf(PlayerSkinPacket.class, subject.sentPackets.get(0));
        assertEquals(1, subject.sentPackets.size());
        assertEquals(subject.getUniqueId(), update.uuid);
        assertEquals("old-skin", update.oldSkinName);
        assertEquals("new-skin", update.newSkinName);
    }

    /**
     * 回归：hidePlayer 不得清除 sentSkins，否则随后的 showPlayer → spawnTo 会重发
     * PlayerList(ADD)，在没有前置 REMOVE 的情况下触发网易 V860 玩家隐形。
     * <p>
     * Regression: hidePlayer must not clear sentSkins; otherwise the following showPlayer →
     * spawnTo resends PlayerList(ADD) without a prior REMOVE, hiding the player on NetEase V860.
     */
    @Test
    void v860HidePlayerKeepsSentSkinsAndAvoidsDuplicateAdd() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124_NETEASE, "subject-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124_NETEASE, "viewer-skin");
        registerViewer(subject, viewer);
        // 模拟 subject 已对 viewer spawnTo：viewer.sentSkins 已含 subject.uuid。
        // Simulate a prior spawnTo of subject to viewer; viewer.sentSkins already holds subject.uuid.

        viewer.hidePlayer(subject);

        // hidePlayer 只发 RemoveEntityPacket，从不下发 PlayerList(REMOVE)。
        // hidePlayer only sends RemoveEntityPacket, never PlayerList(REMOVE).
        List<PlayerListPacket> playerListPackets = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertTrue(playerListPackets.stream().noneMatch(packet -> packet.type == PlayerListPacket.TYPE_REMOVE),
                "hidePlayer must not send a PlayerList REMOVE for an online player");
        // sentSkins 必须保留，使后续 spawnTo 的 ADD 守卫去重。
        // sentSkins must be retained so the spawnTo ADD guard deduplicates on re-show.
        assertTrue(viewer.sentSkins.contains(subject.getUniqueId()),
                "hidePlayer must retain the sentSkins entry to suppress a duplicate ADD on re-show");
    }

    /**
     * 回归：插件通过 removePlayerListData 摘除 Tab 条目（如 vanish）后，sentSkins 必须同步清理；
     * 否则玩家重进视野时 spawnTo 的 ADD 守卫基于过期登记去重，客户端因缺列表项无法渲染实体。
     * <p>
     * Regression: after a plugin removes a tab entry via removePlayerListData (e.g. vanish),
     * sentSkins must be cleared in sync; otherwise the spawnTo ADD guard deduplicates against a
     * stale registration and the client cannot render the entity without a list entry.
     */
    @Test
    void removePlayerListDataClearsSentSkinsSoRespawnResendsAdd() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124, "subject-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124, "viewer-skin");
        registerViewer(subject, viewer);

        this.server.removePlayerListData(subject.getUniqueId(), viewer);

        List<PlayerListPacket> removals = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertEquals(1, removals.size());
        assertEquals(PlayerListPacket.TYPE_REMOVE, removals.get(0).type);
        assertFalse(viewer.sentSkins.contains(subject.getUniqueId()),
                "removePlayerListData must clear the sentSkins registration");

        viewer.sentPackets.clear();
        subject.spawnTo(viewer);

        List<PlayerListPacket> adds = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertEquals(1, adds.size());
        assertEquals(PlayerListPacket.TYPE_ADD, adds.get(0).type);
        assertEquals(subject.getUniqueId(), adds.get(0).entries[0].uuid);
        assertTrue(viewer.sentSkins.contains(subject.getUniqueId()),
                "respawn must re-register the entry after resending ADD");
    }

    /**
     * 回归：即使某条 ADD 路径漏掉了 sentSkins 守卫而重发 ADD，updatePlayerListData 内的集中
     * 守卫也必须先发 REMOVE 再发 ADD，避免网易客户端隐形。
     * <p>
     * Regression: even if an ADD path bypasses the sentSkins guard and resends an ADD, the
     * centralized guard inside updatePlayerListData must send REMOVE before ADD to avoid hiding
     * the player on NetEase clients.
     */
    @Test
    void v860DuplicatePlayerListAddIsReplacedWithRemoveBeforeAdd() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124_NETEASE, "subject-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124_NETEASE, "viewer-skin");
        registerViewer(subject, viewer);

        // viewer 已持有 subject.uuid 条目，再次 ADD 应被守卫转为 REMOVE → ADD。
        // The viewer already holds subject.uuid; a repeat ADD should be converted to REMOVE → ADD.
        this.server.updatePlayerListData(
                new PlayerListPacket.Entry(subject.getUniqueId(), subject.getId(),
                        subject.getDisplayName(), subject.getSkin(), "", subject.getLocatorBarColor()),
                new Player[]{viewer});

        List<PlayerListPacket> packets = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertEquals(2, packets.size());
        assertEquals(PlayerListPacket.TYPE_REMOVE, packets.get(0).type);
        assertEquals(PlayerListPacket.TYPE_ADD, packets.get(1).type);
        assertEquals(subject.getUniqueId(), packets.get(0).entries[0].uuid);
        assertEquals(subject.getUniqueId(), packets.get(1).entries[0].uuid);
    }

    /**
     * 标准客户端对重复 ADD 容错良好，守卫不应改动其行为（避免不必要的 Tab 闪烁）。
     * <p>
     * Standard clients tolerate duplicate ADDs; the guard must not change their behavior to
     * avoid spurious Tab flicker.
     */
    @Test
    void standardClientDuplicatePlayerListAddIsNotReplaced() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124, "subject-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124, "viewer-skin");
        registerViewer(subject, viewer);

        this.server.updatePlayerListData(
                new PlayerListPacket.Entry(subject.getUniqueId(), subject.getId(),
                        subject.getDisplayName(), subject.getSkin(), "", subject.getLocatorBarColor()),
                new Player[]{viewer});

        List<PlayerListPacket> packets = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertEquals(1, packets.size());
        assertEquals(PlayerListPacket.TYPE_ADD, packets.get(0).type);
        assertFalse(viewer.sentPackets.stream().anyMatch(packet -> packet instanceof RemoveEntityPacket));
    }

    /**
     * 回归：addOnlinePlayer 对已在线的网易 V860 观察者注册新玩家时，必须只发一条 ADD，
     * 不得因「先登记 sentSkins 再调用 updatePlayerListData」误判为新条目重发，
     * 触发守卫里 spurious REMOVE → ADD（旧版本里曾因 addOnlinePlayer 用 sentSkins.add
     * 预过滤而破坏了 updatePlayerListData:1443 的「新条目」信号）。
     * <p>
     * Regression: when addOnlinePlayer registers a brand-new player to an already-online
     * NetEase V860 viewer, it must send exactly one ADD — never a spurious REMOVE → ADD.
     * A prior version of addOnlinePlayer pre-registered sentSkins via Set.add before calling
     * updatePlayerListData, which corrupted the "is this a new entry?" signal at
     * updatePlayerListData:1443 and caused a phantom REMOVE before every fresh ADD.
     */
    @Test
    void addOnlinePlayerSendsSingleAddToNetEaseViewerForNewEntry() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124, "subject-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124_NETEASE, "viewer-skin");
        // viewer 已在线并登记进 playerList；subject 尚未注册给 viewer。
        // Viewer is already online and in the playerList; subject is not yet registered to it.
        this.playerList.put(viewer.getUniqueId(), viewer);

        this.server.addOnlinePlayer(subject);

        List<PlayerListPacket> packets = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertEquals(1, packets.size(),
                "A brand-new entry must not be preceded by a spurious REMOVE on NetEase clients");
        assertEquals(PlayerListPacket.TYPE_ADD, packets.get(0).type);
        assertEquals(subject.getUniqueId(), packets.get(0).entries[0].uuid);
        assertTrue(viewer.sentSkins.contains(subject.getUniqueId()));
    }

    /**
     * 回归：addOnlinePlayer 不得对已持有条目的旁观者（如已被 spawnTo 发过的玩家）重发 ADD，
     * 也不得遗漏尚未持有的旁观者（远距离 / 自己），并保留全员广播语义。
     * <p>
     * Regression: addOnlinePlayer must not re-send ADD to viewers that already hold the entry
     * (e.g. those already spawned-to), must still deliver to viewers that don't (remote / self),
     * and must preserve the broadcast-to-all semantics.
     */
    @Test
    void addOnlinePlayerSkipsHoldersAndBroadcastsToFreshViewersAndSelf() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124, "subject-skin");
        RecordingPlayer holder = newPlayer(GameVersion.V1_21_124, "holder-skin");
        RecordingPlayer freshViewer = newPlayer(GameVersion.V1_21_124, "fresh-skin");
        this.playerList.put(holder.getUniqueId(), holder);
        this.playerList.put(freshViewer.getUniqueId(), freshViewer);
        // holder 已通过 spawnTo 收到过 subject 的列表项（sentSkins 已登记）。
        // Holder already received subject's entry via spawnTo (sentSkins registered).
        holder.sentSkins.add(subject.getUniqueId());

        this.server.addOnlinePlayer(subject);

        List<PlayerListPacket> holderPackets = holder.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertTrue(holderPackets.isEmpty(),
                "Holder that already received the entry must not get a duplicate ADD");

        List<PlayerListPacket> freshPackets = freshViewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertEquals(1, freshPackets.size());
        assertEquals(PlayerListPacket.TYPE_ADD, freshPackets.get(0).type);
        assertEquals(subject.getUniqueId(), freshPackets.get(0).entries[0].uuid);

        List<PlayerListPacket> selfPackets = subject.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertEquals(1, selfPackets.size(),
                "Self must still receive its own Tab entry on addOnlinePlayer");
        assertEquals(subject.getUniqueId(), selfPackets.get(0).entries[0].uuid);
        assertTrue(subject.sentSkins.contains(subject.getUniqueId()));
    }

    /**
     * 网易 V860 对同一列表项重复确认同一皮肤会隐藏实体：同指纹必须被抑制，
     * 指纹变化时先 REMOVE→ADD 重建条目再放行。
     * <p>
     * NetEase V860 hides an entity when the same list entry is confirmed twice; an identical
     * fingerprint must be suppressed, while a changed one rebuilds the entry first (REMOVE → ADD).
     */
    @Test
    void prepareConfirmSkinSuppressesDuplicateAndRebuildsOnChange() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124_NETEASE, "subject-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124_NETEASE, "viewer-skin");
        registerViewer(subject, viewer);
        subject.hasSpawned.put(viewer.getLoaderId(), viewer);
        when(this.server.getPlayer(subject.getUniqueId())).thenReturn(Optional.of(subject));

        Skin first = validSkin("subject-skin");
        assertTrue(PlayerEntitySkinSender.prepareConfirmSkin(viewer, subject.getUniqueId(), first));
        assertEquals(first.getContentFingerprint(), viewer.confirmedSkins.get(subject.getUniqueId()));

        assertFalse(PlayerEntitySkinSender.prepareConfirmSkin(viewer, subject.getUniqueId(), first),
                "Confirming the same fingerprint twice must be suppressed");

        Skin changed = validSkin("changed-skin");
        assertTrue(PlayerEntitySkinSender.prepareConfirmSkin(viewer, subject.getUniqueId(), changed),
                "A changed fingerprint must rebuild the entry before confirming");

        List<PlayerListPacket> packets = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertEquals(2, packets.size());
        assertEquals(PlayerListPacket.TYPE_REMOVE, packets.get(0).type);
        assertEquals(subject.getUniqueId(), packets.get(0).entries[0].uuid);
        assertEquals(PlayerListPacket.TYPE_ADD, packets.get(1).type);
        assertEquals(changed.getContentFingerprint(), viewer.confirmedSkins.get(subject.getUniqueId()));
    }

    /**
     * 条目或实体未就绪时不得确认：缺任一项客户端都会静默丢弃确认包，
     * 且一旦记下指纹便不会重发，必须提前拦下。
     * <p>
     * Neither a missing PlayerList entry nor a missing entity may be confirmed; the client drops
     * the packet silently while the recorded fingerprint would suppress any retry.
     */
    @Test
    void prepareConfirmSkinBlocksWhenEntryOrEntityNotReady() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124_NETEASE, "subject-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124_NETEASE, "viewer-skin");
        registerViewer(subject, viewer);

        assertFalse(PlayerEntitySkinSender.prepareConfirmSkin(viewer, subject.getUniqueId(), subject.getSkin()),
                "Entity not spawned to the viewer must block confirmation");

        subject.hasSpawned.put(viewer.getLoaderId(), viewer);
        when(this.server.getPlayer(subject.getUniqueId())).thenReturn(Optional.of(subject));
        assertTrue(PlayerEntitySkinSender.prepareConfirmSkin(viewer, subject.getUniqueId(), subject.getSkin()));
    }

    /**
     * needsSkinConfirmation 用于巡检补发：条目与实体就绪且从未确认过才为 true。
     * <p>
     * needsSkinConfirmation drives the sweep: only entries that are both in place and
     * never confirmed before are reported as pending.
     */
    @Test
    void needsSkinConfirmationReflectsPendingState() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124_NETEASE, "subject-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124_NETEASE, "viewer-skin");

        assertFalse(PlayerEntitySkinSender.needsSkinConfirmation(viewer, subject.getUniqueId()));

        registerViewer(subject, viewer);
        subject.hasSpawned.put(viewer.getLoaderId(), viewer);
        when(this.server.getPlayer(subject.getUniqueId())).thenReturn(Optional.of(subject));
        assertTrue(PlayerEntitySkinSender.needsSkinConfirmation(viewer, subject.getUniqueId()));

        PlayerEntitySkinSender.prepareConfirmSkin(viewer, subject.getUniqueId(), subject.getSkin());
        assertFalse(PlayerEntitySkinSender.needsSkinConfirmation(viewer, subject.getUniqueId()));
    }

    /**
     * 非网易观察者不受 ConfirmSkin 去重约束，始终放行。
     * <p>
     * Non-NetEase viewers are exempt from confirmation dedup and always pass.
     */
    @Test
    void nonNetEaseViewerIsAlwaysConfirmable() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124, "subject-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124, "viewer-skin");

        assertTrue(PlayerEntitySkinSender.prepareConfirmSkin(viewer, subject.getUniqueId(), subject.getSkin()));
        assertTrue(viewer.confirmedSkins.isEmpty());
    }

    /**
     * 皮肤内容指纹：同内容同指纹，不同 skinId 不同指纹。
     * <p>
     * Content fingerprints: identical content hashes equal, distinct skinId does not.
     */
    @Test
    void contentFingerprintDistinguishesSkinContent() {
        Skin a = validSkin("skin-a");
        Skin b = validSkin("skin-a");
        Skin c = validSkin("skin-c");
        assertEquals(a.getContentFingerprint(), b.getContentFingerprint());
        assertNotEquals(a.getContentFingerprint(), c.getContentFingerprint());
    }

    /**
     * 条目被 REMOVE→ADD 重建后，观察者的确认指纹必须作废，否则巡检永远不再补发确认。
     * <p>
     * A REMOVE→ADD rebuild must invalidate the viewer's confirmation fingerprint, or the sweep
     * would never re-confirm the rebuilt entry.
     */
    @Test
    void playerListRebuildInvalidatesConfirmedFingerprint() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124_NETEASE, "subject-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124_NETEASE, "viewer-skin");
        registerViewer(subject, viewer);
        viewer.confirmedSkins.put(subject.getUniqueId(), "stale-fingerprint");
        this.playerList.put(subject.getUniqueId(), subject);

        // 模拟 displayName/locatorBarColor 变更触发的条目重建（REMOVE→ADD）。
        this.server.updatePlayerListData(new PlayerListPacket.Entry(subject.getUniqueId(), subject.getId(),
                "new-name", subject.getSkin(), ""), new Player[]{viewer});

        assertFalse(viewer.confirmedSkins.containsKey(subject.getUniqueId()),
                "Rebuilt entry must invalidate the stale confirmation fingerprint");
        assertTrue(viewer.sentSkins.contains(subject.getUniqueId()));

        List<PlayerListPacket> packets = viewer.sentPackets.stream()
                .filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast)
                .toList();
        assertEquals(2, packets.size());
        assertEquals(PlayerListPacket.TYPE_REMOVE, packets.get(0).type);
        assertEquals(PlayerListPacket.TYPE_ADD, packets.get(1).type);
    }

    /**
     * 换肤后观察者的旧确认指纹必须作废，否则皮肤换回旧样式时确认会被误抑制。
     * <p>
     * A skin change must invalidate the viewer's confirmation fingerprint, or reverting to a
     * former skin suppresses its confirmation as a duplicate.
     */
    @Test
    void skinChangeInvalidatesViewerConfirmationFingerprint() {
        RecordingPlayer subject = newPlayer(GameVersion.V1_21_124_NETEASE, "subject-skin");
        RecordingPlayer viewer = newPlayer(GameVersion.V1_21_124_NETEASE, "viewer-skin");
        registerViewer(subject, viewer);
        viewer.confirmedSkins.put(subject.getUniqueId(), subject.getSkin().getContentFingerprint());

        subject.setSkin(validSkin("new-skin"));

        assertFalse(viewer.confirmedSkins.containsKey(subject.getUniqueId()),
                "Skin change must invalidate the stale confirmation fingerprint");
        assertTrue(viewer.sentSkins.contains(subject.getUniqueId()));
        PlayerSkinPacket update = assertInstanceOf(PlayerSkinPacket.class, viewer.sentPackets.get(0));
        assertEquals(subject.getUniqueId(), update.uuid);
    }

    private static PlayerListPacket newPlayerListPacket(GameVersion gameVersion, PlayerListPacket.Entry entry) {
        PlayerListPacket pk = new PlayerListPacket();
        pk.protocol = gameVersion.getProtocol();
        pk.gameVersion = gameVersion;
        pk.type = PlayerListPacket.TYPE_ADD;
        pk.entries = new PlayerListPacket.Entry[]{entry};
        pk.encode();
        return pk;
    }

    private void registerViewer(RecordingPlayer subject, RecordingPlayer viewer) {
        this.playerList.put(viewer.getUniqueId(), viewer);
        viewer.sentSkins.add(subject.getUniqueId());
    }

    private RecordingPlayer newPlayer(GameVersion gameVersion, String skinId) {
        SourceInterface source = mock(SourceInterface.class);
        when(source.getSession(any(InetSocketAddress.class))).thenReturn(mock(NetworkPlayerSession.class));
        RecordingPlayer player = new RecordingPlayer(source);
        player.configure(UUID.randomUUID(), gameVersion, validSkin(skinId));
        return player;
    }

    private static Skin validSkin(String skinId) {
        Skin skin = new Skin();
        skin.setSkinId(skinId);
        skin.setSkinData(new byte[Skin.SINGLE_SKIN_SIZE]);
        skin.setGeometryName("geometry.humanoid.custom");
        return skin;
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Player> installPlayerList(Server server) {
        try {
            Field field = Server.class.getDeclaredField("playerList");
            field.setAccessible(true);
            Map<UUID, Player> players = new HashMap<>();
            field.set(server, players);
            return players;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to install test player list", e);
        }
    }

    private static final class RecordingPlayer extends Player {

        private final List<DataPacket> sentPackets = new ArrayList<>();

        private RecordingPlayer(SourceInterface source) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19132));
        }

        private void configure(UUID uuid, GameVersion gameVersion, Skin skin) {
            this.uuid = uuid;
            this.gameVersion = gameVersion;
            this.protocol = gameVersion.getProtocol();
            this.displayName = "Original Name";
            this.loginChainData = mock(LoginChainData.class);
            when(this.loginChainData.getXUID()).thenReturn("");
            // 网络构造路径不会执行 initEntity，spawnTo 需要的副手背包在此补齐。
            // The network constructor skips initEntity; spawnTo needs the offhand inventory.
            this.offhandInventory = new PlayerOffhandInventory(this);
            super.setSkin(skin);
            this.spawned = true;
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            this.sentPackets.add(packet);
            return true;
        }
    }
}
