package cn.nukkit;

import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.process.processor.v113.PlayerActionProcessor_v113;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.MobEquipmentPacket;
import cn.nukkit.network.protocol.PlayerActionPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.v113.ContainerSetContentPacket_v113;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 以真实 {@link Player} 实例验证会话状态：传送位置校验、重生请求、效果包版本过滤与旧版登录背包下发。
 * <p>
 * Exercises real {@link Player} instances for session state: teleport position checks, respawn requests, effect packet version filtering, and legacy login inventory sync.
 */
class PlayerSessionStateTest {

    @Nested
    class TeleportPosition {

        @Test
        void checkTeleportPositionIgnoresUnsentDiagonalsWhenChunkRadiusIsOne() {
            TestPlayer player = createPlayer(1);
            player.spawned = true;
            player.teleportPosition = new Location(8, 64, 8, Mockito.mock(Level.class));
            markCrossChunksSent(player, 0, 0);

            assertTrue(player.checkTeleportPosition());
            assertNull(player.teleportPosition);
        }

        @Test
        void checkTeleportPositionStillRequiresDiagonalsWhenChunkRadiusAllowsThreeByThree() {
            TestPlayer player = createPlayer(2);
            player.spawned = true;
            player.teleportPosition = new Location(8, 64, 8, Mockito.mock(Level.class));
            markCrossChunksSent(player, 0, 0);

            assertFalse(player.checkTeleportPosition());
            assertNotNull(player.teleportPosition);
        }

        private static TestPlayer createPlayer(int chunkRadius) {
            MockServer.reset();
            Server server = MockServer.get();
            Mockito.when(server.getViewDistance()).thenReturn(chunkRadius);

            SourceInterface sourceInterface = Mockito.mock(SourceInterface.class);
            Mockito.when(sourceInterface.getSession(Mockito.any(InetSocketAddress.class)))
                    .thenReturn(Mockito.mock(NetworkPlayerSession.class));

            TestPlayer player = new TestPlayer(sourceInterface);
            player.chunkRadius = chunkRadius;
            return player;
        }

        private static void markCrossChunksSent(Player player, int chunkX, int chunkZ) {
            markChunkSent(player, chunkX, chunkZ);
            markChunkSent(player, chunkX + 1, chunkZ);
            markChunkSent(player, chunkX - 1, chunkZ);
            markChunkSent(player, chunkX, chunkZ + 1);
            markChunkSent(player, chunkX, chunkZ - 1);
        }

        private static void markChunkSent(Player player, int chunkX, int chunkZ) {
            player.usedChunks.put(Level.chunkHash(chunkX, chunkZ), Boolean.TRUE);
        }

        private static final class TestPlayer extends Player {

            private TestPlayer(SourceInterface sourceInterface) {
                super(sourceInterface, 1L, new InetSocketAddress("127.0.0.1", 19132));
            }

            @Override
            public void spawnToAll() {
            }
        }
    }

    @Nested
    class RespawnRequest {

        @Test
        void respawnRequestRequiresSpawnedDeadOnlinePlayer() {
            TestPlayer player = createPlayer();
            player.spawned = true;
            player.alive = true;
            player.online = true;

            assertFalse(player.handleRespawnRequest());
            assertEquals(0, player.respawnCalls);

            player.alive = false;

            assertTrue(player.handleRespawnRequest());
            assertEquals(1, player.respawnCalls);
        }

        @Test
        void legacyPlayerActionRespawnUsesCommonRespawnRequest() {
            TestPlayer player = createPlayer();
            player.spawned = true;
            player.alive = false;
            player.online = true;

            PlayerActionPacket packet = new PlayerActionPacket();
            packet.action = PlayerActionPacket.ACTION_RESPAWN;

            PlayerActionProcessor_v113.INSTANCE.handle(new PlayerHandle(player), packet);

            assertEquals(1, player.respawnCalls);
        }

