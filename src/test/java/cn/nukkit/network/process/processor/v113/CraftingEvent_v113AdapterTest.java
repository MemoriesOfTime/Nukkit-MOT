package cn.nukkit.network.process.processor.v113;

import cn.nukkit.*;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.event.player.PlayerAchievementAwardedEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.CraftingEventPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.ContainerIds;
import cn.nukkit.network.protocol.v113.ContainerSetSlotPacket_v113;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 1.1.x 合成走 CraftingEventPacket 的 input/output 快照，结算发生在玩家背包。
 * <p>
 * 1.1.x crafting is validated against the packet snapshot and settled on the player inventory.
 */
class CraftingEvent_v113AdapterTest {

    private CraftingManager craftingManager;

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void setUp() {
        MockServer.reset();
        craftingManager = Mockito.mock(CraftingManager.class);
        Mockito.lenient().when(MockServer.get().getCraftingManager()).thenReturn(craftingManager);
    }

    @Test
    void craftsShapelessRecipeFromPacketSnapshot() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        player.getInventory().setItem(0, Item.get(BlockID.LOG));

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertFalse(player.getInventory().contains(Item.get(BlockID.LOG)));
        assertTrue(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    @Test
    void usesPlayerServerForInventoryChangeEvents() {
        Server playerServer = Mockito.mock(Server.class);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.when(playerServer.getPluginManager()).thenReturn(pluginManager);
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent change) {
                change.setCancelled(true);
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));
        TestPlayer player = new TestPlayer(playerServer);
        player.getInventory().setItemForce(0, Item.get(BlockID.LOG));
        ShapelessRecipe recipe = logToPlanksRecipe();

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertTrue(player.getInventory().contains(Item.get(BlockID.LOG)));
        assertFalse(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
        Mockito.verify(pluginManager).callEvent(any(EntityInventoryChangeEvent.class));
        Mockito.verify(MockServer.get().getPluginManager(), Mockito.never())
                .callEvent(any(EntityInventoryChangeEvent.class));
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void preservesShulkerContentsInEventAndSettledOutputWithoutTrustingClientNbt(boolean dyeFirst, boolean hasContents) {
        TestPlayer player = new TestPlayer();
        Item shulker = Item.get(BlockID.UNDYED_SHULKER_BOX);
        if (hasContents) {
            shulker.setNamedTag(new CompoundTag().putList(new ListTag<CompoundTag>("Items")
                    .add(new CompoundTag().putByte("Slot", 0).putShort("id", ItemID.DIAMOND)
                            .putByte("Count", 32).putShort("Damage", 0))));
        }
        Item dye = Item.get(Item.DYE);
        dye.setCustomName("named dye");
        int shulkerSlot = dyeFirst ? 1 : 0;
        int dyeSlot = dyeFirst ? 0 : 1;
        player.getInventory().setItemForce(shulkerSlot, shulker);
        player.getInventory().setItemForce(dyeSlot, dye);
        UserDataShapelessRecipe recipe = new UserDataShapelessRecipe(Item.get(BlockID.SHULKER_BOX, 15),
                List.of(Item.get(BlockID.UNDYED_SHULKER_BOX), Item.get(Item.DYE)));
        Item forgedOutput = recipe.getResult();
        forgedOutput.setCustomName("forged client output");
        PluginManager pluginManager = MockServer.get().getPluginManager();

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(shulker, shulkerSlot, dye, dyeSlot), new Item[]{forgedOutput}));

