package cn.nukkit;

import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.inventory.transaction.data.UseItemData;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import cn.nukkit.network.session.NetworkPlayerSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A Bedrock client that predicted a full stack consumption reports an air held item on the
 * air-use transaction; that prediction must still run the item use instead of a resync.
 */
class PlayerClickAirPredictionTest {

    private TestPlayer player;

    @BeforeEach
    void setUp() {
        TestItem.used = false;
        MockServer.reset();
        Server server = MockServer.get();
        server.doNotLimitInteractions = true;
        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        when(level.getGameRules()).thenReturn(GameRules.getDefault());
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        level.isBeingConverted = true;
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        when(server.getDefaultLevel()).thenReturn(level);
        SourceInterface source = mock(SourceInterface.class);
        when(source.getSession(any(InetSocketAddress.class))).thenReturn(mock(NetworkPlayerSession.class));
        player = new TestPlayer(source, level, chunk);
    }

    @AfterEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void predictedFullConsumptionStillUsesTheItem() {
        assertTrue(player.getInventory().setItem(0, new TestItem(1)));

        player.handleInventoryTransactionPacket(clickAir(Item.get(Item.AIR)));

        assertTrue(TestItem.used);
    }

    @Test
    void matchingClientItemStillUsesTheItem() {
        assertTrue(player.getInventory().setItem(0, new TestItem(2)));

        player.handleInventoryTransactionPacket(clickAir(Item.get(ItemID.SNOWBALL, 0, 2)));

        assertTrue(TestItem.used);
    }

    @Test
    void predictedFullConsumptionRejectsStacks() {
        assertTrue(player.getInventory().setItem(0, new TestItem(2)));

        player.handleInventoryTransactionPacket(clickAir(Item.get(Item.AIR)));

        assertFalse(TestItem.used);
    }

    private static InventoryTransactionPacket clickAir(Item clientItem) {
        UseItemData data = new UseItemData();
        data.actionType = InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_AIR;
        data.face = BlockFace.UP;
        data.hotbarSlot = 0;
        data.itemInHand = clientItem;
        InventoryTransactionPacket packet = new InventoryTransactionPacket();
        packet.transactionType = InventoryTransactionPacket.TYPE_USE_ITEM;
        packet.transactionData = data;
        packet.actions = new NetworkInventoryAction[0];
        return packet;
    }

    private static final class TestItem extends Item {

        private static boolean used;

        private TestItem(int count) {
            super(ItemID.SNOWBALL, 0, count, "Snowball");
        }

        @Override
        public boolean onClickAir(Player player, Vector3 directionVector) {
            used = true;
            return true;
        }
    }

    private static final class TestPlayer extends Player {

        TestPlayer(SourceInterface source, Level level, FullChunk chunk) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19132));
            protocol = GameVersion.getLastVersion().getProtocol();
            gameVersion = GameVersion.getLastVersion();
            spawned = loggedIn = loginVerified = true;
            gamemode = SURVIVAL;
            health = 20;
            this.level = level;
            this.chunk = chunk;
            inventory = new PlayerInventory(this);
            offhandInventory = new PlayerOffhandInventory(this);
            adventureSettings = new AdventureSettings(this);
            temporalVector = new Vector3();
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            return true;
        }
    }
}
