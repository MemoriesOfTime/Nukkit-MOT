package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBucket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ItemStackResponsePacket;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.*;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeastOnce;

/**
 * 蛋糕（多输出配方：蛋糕 + 3 空桶）ItemStackRequest 合成回归测试（#839）。
 * 客户端流程为 CraftRecipe → Consume×N → Create(slot) → Place，残留物（空桶）也经 Create 交付。
 * <p>
 * Regression tests for cake (multi-output: cake + 3 empty buckets) crafting via
 * ItemStackRequest (#839), covering the documented Consume → Create → Place flow.
 */
class CakeCraftingReproTest {

    static Player player;
    static PlayerUIInventory ui;
    static PlayerInventory inventory;
    static CraftingManager manager;
    static int cakeNetworkId;
    static int breadNetworkId;

    @BeforeAll
    static void init() {
        MockServer.init();
        Item.initCreativeItems();
    }

    @BeforeEach
    void setUp() {
        MockServer.reset();
        manager = new CraftingManager();

        bindPlayer(GameVersion.V1_26_20);

        cakeNetworkId = findNetworkId(Item.CAKE);
        breadNetworkId = findNetworkId(Item.BREAD);
        assertTrue(cakeNetworkId > 0, "cake recipe should be registered");
        assertTrue(breadNetworkId > 0, "bread recipe should be registered");
    }

