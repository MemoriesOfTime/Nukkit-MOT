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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @BeforeEach
    void setUp() {
        MockServer.reset();
        this.server = MockServer.get();
        this.level = this.server.getDefaultLevel();

        lenient().when(this.level.getServer()).thenReturn(this.server);
        lenient().when(this.server.getViewDistance()).thenReturn(2);
        markLevelAsBeingConverted(this.level);
        doCallRealMethod().when(this.server).updatePlayerListData(
                any(UUID.class), anyLong(), anyString(), any(Skin.class), any(Player[].class));
        doCallRealMethod().when(this.server).updatePlayerListData(
                any(PlayerListPacket.Entry.class), any(Player[].class));
        doCallRealMethod().when(this.server).removePlayerListData(any(UUID.class), any(Player.class));
    }

    @Test
    void v860NpcKeepsPlayerListRegistrationUntilDespawn() {
        TestHuman npc = new TestHuman(newMockChunk(), npcNbt());
        RecordingPlayer viewer = newViewer(GameVersion.V1_21_124_NETEASE);

        npc.spawnTo(viewer);

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
