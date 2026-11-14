package cn.nukkit;

import cn.nukkit.block.Block;
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
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A block click where the client predicted the whole stack consumed reports air. The action must still
 * reach {@code Level.useItemOn}: the clicked block's own use (e.g. the last ender eye on a portal frame)
 * does not depend on the held item being activatable.
 */
class PlayerClickBlockPredictionTest {

    private TestPlayer player;
    private Level level;

    @BeforeEach
    void setUp() {
        MockServer.reset();
        Server server = MockServer.get();
        server.doNotLimitInteractions = true;
        level = mock(Level.class);
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
        doReturn(mock(Block.class)).when(level).getBlock(any(Vector3.class));
    }

    @AfterEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void predictedFullConsumptionReachesTheBlockUse() {
        Item eye = Item.get(ItemID.ENDER_EYE);
        player.getInventory().setItem(0, eye);
        when(level.useItemOn(any(), any(), any(), anyFloat(), anyFloat(), anyFloat(), any())).thenReturn(eye);

        player.handleInventoryTransactionPacket(clickBlock(Item.get(Item.AIR)));

        verify(level, times(1)).useItemOn(any(), any(), any(), anyFloat(), anyFloat(), anyFloat(), any());
    }

    @Test
    void matchingClientItemStillReachesTheBlockUse() {
        Item eye = Item.get(ItemID.ENDER_EYE);
        player.getInventory().setItem(0, eye);
        when(level.useItemOn(any(), any(), any(), anyFloat(), anyFloat(), anyFloat(), any())).thenReturn(eye);

        player.handleInventoryTransactionPacket(clickBlock(eye));

        verify(level, times(1)).useItemOn(any(), any(), any(), anyFloat(), anyFloat(), anyFloat(), any());
    }

    @Test
    void predictedFullConsumptionRejectsStacks() {
        player.getInventory().setItem(0, Item.get(ItemID.ENDER_EYE, 0, 2));

        player.handleInventoryTransactionPacket(clickBlock(Item.get(Item.AIR)));

        verify(level, never()).useItemOn(any(), any(), any(), anyFloat(), anyFloat(), anyFloat(), any());
    }

    private static InventoryTransactionPacket clickBlock(Item clientItem) {
        UseItemData data = new UseItemData();
        data.actionType = InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_BLOCK;
        data.blockPos = new BlockVector3(0, 0, 0);
        data.face = BlockFace.UP;
        data.hotbarSlot = 0;
        data.itemInHand = clientItem;
        data.playerPos = new Vector3();
        data.clickPos = new Vector3f(0.5f, 0.5f, 0.5f);
        InventoryTransactionPacket packet = new InventoryTransactionPacket();
        packet.transactionType = InventoryTransactionPacket.TYPE_USE_ITEM;
        packet.transactionData = data;
        packet.actions = new NetworkInventoryAction[0];
        return packet;
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