        private static TestPlayer createPlayer() {
            MockServer.reset();

            SourceInterface sourceInterface = Mockito.mock(SourceInterface.class);
            Mockito.when(sourceInterface.getSession(Mockito.any(InetSocketAddress.class)))
                    .thenReturn(Mockito.mock(NetworkPlayerSession.class));

            return new TestPlayer(sourceInterface);
        }

        private static final class TestPlayer extends Player {

            private boolean alive;
            private boolean online;
            private int respawnCalls;

            private TestPlayer(SourceInterface sourceInterface) {
                super(sourceInterface, 1L, new InetSocketAddress("127.0.0.1", 19132));
            }

            @Override
            public boolean isAlive() {
                return this.alive;
            }

            @Override
            public boolean isOnline() {
                return this.online;
            }

            @Override
            protected void respawn() {
                this.respawnCalls++;
            }
        }
    }

    /**
     * 回归 #765：1.21.0 新增的特殊箭矢（风充能/缠绕/渗出/虫蚀）效果包不得发给旧版客户端（否则崩溃），
     * 但 {@code canBeAffected} 仍须放行，服务端效果照常生效。
     * <p>
     * Regression for #765: effect packets of the special arrows added in 1.21.0 must not reach older
     * clients (they crash), while {@code canBeAffected} still admits them so the server-side effect applies.
     */
    @Nested
    class EffectVersionFilter {

        @Test
        void pre121SuppressesPacketsForNewEffects() {
            TestPlayer player = createPlayer();
            player.protocol = ProtocolInfo.v1_20_10; // 1.20.12 maps to protocol 594

            assertTrue(player.canReceiveEffectPacket(Effect.SPEED));
            assertTrue(player.canReceiveEffectPacket(Effect.SLOWNESS));
            assertTrue(player.canReceiveEffectPacket(Effect.WITHER));

            assertFalse(player.canReceiveEffectPacket(Effect.WIND_CHARGED));
            assertFalse(player.canReceiveEffectPacket(Effect.WEAVING));
            assertFalse(player.canReceiveEffectPacket(Effect.OOZING));
            assertFalse(player.canReceiveEffectPacket(Effect.INFESTED));
        }

        @Test
        void v121AllowsAllKnownEffectPackets() {
            TestPlayer player = createPlayer();
            player.protocol = ProtocolInfo.v1_21_0;

            assertTrue(player.canReceiveEffectPacket(Effect.WIND_CHARGED));
            assertTrue(player.canReceiveEffectPacket(Effect.WEAVING));
            assertTrue(player.canReceiveEffectPacket(Effect.OOZING));
            assertTrue(player.canReceiveEffectPacket(Effect.INFESTED));
        }

        /**
         * Even on pre-1.21.0 clients the effects are still applied server-side
         * (the death-triggered side effects etc. run regardless); only the packet
         * is suppressed. So {@code canBeAffected} must keep returning true.
         */
        @Test
        void pre121StillAppliesNewEffectsServerSide() {
            TestPlayer player = createPlayer();
            player.protocol = ProtocolInfo.v1_20_10;

            assertTrue(player.canBeAffected(Effect.WIND_CHARGED));
            assertTrue(player.canBeAffected(Effect.WEAVING));
            assertTrue(player.canBeAffected(Effect.OOZING));
            assertTrue(player.canBeAffected(Effect.INFESTED));
        }

        private static TestPlayer createPlayer() {
            MockServer.reset();

            SourceInterface sourceInterface = Mockito.mock(SourceInterface.class);
            Mockito.when(sourceInterface.getSession(Mockito.any(InetSocketAddress.class)))
                    .thenReturn(Mockito.mock(NetworkPlayerSession.class));

            return new TestPlayer(sourceInterface);
        }

        private static final class TestPlayer extends Player {

            private TestPlayer(SourceInterface sourceInterface) {
                super(sourceInterface, 1L, new InetSocketAddress("127.0.0.1", 19132));
            }
        }
    }

    @Nested
    class LegacyLoginInventory {

