package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.entity.passive.EntityMooshroom;
import cn.nukkit.event.player.PlayerInteractEntityEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.inventory.transaction.data.UseItemOnEntityData;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import cn.nukkit.network.session.NetworkPlayerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Goes through Player's real USE_ITEM_ON_ENTITY dispatch, including its automatic debit. */
class PlayerMooshroomInteractionTest {
    private Level level;
    private cn.nukkit.plugin.PluginManager plugins;
    private EntityMooshroom cow;

    @BeforeEach
    void setUp() {
        MockServer.init();
        MockServer.reset();
        plugins = MockServer.get().getPluginManager();
        level = mock(Level.class);
        when(level.getServer()).thenReturn(MockServer.get());
        when(level.getGameRules()).thenReturn(GameRules.getDefault());
        when(level.getChunkPlayers(0, 0)).thenReturn(Map.of());
        level.isBeingConverted = true;
        when(MockServer.get().getDefaultLevel()).thenReturn(level);
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        cow = new EntityMooshroom(chunk, Entity.getDefaultNBT(new Vector3(0.5, 64, 0.5)).putInt("Variant", 1));
        when(level.getEntity(cow.getId())).thenReturn(cow);
    }

    @ParameterizedTest
    @ValueSource(ints = {589, 766, 2193})
    void cancelledInteractEventCannotConsumeOrChangeCow(int protocol) {
        TestPlayer player = player(protocol, Player.SURVIVAL);
        Item flower = Item.fromString("minecraft:poppy");
        flower.setCount(2);
        player.getInventory().setItemInHand(flower);
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof PlayerInteractEntityEvent event) event.setCancelled();
            return null;
        }).when(plugins).callEvent(any());

        interact(player);

        assertEquals(2, player.getInventory().getItemInHand().getCount());
        assertEquals(-1, cow.getDataPropertyInt(Entity.DATA_MARK_VARIANT));
    }

    @ParameterizedTest
    @CsvSource({"589,0", "766,0", "2193,0", "589,2", "766,2", "2193,2"})
    void dispatchConsumesFlowerAndStackedBowlExactlyOnce(int protocol, int mode) {
        TestPlayer player = player(protocol, mode);
        Item flower = Item.fromString("minecraft:poppy");
        flower.setCount(2);
        player.getInventory().setItemInHand(flower);
        interact(player);
        assertEquals(1, player.getInventory().getItemInHand().getCount());
        assertEquals(0, cow.getDataPropertyInt(Entity.DATA_MARK_VARIANT));

        player.getInventory().setItemInHand(Item.get(Item.BOWL, 0, 2));
        interact(player);
        assertEquals(Item.BOWL, player.getInventory().getItemInHand().getId());
        assertEquals(1, player.getInventory().getItemInHand().getCount());
        assertEquals(1, player.getInventory().getContents().values().stream()
                .filter(item -> item.getId() == Item.SUSPICIOUS_STEW && item.getDamage() == 0)
                .mapToInt(Item::getCount).sum());
        assertEquals(-1, cow.getDataPropertyInt(Entity.DATA_MARK_VARIANT));
    }

    @ParameterizedTest
    @ValueSource(ints = {589, 766, 2193})
    void dispatchDoesNotDeleteInPlaceStewAfterSingleBowlConversion(int protocol) {
        TestPlayer player = player(protocol, Player.SURVIVAL);
        cow.setDataProperty(new IntEntityData(Entity.DATA_MARK_VARIANT, 9));
        player.getInventory().setItemInHand(Item.get(Item.BOWL, 0, 1));
        interact(player);
        assertEquals(Item.SUSPICIOUS_STEW, player.getInventory().getItemInHand().getId());
        assertEquals(9, player.getInventory().getItemInHand().getDamage());
        assertEquals(1, player.getInventory().getItemInHand().getCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {589, 766, 2193})
    void spectatorDispatchNeverFeedsOrMilksCow(int protocol) {
        TestPlayer player = player(protocol, Player.SPECTATOR);
        cow.setDataProperty(new IntEntityData(Entity.DATA_MARK_VARIANT, 9));
        player.getInventory().setItemInHand(Item.get(Item.BOWL, 0, 1));
        interact(player);
        assertEquals(Item.BOWL, player.getInventory().getItemInHand().getId());
        assertEquals(9, cow.getDataPropertyInt(Entity.DATA_MARK_VARIANT));
    }

    @ParameterizedTest
    @ValueSource(ints = {589, 766, 2193})
    void creativeDispatchKeepsInputsAndProducesExactlyOneStew(int protocol) {
        TestPlayer player = player(protocol, Player.CREATIVE);
        player.getInventory().setItemInHand(Item.fromString("minecraft:poppy"));
        interact(player);
        assertEquals(1, player.getInventory().getItemInHand().getCount());
        assertEquals(0, cow.getDataPropertyInt(Entity.DATA_MARK_VARIANT));
        player.getInventory().setItemInHand(Item.get(Item.BOWL, 0, 1));
        interact(player);
        assertEquals(Item.BOWL, player.getInventory().getItemInHand().getId());
        assertEquals(1, player.getInventory().getItemInHand().getCount());
        assertEquals(1, player.getInventory().getContents().values().stream()
                .filter(item -> item.getId() == Item.SUSPICIOUS_STEW).mapToInt(Item::getCount).sum());
        assertEquals(-1, cow.getDataPropertyInt(Entity.DATA_MARK_VARIANT));
    }

    private void interact(TestPlayer player) {
        UseItemOnEntityData data = new UseItemOnEntityData();
        data.entityRuntimeId = cow.getId();
        data.actionType = InventoryTransactionPacket.USE_ITEM_ON_ENTITY_ACTION_INTERACT;
        data.hotbarSlot = 0;
        data.itemInHand = player.getInventory().getItemInHand();
        data.playerPos = new Vector3(player.x, player.y, player.z);
        data.clickPos = new Vector3();
        InventoryTransactionPacket packet = new InventoryTransactionPacket();
        packet.transactionType = InventoryTransactionPacket.TYPE_USE_ITEM_ON_ENTITY;
        packet.transactionData = data;
        packet.actions = new NetworkInventoryAction[0];
        player.handleInventoryTransactionPacket(packet);
    }

    private TestPlayer player(int protocol, int mode) {
        SourceInterface source = mock(SourceInterface.class);
        when(source.getSession(any(InetSocketAddress.class))).thenReturn(mock(NetworkPlayerSession.class));
        return new TestPlayer(source, protocol, mode);
    }

    private final class TestPlayer extends Player {
        TestPlayer(SourceInterface source, int protocol, int mode) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 1));
            this.level = PlayerMooshroomInteractionTest.this.level;
            this.protocol = protocol;
            this.gameVersion = GameVersion.byProtocol(protocol, false);
            this.gamemode = mode;
            this.x = 0.5;
            this.y = 64;
            this.z = 0.5;
            this.spawned = true;
            this.loggedIn = true;
            this.inventory = new PlayerInventory(this);
            this.offhandInventory = new PlayerOffhandInventory(this);
            this.namedTag = new CompoundTag();
            this.adventureSettings = new AdventureSettings(this);
            this.playerUIInventory = new PlayerUIInventory(this);
            this.craftingGrid = this.playerUIInventory.getCraftingGrid();
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            return true;
        }
    }
}
