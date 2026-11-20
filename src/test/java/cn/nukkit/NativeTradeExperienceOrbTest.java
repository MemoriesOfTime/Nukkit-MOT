package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.event.entity.EntityDespawnEvent;
import cn.nukkit.event.inventory.ItemStackRequestActionEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.inventory.TradeInventory;
import cn.nukkit.inventory.TradeInventoryRecipe;
import cn.nukkit.inventory.request.ItemStackRequestHandler;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.ItemStackResponsePacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.network.protocol.types.ContainerIds;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ConsumeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftRecipeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.PlaceAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.TradeRecipeBuildUtils;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NativeTradeExperienceOrbTest {
    @BeforeAll
    static void initialize() {
        MockServer.init();
    }

    @Test
    void acceptedModernTradePublishesOneOrbAndRepairsThroughNativePickup() throws Exception {
        try (Fixture f = new Fixture(true, true, 1)) {
            f.modernTrade(false);
            f.assertAcceptedTrade();
            assertEquals(ItemStackResponseStatus.OK, f.responseStatus());
            f.assertOneOrbThenRepair();
            assertEquals(1, f.recipe.getInt("uses"));
        }
    }

    @Test
    void acceptedLegacyTradePublishesOneOrbAndRepairsThroughNativePickup() throws Exception {
        try (Fixture f = new Fixture(false, true, 1)) {
            f.legacyTrade(true);
            f.assertAcceptedTrade();
            f.assertOneOrbThenRepair();
        }
    }

    @Test
    void cancelledModernConsumeRollsBackBeforePublishingAnyExperience() throws Exception {
        try (Fixture f = new Fixture(true, true, 1)) {
            doAnswer(call -> {
                ItemStackRequestActionEvent event = call.getArgument(0);
                if (event.getAction() instanceof ConsumeAction) {
                    event.setCancelled();
                }
                return null;
            }).when(f.plugins).callEvent(any(ItemStackRequestActionEvent.class));
            f.modernTrade(false);
            assertEquals(ItemStackResponseStatus.ERROR, f.responseStatus());
            f.assertRefusedTrade();
            assertEquals(0, f.recipe.getInt("uses"));
        }
    }

    @Test
    void foreignModernRecipeCannotPublishExperienceOrRepairEquipment() throws Exception {
        try (Fixture f = new Fixture(true, true, 1)) {
            f.modernTrade(true);
            assertEquals(ItemStackResponseStatus.ERROR, f.responseStatus());
            f.assertRefusedTrade();
        }
    }

    @Test
    void invalidLegacyTradeCannotPublishExperienceOrRepairEquipment() throws Exception {
        try (Fixture f = new Fixture(false, true, 1)) {
            f.legacyTrade(false);
            f.assertRefusedTrade();
        }
    }

    @Test
    void modernTradeWithoutExperienceRewardStillTransfersItemsOnly() throws Exception {
        try (Fixture f = new Fixture(true, true, 0)) {
            f.modernTrade(false);
            f.assertAcceptedTrade();
            f.assertNoExperience();
        }
    }

    @Test
    void legacyTradeWithoutExperienceRewardStillTransfersItemsOnly() throws Exception {
        try (Fixture f = new Fixture(false, true, 0)) {
            f.legacyTrade(true);
            f.assertAcceptedTrade();
            f.assertNoExperience();
        }
    }

    @Test
    void modernTradeWithoutMendingCreditsTheCollectedOrbOnce() throws Exception {
        try (Fixture f = new Fixture(true, false, 1)) {
            f.modernTrade(false);
            f.assertAcceptedTrade();
            f.assertOneOrbThenCredit();
        }
    }

    @Test
    void legacyTradeWithoutMendingCreditsTheCollectedOrbOnce() throws Exception {
        try (Fixture f = new Fixture(false, false, 1)) {
            f.legacyTrade(true);
            f.assertAcceptedTrade();
            f.assertOneOrbThenCredit();
        }
    }

    @Test
    void recursivePickupDuringOrbCloseCannotRepairModernTradeTwice() throws Exception {
        try (Fixture f = new Fixture(true, true, 1)) {
            f.modernTrade(false);
            f.assertAcceptedTrade();
            f.assertRecursivePickupRepairsOnce();
        }
    }

    @Test
    void recursivePickupDuringOrbCloseCannotRepairLegacyTradeTwice() throws Exception {
        try (Fixture f = new Fixture(false, true, 1)) {
            f.legacyTrade(true);
            f.assertAcceptedTrade();
            f.assertRecursivePickupRepairsOnce();
        }
    }

    @Test
    void administrativeExperienceRemainsImmediateAndDoesNotRepairEquipment() throws Exception {
        try (Fixture f = new Fixture(true, true, 1)) {
            f.player.addExperience(1);
            assertEquals(1, f.player.getExperience());
            assertEquals(0, f.player.getExperienceLevel());
            assertEquals(50, f.inventory.getItemInHand().getDamage());
            assertTrue(f.orbs.isEmpty());
            assertEquals(2, f.trade.getItem(0).getCount());
        }
    }

    /** Real request/transaction handlers, native inventories, orb constructor and pickup. */
    private static final class Fixture implements AutoCloseable {
        final Server server = MockServer.get();
        final PluginManager originalPlugins = server.getPluginManager();
        final int originalTick = server.getTick();
        final PluginManager plugins = mock(PluginManager.class);
        final NetworkPlayerSession session = mock(NetworkPlayerSession.class);
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final Level level = mock(Level.class);
        final BaseFullChunk chunk = mock(BaseFullChunk.class);
        final PlayerInventory inventory = new PlayerInventory(player);
        final PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        final PlayerUIInventory ui = new PlayerUIInventory(player);
        final EntityVillager villager = mock(EntityVillager.class);
        final TradeInventory trade = new TradeInventory(villager);
        final List<EntityXPOrb> orbs = new ArrayList<>();
        final List<ItemStackResponsePacket> responses = new ArrayList<>();
        final CompoundTag recipe;
        final MockedStatic<Entity> entityFactory;

        Fixture(boolean modern, boolean mending, int experience) throws Exception {
            when(server.getPluginManager()).thenReturn(plugins);
            when(server.getTick()).thenReturn(20);
            set(player, "server", server);
            set(player, "inventory", inventory);
            set(player, "offhandInventory", offhand);
            set(player, "playerUIInventory", ui);
            set(player, "craftingGrid", ui.getCraftingGrid());
            set(player, "networkSession", session);
            // Optional public window-lifecycle fields also need their constructor sentinels.
            setIfPresent(player, "pendingCloseWindowId", Integer.MIN_VALUE);
            setIfPresent(player, "closingWindowId", Integer.MIN_VALUE);
            BiMap<Inventory, Integer> windows = HashBiMap.create();
            set(player, "windows", windows);
            set(player, "windowIndex", windows.inverse());
            set(player, "permanentWindows", new IntOpenHashSet());
            // Mockito skips Entity's constructor: native inventory sends need its viewer map.
            set(player, "hasSpawned", new ConcurrentHashMap<Integer, Player>());
            player.level = level;
            player.spawned = true;
            player.connected = true;
            player.protocol = modern ? ProtocolInfo.v1_21_130 : ProtocolInfo.v1_20_0;
            player.gameVersion = modern ? GameVersion.V1_21_130 : GameVersion.V1_20_0;
            player.x = 0.5;
            player.y = 64;
            player.z = 0.5;
            player.boundingBox = new SimpleAxisAlignedBB(0, 63, 0, 1, 66, 1);
            doReturn(true).when(player).isAlive();
            doReturn(true).when(player).isOnline();
            doReturn(false).when(player).isSpectator();
            doReturn(false).when(player).isCreative();
            doReturn(modern).when(player).isInventoryServerAuthoritative();
            doReturn("native-trader").when(player).getName();
            doAnswer(call -> {
                if (call.getArgument(0) instanceof ItemStackResponsePacket response) {
                    responses.add(response);
                }
                return null;
            }).when(session).sendPacket(any(DataPacket.class));

            when(level.getServer()).thenReturn(server);
            when(level.getGameRules()).thenReturn(GameRules.getDefault());
            when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
            level.isBeingConverted = true;
            LevelProvider provider = mock(LevelProvider.class);
            when(provider.getLevel()).thenReturn(level);
            when(chunk.getProvider()).thenReturn(provider);
            when(level.getChunk(anyInt(), anyInt())).thenReturn(chunk);
            doCallRealMethod().when(level).dropExpOrb(any(Vector3.class), anyInt());
            doCallRealMethod().when(level).dropExpOrb(any(Vector3.class), anyInt(), nullable(Vector3.class));
            doCallRealMethod().when(level).dropExpOrb(any(Vector3.class), anyInt(), nullable(Vector3.class), anyInt());

            villager.namedTag = new CompoundTag();
            villager.tierExpRequirement = new int[]{0, 10, 70, 150, 250};
            recipe = new TradeInventoryRecipe(Item.get(Item.APPLE), Item.get(Item.COAL))
                    .setRewardExp(experience).setTraderExp(1).toNBT();
            if (!modern) {
                // Legacy TradeAction requires explicit namespaces in public recipe NBT.
                // Publish that server-authored form; the stock builder's omitted Name is a separate defect.
                recipe.getCompound("buyA").putString("Name", Item.get(Item.COAL).getNamespaceId());
                recipe.getCompound("sell").putString("Name", Item.get(Item.APPLE).getNamespaceId());
            }
            ListTag<Tag> recipes = new ListTag<>("Recipes");
            recipes.add(recipe);
            when(villager.getRecipes()).thenReturn(recipes);
            when(villager.getNameTag()).thenReturn("Villager");
            assertEquals(ContainerIds.INVENTORY,
                    player.addWindow(inventory, ContainerIds.INVENTORY, true, true));
            assertEquals(ContainerIds.UI, player.addWindow(ui, ContainerIds.UI, true, true));
            assertEquals(ContainerIds.OFFHAND, player.addWindow(offhand, ContainerIds.OFFHAND, true, true));
            assertEquals(Player.TRADE_WINDOW_ID, player.addWindow(trade, Player.TRADE_WINDOW_ID));
            assertTrue(trade.setItem(0, Item.get(Item.COAL, 0, 2), false));

            Item tool = Item.get(Item.DIAMOND_PICKAXE, 50, 1);
            if (mending) {
                tool.addEnchantment(Enchantment.get(Enchantment.ID_MENDING));
            }
            assertTrue(inventory.setItem(0, tool, false));
            inventory.setHeldItemIndex(0, false);
            responses.clear();

            // Substitute only registry dispatch; create the actual native orb from Level's NBT.
            entityFactory = mockStatic(Entity.class, CALLS_REAL_METHODS);
            entityFactory.when(() -> Entity.createEntity(eq("XpOrb"), same(chunk), any(CompoundTag.class)))
                    .thenAnswer(call -> {
                        EntityXPOrb orb = new EntityXPOrb(chunk, call.getArgument(2));
                        orbs.add(orb);
                        return orb;
                    });
        }

        void modernTrade(boolean foreignRecipe) {
            int recipeId = recipe.getInt("netId") + (foreignRecipe ? 1 : 0);
            Item input = trade.getItem(0);
            ItemStackRequestAction[] actions = {
                    new CraftRecipeAction(recipeId, 1),
                    new ConsumeAction(1, new ItemStackRequestSlotData(
                            ContainerSlotType.TRADE2_INGREDIENT_1, 0, input.getStackNetId(), null)),
                    new PlaceAction(1,
                            new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT,
                                    PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, 0, null),
                            new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 1, 0, null))
            };
            ItemStackRequestHandler.handleRequests(player, List.of(new ItemStackRequest(1, actions, new String[0])));
        }

        void legacyTrade(boolean valid) {
            assertEquals(Item.get(Item.APPLE).getNamespaceId(), recipe.getCompound("sell").getString("Name"),
                    "legacy caller uses an explicitly published server-authored recipe");
            InventoryTransactionPacket packet = new InventoryTransactionPacket();
            packet.transactionType = InventoryTransactionPacket.TYPE_NORMAL;
            packet.isTradeItemPart = true;
            packet.actions = new NetworkInventoryAction[]{
                    action(NetworkInventoryAction.SOURCE_CONTAINER, Player.TRADE_WINDOW_ID, 0,
                            trade.getItem(0), Item.get(Item.COAL)),
                    action(NetworkInventoryAction.SOURCE_CONTAINER, ContainerIds.INVENTORY, 1,
                            Item.AIR_ITEM, Item.get(Item.APPLE)),
                    action(NetworkInventoryAction.SOURCE_TODO,
                            NetworkInventoryAction.SOURCE_TYPE_TRADING_OUTPUT, 0,
                            Item.get(valid ? Item.APPLE : Item.DIAMOND), Item.AIR_ITEM)
            };
            player.handleInventoryTransactionPacket(packet);
        }

        void assertAcceptedTrade() {
            assertEquals(Item.COAL, trade.getItem(0).getId());
            assertEquals(1, trade.getItem(0).getCount(), "accepted trade spends exactly one source item");
            assertEquals(Item.APPLE, inventory.getItem(1).getId());
            assertEquals(1, inventory.getItem(1).getCount(), "accepted trade delivers exactly one output");
            assertEquals(50, inventory.getItemInHand().getDamage(), "repair waits for orb pickup");
        }

        void assertRefusedTrade() {
            assertEquals(2, trade.getItem(0).getCount());
            assertTrue(inventory.getItem(1).isNull());
            assertNoExperience();
        }

        void assertNoExperience() {
            assertTrue(orbs.isEmpty());
            assertEquals(0, player.getExperience());
            assertEquals(0, player.getExperienceLevel());
            assertEquals(50, inventory.getItemInHand().getDamage());
        }

        EntityXPOrb requireOrb() {
            assertEquals(1, orbs.size(), "accepted trade must publish its earned XP as one real orb");
            EntityXPOrb orb = orbs.get(0);
            assertEquals(1, orb.getExp());
            assertEquals(10, orb.getPickupDelay(), "retain native orb pickup delay");
            assertEquals(0, player.getExperience(), "publishing an orb must not also credit direct XP");
            assertFalse(player.pickupEntity(orb, true), "fresh orb must respect native pickup delay");
            orb.setPickupDelay(0);
            return orb;
        }

        void assertOneOrbThenRepair() {
            EntityXPOrb orb = requireOrb();
            assertTrue(player.pickupEntity(orb, true));
            assertTrue(orb.isClosed());
            assertEquals(48, inventory.getItemInHand().getDamage());
            assertEquals(0, player.getExperience());
            assertFalse(player.pickupEntity(orb, true));
            assertEquals(48, inventory.getItemInHand().getDamage());
        }

        void assertOneOrbThenCredit() {
            EntityXPOrb orb = requireOrb();
            assertTrue(player.pickupEntity(orb, true));
            assertEquals(1, player.getExperience());
            assertEquals(50, inventory.getItemInHand().getDamage());
            assertTrue(orb.isClosed());
            assertFalse(player.pickupEntity(orb, true));
            assertEquals(1, player.getExperience());
        }

        void assertRecursivePickupRepairsOnce() {
            EntityXPOrb orb = requireOrb();
            AtomicBoolean callback = new AtomicBoolean();
            doAnswer(call -> {
                EntityDespawnEvent event = call.getArgument(0);
                if (event.getEntity() == orb) {
                    callback.set(true);
                    assertFalse(player.pickupEntity(orb, true));
                    assertEquals(50, inventory.getItemInHand().getDamage());
                }
                return null;
            }).when(plugins).callEvent(any(EntityDespawnEvent.class));
            assertTrue(player.pickupEntity(orb, true));
            assertTrue(callback.get());
            assertEquals(48, inventory.getItemInHand().getDamage());
            assertEquals(0, player.getExperience());
            assertEquals(1, trade.getItem(0).getCount());
            assertEquals(1, inventory.getItem(1).getCount());
        }

        ItemStackResponseStatus responseStatus() {
            assertEquals(1, responses.size());
            return responses.get(0).entries.get(0).getResult();
        }

        @Override
        public void close() {
            try {
                for (EntityXPOrb orb : orbs) {
                    if (!orb.isClosed()) {
                        orb.close();
                    }
                }
                TradeRecipeBuildUtils.RECIPE_MAP.remove(recipe.getInt("netId"));
            } finally {
                entityFactory.close();
                when(server.getPluginManager()).thenReturn(originalPlugins);
                when(server.getTick()).thenReturn(originalTick);
            }
        }

        private static NetworkInventoryAction action(int source, int window, int slot, Item oldItem, Item newItem) {
            NetworkInventoryAction action = new NetworkInventoryAction();
            action.sourceType = source;
            action.windowId = window;
            action.inventorySlot = slot;
            action.oldItem = oldItem;
            action.newItem = newItem;
            return action;
        }

        private static void set(Object object, String name, Object value) throws Exception {
            for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    field.set(object, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw new NoSuchFieldException(name);
        }

        private static void setIfPresent(Object object, String name, Object value) throws Exception {
            try {
                set(object, name, value);
            } catch (NoSuchFieldException absentOnStandaloneMaster) {
                // Keep this fixture portable before and after public window-lifecycle changes.
            }
        }
    }
}