        @Test
        void firstSpawnSendsSavedInventoryStateToLegacyClient() {
            TestPlayer player = createLegacyPlayer();
            PlayerInventory inventory = player.getInventory();
            Item heldItem = Item.get(ItemID.DIAMOND, 0, 3);
            Item helmet = Item.get(ItemID.IRON_HELMET);
            inventory.setItem(0, heldItem);
            inventory.setArmorItem(0, helmet, true);

            player.runFirstSpawn();

            int inventoryWindowId = player.getWindowId(inventory);
            ContainerSetContentPacket_v113 inventoryPacket = player.findContentPacket(inventoryWindowId);
            assertNotNull(inventoryPacket);
            assertEquals(ItemID.DIAMOND, inventoryPacket.slots[0].getId());
            assertEquals(3, inventoryPacket.slots[0].getCount());

            ContainerSetContentPacket_v113 armorPacket = player.findContentPacket(ContainerSetContentPacket_v113.SPECIAL_ARMOR);
            assertNotNull(armorPacket);
            assertEquals(ItemID.IRON_HELMET, armorPacket.slots[0].getId());

            assertTrue(player.sentPackets.stream()
                    .filter(MobEquipmentPacket.class::isInstance)
                    .map(MobEquipmentPacket.class::cast)
                    .anyMatch(packet -> packet.item.getId() == ItemID.DIAMOND && packet.item.getCount() == 3));
        }

        private static TestPlayer createLegacyPlayer() {
            MockServer.reset();
            Server server = MockServer.get();
            Mockito.when(server.getViewDistance()).thenReturn(2);

            Level level = server.getDefaultLevel();
            Position spawn = new Position(0, 64, 0, level);
            Mockito.when(level.getSafeSpawn()).thenReturn(spawn);
            Mockito.when(level.getSafeSpawn(Mockito.any(Position.class))).thenReturn(spawn);
            Mockito.when(level.getChunkEntities(Mockito.anyInt(), Mockito.anyInt())).thenReturn(Collections.emptyMap());

            SourceInterface sourceInterface = Mockito.mock(SourceInterface.class);
            Mockito.when(sourceInterface.getSession(Mockito.any(InetSocketAddress.class)))
                    .thenReturn(Mockito.mock(NetworkPlayerSession.class));

            TestPlayer player = new TestPlayer(sourceInterface);
            player.initializeLegacyInventoryState();
            return player;
        }

        private static final class TestPlayer extends Player {

            private final List<DataPacket> sentPackets = new ArrayList<>();

            private TestPlayer(SourceInterface sourceInterface) {
                super(sourceInterface, 1L, new InetSocketAddress("127.0.0.1", 19132));
            }

            private void initializeLegacyInventoryState() {
                this.protocol = ProtocolInfo.v1_1_0;
                this.gameVersion = GameVersion.V1_1_0;
                this.adventureSettings = new AdventureSettings(this);
                this.inventory = new PlayerInventory(this);
                this.offhandInventory = new PlayerOffhandInventory(this);
                this.addDefaultWindows();
            }

            private void runFirstSpawn() {
                this.doFirstSpawn();
            }

            private ContainerSetContentPacket_v113 findContentPacket(int windowId) {
                return this.sentPackets.stream()
                        .filter(ContainerSetContentPacket_v113.class::isInstance)
                        .map(ContainerSetContentPacket_v113.class::cast)
                        .filter(packet -> packet.windowid == windowId)
                        .findFirst()
                        .orElse(null);
            }

            @Override
            public boolean dataPacket(DataPacket packet) {
                this.sentPackets.add(packet);
                return true;
            }

            @Override
            public boolean setPosition(Vector3 pos) {
                this.x = pos.x;
                this.y = pos.y;
                this.z = pos.z;
                return true;
            }

            @Override
            public void sendPosition(Vector3 pos, double yaw, double pitch, double headYaw, int mode, Player[] targets) {
            }

            @Override
            public void checkSpawnBlockPosition() {
            }

            @Override
            public void spawnToAll() {
            }
        }
    }
}
