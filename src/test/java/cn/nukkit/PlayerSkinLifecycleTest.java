package cn.nukkit;

import cn.nukkit.entity.data.Skin;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.PlayerListPacket;
import cn.nukkit.network.protocol.PlayerSkinPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.utils.LoginChainData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
