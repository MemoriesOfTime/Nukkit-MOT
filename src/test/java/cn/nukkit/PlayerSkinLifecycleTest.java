package cn.nukkit;

import cn.nukkit.entity.data.Skin;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.PlayerListPacket;
import cn.nukkit.network.protocol.PlayerSkinPacket;
import cn.nukkit.network.protocol.RemoveEntityPacket;
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