    static void bindPlayer(GameVersion gameVersion) {
        player = mockPlayer(gameVersion);
        ui = new PlayerUIInventory(player);
        inventory = new PlayerInventory(player);
        cn.nukkit.inventory.PlayerOffhandInventory offhand = new cn.nukkit.inventory.PlayerOffhandInventory(player);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        // 打开工作台状态: 3x3 BigCraftingGrid (BlockCraftingTable#onActivate)
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getBigCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getWindowId(inventory)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);
        Mockito.when(player.getServer()).thenReturn(MockServer.get());
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(pluginManager);
        Mockito.when(MockServer.get().getCraftingManager()).thenReturn(manager);
    }

    // A: 不带 Create 直接 Place（多输出不预写输出，须拒绝）
    @Test
    void sequenceA_multiOutputWithoutCreateIsRejected() {
        fillGrid();
        ItemStackRequest request = new ItemStackRequest(1, join(
                craftAction(),
                consumeAll(),
                placeCreatedToHotbar(0, 1)
        ), new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        ItemStackResponsePacket response = lastResponse();
        assertEquals(ItemStackResponseStatus.ERROR, response.entries.get(0).getResult(),
                "multi-output recipe must go through Create; direct Place has nothing staged");
        assertTrue(inventory.getItem(0).isNull(), "no cake should reach the hotbar");
    }

    // B: #839 场景——Consume 后 Create(0) 取主产物
    @Test
    void sequenceB_craftConsumeCreate0Place() {
        fillGrid();
        ItemStackRequest request = new ItemStackRequest(1, join(
                craftAction(),
                consumeAll(),
                createAction(0),
                placeCreatedToHotbar(0, 1)
        ), new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        assertOk();
        assertEquals(Item.CAKE, inventory.getItem(0).getId(), "cake should reach hotbar 0");
        assertEquals(1, inventory.getItem(0).getCount());
    }

    // B2: Consume 后 Create(1) 取残留物（3 空桶）
    @Test
    void sequenceB2_craftConsumeCreate1PlaceBuckets() {
        fillGrid();
        ItemStackRequest request = new ItemStackRequest(1, join(
                craftAction(),
                consumeAll(),
                createAction(1),
                placeCreatedToHotbar(1, 3)
        ), new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        assertOk();
        assertEquals(Item.BUCKET, inventory.getItem(1).getId(), "leftover buckets should reach hotbar 1");
        assertEquals(ItemBucket.EMPTY_BUCKET, inventory.getItem(1).getDamage());
        assertEquals(3, inventory.getItem(1).getCount());
    }

    // C: Create 在 Consume 之前的顺序变体
    @Test
    void sequenceC_craftCreatePlaceConsume() {
        fillGrid();
        ItemStackRequest request = new ItemStackRequest(1, join(
                craftAction(),
                createAction(0),
                placeCreatedToHotbar(0, 1),
                consumeAll()
        ), new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        assertOk();
        assertEquals(Item.CAKE, inventory.getItem(0).getId());
    }

    // D: 完整多输出流程：蛋糕与空桶都要送达
    @Test
    void sequenceD_fullMultiOutputDeliversCakeAndBuckets() {
        fillGrid();
        ItemStackRequest request = new ItemStackRequest(1, join(
                craftAction(),
                consumeAll(),
                createAction(0),
                placeCreatedToHotbar(0, 1),
                createAction(1),
                placeCreatedToHotbar(1, 3)
        ), new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        assertOk();
        assertEquals(Item.CAKE, inventory.getItem(0).getId(), "cake should reach hotbar 0");
        assertEquals(Item.BUCKET, inventory.getItem(1).getId(), "buckets should reach hotbar 1");
        assertEquals(3, inventory.getItem(1).getCount(), "3 empty buckets returned");
        assertTrue(ui.getBigCraftingGrid().getItem(0).isNull(), "grid consumed");
    }

    // E: 独立请求中的 Create（无 CraftRecipeAction 上下文）拒绝
    @Test
    void sequenceE_createWithoutCraftActionInSameRequestFails() {
        fillGrid();
        ItemStackRequest r1 = new ItemStackRequest(1, join(
                craftAction(),
                consumeAll(),
                createAction(0),
                placeCreatedToHotbar(0, 1)
        ), new String[0]);
        ItemStackRequest r2 = new ItemStackRequest(2, join(
                createAction(1),
                placeCreatedToHotbar(1, 3)
        ), new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(r1, r2));

        ItemStackResponsePacket response = lastResponse();
        assertEquals(ItemStackResponseStatus.OK, response.entries.get(0).getResult(), "request 1 (cake) succeeds");
        assertEquals(ItemStackResponseStatus.ERROR, response.entries.get(1).getResult(),
                "request 2 has no CraftRecipeAction context");
    }

    // F: 1.21.130 对照（修复非 1.26 独有）
    @Test
    void sequenceF_v1_21_130_multiOutput() {
        bindPlayer(GameVersion.V1_21_130);
        fillGrid();
        ItemStackRequest request = new ItemStackRequest(1, join(
                craftAction(),
                consumeAll(),
                createAction(1),
                placeCreatedToHotbar(1, 3)
        ), new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        assertOk();
        assertEquals(Item.BUCKET, inventory.getItem(1).getId());
        assertEquals(3, inventory.getItem(1).getCount());
    }

    // G: 单输出配方（面包）无 Create 的经典流程
    @Test
    void sequenceG_singleOutputBreadWithoutCreateStillWorks() {
        ui.getBigCraftingGrid().setItem(0, Item.get(Item.WHEAT, 0, 1), false);
        ui.getBigCraftingGrid().setItem(1, Item.get(Item.WHEAT, 0, 1), false);
        ui.getBigCraftingGrid().setItem(2, Item.get(Item.WHEAT, 0, 1), false);

        ItemStackRequest request = new ItemStackRequest(1, join(
                new CraftRecipeAction(breadNetworkId, 1),
                new ConsumeAction(1, gridSlot(0)),
                new ConsumeAction(1, gridSlot(1)),
                new ConsumeAction(1, gridSlot(2)),
                placeCreatedToHotbar(0, 1)
        ), new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        assertOk();
        assertEquals(Item.BREAD, inventory.getItem(0).getId(), "bread should reach hotbar 0");
    }

    // H: 同一输出重复 Create
    @Test
    void sequenceH_duplicateCreateSameSlotRejected() {
        fillGrid();
        ItemStackRequest request = new ItemStackRequest(1, join(
                craftAction(),
                consumeAll(),
                createAction(0),
                placeCreatedToHotbar(0, 1),
                createAction(0),
                placeCreatedToHotbar(2, 1)
        ), new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        ItemStackResponsePacket response = lastResponse();
        assertEquals(ItemStackResponseStatus.ERROR, response.entries.get(0).getResult(),
                "duplicate Create of the same output slot must be rejected");
        assertTrue(inventory.getItem(2).isNull(), "no duplicated cake in hotbar 2");
    }

    // I: 不带 Consume（材料未消耗不得产出）
    @Test
    void sequenceI_createWithoutConsumeRejected() {
        fillGrid();
        ItemStackRequest request = new ItemStackRequest(1, join(
                craftAction(),
                createAction(0),
                placeCreatedToHotbar(0, 1)
        ), new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        ItemStackResponsePacket response = lastResponse();
        assertEquals(ItemStackResponseStatus.ERROR, response.entries.get(0).getResult(),
                "consume plan validation must reject crafting without Consume actions");
        assertTrue(inventory.getItem(0).isNull(), "no free cake");
        assertFalse(ui.getBigCraftingGrid().getItem(0).isNull(), "grid rolled back");
    }

    private static void assertOk() {
        ItemStackResponsePacket response = lastResponse();
        assertNotNull(response, "response packet expected");
        assertEquals(ItemStackResponseStatus.OK, response.entries.get(0).getResult());
    }

    static ItemStackResponsePacket lastResponse() {
        try {
            ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
            Mockito.verify(player, atLeastOnce()).dataPacket(captor.capture());
            ItemStackResponsePacket found = null;
            for (DataPacket packet : captor.getAllValues()) {
                if (packet instanceof ItemStackResponsePacket) {
                    found = (ItemStackResponsePacket) packet;
                }
            }
            return found;
        } catch (Exception e) {
            return null;
        }
    }

    static void fillGrid() {
        // 原版蛋糕摆法: 上排牛奶桶、中排糖-蛋-糖、下排小麦
        cn.nukkit.inventory.CraftingGrid grid = ui.getBigCraftingGrid();
        grid.setItem(0, Item.get(Item.BUCKET, ItemBucket.MILK_BUCKET, 1), false);
        grid.setItem(1, Item.get(Item.BUCKET, ItemBucket.MILK_BUCKET, 1), false);
        grid.setItem(2, Item.get(Item.BUCKET, ItemBucket.MILK_BUCKET, 1), false);
        grid.setItem(3, Item.get(Item.SUGAR, 0, 1), false);
        grid.setItem(4, Item.get(Item.EGG, 0, 1), false);
        grid.setItem(5, Item.get(Item.SUGAR, 0, 1), false);
        grid.setItem(6, Item.get(Item.WHEAT, 0, 1), false);
        grid.setItem(7, Item.get(Item.WHEAT, 0, 1), false);
        grid.setItem(8, Item.get(Item.WHEAT, 0, 1), false);
    }

    // J: 多输出配方 times>1
    @Test
    void sequenceJ_multiOutputWithTimesCraftedAboveOneRejected() {
        fillGrid();
        ItemStackRequest request = new ItemStackRequest(1, join(
                new CraftRecipeAction(cakeNetworkId, 2),
                createAction(0),
                placeCreatedToHotbar(0, 1)
        ), new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        ItemStackResponsePacket response = lastResponse();
        assertEquals(ItemStackResponseStatus.ERROR, response.entries.get(0).getResult(),
                "multi-output craft with numberOfRequestedCrafts=2 must be rejected");
        assertTrue(inventory.getItem(0).isNull(), "no oversized cake stack");
    }

    @SafeVarargs
    static ItemStackRequestAction[] join(Object... parts) {
        List<ItemStackRequestAction> actions = new ArrayList<>();
        for (Object part : parts) {
            if (part instanceof ItemStackRequestAction action) {
                actions.add(action);
            } else if (part instanceof ItemStackRequestAction[] arr) {
                actions.addAll(List.of(arr));
            }
        }
        return actions.toArray(new ItemStackRequestAction[0]);
    }

    static CraftRecipeAction craftAction() {
        return new CraftRecipeAction(cakeNetworkId, 1);
    }

    static ConsumeAction[] consumeAll() {
        List<ConsumeAction> actions = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            actions.add(new ConsumeAction(1, gridSlot(slot)));
        }
        return actions.toArray(new ConsumeAction[0]);
    }

    static CreateAction createAction(int slot) {
        return new CreateAction(slot);
    }

    static PlaceAction placeCreatedToHotbar(int hotbarSlot, int count) {
        return new PlaceAction(count,
                new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT, 50, 0, null),
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, hotbarSlot, 0, null));
    }

    static ItemStackRequestSlotData gridSlot(int internalSlot) {
        return new ItemStackRequestSlotData(ContainerSlotType.CRAFTING_INPUT, 32 + internalSlot, 0, null);
    }

    int findNetworkId(int resultItemId) {
        for (Recipe recipe : manager.getRecipes()) {
            if (recipe instanceof ShapedRecipe shaped && shaped.getResult().getId() == resultItemId) {
                return shaped.getNetworkId();
            }
        }
        return -1;
    }

    static Player mockPlayer(GameVersion gameVersion) {
        Player player = Mockito.mock(Player.class);
        player.protocol = gameVersion.getProtocol();
        Mockito.when(player.getServer()).thenReturn(MockServer.get());
        Mockito.when(player.getName()).thenReturn("test");
        Mockito.when(player.isCreative()).thenReturn(false);
        Mockito.when(player.getGameVersion()).thenReturn(gameVersion);
        return player;
    }
}