        ArgumentCaptor<CraftItemEvent> event = ArgumentCaptor.forClass(CraftItemEvent.class);
        Mockito.verify(pluginManager).callEvent(event.capture());
        Item expected = recipe.getResult();
        expected.setCompoundTag(shulker.getCompoundTag());
        assertTrue(event.getValue().getTransaction().getPrimaryOutput().equalsExact(expected));
        assertTrue(player.getInventory().getItem(0).equalsExact(expected));
        assertEquals(1, player.getInventory().getContents().size());
        assertFalse(recipe.getResult().hasCompoundTag(), "继承输入 NBT 不得污染配方模板");
    }

    @Test
    void consumesOneItemPerGridCellNotTheWholeStack() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        player.getInventory().setItem(0, Item.get(BlockID.LOG, 0, 5));

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 5), 0), output(recipe)));

        assertEquals(4, player.getInventory().getItem(0).getCount());
        assertTrue(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    @Test
    void craftsShapedRecipeFromPacketSnapshot() {
        TestPlayer player = new TestPlayer();
        ShapedRecipe recipe = planksToCraftingTable();
        player.getInventory().setItem(0, Item.get(BlockID.PLANKS, 0, 4));

        Item[] input = grid(
                item(BlockID.PLANKS, 0, 1), 0,
                item(BlockID.PLANKS, 0, 1), 1,
                item(BlockID.PLANKS, 0, 1), 3,
                item(BlockID.PLANKS, 0, 1), 4);

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe, input, output(recipe)));
        assertTrue(player.getInventory().contains(Item.get(BlockID.WORKBENCH)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void awardsCraftingAchievementOnlyAfterSuccessfulSettlement(int cancellation) {
        TestPlayer player = new TestPlayer();
        ShapedRecipe recipe = planksToCraftingTable();
        recipe.setId(java.util.UUID.randomUUID());
        player.achievements.addAll(List.of("openInventory", "mineWood"));
        player.getInventory().setItem(0, item(BlockID.PLANKS, 0, 4));
        Mockito.when(craftingManager.getRecipes()).thenReturn(List.of(recipe));
        Mockito.when(MockServer.get().getLanguage()).thenReturn(new BaseLang("eng"));
        PluginManager pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (cancellation == 1 && event instanceof CraftItemEvent craftEvent) {
                craftEvent.setCancelled(true);
            } else if (cancellation == 2 && event instanceof EntityInventoryChangeEvent change
                    && change.getNewItem().getId() == BlockID.WORKBENCH) {
                change.setCancelled(true);
            } else if (cancellation == 3 && event instanceof PlayerAchievementAwardedEvent achievement) {
                achievement.setCancelled(true);
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        CraftingEventPacket packet = new CraftingEventPacket();
        packet.protocol = ProtocolInfo.v1_1_0;
        packet.gameVersion = GameVersion.V1_1_0;
        packet.windowId = ContainerIds.INVENTORY;
        packet.id = recipe.getId();
        packet.input = grid(item(BlockID.PLANKS, 0, 1), 0, item(BlockID.PLANKS, 0, 1), 1,
                item(BlockID.PLANKS, 0, 1), 3, item(BlockID.PLANKS, 0, 1), 4);
        packet.output = output(recipe);

        boolean achievementsEnabled = MockServer.get().achievementsEnabled;
        MockServer.get().achievementsEnabled = true;
        try {
            CraftingEventProcessor_v113.INSTANCE.handle(new PlayerHandle(player), packet);

            assertEquals(cancellation == 0, player.hasAchievement("buildWorkBench"));
            assertEquals(cancellation == 0 || cancellation == 3,
                    player.getInventory().contains(Item.get(BlockID.WORKBENCH)));
            assertEquals(cancellation == 1 || cancellation == 2,
                    player.getInventory().contains(item(BlockID.PLANKS, 0, 4)));
        } finally {
            MockServer.get().achievementsEnabled = achievementsEnabled;
        }
    }

    @Test
    void acceptsShapedRecipePlacedAtGridOffset() {
        TestPlayer player = new TestPlayer();
        ShapedRecipe recipe = planksToCraftingTable();
        player.getInventory().setItem(0, Item.get(BlockID.PLANKS, 0, 4));

        Item[] input = grid(
                item(BlockID.PLANKS, 0, 1), 4,
                item(BlockID.PLANKS, 0, 1), 5,
                item(BlockID.PLANKS, 0, 1), 7,
                item(BlockID.PLANKS, 0, 1), 8);

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe, input, output(recipe)));
        assertTrue(player.getInventory().contains(Item.get(BlockID.WORKBENCH)));
    }

    @Test
    void rejectsCraftingTableRecipeInSmallGrid() {
        TestPlayer player = new TestPlayer();
        ShapedRecipe recipe = threeByThreeRecipe();
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, Item.get(BlockID.PLANKS));
        }

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe, fullPlankGrid(), output(recipe)));
        assertFalse(player.getInventory().contains(Item.get(ItemID.DIAMOND)));
    }

    @Test
    void craftsThreeByThreeRecipeInBigGrid() {
        TestPlayer player = new TestPlayer();
        player.craftingType = Player.CRAFTING_BIG;
        ShapedRecipe recipe = threeByThreeRecipe();
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, Item.get(BlockID.PLANKS));
        }

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe, fullPlankGrid(), output(recipe)));
        assertTrue(player.getInventory().contains(Item.get(ItemID.DIAMOND)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptsTableRecipeWithRetainedBigGridEvenWhenChestIsOpen(boolean chestOpen) {
        TestPlayer player = new TestPlayer();
        // 打开工作台后 UI 槽位更新可能把 craftingType 重置为 SMALL，但服务端合成格仍是 3x3。
        // Opening a table can reset craftingType to SMALL while the server grid stays 3x3.
        player.setCraftingGrid(player.getUIInventory().getBigCraftingGrid());
        player.craftingType = Player.CRAFTING_SMALL;
        if (chestOpen) {
            ChestInventory chest = new ChestInventory(Mockito.mock(BlockEntityChest.class));
            assertNotEquals(-1, player.addWindow(chest));
            assertSame(chest, player.getTopWindow().orElseThrow());
            assertEquals(Player.CRAFTING_SMALL, player.craftingType);
        }
        ShapedRecipe recipe = threeByThreeRecipe();
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, Item.get(BlockID.PLANKS));
        }

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe, fullPlankGrid(), output(recipe)));
        assertTrue(player.getInventory().contains(Item.get(ItemID.DIAMOND)));
    }

    @Test
    void rejectsTableRecipeWhenAnotherUiIsOpenEvenWithBigGrid() {
        TestPlayer player = new TestPlayer();
        player.setCraftingGrid(player.getUIInventory().getBigCraftingGrid());
        player.craftingType = Player.ENCHANT_WINDOW_ID;
        ShapedRecipe recipe = threeByThreeRecipe();
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, Item.get(BlockID.PLANKS));
        }

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe, fullPlankGrid(), output(recipe)));
        assertFalse(player.getInventory().contains(Item.get(ItemID.DIAMOND)));
    }

    @Test
    void rejectsMismatchedPacketOutput() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        player.getInventory().setItem(0, Item.get(BlockID.LOG));

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0),
                new Item[]{Item.get(ItemID.DIAMOND)}));

        assertTrue(player.getInventory().contains(Item.get(BlockID.LOG)));
    }

    @Test
    void rejectsInputThatDoesNotMatchRecipe() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        player.getInventory().setItem(0, Item.get(BlockID.LOG));

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.DIRT, 0, 1), 0), output(recipe)));

        assertTrue(player.getInventory().contains(Item.get(BlockID.LOG)));
    }

    @Test
    void rejectsWhenInventoryLacksMaterials() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertFalse(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    @Test
    void rollsBackEarlierConsumptionWhenLaterSlotChangeIsCancelled() {
        TestPlayer player = new TestPlayer();
        ShapedRecipe recipe = planksToCraftingTable();
        player.getInventory().setItem(0, Item.get(BlockID.PLANKS, 0, 3));
        player.getInventory().setItem(1, Item.get(BlockID.PLANKS));
        Item[] input = grid(item(BlockID.PLANKS, 0, 1), 0, item(BlockID.PLANKS, 0, 1), 1,
                item(BlockID.PLANKS, 0, 1), 3, item(BlockID.PLANKS, 0, 1), 4);
        PluginManager pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent event
                    && (event.getSlot() == 1 || event.getNewItem().getCount() > event.getOldItem().getCount())) {
                // 加数量事件同样拦截，避免中止路径通过 setItem 回补材料。
                // Count increases are rejected too, so an abort cannot refund through setItem.
                event.setCancelled(true);
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe, input, output(recipe)));
        assertEquals(3, player.getInventory().getItem(0).getCount());
        assertEquals(1, player.getInventory().getItem(1).getCount());
        assertFalse(player.getInventory().contains(Item.get(BlockID.WORKBENCH)));
    }

    @Test
    void consumesPacketMaterialWithoutTakingEarlierNamedStack() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        Item namedLog = Item.get(BlockID.LOG);
        namedLog.setCustomName("PreciousLog");
        player.getInventory().setItem(0, namedLog);
        player.getInventory().setItem(1, Item.get(BlockID.LOG));

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertTrue(player.getInventory().getItem(0).equalsExact(namedLog));
        assertTrue(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
        assertEquals(1, player.getInventory().getContents().values().stream()
                .filter(item -> item.getId() == BlockID.LOG).mapToInt(Item::getCount).sum());
    }

    @ParameterizedTest
    @CsvSource({"1, 1, false", "1, 2, false", "2, 1, false", "2, 2, true", "5, 5, true"})
    void respectsShapedIngredientCount(int gridCount, int inventoryCount, boolean succeeds) {
        TestPlayer player = new TestPlayer();
        player.getInventory().setItem(0, Item.get(BlockID.LOG, 0, inventoryCount));
        ShapedRecipe recipe = new ShapedRecipe(Item.get(BlockID.PLANKS, 0, 8),
                new String[]{"L"}, Map.of('L', Item.get(BlockID.LOG, 0, 2)), List.of());

        assertEquals(succeeds, CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, gridCount), 0), output(recipe)));

        assertEquals(inventoryCount - (succeeds ? 2 : 0), player.getInventory().getContents().values().stream()
                .filter(item -> item.getId() == BlockID.LOG).mapToInt(Item::getCount).sum());
        assertEquals(succeeds, player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 8)));
    }

    @Test
    void consumesStackedShapedMaterialAcrossInventorySlotsForMirroredRecipe() {
        TestPlayer player = new TestPlayer();
        player.craftingType = Player.CRAFTING_BIG;
        player.getInventory().setItem(0, Item.get(BlockID.LOG));
        player.getInventory().setItem(1, Item.get(BlockID.LOG));
        player.getInventory().setItem(2, Item.get(BlockID.STONE));
        ShapedRecipe recipe = new ShapedRecipe(Item.get(ItemID.DIAMOND), new String[]{"LS"},
                Map.of('L', Item.get(BlockID.LOG, 0, 2), 'S', Item.get(BlockID.STONE)), List.of());

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.STONE, 0, 1), 4, item(BlockID.LOG, 0, 2), 5), output(recipe)));

        assertFalse(player.getInventory().contains(Item.get(BlockID.LOG)));
        assertFalse(player.getInventory().contains(Item.get(BlockID.STONE)));
        assertTrue(player.getInventory().contains(Item.get(ItemID.DIAMOND)));
    }

    @ParameterizedTest
    @CsvSource({"1, false", "3, false", "1, true", "3, true"})
    void rejectsCancelledOrRewrittenConsumption(int count, boolean rewrite) {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        Item original = Item.get(BlockID.LOG, 0, count);
        player.getInventory().setItem(0, original);
        PluginManager pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent event
                    && event.getOldItem().getId() == BlockID.LOG) {
                if (rewrite) {
                    event.setNewItem(event.getOldItem().clone());
                } else {
                    event.setCancelled(true);
                }
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(original.clone(), 0), output(recipe)));

        assertTrue(player.getInventory().getItem(0).equalsExact(original));
        assertFalse(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    @Test
    void rejectsInPlaceRewriteOfConsumption() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        Item original = Item.get(BlockID.LOG, 0, 3);
        player.getInventory().setItem(0, original);
        PluginManager pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent event
                    && event.getOldItem().getId() == BlockID.LOG) {
                event.getNewItem().setCount(event.getOldItem().getCount());
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(original.clone(), 0), output(recipe)));

        assertTrue(player.getInventory().getItem(0).equalsExact(original));
        assertFalse(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void revalidatesMaterialsAfterCraftEvent(boolean moveMaterial) {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        player.getInventory().setItem(0, Item.get(BlockID.LOG));
        PluginManager pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof CraftItemEvent) {
                player.getInventory().setItemForce(0, Item.get(ItemID.DIAMOND));
                if (moveMaterial) {
                    player.getInventory().setItemForce(1, Item.get(BlockID.LOG));
                }
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        assertEquals(moveMaterial, CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertTrue(player.getInventory().contains(Item.get(ItemID.DIAMOND)),
                "不能按事件前的槽位记录误扣插件放入的物品");
        assertFalse(player.getInventory().contains(Item.get(BlockID.LOG)));
        assertEquals(moveMaterial, player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void consumesSelectedNamedMaterialAndReportsActualEventInput(boolean shaped) {
        TestPlayer player = new TestPlayer();
        CraftingRecipe recipe = shaped
                ? new ShapedRecipe(Item.get(BlockID.PLANKS, 0, 4), new String[]{"L"},
                        Map.of('L', Item.get(BlockID.LOG)), List.of())
                : logToPlanksRecipe();
        Item namedLog = Item.get(BlockID.LOG, 0, 2);
        namedLog.setCustomName("SelectedLog");
        player.getInventory().setItem(0, Item.get(BlockID.LOG));
        player.getInventory().setItem(1, namedLog);
        Item[] input = grid(namedLog.clone(), 0);

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                input, output(recipe)));

        assertTrue(player.getInventory().getItem(0).equalsExact(Item.get(BlockID.LOG)));
        Item consumed = namedLog.clone();
        consumed.setCount(1);
        assertTrue(player.getInventory().getItem(1).equalsExact(consumed));
        assertTrue(input[0].equalsExact(namedLog), "不能修改调用方传入的快照");
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(MockServer.get().getPluginManager(), Mockito.atLeastOnce()).callEvent(captor.capture());
        CraftItemEvent craftEvent = captor.getAllValues().stream().filter(CraftItemEvent.class::isInstance)
                .map(CraftItemEvent.class::cast).findFirst().orElseThrow();
        assertEquals(1, craftEvent.getInput().length);
        assertTrue(craftEvent.getInput()[0].equalsExact(consumed));
        assertTrue(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    @Test
    void rejectsPacketMaterialWhoseNbtIsAbsentFromInventory() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        player.getInventory().setItem(0, Item.get(BlockID.LOG));
        Item forgedLog = Item.get(BlockID.LOG);
        forgedLog.setCustomName("MissingLog");

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(forgedLog, 0), output(recipe)));

        assertTrue(player.getInventory().getItem(0).equalsExact(Item.get(BlockID.LOG)));
        assertFalse(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    @Test
    void debitEventsSeeOriginalMaterialsUntilAllChangesAreApproved() {
        TestPlayer player = new TestPlayer();
        ShapedRecipe recipe = planksToCraftingTable();
        player.getInventory().setItem(0, Item.get(BlockID.PLANKS, 0, 3));
        player.getInventory().setItem(1, Item.get(BlockID.PLANKS));
        PluginManager pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent event && event.getSlot() == 1) {
                player.getInventory().setItemForce(0, Item.get(BlockID.PLANKS, 0, 3));
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.PLANKS, 0, 1), 0, item(BlockID.PLANKS, 0, 1), 1,
                        item(BlockID.PLANKS, 0, 1), 3, item(BlockID.PLANKS, 0, 1), 4), output(recipe)));

        assertFalse(player.getInventory().contains(Item.get(BlockID.PLANKS)));
        assertTrue(player.getInventory().contains(Item.get(BlockID.WORKBENCH)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 9})
    void processorLogsReasonWhenRejectingCraft(int inputLength) {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        recipe.setId(java.util.UUID.randomUUID());
        player.getInventory().setItem(0, Item.get(BlockID.LOG));

        Mockito.lenient().when(craftingManager.getRecipes()).thenReturn(List.of(recipe));

        CraftingEventPacket packet = new CraftingEventPacket();
        packet.protocol = ProtocolInfo.v1_1_0;
        packet.gameVersion = GameVersion.V1_1_0;
        packet.windowId = ContainerIds.INVENTORY;
        packet.id = recipe.getId();
        packet.input = inputLength < 0 ? null : new Item[inputLength];
        packet.output = output(recipe);

        CraftingEventProcessor_v113.INSTANCE.handle(new PlayerHandle(player), packet);

        assertTrue(player.getInventory().contains(Item.get(BlockID.LOG)));
        assertFalse(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
        String reason = inputLength < 0 ? "missing_input" : inputLength == 0 ? "empty_input" : "validation_failed";
        Mockito.verify(player.getServer().getLogger()).debug(Mockito.argThat((String message) ->
                message.contains("reason=" + reason)
                        && message.contains("protocol=" + ProtocolInfo.v1_1_0)
                        && message.contains(packet.id.toString())));
    }

    @ParameterizedTest
    @CsvSource({
            "unknown_recipe, -1", "unknown_recipe, 0", "unknown_recipe, 9",
            "unknown_window, -1", "unknown_window, 0", "unknown_window, 9",
            "missing_anvil, -1", "missing_anvil, 0", "missing_anvil, 9",
            "inactive_player, -1", "inactive_player, 0", "inactive_player, 9"
    })
    void logsReasonAtEveryEarlyRejection(String reason, int inputLength) {
        TestPlayer player = new TestPlayer();
        player.getInventory().setItemForce(0, Item.get(BlockID.LOG));
        Mockito.when(craftingManager.getRecipes()).thenReturn(List.of());
        CraftingEventPacket packet = new CraftingEventPacket();
        packet.protocol = ProtocolInfo.v1_1_0;
        packet.gameVersion = GameVersion.V1_1_0;
        packet.windowId = reason.equals("unknown_window") ? 123 : ContainerIds.INVENTORY;
        packet.id = new java.util.UUID(0, 0);
        packet.input = inputLength < 0 ? null : new Item[inputLength];
        packet.output = new Item[]{Item.get(BlockID.PLANKS, 0, 4)};
        if (reason.equals("missing_anvil")) {
            player.craftingType = Player.CRAFTING_ANVIL;
        } else if (reason.equals("inactive_player")) {
            player.spawned = false;
        }

        CraftingEventProcessor_v113.INSTANCE.handle(new PlayerHandle(player), packet);

        Mockito.verify(player.getServer().getLogger()).debug(Mockito.argThat((String message) ->
                message.contains("reason=" + reason)
                        && message.contains("protocol=" + ProtocolInfo.v1_1_0)
                        && message.contains(packet.id.toString())));
        assertTrue(player.getInventory().getItem(0).equalsExact(Item.get(BlockID.LOG)));
        assertEquals(1, player.getInventory().getContents().size());
    }

    @Test
    void processorCraftsFromPacketInput() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        recipe.setId(java.util.UUID.randomUUID());
        player.getInventory().setItem(0, Item.get(BlockID.LOG));

        Mockito.lenient().when(craftingManager.getRecipes()).thenReturn(List.of(recipe));

        CraftingEventPacket packet = new CraftingEventPacket();
        packet.windowId = ContainerIds.INVENTORY;
        packet.id = recipe.getId();
        packet.input = grid(item(BlockID.LOG, 0, 1), 0);
        packet.output = output(recipe);

        CraftingEventProcessor_v113.INSTANCE.handle(new PlayerHandle(player), packet);

        assertFalse(player.getInventory().contains(Item.get(BlockID.LOG)));
        assertTrue(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    @Test
    void craftsShapelessRecipeWithStackedIngredient() {
        TestPlayer player = new TestPlayer();
        player.getInventory().setItem(0, Item.get(BlockID.LOG, 0, 4));
        ShapelessRecipe recipe = new ShapelessRecipe(
                Item.get(BlockID.PLANKS, 0, 8), List.of(Item.get(BlockID.LOG, 0, 2)));

        Item[] input = grid(item(BlockID.LOG, 0, 1), 0, item(BlockID.LOG, 0, 1), 1);

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe, input, output(recipe)),
                "count > 1 的 shapeless 材料应能按两个格子匹配");
        assertEquals(2, player.getInventory().getItem(0).getCount());
        assertTrue(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 8)));
    }

    @Test
    void rejectsStackedShapelessIngredientWhenInventoryShort() {
        TestPlayer player = new TestPlayer();
        player.getInventory().setItem(0, Item.get(BlockID.LOG, 0, 1));
        ShapelessRecipe recipe = new ShapelessRecipe(
                Item.get(BlockID.PLANKS, 0, 8), List.of(Item.get(BlockID.LOG, 0, 2)));

        Item[] input = grid(item(BlockID.LOG, 0, 1), 0, item(BlockID.LOG, 0, 1), 1);

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe, input, output(recipe)));
        assertTrue(player.getInventory().contains(Item.get(BlockID.LOG)));
        assertFalse(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 8)));
    }

    @Test
    void rejectsEmptyPacketOutput() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        player.getInventory().setItem(0, Item.get(BlockID.LOG));

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), new Item[0]));
        assertTrue(player.getInventory().contains(Item.get(BlockID.LOG)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void clearsServerCraftingGridAfterCraftIncludingPluginWrites(boolean pluginWrite) {
        TestPlayer player = new TestPlayer();
        player.getInventory().setItem(0, Item.get(BlockID.LOG, 0, 1));
        if (pluginWrite) {
            PluginManager pluginManager = MockServer.get().getPluginManager();
            Mockito.doAnswer(invocation -> {
                if (invocation.getArgument(0) instanceof CraftItemEvent) {
                    player.getCraftingGrid().setItem(0, Item.get(ItemID.DIAMOND));
                }
                return null;
            }).when(pluginManager).callEvent(any(Event.class));
        } else {
            player.getCraftingGrid().setItem(0, Item.get(BlockID.LOG, 0, 1));
        }

        ShapelessRecipe recipe = logToPlanksRecipe();
        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertTrue(player.getCraftingGrid().getItem(0).isNull(), "成功结算后应清空服务端合成格镜像");
        assertFalse(player.getInventory().contains(Item.get(ItemID.DIAMOND)), "插件写入镜像的物品同样会被清除");
        assertFalse(player.getInventory().contains(Item.get(BlockID.LOG)), "材料应从背包扣除");
        assertTrue(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));

        // 关闭界面时 resetCraftingGridType() 不得把残留退回背包，否则材料重复获得。
        // Closing the grid must not refund leftovers; otherwise materials are duplicated.
        player.resetCraftingGridType();
        assertFalse(player.getInventory().contains(Item.get(BlockID.LOG)),
                "合成格残留不得在关闭界面时退回背包");
        assertTrue(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    @Test
    void clearsMirrorBeforePacketCallbackCanCloseCrafting() {
        TestPlayer player = new TestPlayer();
        player.getInventory().setItemForce(0, Item.get(BlockID.LOG));
        player.getCraftingGrid().setItemForce(0, Item.get(BlockID.LOG));
        ShapelessRecipe recipe = logToPlanksRecipe();
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        player.packetObserver = packet -> {
            if (packet instanceof ContainerSetSlotPacket_v113 && !closed.getAndSet(true)) {
                player.resetCraftingGridType();
            }
        };

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(Item.get(BlockID.LOG), 0), output(recipe)));

        assertTrue(closed.get(), "必须触发发包期间关闭界面的回调");
        assertFalse(player.getInventory().contains(Item.get(BlockID.LOG)), "已消耗材料不能从镜像格返还");
        assertTrue(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
        assertEquals(1, player.getInventory().getContents().size());
    }

    @Test
    void clearsMirrorBeforeOverflowDropCallbackCanCloseCrafting() {
        TestPlayer player = new TestPlayer();
        Level level = Mockito.mock(Level.class);
        player.level = level;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItemForce(slot, Item.get(BlockID.STONE, 0, 64));
        }
        player.getInventory().setItemForce(0, Item.get(BlockID.LOG, 0, 2));
        player.getCraftingGrid().setItemForce(0, Item.get(BlockID.LOG));
        Mockito.doAnswer(invocation -> {
            player.resetCraftingGridType();
            return null;
        }).when(level).dropItem(Mockito.eq(player), any(Item.class));
        ShapelessRecipe recipe = logToPlanksRecipe();

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(Item.get(BlockID.LOG), 0), output(recipe)));

        assertTrue(player.getInventory().getItem(0).equalsExact(Item.get(BlockID.LOG)));
        assertFalse(player.getInventory().contains(Item.get(BlockID.PLANKS)));
        Mockito.verify(level).dropItem(Mockito.eq(player), Mockito.argThat(item ->
                item.equalsExact(Item.get(BlockID.PLANKS, 0, 4))));
    }

    @Test
    void craftItemEventCarriesReadOnlyTransaction() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        player.getInventory().setItem(0, Item.get(BlockID.LOG, 0, 1));

        PluginManager pluginManager = MockServer.get().getPluginManager();
        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(pluginManager, Mockito.atLeastOnce()).callEvent(captor.capture());
        CraftItemEvent craftEvent = captor.getAllValues().stream()
                .filter(CraftItemEvent.class::isInstance)
                .map(CraftItemEvent.class::cast)
                .findFirst()
                .orElse(null);

        assertNotNull(craftEvent, "合成成功应触发 CraftItemEvent");
        assertNotNull(craftEvent.getTransaction(), "CraftItemEvent#getTransaction() 不应为 null");
        assertSame(player, craftEvent.getTransaction().getSource());
        assertSame(recipe, craftEvent.getTransaction().getTransactionRecipe());
    }

    @Test
    void appliesPluginRewrittenOutput() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        player.getInventory().setItem(0, Item.get(BlockID.LOG));

        // setPrimaryOutput 只允许改 NBT/自定义名。
        // setPrimaryOutput only allows NBT/custom-name rewrites.
        Item named = recipe.getResult().clone();
        named.setCustomName("EventNamed");

        PluginManager pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof CraftItemEvent craftEvent) {
                craftEvent.getTransaction().setPrimaryOutput(named);
            }
            return null;
        }).when(pluginManager).callEvent(Mockito.any(Event.class));

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertNotNull(findByName(player, "EventNamed"),
                "插件通过 setPrimaryOutput 改写的产物必须落地，不能被结算忽略");
        assertFalse(player.getInventory().contains(Item.get(BlockID.LOG)));
    }

    @ParameterizedTest
    @CsvSource({"false, 0", "true, 0", "false, 1", "true, 1", "false, 2", "true, 2"})
    void rollsBackMaterialsWhenOutputInsertionIsCancelledOrRewritten(boolean merge, int rewrite) {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        player.getInventory().setItem(0, Item.get(BlockID.LOG));
        if (merge) {
            player.getInventory().setItem(1, Item.get(BlockID.PLANKS, 0, 63));
        }
        PluginManager pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent event
                    && event.getNewItem().getId() == BlockID.PLANKS) {
                if (rewrite == 0) {
                    event.setCancelled(true);
                } else if (rewrite == 1) {
                    event.setNewItem(Item.get(ItemID.DIAMOND));
                } else {
                    event.getNewItem().setCount(event.getNewItem().getCount() - 1);
                }
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertTrue(player.getInventory().getItem(0).equalsExact(Item.get(BlockID.LOG)));
        assertEquals(merge ? 63 : 0, player.getInventory().getContents().values().stream()
                .filter(item -> item.getId() == BlockID.PLANKS).mapToInt(Item::getCount).sum());
        assertFalse(player.getInventory().contains(Item.get(ItemID.DIAMOND)));
    }

    @ParameterizedTest
    @CsvSource({"false, false", "true, false", "false, true", "true, true"})
    void rejectsOverstackRewriteBeforeItCanSpillOutsideRollbackSlots(boolean debit, boolean full) {
        TestPlayer player = new TestPlayer();
        Level level = Mockito.mock(Level.class);
        player.level = level;
        if (full) {
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                player.getInventory().setItem(slot, Item.get(BlockID.STONE, 0, 64));
            }
            player.getInventory().setItem(1, Item.get(BlockID.PLANKS, 0, 60));
        }
        player.getInventory().setItem(0, Item.get(BlockID.LOG, 0, 2));
        ShapelessRecipe recipe = logToPlanksRecipe();
        PluginManager pluginManager = MockServer.get().getPluginManager();
        java.util.concurrent.atomic.AtomicBoolean rewritten = new java.util.concurrent.atomic.AtomicBoolean();
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent event
                    && event.getNewItem().getId() == (debit ? BlockID.LOG : BlockID.PLANKS)
                    && !rewritten.getAndSet(true)) {
                event.getNewItem().setCount(65);
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertTrue(player.getInventory().getItem(0).equalsExact(Item.get(BlockID.LOG, 0, 2)));
        assertEquals(full ? player.getInventory().getSize() : 1, player.getInventory().getContents().size(),
                "改写产生的溢出物品不能逃逸回滚");
        if (full) {
            assertTrue(player.getInventory().getItem(1).equalsExact(Item.get(BlockID.PLANKS, 0, 60)));
        }
        Mockito.verify(level, Mockito.never()).dropItem(Mockito.any(), Mockito.any(Item.class));
    }

    @Test
    void doesNotExposePrimaryOutputBeforeExtraResultApproval() {
        TestPlayer player = new TestPlayer();
        player.getInventory().setItemForce(0, Item.get(BlockID.LOG));
        ShapedRecipe recipe = new ShapedRecipe(Item.get(ItemID.DIAMOND), new String[]{"L"},
                Map.of('L', Item.get(BlockID.LOG)), List.of(Item.get(ItemID.GOLD_INGOT)));
        PluginManager pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent event
                    && event.getNewItem().getId() == ItemID.GOLD_INGOT) {
                Item primary = player.getInventory().getItem(0);
                if (primary.getId() == ItemID.DIAMOND) {
                    player.getInventory().setItemForce(10, primary);
                    player.getInventory().setItemForce(0, Item.get(Item.AIR));
                }
                event.setCancelled(true);
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertTrue(player.getInventory().getItem(0).equalsExact(Item.get(BlockID.LOG)));
        assertEquals(1, player.getInventory().getContents().size());
        assertFalse(player.getInventory().contains(Item.get(ItemID.DIAMOND)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publishesOnlyCompletedInventoryAfterAllApprovals(boolean cancel) {
        TestPlayer player = new TestPlayer();
        player.getInventory().setItemForce(0, Item.get(BlockID.LOG));
        ShapedRecipe recipe = new ShapedRecipe(Item.get(ItemID.DIAMOND), new String[]{"L"},
                Map.of('L', Item.get(BlockID.LOG)), List.of(Item.get(ItemID.GOLD_INGOT)));
        PluginManager pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent event) {
                assertTrue(player.getInventory().getItem(0).equalsExact(Item.get(BlockID.LOG)));
                assertEquals(1, player.getInventory().getContents().size());
                if (cancel && event.getNewItem().getId() == ItemID.GOLD_INGOT) {
                    event.setCancelled(true);
                }
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));
        java.util.concurrent.atomic.AtomicInteger sent = new java.util.concurrent.atomic.AtomicInteger();
        player.packetObserver = packet -> {
            if (packet instanceof ContainerSetSlotPacket_v113) {
                sent.incrementAndGet();
                assertFalse(player.getInventory().contains(Item.get(BlockID.LOG)));
                assertTrue(player.getInventory().contains(Item.get(ItemID.DIAMOND)));
                assertTrue(player.getInventory().contains(Item.get(ItemID.GOLD_INGOT)));
            }
        };

        assertEquals(!cancel, CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertEquals(cancel ? 0 : 2, sent.get());
    }

    @Test
    void rollsBackPrimaryOutputWhenExtraResultIsCancelled() {
        TestPlayer player = new TestPlayer();
        player.getInventory().setItem(0, Item.get(BlockID.LOG));
        ShapedRecipe recipe = new ShapedRecipe(Item.get(ItemID.DIAMOND), new String[]{"L"},
                Map.of('L', Item.get(BlockID.LOG)), List.of(Item.get(ItemID.GOLD_INGOT)));
        PluginManager pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent event
                    && event.getNewItem().getId() == ItemID.GOLD_INGOT) {
                event.setCancelled(true);
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertTrue(player.getInventory().getItem(0).equalsExact(Item.get(BlockID.LOG)));
        assertEquals(1, player.getInventory().getContents().size());
    }

    @Test
    void outputEventsSeeOriginalMaterialsUntilAllChangesAreApproved() {
        TestPlayer player = new TestPlayer();
        player.getInventory().setItem(0, Item.get(BlockID.LOG, 0, 2));
        ShapelessRecipe recipe = logToPlanksRecipe();
        PluginManager pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent event
                    && event.getNewItem().getId() == BlockID.PLANKS) {
                player.getInventory().setItemForce(0, Item.get(BlockID.LOG, 0, 2));
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertTrue(player.getInventory().getItem(0).equalsExact(Item.get(BlockID.LOG)));
        assertTrue(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
        assertEquals(2, player.getInventory().getContents().size());
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void abortsWithoutRestoringMaterialsMovedByInventoryEvent(boolean outputEvent, boolean cancel) {
        TestPlayer player = new TestPlayer();
        player.getInventory().setItemForce(0, Item.get(BlockID.LOG, 0, 2));
        ShapelessRecipe recipe = logToPlanksRecipe();
        PluginManager pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent event
                    && event.getNewItem().getId() == (outputEvent ? BlockID.PLANKS : BlockID.LOG)) {
                player.getInventory().setItemForce(10, player.getInventory().getItem(0));
                player.getInventory().setItemForce(0, Item.get(Item.AIR));
                event.setCancelled(cancel);
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertTrue(player.getInventory().getItem(0).isNull());
        assertTrue(player.getInventory().getItem(10).equalsExact(Item.get(BlockID.LOG, 0, 2)));
        assertEquals(1, player.getInventory().getContents().size());
        assertFalse(player.getInventory().contains(Item.get(BlockID.PLANKS)));
    }

    @Test
    void mergesResultsAndDropsOnlyOverflowIncludingExtraResults() {
        TestPlayer player = new TestPlayer();
        Level level = Mockito.mock(Level.class);
        player.level = level;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, Item.get(BlockID.STONE, 0, 64));
        }
        player.getInventory().setItem(0, Item.get(BlockID.LOG, 0, 2));
        player.getInventory().setItem(1, Item.get(BlockID.PLANKS, 0, 63));
        ShapedRecipe recipe = new ShapedRecipe(Item.get(BlockID.PLANKS, 0, 4), new String[]{"L"},
                Map.of('L', Item.get(BlockID.LOG)), List.of(Item.get(ItemID.BUCKET, 0, 3)));

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertTrue(player.getInventory().getItem(0).equalsExact(Item.get(BlockID.LOG)));
        assertTrue(player.getInventory().getItem(1).equalsExact(Item.get(BlockID.PLANKS, 0, 64)));
        for (int slot = 2; slot < player.getInventory().getSize(); slot++) {
            assertTrue(player.getInventory().getItem(slot).equalsExact(Item.get(BlockID.STONE, 0, 64)));
        }
        ArgumentCaptor<Item> drops = ArgumentCaptor.forClass(Item.class);
        Mockito.verify(level, Mockito.times(2)).dropItem(Mockito.eq(player), drops.capture());
        assertTrue(drops.getAllValues().get(0).equalsExact(Item.get(BlockID.PLANKS, 0, 3)));
        assertTrue(drops.getAllValues().get(1).equalsExact(Item.get(ItemID.BUCKET, 0, 3)));
    }

    @Test
    void splitsUnstackableResultsAcrossSlots() {
        TestPlayer player = new TestPlayer();
        player.getInventory().setItem(0, Item.get(BlockID.LOG));
        ShapelessRecipe recipe = new ShapelessRecipe(Item.get(ItemID.DIAMOND_SWORD, 0, 2),
                List.of(Item.get(BlockID.LOG)));

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)));

        assertEquals(2, player.getInventory().getContents().size());
        assertTrue(player.getInventory().getItem(0).equalsExact(Item.get(ItemID.DIAMOND_SWORD)));
        assertTrue(player.getInventory().getItem(1).equalsExact(Item.get(ItemID.DIAMOND_SWORD)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void matchesShapelessWildcardWithoutStealingSpecificIngredient(boolean named) {
        TestPlayer player = new TestPlayer();
        Item oak = Item.get(BlockID.LOG, 0, 1);
        if (named) {
            oak.setCustomName("SpecificLog");
        }
        Item spruce = Item.get(BlockID.LOG, 1, 1);
        player.getInventory().setItem(0, oak);
        player.getInventory().setItem(1, spruce);
        ShapelessRecipe recipe = new ShapelessRecipe(Item.get(ItemID.DIAMOND),
                List.of(Item.get(BlockID.LOG, -1, 1), oak));

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(oak, 0, spruce, 1), output(recipe)));

        assertFalse(player.getInventory().contains(oak));
        assertFalse(player.getInventory().contains(spruce));
        assertTrue(player.getInventory().contains(Item.get(ItemID.DIAMOND)));
    }

    @Test
    void requiresTableForShapelessIngredientCountsExpandedBeyondFourCells() {
        TestPlayer player = new TestPlayer();
        player.getInventory().setItem(0, Item.get(BlockID.LOG, 0, 5));
        ShapelessRecipe recipe = new ShapelessRecipe(Item.get(ItemID.DIAMOND),
                List.of(Item.get(BlockID.LOG, 0, 5)));
        Item[] input = grid(item(BlockID.LOG, 0, 1), 0, item(BlockID.LOG, 0, 1), 1,
                item(BlockID.LOG, 0, 1), 2, item(BlockID.LOG, 0, 1), 3, item(BlockID.LOG, 0, 1), 4);

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe, input, output(recipe)));
        assertTrue(player.getInventory().getItem(0).equalsExact(Item.get(BlockID.LOG, 0, 5)));

        player.craftingType = Player.CRAFTING_BIG;
        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe, input, output(recipe)));
        assertFalse(player.getInventory().contains(Item.get(BlockID.LOG)));
        assertTrue(player.getInventory().contains(Item.get(ItemID.DIAMOND)));
    }

    @Test
    void creativeCraftDoesNotConsumeMaterials() {
        TestPlayer player = new TestPlayer();
        player.gamemode = Player.CREATIVE;
        ShapelessRecipe recipe = logToPlanksRecipe();

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                grid(item(BlockID.LOG, 0, 1), 0), output(recipe)),
                "创造模式不应因背包缺少材料而拒绝合成");
        assertTrue(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportsNamedAndPlainMaterialsSeparatelyRegardlessOfGridOrder(boolean namedFirst) {
        TestPlayer player = new TestPlayer();
        Item plain = Item.get(BlockID.LOG);
        Item named = Item.get(BlockID.LOG).setCustomName("ProtectedLog");
        player.getInventory().setItem(0, plain);
        player.getInventory().setItem(1, named);
        ShapelessRecipe recipe = new ShapelessRecipe(Item.get(ItemID.DIAMOND),
                List.of(Item.get(BlockID.LOG, 0, 2)));
        Item[] input = namedFirst ? grid(named, 0, plain, 1) : grid(plain, 0, named, 1);

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe, input, output(recipe)));

        ArgumentCaptor<Event> events = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(MockServer.get().getPluginManager(), Mockito.atLeastOnce()).callEvent(events.capture());
        CraftItemEvent craftEvent = events.getAllValues().stream().filter(CraftItemEvent.class::isInstance)
                .map(CraftItemEvent.class::cast).findFirst().orElseThrow();
        assertEquals(2, craftEvent.getInput().length, "普通材料不得与命名材料合并为同一种身份");
        assertEquals(1, java.util.Arrays.stream(craftEvent.getInput())
                .filter(item -> item.equals(named, true, true)).mapToInt(Item::getCount).sum());
        assertEquals(1, java.util.Arrays.stream(craftEvent.getInput())
                .filter(item -> item.equals(plain, true, true)).mapToInt(Item::getCount).sum());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void doesNotIgnoreNonEmptyInputBeyondNineCells(boolean extraMaterial) {
        TestPlayer player = new TestPlayer();
        player.getInventory().setItem(0, Item.get(BlockID.LOG));
        ShapelessRecipe recipe = logToPlanksRecipe();
        Item[] input = java.util.Arrays.copyOf(grid(item(BlockID.LOG, 0, 1), 0), 10);
        input[9] = extraMaterial ? Item.get(ItemID.DIAMOND) : Item.get(Item.AIR);

        assertEquals(!extraMaterial, CraftingEvent_v113Adapter.execute(player, recipe, input, output(recipe)));

        assertEquals(extraMaterial, player.getInventory().contains(Item.get(BlockID.LOG)));
        assertEquals(!extraMaterial, player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    @ParameterizedTest
    @CsvSource({"0,1,2", "0,2,1", "1,0,2", "1,2,0", "2,0,1", "2,1,0"})
    void matchesOverlappingShapelessIngredientsForEveryInputAndRecipeOrder(int first, int second, int third) {
        Item named = Item.get(BlockID.LOG).setCustomName("SpecificLog");
        Item[] actual = {named, Item.get(BlockID.LOG), Item.get(BlockID.LOG, 1)};
        Item[] templates = {Item.get(BlockID.LOG, -1), Item.get(BlockID.LOG), named};
        int[][] recipeOrders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        for (int[] order : recipeOrders) {
            TestPlayer player = new TestPlayer();
            for (int slot = 0; slot < actual.length; slot++) {
                player.getInventory().setItem(slot, actual[slot].clone());
            }
            ShapelessRecipe recipe = new ShapelessRecipe(Item.get(ItemID.DIAMOND),
                    List.of(templates[order[0]], templates[order[1]], templates[order[2]]));

            assertTrue(CraftingEvent_v113Adapter.execute(player, recipe,
                    grid(actual[first], 0, actual[second], 1, actual[third], 2), output(recipe)));

            assertEquals(1, player.getInventory().getContents().size());
            assertTrue(player.getInventory().getItem(0).equalsExact(Item.get(ItemID.DIAMOND)));
        }
    }

    private static Item findByName(TestPlayer player, String customName) {
        for (Item item : player.getInventory().getContents().values()) {
            if (customName.equals(item.getCustomName())) {
                return item;
            }
        }
        return null;
    }

    private static Item item(int id, int damage, int count) {
        return Item.get(id, damage, count);
    }

    /**
     * 客户端上报的产物。必须独立实例：复用 {@code recipe.getResult()} 会让 matchesOutput 变成自比较。
     * The client output must be its own instance; reusing {@code recipe.getResult()} self-compares.
     */
    private static Item[] output(CraftingRecipe recipe) {
        return new Item[]{recipe.getResult().clone()};
    }

    private static Item[] fullPlankGrid() {
        Item[] input = new Item[9];
        for (int i = 0; i < 9; i++) {
            input[i] = item(BlockID.PLANKS, 0, 1);
        }
        return input;
    }

    private static Item[] grid(Object... slots) {
        Item[] input = new Item[9];
        for (int i = 0; i < 9; i++) {
            input[i] = Item.get(Item.AIR);
        }
        for (int i = 0; i + 1 < slots.length; i += 2) {
            input[(int) slots[i + 1]] = (Item) slots[i];
        }
        return input;
    }

    private static ShapelessRecipe logToPlanksRecipe() {
        return new ShapelessRecipe(Item.get(BlockID.PLANKS, 0, 4), List.of(Item.get(BlockID.LOG)));
    }

    private static ShapedRecipe planksToCraftingTable() {
        return new ShapedRecipe(
                Item.get(BlockID.WORKBENCH),
                new String[]{"PP", "PP"},
                Map.of('P', Item.get(BlockID.PLANKS)),
                List.of()
        );
    }

    private static ShapedRecipe threeByThreeRecipe() {
        return new ShapedRecipe(
                Item.get(ItemID.DIAMOND),
                new String[]{"PPP", "PPP", "PPP"},
                Map.of('P', Item.get(BlockID.PLANKS)),
                List.of()
        );
    }

    private static final class TestPlayer extends Player {

        private java.util.function.Consumer<DataPacket> packetObserver;

        private TestPlayer(Server server) {
            this();
            this.server = server;
        }

        private TestPlayer() {
            super(source(), 1L, new InetSocketAddress("127.0.0.1", 19132));
            this.inventory = new PlayerInventory(this);
            this.playerUIInventory = new PlayerUIInventory(this);
            this.craftingGrid = this.playerUIInventory.getCraftingGrid();
            this.addWindow(this.inventory, ContainerIds.INVENTORY, true, true);
            this.addWindow(this.playerUIInventory, ContainerIds.UI, true, true);
            this.addWindow(this.craftingGrid, ContainerIds.NONE, true, true);
            this.craftingType = Player.CRAFTING_SMALL;
            this.protocol = ProtocolInfo.v1_1_0;
            this.gameVersion = GameVersion.V1_1_0;
            this.spawned = true;
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            if (packetObserver != null) {
                packetObserver.accept(packet);
            }
            return false;
        }

        @Override
        public String getName() {
            return "TestPlayer";
        }
    }

    private static SourceInterface source() {
        SourceInterface source = Mockito.mock(SourceInterface.class);
        NetworkPlayerSession session = Mockito.mock(NetworkPlayerSession.class);
        Mockito.lenient().when(source.getSession(any())).thenReturn(session);
        return source;
    }
}
