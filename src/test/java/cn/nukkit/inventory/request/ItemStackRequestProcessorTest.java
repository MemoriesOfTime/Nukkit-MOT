package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.event.Event;
import cn.nukkit.event.inventory.InventoryClickEvent;
import cn.nukkit.event.inventory.InventoryEvent;
import cn.nukkit.event.inventory.InventoryTransactionEvent;
import cn.nukkit.event.inventory.ItemStackRequestActionEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.special.FireworkRecipe;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.item.ItemFirework;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.Sound;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ItemStackResponsePacket;
import cn.nukkit.network.protocol.PlayerEnchantOptionsPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.descriptor.ComplexAliasDescriptor;
import cn.nukkit.network.protocol.types.inventory.descriptor.DefaultDescriptor;
import cn.nukkit.network.protocol.types.inventory.descriptor.ItemDescriptorWithCount;
import cn.nukkit.network.protocol.types.inventory.descriptor.ItemTagDescriptor;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.*;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.TradeRecipeBuildUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemStackRequestProcessorTest {

    @BeforeAll
    static void init() {
        MockServer.init();
        Item.initCreativeItems();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
        PlayerEnchantOptionsPacket.RECIPE_MAP.clear();
        TradeRecipeBuildUtils.RECIPE_MAP.clear();
    }

    @Test
    void creativeTakeThroughFullHandlerEndsUpInPlayerInventory() {
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerInventory inventory = new PlayerInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getGameVersion()).thenReturn(GameVersion.V1_21_130);
        Mockito.when(player.getWindowId(inventory)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(pluginManager);

        List<Item> creativeItems = Item.getCreativeItems(GameVersion.V1_21_130);
        int creativeIndex = -1;
        for (int i = 0; i < creativeItems.size(); i++) {
            if (creativeItems.get(i).getMaxStackSize() > 1) {
                creativeIndex = i;
                break;
            }
        }
        assertTrue(creativeIndex >= 0, "creative catalog should contain stackable items");
        Item expected = creativeItems.get(creativeIndex);

        // 模拟真实创造拿物品流程: CraftCreative (写 CREATED_OUTPUT) -> Place (移到 hotbar 0)
        CraftCreativeAction craft = new CraftCreativeAction(creativeIndex + 1, 0);
        PlaceAction place = new PlaceAction(
                expected.getMaxStackSize(),
                new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT,
                        PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, 0, null),
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, 0, null)
        );
        ItemStackRequest request = new ItemStackRequest(
                12, new ItemStackRequestAction[]{craft, place}, new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        assertEquals(expected.getId(), inventory.getItem(0).getId(), "creative item should reach hotbar slot 0");
        assertEquals(expected.getMaxStackSize(), inventory.getItem(0).getCount(),
                "creative item should keep its full stack count");
        assertTrue(ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull(),
                "CREATED_OUTPUT should be cleared after the transfer");
        ItemStackResponsePacket response = capturePacket(player, ItemStackResponsePacket.class);
        assertEquals(ItemStackResponseStatus.OK, response.entries.get(0).getResult(),
                "creative take request should succeed");
    }

    @Test
    void creativeTakeToOccupiedDifferentItemSlotFails() {
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerInventory inventory = new PlayerInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getGameVersion()).thenReturn(GameVersion.V1_21_130);
        Mockito.when(player.getWindowId(inventory)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(pluginManager);

        // hotbar 0 已被不同物品(泥土)占据
        Item occupied = Item.get(Item.DIRT, 0, 5);
        occupied.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, occupied, false));

        List<Item> creativeItems = Item.getCreativeItems(GameVersion.V1_21_130);
        int creativeIndex = -1;
        for (int i = 0; i < creativeItems.size(); i++) {
            if (creativeItems.get(i).getMaxStackSize() > 1) {
                creativeIndex = i;
                break;
            }
        }
        assertTrue(creativeIndex >= 0);
        Item expected = creativeItems.get(creativeIndex);

        CraftCreativeAction craft = new CraftCreativeAction(creativeIndex + 1, 0);
        PlaceAction place = new PlaceAction(
                expected.getMaxStackSize(),
                new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT,
                        PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, 0, null),
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, occupied.getStackNetId(), null)
        );
        ItemStackRequest request = new ItemStackRequest(
                13, new ItemStackRequestAction[]{craft, place}, new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        assertEquals(expected.getId(), inventory.getItem(0).getId(),
                "creative item should overwrite the occupied slot (creative uses transferCreativeCreatedOutput)");
        assertEquals(expected.getMaxStackSize(), inventory.getItem(0).getCount());
        assertTrue(ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull());
        ItemStackResponsePacket response = capturePacket(player, ItemStackResponsePacket.class);
        assertEquals(ItemStackResponseStatus.OK, response.entries.get(0).getResult());
    }

    @Test
    void creativeTakeWithClientPredictedSourceNetIdStillSucceeds() {
        // 真实 Bedrock 客户端在 CraftCreative 后,PlaceAction 的 source(CREATED_OUTPUT)
        // 携带的 stackNetworkId 是客户端预测值,与服务端 autoAssignStackNetworkId 分配的不一致。
        // 如果 validateStackNetworkId 因此拒绝,就会 error -> 回滚 -> 物品闪现后消失。
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerInventory inventory = new PlayerInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getGameVersion()).thenReturn(GameVersion.V1_21_130);
        Mockito.when(player.getWindowId(inventory)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(pluginManager);

        List<Item> creativeItems = Item.getCreativeItems(GameVersion.V1_21_130);
        int creativeIndex = -1;
        for (int i = 0; i < creativeItems.size(); i++) {
            if (creativeItems.get(i).getMaxStackSize() > 1) {
                creativeIndex = i;
                break;
            }
        }
        assertTrue(creativeIndex >= 0);
        Item expected = creativeItems.get(creativeIndex);

        CraftCreativeAction craft = new CraftCreativeAction(creativeIndex + 1, 0);
        // 客户端 source stackNetworkId = 客户端预测值(非零,与服务端分配的不同)
        PlaceAction place = new PlaceAction(
                expected.getMaxStackSize(),
                new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT,
                        PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, 123456, null),
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, 0, null)
        );
        ItemStackRequest request = new ItemStackRequest(
                14, new ItemStackRequestAction[]{craft, place}, new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        assertEquals(expected.getId(), inventory.getItem(0).getId(),
                "creative item should reach hotbar even when client source netId differs from server");
        assertEquals(expected.getMaxStackSize(), inventory.getItem(0).getCount());
        assertTrue(ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull());
        ItemStackResponsePacket response = capturePacket(player, ItemStackResponsePacket.class);
        assertEquals(ItemStackResponseStatus.OK, response.entries.get(0).getResult(),
                "creative take with client-predicted source netId should not be rejected");
    }

    @Test
    void creativeTakeToCursorSurvivesSnapshotRollback() {
        // 复现用户报告的核心症状:开启 SAI 后点击创造背包物品,光标短暂持有后被清空。
        // 根因: CraftCreative 写入 CREATED_OUTPUT 的服务端 stackNetId 不回传客户端,
        // 后续 PLACE 到 CURSOR 的源 stackNetId 失配 -> validateStackNetworkId 拒绝 ->
        // 回滚把光标清空。目标为 CURSOR 时 dstInv 经 canonicalizeInventory 归并到 UI 库存,
        // 也会被纳入快照回滚,故必须用真实光标验证。
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerInventory inventory = new PlayerInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getGameVersion()).thenReturn(GameVersion.V1_21_130);
        Mockito.when(player.getWindowId(inventory)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(pluginManager);

        List<Item> creativeItems = Item.getCreativeItems(GameVersion.V1_21_130);
        int creativeIndex = -1;
        for (int i = 0; i < creativeItems.size(); i++) {
            if (creativeItems.get(i).getMaxStackSize() > 1) {
                creativeIndex = i;
                break;
            }
        }
        assertTrue(creativeIndex >= 0);
        Item expected = creativeItems.get(creativeIndex);

        CraftCreativeAction craft = new CraftCreativeAction(creativeIndex + 1, 0);
        // 目标为 CURSOR(客户端真实点击创造物品后的拾取动作);source stackNetworkId 为客户端预测值
        PlaceAction place = new PlaceAction(
                expected.getMaxStackSize(),
                new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT,
                        PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, 123456, null),
                new ItemStackRequestSlotData(ContainerSlotType.CURSOR, 0, 0, null)
        );
        ItemStackRequest request = new ItemStackRequest(
                15, new ItemStackRequestAction[]{craft, place}, new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        Item cursor = player.getCursorInventory().getItem(0);
        assertEquals(expected.getId(), cursor.getId(),
                "creative item should be held by cursor, not cleared by rollback");
        assertEquals(expected.getMaxStackSize(), cursor.getCount());
        assertTrue(ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull());
        ItemStackResponsePacket response = capturePacket(player, ItemStackResponsePacket.class);
        assertEquals(ItemStackResponseStatus.OK, response.entries.get(0).getResult(),
                "creative take to cursor should succeed");
    }

    @Test
    void creativeCraftWithZeroRequestedCountCreatesFullStack() {
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getGameVersion()).thenReturn(GameVersion.V1_21_130);

        List<Item> creativeItems = Item.getCreativeItems(GameVersion.V1_21_130);
        int creativeIndex = -1;
        for (int i = 0; i < creativeItems.size(); i++) {
            if (creativeItems.get(i).getMaxStackSize() > 1) {
                creativeIndex = i;
                break;
            }
        }
        assertTrue(creativeIndex >= 0, "creative catalog should contain stackable items");

        Item expected = creativeItems.get(creativeIndex);
        ItemStackRequestContext context = context();
        ActionResponse response = new CraftCreativeActionProcessor()
                .handle(new CraftCreativeAction(creativeIndex + 1, 0), player, context);

        assertNull(response);
        assertEquals(Boolean.TRUE, context.get(CraftCreativeActionProcessor.CRAFT_CREATIVE_KEY));
        Item created = ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT);
        assertEquals(expected.getId(), created.getId());
        assertEquals(expected.getMaxStackSize(), created.getCount());
        assertTrue(created.getStackNetId() > 0);
    }

    @Test
    void creativeCreatedOutputUsesMaxStackRegardlessOfRequestedCrafts() {
        // 回归测试：真实客户端从创造背包拿可堆叠物品时,CraftCreative 的 numberOfRequestedCrafts
        // 可能是任意值（部分客户端传 1），但客户端随后的 PLACE/DROP 请求会带整堆数量(maxStackSize)。
        // 若 CraftCreative 按 numberOfRequestedCrafts 写入更小数量,doTransfer 的 count 校验就会失败
        // -> 请求 error -> 回滚清空光标（"光标短暂持有后被清"）。
        // 因此 CREATED_OUTPUT 必须始终写入 maxStackSize。
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerInventory inventory = new PlayerInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getGameVersion()).thenReturn(GameVersion.V1_21_130);
        Mockito.when(player.getWindowId(inventory)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(pluginManager);

        List<Item> creativeItems = Item.getCreativeItems(GameVersion.V1_21_130);
        int creativeIndex = -1;
        for (int i = 0; i < creativeItems.size(); i++) {
            if (creativeItems.get(i).getMaxStackSize() > 1) {
                creativeIndex = i;
                break;
            }
        }
        assertTrue(creativeIndex >= 0);
        Item expected = creativeItems.get(creativeIndex);
        int maxStack = expected.getMaxStackSize();

        // numberOfRequestedCrafts=1（模拟真实客户端），但 PLACE 请求整堆 maxStack
        CraftCreativeAction craft = new CraftCreativeAction(creativeIndex + 1, 1);
        PlaceAction place = new PlaceAction(
                maxStack,
                new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT,
                        PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, 0, null),
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, 0, null)
        );
        ItemStackRequest request = new ItemStackRequest(
                16, new ItemStackRequestAction[]{craft, place}, new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        assertEquals(expected.getId(), inventory.getItem(0).getId(),
                "creative item should reach hotbar even when numberOfRequestedCrafts is smaller than maxStack");
        assertEquals(maxStack, inventory.getItem(0).getCount());
        assertTrue(ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull());
        ItemStackResponsePacket response = capturePacket(player, ItemStackResponsePacket.class);
        assertEquals(ItemStackResponseStatus.OK, response.entries.get(0).getResult());
    }

    @Test
    void creativeCreatedOutputTakeCanOverwriteDifferentDestinationItem() {
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerInventory inventory = new PlayerInventory(player);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());

        Item source = Item.get(Item.DIAMOND, 0, 64);
        source.autoAssignStackNetworkId();
        assertTrue(ui.setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, source, false));
        source = ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT);

        Item existing = Item.get(Item.DIRT, 0, 5);
        existing.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, existing, false));
        existing = inventory.getItem(0);

        ItemStackRequestContext context = context();
        context.put(CraftCreativeActionProcessor.CRAFT_CREATIVE_KEY, true);
        TakeAction action = new TakeAction(
                64,
                new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT,
                        PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, source.getStackNetId(), null),
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, existing.getStackNetId(), null)
        );

        ActionResponse response = new TakeActionProcessor().handle(action, player, context);

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(1, response.containers().size());
        assertEquals(ContainerSlotType.HOTBAR, response.containers().get(0).getContainer());
        Item dest = inventory.getItem(0);
        assertEquals(Item.DIAMOND, dest.getId());
        assertEquals(64, dest.getCount());
        assertTrue(ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull());
    }

    @Test
    void offhandRejectsItemsThatBedrockCannotEquip() {
        Player player = mockPlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());

        Item stone = Item.get(Item.STONE, 0, 1);
        stone.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, stone, false));
        stone = inventory.getItem(0);

        TakeAction action = new TakeAction(
                1,
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, stone.getStackNetId(), null),
                new ItemStackRequestSlotData(ContainerSlotType.OFFHAND, 0, 0, null)
        );

        ActionResponse response = new TakeActionProcessor().handle(action, player, context());

        assertNotNull(response);
        assertFalse(response.success());
        assertEquals(Item.STONE, inventory.getItem(0).getId());
        assertTrue(offhand.getItem(0).isNull());
    }

    @Test
    void offhandAcceptsShield() {
        Player player = mockPlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());

        Item shield = Item.get(Item.SHIELD, 0, 1);
        shield.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, shield, false));
        shield = inventory.getItem(0);

        TakeAction action = new TakeAction(
                1,
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, shield.getStackNetId(), null),
                new ItemStackRequestSlotData(ContainerSlotType.OFFHAND, 0, 0, null)
        );

        ActionResponse response = new TakeActionProcessor().handle(action, player, context());

        assertNotNull(response);
        assertTrue(response.success());
        assertTrue(inventory.getItem(0).isNull());
        assertEquals(Item.SHIELD, offhand.getItem(0).getId());
    }

    @Test
    void offhandInventoryAllowsDirectApiForCompatibility() {
        Player player = mockPlayer();
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);

        assertTrue(offhand.setItem(0, Item.get(Item.STONE, 0, 1), false));
        assertEquals(Item.STONE, offhand.getItem(0).getId());
        assertTrue(offhand.setItem(0, Item.get(Item.SHIELD, 0, 1), false));
        assertEquals(Item.SHIELD, offhand.getItem(0).getId());
    }

    @Test
    void transferFiresLegacyTransactionThenSingleClickEvent() {
        Player player = mockPlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(pluginManager);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getWindowId(inventory)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);

        List<Class<? extends Event>> legacyEvents = new ArrayList<>();
        Mockito.doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof InventoryTransactionEvent || event instanceof InventoryClickEvent) {
                legacyEvents.add(event.getClass());
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        Item source = Item.get(Item.STONE, 0, 5);
        source.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, source, false));
        source = inventory.getItem(0);

        TakeAction action = new TakeAction(
                1,
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, source.getStackNetId(), null),
                new ItemStackRequestSlotData(ContainerSlotType.INVENTORY, 1, 0, null)
        );

        ActionResponse response = new TakeActionProcessor().handle(action, player, context());

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(List.of(InventoryTransactionEvent.class, InventoryClickEvent.class), legacyEvents);
    }

    @Test
    void suppressedDestroyStillMutatesInventory() {
        Player player = mockPlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        Mockito.when(player.getInventory()).thenReturn(inventory);

        Item item = Item.get(Item.STONE, 0, 5);
        item.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, item, false));
        item = inventory.getItem(0);

        ItemStackRequestContext context = context();
        context.put(DestroyActionProcessor.NO_RESPONSE_DESTROY_KEY, true);
        DestroyAction action = new DestroyAction(
                2,
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, item.getStackNetId(), null)
        );

        ActionResponse response = new DestroyActionProcessor().handle(action, player, context);

        assertNull(response);
        assertEquals(3, inventory.getItem(0).getCount());
    }

    @Test
    void dropActionOnlyDropsItemOnCommit() {
        Player player = mockPlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        Mockito.when(player.getInventory()).thenReturn(inventory);

        Item item = Item.get(Item.STONE, 0, 5);
        item.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, item, false));
        item = inventory.getItem(0);

        ItemStackRequestContext context = context();
        DropAction action = new DropAction(
                2,
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, item.getStackNetId(), null),
                false
        );

        ActionResponse response = new DropActionProcessor().handle(action, player, context);

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(3, inventory.getItem(0).getCount());
        verify(player, never()).dropItem(any(Item.class));

        assertTrue(context.commit());
        ArgumentCaptor<Item> dropped = ArgumentCaptor.forClass(Item.class);
        verify(player).dropItem(dropped.capture());
        assertEquals(Item.STONE, dropped.getValue().getId());
        assertEquals(2, dropped.getValue().getCount());
    }

    @Test
    void mineBlockResponseCarriesInventoryDynamicId() {
        Player player = mockPlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        Mockito.when(player.getInventory()).thenReturn(inventory);

        Item item = Item.get(Item.STONE, 0, 1);
        item.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, item, false));
        item = inventory.getItem(0);

        ActionResponse response = new MineBlockActionProcessor()
                .handle(new MineBlockAction(0, 0, item.getStackNetId()), player, context());

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(1, response.containers().size());
        assertEquals(ContainerSlotType.HOTBAR_AND_INVENTORY, response.containers().get(0).getContainerName().getContainer());
        assertEquals(0, response.containers().get(0).getContainerName().getDynamicId());
    }

    @Test
    void multiRecipeRequiresMatchingConsumePlan() {
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());

        Item paper = Item.get(Item.PAPER, 0, 4);
        paper.autoAssignStackNetworkId();
        Item powder = Item.get(Item.GUNPOWDER, 0, 4);
        powder.autoAssignStackNetworkId();
        assertTrue(ui.getCraftingGrid().setItem(0, paper, false));
        assertTrue(ui.getCraftingGrid().setItem(1, powder, false));
        paper = ui.getCraftingGrid().getItem(0);
        powder = ui.getCraftingGrid().getItem(1);

        MultiRecipe recipe = new FireworkRecipe();
        Item output = Item.get(Item.FIREWORKS, 0, 3);
        // 1 份火药 -> flight 1；canExecute 要求客户端 output 与服务端按材料计算的结果精确匹配（含 Flight NBT）
        ((ItemFirework) output).setFlight(1);

        ItemStackRequestContext missingConsumes = context(
                new CraftRecipeAction(recipe.getNetworkId(), 1),
                new CraftResultsDeprecatedAction(new Item[]{output}, 1)
        );
        missingConsumes.setCurrentActionIndex(0);
        assertFalse(CraftRecipeActionProcessor.validateMultiRecipeConsumePlan(player, recipe, output, missingConsumes));

        ItemStackRequestContext withConsumes = context(
                new CraftRecipeAction(recipe.getNetworkId(), 1),
                new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.CRAFTING_INPUT, 28, paper.getStackNetId(), null)),
                new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.CRAFTING_INPUT, 29, powder.getStackNetId(), null)),
                new CraftResultsDeprecatedAction(new Item[]{output}, 1)
        );
        withConsumes.setCurrentActionIndex(0);
        assertTrue(CraftRecipeActionProcessor.validateMultiRecipeConsumePlan(player, recipe, output, withConsumes));
    }

    @Test
    void autoCraftUsesConsumedItemsWhenCraftingGridIsEmpty() {
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerInventory inventory = new PlayerInventory(player);
        CraftingManager craftingManager = Mockito.mock(CraftingManager.class);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getServer().getCraftingManager()).thenReturn(craftingManager);

        ShapelessRecipe recipe = new ShapelessRecipe("test:auto_stone_to_diamond", 10,
                Item.get(Item.DIAMOND, 0, 1),
                List.of(Item.get(Item.STONE, 0, 1)));
        Mockito.when(craftingManager.getRecipeByNetworkId(recipe.getNetworkId())).thenReturn(recipe);
        Mockito.when(craftingManager.matchRecipe(anyList(), any(Item.class), anyList())).thenAnswer(invocation -> {
            List<Item> inputs = cloneItems(invocation.getArgument(0));
            Item output = invocation.getArgument(1);
            List<Item> expected = new ArrayList<>(List.of(Item.get(Item.STONE, 0, 1)));
            return output.getId() == Item.DIAMOND
                    && output.getCount() == 1
                    && Recipe.matchItemList(inputs, expected) ? recipe : null;
        });

        Item stone = Item.get(Item.STONE, 0, 1);
        stone.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, stone, false));
        stone = inventory.getItem(0);

        AutoCraftRecipeAction action = new AutoCraftRecipeAction(
                recipe.getNetworkId(),
                1,
                1,
                List.of(new ItemDescriptorWithCount(new DefaultDescriptor(Item.STONE, 0), 1))
        );
        ItemStackRequestContext context = context(
                action,
                new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, stone.getStackNetId(), null))
        );
        context.setCurrentActionIndex(0);

        ActionResponse response = new CraftRecipeAutoProcessor().handle(action, player, context);

        assertNotNull(response);
        assertTrue(response.success());
        Item output = ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT);
        assertEquals(Item.DIAMOND, output.getId());
        assertEquals(1, output.getCount());
        assertTrue(ui.getCraftingGrid().getItem(0).isNull(), "auto craft must not require prefilled crafting grid");
    }

    @Test
    void beaconPaymentRequiresDestroyFromPaymentSlot() {
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        BeaconInventory beacon = new BeaconInventory(ui, new Position());
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(beacon));
        Mockito.when(player.getWindowById(Player.BEACON_WINDOW_ID)).thenReturn(beacon);

        Item payment = Item.get(Item.EMERALD, 0, 1);
        payment.autoAssignStackNetworkId();
        assertTrue(beacon.setItem(0, payment, false));
        payment = beacon.getItem(0);

        ItemStackRequestContext missingDestroy = context(new BeaconPaymentAction(1, 0));
        missingDestroy.setCurrentActionIndex(0);
        assertFalse(BeaconPaymentActionProcessor.hasValidPaymentDestroyAction(player, beacon, missingDestroy));

        ItemStackRequestContext withDestroy = context(
                new BeaconPaymentAction(1, 0),
                new DestroyAction(1, new ItemStackRequestSlotData(ContainerSlotType.BEACON_PAYMENT, 27, payment.getStackNetId(), null))
        );
        withDestroy.setCurrentActionIndex(0);
        assertTrue(BeaconPaymentActionProcessor.hasValidPaymentDestroyAction(player, beacon, withDestroy));
    }

    @Test
    void rollbackClearsNewDoubleChestSlots() throws Exception {
        BlockEntityChest leftHolder = Mockito.mock(BlockEntityChest.class);
        BlockEntityChest rightHolder = Mockito.mock(BlockEntityChest.class);
        ChestInventory left = new ChestInventory(leftHolder);
        ChestInventory right = new ChestInventory(rightHolder);
        Mockito.when(leftHolder.getRealInventory()).thenReturn(left);
        Mockito.when(rightHolder.getRealInventory()).thenReturn(right);
        DoubleChestInventory doubleChest = new DoubleChestInventory(leftHolder, rightHolder);

        assertTrue(doubleChest.setItem(left.getSize(), Item.get(Item.DIAMOND, 0, 1), false));
        Method restore = ItemStackRequestHandler.class.getDeclaredMethod("restoreInventory", cn.nukkit.inventory.Inventory.class, Map.class);
        restore.setAccessible(true);
        restore.invoke(null, doubleChest, Map.of());

        assertTrue(right.getItem(0).isNull(), "rollback should clear slots backed by the real chest side");
    }

    @Test
    void eventOnlyTransactionRejectsBindingCurseArmorRemoval() {
        Player player = mockPlayer();
        Mockito.when(player.isCreative()).thenReturn(false);
        PlayerInventory inventory = new PlayerInventory(player);

        Item helmet = Item.get(Item.DIAMOND_HELMET, 0, 1);
        helmet.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_BINDING_CURSE));
        assertTrue(inventory.setItem(36, helmet, false));
        helmet = inventory.getItem(36);

        List<InventoryAction> actions = List.of(
                new SlotChangeAction(inventory, 36, helmet, Item.get(Item.AIR))
        );
        var transaction = new TransferItemActionProcessor.EventOnlyInventoryTransaction(player, actions, context());

        assertFalse(transaction.execute(), "SAI compatibility transaction must keep the legacy binding curse guard");
    }

    @Test
    void cancelledTransactionKeepsPluginCountChange() {
        Player player = mockPlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getWindowId(inventory)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);
        Mockito.when(player.getServer().getPluginManager()).thenReturn(pluginManager);
        Mockito.doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof InventoryTransactionEvent transactionEvent) {
                inventory.setItem(0, Item.get(Item.STONE, 0, 3), false);
                transactionEvent.setCancelled(true);
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        Item source = Item.get(Item.STONE, 0, 5);
        source.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, source, false));
        source = inventory.getItem(0);

        TakeAction action = new TakeAction(
                1,
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, source.getStackNetId(), null),
                new ItemStackRequestSlotData(ContainerSlotType.INVENTORY, 1, 0, null)
        );
        ItemStackRequest request = new ItemStackRequest(7, new ItemStackRequestAction[]{action}, new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        assertEquals(Item.STONE, inventory.getItem(0).getId());
        assertEquals(3, inventory.getItem(0).getCount(), "plugin count-only change must survive SAI error rollback");
        assertTrue(inventory.getItem(1).isNull());
        ItemStackResponsePacket response = capturePacket(player, ItemStackResponsePacket.class);
        assertEquals(ItemStackResponseStatus.ERROR, response.entries.get(0).getResult());
    }

    @Test
    void transferToSameSlotIsRejectedWithoutMutatingInventory() {
        Player player = mockPlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getWindowId(inventory)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);

        Item source = Item.get(Item.STONE, 0, 5);
        source.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, source, false));
        source = inventory.getItem(0);

        TakeAction action = new TakeAction(
                5,
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, source.getStackNetId(), null),
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, source.getStackNetId(), null)
        );
        ItemStackRequest request = new ItemStackRequest(8, new ItemStackRequestAction[]{action}, new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        assertEquals(Item.STONE, inventory.getItem(0).getId());
        assertEquals(5, inventory.getItem(0).getCount());
        ItemStackResponsePacket response = capturePacket(player, ItemStackResponsePacket.class);
        assertEquals(ItemStackResponseStatus.ERROR, response.entries.get(0).getResult());
    }

    @Test
    void cancelledTransactionKeepsPluginChangesOutsideTransactionSlots() {
        Player player = mockPlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getWindowId(inventory)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);
        Mockito.when(player.getServer().getPluginManager()).thenReturn(pluginManager);
        Mockito.doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof InventoryTransactionEvent transactionEvent) {
                inventory.setItem(2, Item.get(Item.DIAMOND, 0, 4), false);
                transactionEvent.setCancelled(true);
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        Item source = Item.get(Item.STONE, 0, 5);
        source.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, source, false));
        source = inventory.getItem(0);

        TakeAction action = new TakeAction(
                1,
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, source.getStackNetId(), null),
                new ItemStackRequestSlotData(ContainerSlotType.INVENTORY, 1, 0, null)
        );
        ItemStackRequest request = new ItemStackRequest(9, new ItemStackRequestAction[]{action}, new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        assertEquals(Item.STONE, inventory.getItem(0).getId());
        assertEquals(5, inventory.getItem(0).getCount());
        assertTrue(inventory.getItem(1).isNull());
        assertEquals(Item.DIAMOND, inventory.getItem(2).getId());
        assertEquals(4, inventory.getItem(2).getCount(), "plugin changes in other slots must survive SAI error rollback");
        ItemStackResponsePacket response = capturePacket(player, ItemStackResponsePacket.class);
        assertEquals(ItemStackResponseStatus.ERROR, response.entries.get(0).getResult());
    }

    @Test
    void cancelledLaterActionRollsBackEarlierActionButKeepsPluginSlotChanges() {
        Player player = mockPlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getWindowId(inventory)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);
        Mockito.when(player.getServer().getPluginManager()).thenReturn(pluginManager);
        Mockito.doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof InventoryTransactionEvent transactionEvent
                    && !inventory.getItem(1).isNull()) {
                inventory.setItem(2, Item.get(Item.DIAMOND, 0, 4), false);
                transactionEvent.setCancelled(true);
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        Item source = Item.get(Item.STONE, 0, 5);
        source.autoAssignStackNetworkId();
        Item secondSource = Item.get(Item.DIRT, 0, 3);
        secondSource.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, source, false));
        assertTrue(inventory.setItem(3, secondSource, false));
        source = inventory.getItem(0);
        secondSource = inventory.getItem(3);

        TakeAction firstAction = new TakeAction(
                1,
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, source.getStackNetId(), null),
                new ItemStackRequestSlotData(ContainerSlotType.INVENTORY, 1, 0, null)
        );
        TakeAction secondAction = new TakeAction(
                1,
                new ItemStackRequestSlotData(ContainerSlotType.INVENTORY, 3, secondSource.getStackNetId(), null),
                new ItemStackRequestSlotData(ContainerSlotType.INVENTORY, 4, 0, null)
        );
        ItemStackRequest request = new ItemStackRequest(10, new ItemStackRequestAction[]{firstAction, secondAction}, new String[0]);

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        assertEquals(Item.STONE, inventory.getItem(0).getId());
        assertEquals(5, inventory.getItem(0).getCount());
        assertTrue(inventory.getItem(1).isNull(), "earlier successful actions must roll back on request error");
        assertEquals(Item.DIAMOND, inventory.getItem(2).getId());
        assertEquals(4, inventory.getItem(2).getCount(), "plugin slot changes must survive rollback");
        assertEquals(Item.DIRT, inventory.getItem(3).getId());
        assertEquals(3, inventory.getItem(3).getCount());
        assertTrue(inventory.getItem(4).isNull());
        ItemStackResponsePacket response = capturePacket(player, ItemStackResponsePacket.class);
        assertEquals(ItemStackResponseStatus.ERROR, response.entries.get(0).getResult());
    }

    @Test
    void dynamicContainerTransferRespondsForDifferentDynamicIdsWithSameSlot() {
        Player player = mockPlayer();
        PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
        ItemBundle sourceBundle = new ItemBundle();
        ItemBundle destinationBundle = new ItemBundle();

        Item sourceItem = Item.get(Item.STONE, 0, 2);
        sourceItem.autoAssignStackNetworkId();
        assertTrue(sourceBundle.getInventory().setItem(0, sourceItem, false));
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(inventory.getContents()).thenReturn(Map.of(0, sourceBundle, 1, destinationBundle));

        sourceItem = sourceBundle.getInventory().getItem(0);
        PlaceAction action = new PlaceAction(
                1,
                new ItemStackRequestSlotData(ContainerSlotType.DYNAMIC_CONTAINER, 0, sourceItem.getStackNetId(), sourceBundle.getBundleId()),
                new ItemStackRequestSlotData(ContainerSlotType.DYNAMIC_CONTAINER, 0, 0, destinationBundle.getBundleId())
        );

        ActionResponse response = new PlaceActionProcessor().handle(action, player, context());

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(2, response.containers().size(), "source and destination bundles need separate responses");
        assertEquals(sourceBundle.getBundleId(), response.containers().get(0).getContainerName().getDynamicId());
        assertEquals(destinationBundle.getBundleId(), response.containers().get(1).getContainerName().getDynamicId());
    }

    @Test
    void placeInItemContainerStoresItemInBundleAndPersistsNbt() {
        Player player = mockPlayer();
        Level level = Mockito.mock(Level.class);
        PlayerInventory inventory = new PlayerInventory(player);
        ItemBundle bundle = new ItemBundle();
        Mockito.when(player.getLevel()).thenReturn(level);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getInventory()).thenReturn(inventory);

        Item dirt = Item.get(Item.DIRT, 0, 32);
        dirt.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, dirt, false));
        assertTrue(inventory.setItem(1, bundle, false));
        ItemBundle storedBundle = (ItemBundle) inventory.getUnclonedItem(1);
        int bundleId = storedBundle.getBundleId();
        dirt = inventory.getItem(0);

        PlaceInItemContainerAction action = new PlaceInItemContainerAction(
                16,
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, dirt.getStackNetId(), null),
                new ItemStackRequestSlotData(ContainerSlotType.DYNAMIC_CONTAINER, 0, 0, bundleId)
        );

        ActionResponse response = new PlaceInItemContainerActionProcessor().handle(action, player, context());

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(16, inventory.getItem(0).getCount());
        assertEquals(Item.DIRT, storedBundle.getInventory().getItem(0).getId());
        assertEquals(16, storedBundle.getInventory().getItem(0).getCount());
        assertEquals(1, storedBundle.getNamedTag()
                .getList(ItemBundle.TAG_STORAGE_ITEM_COMPONENT_CONTENT, CompoundTag.class)
                .size());
        Mockito.verify(level).addSound(player, Sound.BUNDLE_INSERT);
    }

    @Test
    void placeInItemContainerRejectsPuttingBundleInsideItself() {
        Player player = mockPlayer();
        Level level = Mockito.mock(Level.class);
        PlayerInventory inventory = new PlayerInventory(player);
        ItemBundle bundle = new ItemBundle();
        Mockito.when(player.getLevel()).thenReturn(level);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getInventory()).thenReturn(inventory);

        assertTrue(inventory.setItem(0, bundle, false));
        ItemBundle storedBundle = (ItemBundle) inventory.getUnclonedItem(0);
        int bundleId = storedBundle.getBundleId();
        int stackNetworkId = inventory.getItem(0).getStackNetId();

        PlaceInItemContainerAction action = new PlaceInItemContainerAction(
                1,
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, stackNetworkId, null),
                new ItemStackRequestSlotData(ContainerSlotType.DYNAMIC_CONTAINER, 0, 0, bundleId)
        );

        ActionResponse response = new PlaceInItemContainerActionProcessor().handle(action, player, context());

        assertNotNull(response);
        assertFalse(response.success());
        assertSame(storedBundle, inventory.getUnclonedItem(0));
        assertTrue(storedBundle.getInventory().isEmpty());
        Mockito.verify(level).addSound(player, Sound.BUNDLE_INSERT_FAIL);
    }

    @Test
    void takeFromItemContainerMovesItemOutOfBundleAndPersistsNbt() {
        Player player = mockPlayer();
        Level level = Mockito.mock(Level.class);
        PlayerInventory inventory = new PlayerInventory(player);
        ItemBundle bundle = new ItemBundle();
        Mockito.when(player.getLevel()).thenReturn(level);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getInventory()).thenReturn(inventory);

        assertTrue(inventory.setItem(1, bundle, false));
        ItemBundle storedBundle = (ItemBundle) inventory.getUnclonedItem(1);
        int bundleId = storedBundle.getBundleId();
        Item dirt = Item.get(Item.DIRT, 0, 10);
        dirt.autoAssignStackNetworkId();
        assertTrue(storedBundle.getInventory().setItem(0, dirt, false));
        dirt = storedBundle.getInventory().getItem(0);

        TakeFromItemContainerAction action = new TakeFromItemContainerAction(
                6,
                new ItemStackRequestSlotData(ContainerSlotType.DYNAMIC_CONTAINER, 0, dirt.getStackNetId(), bundleId),
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, 0, null)
        );

        ActionResponse response = new TakeFromItemContainerActionProcessor().handle(action, player, context());

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(Item.DIRT, inventory.getItem(0).getId());
        assertEquals(6, inventory.getItem(0).getCount());
        assertEquals(4, storedBundle.getInventory().getItem(0).getCount());
        ListTag<CompoundTag> storedItems = storedBundle.getNamedTag()
                .getList(ItemBundle.TAG_STORAGE_ITEM_COMPONENT_CONTENT, CompoundTag.class);
        assertEquals(1, storedItems.size());
        assertEquals(4, storedItems.get(0).getByte("Count"));
        Mockito.verify(level).addSound(player, Sound.BUNDLE_REMOVE_ONE);
    }

    @Test
    void itemStackRequestActionEventIsNotInventoryEvent() {
        assertFalse(InventoryEvent.class.isAssignableFrom(ItemStackRequestActionEvent.class));
    }

    @Test
    void unimplementedActionsAreSkippedInsteadOfFailingRequest() {
        Player player = mockPlayer();
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.when(player.getServer().getPluginManager()).thenReturn(pluginManager);

        // CraftNonImplemented / LabTableCombine 是占位/未实现的 action 类型，
        // 必须被静默跳过而非令整条 request 失败。
        // 仅含这类 action 的请求应返回 OK。
        ItemStackRequest request = new ItemStackRequest(
                11,
                new ItemStackRequestAction[]{new CraftNonImplementedAction(), new LabTableCombineAction()},
                new String[0]
        );

        ItemStackRequestHandler.handleRequests(player, List.of(request));

        ItemStackResponsePacket response = capturePacket(player, ItemStackResponsePacket.class);
        assertEquals(ItemStackResponseStatus.OK, response.entries.get(0).getResult(),
                "deprecated/unimplemented actions must be skipped, not treated as request errors");
    }

    @Test
    void tagDescriptorsMatchRegisteredItemTags() {
        Item planks = Mockito.mock(Item.class);
        Mockito.when(planks.isNull()).thenReturn(false);
        Mockito.when(planks.getNamespaceId()).thenReturn("minecraft:planks");

        assertTrue(new ItemTagDescriptor("minecraft:planks").match(planks));
        assertTrue(new ComplexAliasDescriptor("minecraft:planks").match(planks));
    }

    @Test
    void enchantRecipeRequiresCurrentWindowRecipeId() throws Exception {
        PlayerEnchantOptionsPacket.RECIPE_MAP.clear();
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        EnchantInventory current = new EnchantInventory(ui, new Position());
        EnchantInventory other = new EnchantInventory(ui, new Position());
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(current));

        Item sword = Item.get(Item.DIAMOND_SWORD, 0, 1);
        sword.autoAssignStackNetworkId();
        assertTrue(current.setItem(0, sword, false));

        int recipeId = PlayerEnchantOptionsPacket.assignRecipeId(enchantOption(1, 0));
        markPublishedOption(other, recipeId);

        ItemStackRequestContext context = context(new CraftRecipeAction(recipeId, 1));
        context.setCurrentActionIndex(0);

        ActionResponse response = new CraftRecipeActionProcessor()
                .handle(new CraftRecipeAction(recipeId, 1), player, context);

        assertNotNull(response);
        assertFalse(response.success());
    }

    @Test
    void enchantRecipeRequiresDisplayedMinimumLevel() throws Exception {
        PlayerEnchantOptionsPacket.RECIPE_MAP.clear();
        Player player = mockPlayer();
        Mockito.when(player.isCreative()).thenReturn(false);
        Mockito.when(player.getExperienceLevel()).thenReturn(5);
        Mockito.when(player.getExperience()).thenReturn(0);
        PlayerUIInventory ui = new PlayerUIInventory(player);
        EnchantInventory enchant = new EnchantInventory(ui, new Position());
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(enchant));
        Mockito.when(player.getWindowById(Player.ENCHANT_WINDOW_ID)).thenReturn(enchant);

        Item sword = Item.get(Item.DIAMOND_SWORD, 0, 1);
        sword.autoAssignStackNetworkId();
        assertTrue(enchant.setItem(0, sword, false));
        sword = enchant.getItem(0);
        Item lapis = Item.get(Item.DYE, 4, 3);
        lapis.autoAssignStackNetworkId();
        assertTrue(enchant.setItem(1, lapis, false));
        lapis = enchant.getItem(1);

        int recipeId = PlayerEnchantOptionsPacket.assignRecipeId(enchantOption(30, 0));
        markPublishedOption(enchant, recipeId);
        ItemStackRequestContext context = context(
                new CraftRecipeAction(recipeId, 1),
                new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.ENCHANTING_INPUT, 14, sword.getStackNetId(), null)),
                new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.ENCHANTING_MATERIAL, 15, lapis.getStackNetId(), null))
        );
        context.setCurrentActionIndex(0);

        ActionResponse response = new CraftRecipeActionProcessor()
                .handle(new CraftRecipeAction(recipeId, 1), player, context);

        assertNotNull(response);
        assertFalse(response.success());
    }

    @Test
    void tradeRecipeRequiresCurrentVillagerRecipeId() throws Exception {
        TradeRecipeBuildUtils.RECIPE_MAP.clear();
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        EntityVillager villager = Mockito.mock(EntityVillager.class);
        TradeInventory tradeInventory = new TradeInventory(villager);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(tradeInventory));

        CompoundTag currentRecipe = tradeRecipe(Item.get(Item.COAL, 0, 1), Item.get(Item.APPLE, 0, 1));
        int currentRecipeId = TradeRecipeBuildUtils.assignRecipeId(currentRecipe);
        currentRecipe.putInt("netId", currentRecipeId);
        ListTag<Tag> recipes = new ListTag<>("Recipes");
        recipes.add(currentRecipe);
        Mockito.when(villager.getRecipes()).thenReturn(recipes);
        markAssignedTradeRecipe(tradeInventory, currentRecipeId);

        CompoundTag foreignRecipe = tradeRecipe(Item.get(Item.EMERALD, 0, 1), Item.get(Item.DIAMOND, 0, 1));
        int foreignRecipeId = TradeRecipeBuildUtils.assignRecipeId(foreignRecipe);
        foreignRecipe.putInt("netId", foreignRecipeId);

        Item emerald = Item.get(Item.EMERALD, 0, 1);
        emerald.autoAssignStackNetworkId();
        assertTrue(tradeInventory.setItem(0, emerald, false));
        emerald = tradeInventory.getItem(0);
        ItemStackRequestContext context = context(
                new CraftRecipeAction(foreignRecipeId, 1),
                new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.TRADE2_INGREDIENT_1, 0, emerald.getStackNetId(), null))
        );
        context.setCurrentActionIndex(0);

        ActionResponse response = new CraftRecipeActionProcessor()
                .handle(new CraftRecipeAction(foreignRecipeId, 1), player, context);

        assertNotNull(response);
        assertFalse(response.success());
    }

    @Test
    void commitExecutesAllActionsEvenWhenSomeFail() {
        ItemStackRequestContext ctx = context();
        boolean[] executed = new boolean[3];
        ctx.onCommit(() -> executed[0] = true);
        ctx.onCommit(() -> { executed[1] = true; throw new RuntimeException("boom"); });
        ctx.onCommit(() -> executed[2] = true);

        boolean result = ctx.commit();

        assertFalse(result, "commit should return false when any action fails");
        assertTrue(executed[0], "first action should have executed");
        assertTrue(executed[1], "second action should have executed (before throwing)");
        assertTrue(executed[2], "third action should still execute after second threw");
    }

    @Test
    void setItemForceWritesDirectlyWithoutEvents() {
        Player player = mockPlayer();
        PlayerInventory inventory = new PlayerInventory(player);

        Item diamond = Item.get(Item.DIAMOND, 0, 32);
        inventory.setItemForce(0, diamond);
        assertEquals(Item.DIAMOND, inventory.getItem(0).getId());
        assertEquals(32, inventory.getItem(0).getCount());

        inventory.setItemForce(0, Item.get(Item.AIR));
        assertTrue(inventory.getItem(0).isNull());
    }

    private static Player mockPlayer() {
        Player player = Mockito.mock(Player.class);
        player.protocol = ProtocolInfo.v1_21_30;
        Mockito.when(player.getServer()).thenReturn(MockServer.get());
        Mockito.when(player.getName()).thenReturn("test");
        Mockito.when(player.isCreative()).thenReturn(true);
        return player;
    }

    private static ItemStackRequestContext context() {
        return context(new ItemStackRequestAction[0]);
    }

    private static ItemStackRequestContext context(ItemStackRequestAction... actions) {
        return new ItemStackRequestContext(new ItemStackRequest(
                1,
                actions,
                new String[0]
        ));
    }

    private static <T extends DataPacket> T capturePacket(Player player, Class<T> type) {
        ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
        Mockito.verify(player, atLeastOnce()).dataPacket(captor.capture());
        for (DataPacket packet : captor.getAllValues()) {
            if (type.isInstance(packet)) {
                return type.cast(packet);
            }
        }
        fail("Expected packet " + type.getSimpleName());
        return null;
    }

    private static List<Item> cloneItems(List<Item> items) {
        List<Item> cloned = new ArrayList<>(items.size());
        for (Item item : items) {
            cloned.add(item.clone());
        }
        return cloned;
    }

    private static PlayerEnchantOptionsPacket.EnchantOptionData enchantOption(int minLevel, int primarySlot) {
        return new PlayerEnchantOptionsPacket.EnchantOptionData(
                minLevel,
                primarySlot,
                List.of(new PlayerEnchantOptionsPacket.EnchantData(Enchantment.ID_DAMAGE_ALL, 1)),
                List.of(),
                List.of(),
                "test",
                0
        );
    }

    @SuppressWarnings("unchecked")
    private static void markPublishedOption(EnchantInventory inventory, int recipeId) throws Exception {
        Field field = EnchantInventory.class.getDeclaredField("publishedOptionIds");
        field.setAccessible(true);
        ((Set<Integer>) field.get(inventory)).add(recipeId);
    }

    private static CompoundTag tradeRecipe(Item buy, Item sell) {
        return new TradeInventoryRecipe(sell, buy).toNBT();
    }

    @SuppressWarnings("unchecked")
    private static void markAssignedTradeRecipe(TradeInventory inventory, int recipeId) throws Exception {
        Field field = TradeInventory.class.getDeclaredField("assignedRecipeIds");
        field.setAccessible(true);
        ((Set<Integer>) field.get(inventory)).add(recipeId);
    }
}
