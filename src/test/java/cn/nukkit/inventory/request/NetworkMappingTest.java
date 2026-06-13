package cn.nukkit.inventory.request;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NetworkMappingTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void levelEntityResolvesToTopWindow() {
        Player player = Mockito.mock(Player.class);
        Inventory topWindow = Mockito.mock(Inventory.class);

        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(topWindow));

        // LEVEL_ENTITY is a catch-all (chest/hopper/dispenser/...) and intentionally
        // returns the open window without a type check.
        assertSame(topWindow, NetworkMapping.getInventory(player, ContainerSlotType.LEVEL_ENTITY, null));
    }

    @Test
    void typedContainerSlotsRejectMismatchedTopWindow() {
        Player player = Mockito.mock(Player.class);
        // topWindow is a barrel, but the client claims furnace/brewing/shulker/crafter/trade slots.
        Inventory barrel = Mockito.mock(BarrelInventory.class);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(barrel));

        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.FURNACE_INGREDIENT, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.FURNACE_FUEL, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.BLAST_FURNACE_INGREDIENT, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.SMOKER_INGREDIENT, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.BREWING_INPUT, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.BREWING_FUEL, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.SHULKER_BOX, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.CRAFTER_BLOCK_CONTAINER, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.TRADE_INGREDIENT_1, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.TRADE2_RESULT, null));
    }

    @Test
    void typedContainerSlotsResolveWhenTopWindowMatches() {
        Player player = Mockito.mock(Player.class);

        FurnaceInventory furnace = Mockito.mock(FurnaceInventory.class);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(furnace));
        assertSame(furnace, NetworkMapping.getInventory(player, ContainerSlotType.FURNACE_INGREDIENT, null));
        assertSame(furnace, NetworkMapping.getInventory(player, ContainerSlotType.FURNACE_FUEL, null));
        assertSame(furnace, NetworkMapping.getInventory(player, ContainerSlotType.FURNACE_RESULT, null));

        // BlastFurnaceInventory / SmokerInventory extend FurnaceInventory, so a single
        // instanceof check covers all three furnace variants.
        BlastFurnaceInventory blast = Mockito.mock(BlastFurnaceInventory.class);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(blast));
        assertSame(blast, NetworkMapping.getInventory(player, ContainerSlotType.BLAST_FURNACE_INGREDIENT, null));

        SmokerInventory smoker = Mockito.mock(SmokerInventory.class);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(smoker));
        assertSame(smoker, NetworkMapping.getInventory(player, ContainerSlotType.SMOKER_INGREDIENT, null));

        BrewingInventory brewing = Mockito.mock(BrewingInventory.class);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(brewing));
        assertSame(brewing, NetworkMapping.getInventory(player, ContainerSlotType.BREWING_INPUT, null));

        ShulkerBoxInventory shulker = Mockito.mock(ShulkerBoxInventory.class);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(shulker));
        assertSame(shulker, NetworkMapping.getInventory(player, ContainerSlotType.SHULKER_BOX, null));

        BarrelInventory barrel = Mockito.mock(BarrelInventory.class);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(barrel));
        assertSame(barrel, NetworkMapping.getInventory(player, ContainerSlotType.BARREL, null));

        CrafterInventory crafter = Mockito.mock(CrafterInventory.class);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(crafter));
        assertSame(crafter, NetworkMapping.getInventory(player, ContainerSlotType.CRAFTER_BLOCK_CONTAINER, null));

        TradeInventory trade = Mockito.mock(TradeInventory.class);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(trade));
        assertSame(trade, NetworkMapping.getInventory(player, ContainerSlotType.TRADE_INGREDIENT_1, null));
        assertSame(trade, NetworkMapping.getInventory(player, ContainerSlotType.TRADE2_RESULT, null));
    }

    @Test
    void typedContainerSlotsRejectNullTopWindow() {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());

        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.FURNACE_INGREDIENT, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.BARREL, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.SHULKER_BOX, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.CRAFTER_BLOCK_CONTAINER, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.TRADE_INGREDIENT_1, null));
    }

    @Test
    void educationEditionSlotsReturnNullRegardlessOfTopWindow() {
        Player player = Mockito.mock(Player.class);
        Inventory topWindow = Mockito.mock(Inventory.class);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(topWindow));

        // MOT does not implement education-edition chemistry containers.
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.COMPOUND_CREATOR_INPUT, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.COMPOUND_CREATOR_OUTPUT, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.MATERIAL_REDUCER_INPUT, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.MATERIAL_REDUCER_OUTPUT, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.LAB_TABLE_INPUT, null));
        assertNull(NetworkMapping.getInventory(player, ContainerSlotType.ELEMENT_CONSTRUCTOR_OUTPUT, null));
    }

    @Test
    void craftingInputResolvesToCurrentPlayerCraftingGrid() {
        Player player = Mockito.mock(Player.class);
        Inventory unrelatedTopWindow = Mockito.mock(Inventory.class);
        CraftingGrid activeGrid = Mockito.mock(CraftingGrid.class);

        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(unrelatedTopWindow));
        Mockito.when(player.getCraftingGrid()).thenReturn(activeGrid);

        assertSame(activeGrid, NetworkMapping.getInventory(player, ContainerSlotType.CRAFTING_INPUT, null));
    }

    @Test
    void uiNetworkSlotsMapToComponentLocalSlots() {
        assertEquals(0, NetworkMapping.toInternalSlot(ContainerSlotType.CRAFTING_INPUT, 28));
        assertEquals(3, NetworkMapping.toInternalSlot(ContainerSlotType.CRAFTING_INPUT, 31));
        assertEquals(0, NetworkMapping.toInternalSlot(ContainerSlotType.CRAFTING_INPUT, 32));
        assertEquals(8, NetworkMapping.toInternalSlot(ContainerSlotType.CRAFTING_INPUT, 40));
        assertEquals(0, NetworkMapping.toInternalSlot(ContainerSlotType.ENCHANTING_INPUT, 14));
        assertEquals(1, NetworkMapping.toInternalSlot(ContainerSlotType.ENCHANTING_MATERIAL, 15));
        assertEquals(0, NetworkMapping.toInternalSlot(ContainerSlotType.SMITHING_TABLE_INPUT, 51));
        assertEquals(1, NetworkMapping.toInternalSlot(ContainerSlotType.SMITHING_TABLE_MATERIAL, 52));
        assertEquals(2, NetworkMapping.toInternalSlot(ContainerSlotType.SMITHING_TABLE_TEMPLATE, 53));
        assertEquals(0, NetworkMapping.toInternalSlot(ContainerSlotType.STONECUTTER_INPUT, 3));
    }

    @Test
    void dynamicContainerCanResolveNestedBundleFromAccessibleInventories() {
        Player player = Mockito.mock(Player.class);
        PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
        PlayerOffhandInventory offhand = Mockito.mock(PlayerOffhandInventory.class);
        PlayerCursorInventory cursor = Mockito.mock(PlayerCursorInventory.class);
        CraftingGrid craftingGrid = Mockito.mock(CraftingGrid.class);

        ItemBundle outerBundle = new ItemBundle();
        ItemBundle innerBundle = new ItemBundle();
        outerBundle.getInventory().setItem(0, innerBundle, false);

        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getCursorInventory()).thenReturn(cursor);
        Mockito.when(player.getCraftingGrid()).thenReturn(craftingGrid);
        Mockito.when(inventory.getContents()).thenReturn(Map.of());
        Mockito.when(offhand.getContents()).thenReturn(Map.of(0, outerBundle));
        Mockito.when(cursor.getContents()).thenReturn(Map.of());
        Mockito.when(craftingGrid.getContents()).thenReturn(Map.of());

        Inventory resolved = NetworkMapping.getInventory(player, ContainerSlotType.DYNAMIC_CONTAINER, innerBundle.getBundleId());
        BundleInventory bundleInventory = assertInstanceOf(BundleInventory.class, resolved);

        assertEquals(innerBundle.getBundleId(), bundleInventory.getHolder().getBundleId());
        assertEquals(innerBundle.getInventory().getContents(), bundleInventory.getContents());
    }

    @Test
    void clonedBundlesReceiveDistinctDynamicContainerIdsInSameInventory() {
        Player player = Mockito.mock(Player.class);
        PlayerOffhandInventory offhand = Mockito.mock(PlayerOffhandInventory.class);
        PlayerCursorInventory cursor = Mockito.mock(PlayerCursorInventory.class);
        CraftingGrid craftingGrid = Mockito.mock(CraftingGrid.class);

        Player holder = Mockito.mock(Player.class);
        PlayerInventory realInventory = new PlayerInventory(holder);

        ItemBundle firstBundle = new ItemBundle();
        firstBundle.getInventory().setItem(0, Item.get(Item.STONE, 0, 1), false);
        ItemBundle secondBundle = firstBundle.clone();
        assertTrue(realInventory.setItem(0, firstBundle, false));
        assertTrue(realInventory.setItem(1, secondBundle, false));
        firstBundle = (ItemBundle) realInventory.getUnclonedItem(0);
        secondBundle = (ItemBundle) realInventory.getUnclonedItem(1);

        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getInventory()).thenReturn(realInventory);
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getCursorInventory()).thenReturn(cursor);
        Mockito.when(player.getCraftingGrid()).thenReturn(craftingGrid);
        Mockito.when(offhand.getContents()).thenReturn(Map.of());
        Mockito.when(cursor.getContents()).thenReturn(Map.of());
        Mockito.when(craftingGrid.getContents()).thenReturn(Map.of());

        assertNotEquals(firstBundle.getBundleId(), secondBundle.getBundleId());

        Inventory resolved = NetworkMapping.getInventory(player, ContainerSlotType.DYNAMIC_CONTAINER, secondBundle.getBundleId());
        BundleInventory bundleInventory = assertInstanceOf(BundleInventory.class, resolved);

        assertSame(secondBundle.getInventory(), bundleInventory);
    }

    @Test
    void clonedBundleIdDoesNotCollideWithNestedBundleInSameAccessibleInventory() {
        Player player = Mockito.mock(Player.class);
        PlayerOffhandInventory offhand = Mockito.mock(PlayerOffhandInventory.class);
        PlayerCursorInventory cursor = Mockito.mock(PlayerCursorInventory.class);
        CraftingGrid craftingGrid = Mockito.mock(CraftingGrid.class);

        Player holder = Mockito.mock(Player.class);
        PlayerInventory realInventory = new PlayerInventory(holder);

        ItemBundle nestedBundle = new ItemBundle();
        ItemBundle outerBundle = new ItemBundle();
        assertTrue(outerBundle.getInventory().setItem(0, nestedBundle, false));
        ItemBundle looseBundle = nestedBundle.clone();
        assertTrue(realInventory.setItem(0, outerBundle, false));
        assertTrue(realInventory.setItem(1, looseBundle, false));
        outerBundle = (ItemBundle) realInventory.getUnclonedItem(0);
        looseBundle = (ItemBundle) realInventory.getUnclonedItem(1);
        nestedBundle = (ItemBundle) outerBundle.getInventory().getUnclonedItem(0);

        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getInventory()).thenReturn(realInventory);
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getCursorInventory()).thenReturn(cursor);
        Mockito.when(player.getCraftingGrid()).thenReturn(craftingGrid);
        Mockito.when(offhand.getContents()).thenReturn(Map.of());
        Mockito.when(cursor.getContents()).thenReturn(Map.of());
        Mockito.when(craftingGrid.getContents()).thenReturn(Map.of());

        assertNotEquals(nestedBundle.getBundleId(), looseBundle.getBundleId());

        Inventory resolved = NetworkMapping.getInventory(player, ContainerSlotType.DYNAMIC_CONTAINER, looseBundle.getBundleId());
        BundleInventory bundleInventory = assertInstanceOf(BundleInventory.class, resolved);

        assertSame(looseBundle.getInventory(), bundleInventory);
    }
}
