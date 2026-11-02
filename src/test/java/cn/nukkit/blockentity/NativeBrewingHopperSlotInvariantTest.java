package cn.nukkit.blockentity;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.NativeContainerTransaction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemPotion;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import cn.nukkit.potion.Potion;
import java.util.stream.IntStream;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Native ordinary potion bottle-slot regression.
 * Official bottle/ingredient roles are independent of hopper direction. Refusal is
 * allowed by the original invariant; adjacent tests preserve available bottle slots
 * without prescribing sided routing, fuel, empty-bottle, or extraction rules.
 * Server/Level/Chunk infrastructure is mocked; items, holders and transfers are native.
 */
@org.junit.jupiter.api.parallel.Isolated("MockServer native registries")
public final class NativeBrewingHopperSlotInvariantTest {
    private static Server server;
    private static PluginManager plugins;

    @BeforeAll
    static void initializeNativeRegistries() {
        MockServer.init(); // Includes real item/runtime registries; palette download disabled.
        server = MockServer.get();
        plugins = server.getPluginManager();
    }

    @AfterEach
    void clearEventStub() {
        reset(plugins);
    }

    @AfterAll
    static void restoreServer() {
        MockServer.restore();
    }

    @Test
    void fixtureUsesRegisteredSingleStackWaterBottles() {
        Item bottle = waterBottle();
        assertInstanceOf(ItemPotion.class, bottle);
        assertEquals(1, bottle.getMaxStackSize());
        for (GameVersion version : List.of(GameVersion.V1_20_0, GameVersion.V1_26_50)) {
            assertNotNull(RuntimeItems.getMapping(version).toRuntime(Item.POTION, 0),
                    "water-bottle runtime mapping: " + version);
        }
    }

    @ParameterizedTest(name = "water bottle never occupies ingredient; hopper facing={0}")
    @ValueSource(ints = {0, 2, 3, 4, 5})
    void hopperCannotPlaceWaterBottleInIngredient(int facing) {
        Context context = context(facing);
        boolean moved = context.hopper.pushItems();
        assertConserved(context);
        System.out.println("feedback777 facing=" + facing + " moved=" + moved
                + " ingredient=" + context.stand.getInventory().getIngredient()
                + " fuel=" + context.stand.getInventory().getFuel());
        assertTrue(context.stand.getInventory().getIngredient().isNull(),
                "water bottle must not occupy ingredient slot 0");
        if (moved) {
            assertTrue(context.hopper.getInventory().isEmpty());
            assertEquals(1, count(context.stand.getInventory()));
        } else {
            assertEquals(1, count(context.hopper.getInventory()));
            assertTrue(context.stand.getInventory().isEmpty());
        }
    }

    @Test
    void cancelledNativeTransferKeepsBottleInSource() {
        Context context = context(5);
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof InventoryMoveItemEvent event) {
                event.setCancelled(true);
            }
            return null;
        }).when(plugins).callEvent(any());
        assertFalse(context.hopper.pushItems());
        assertConserved(context);
        assertTrue(context.stand.getInventory().isEmpty());
        assertEquals(1, count(context.hopper.getInventory()));
    }

    @ParameterizedTest
    @CsvSource({"1,false", "2,false", "3,false", "1,true", "2,true", "3,true"})
    void remainingBottleSlotAcceptsWaterWithoutTouchingIngredient(int freeSlot, boolean ingredientOccupied) {
        Context context = context(5);
        if (ingredientOccupied) context.stand.getInventory().setIngredient(Item.get(Item.NETHER_WART, 0, 12));
        for (int slot = 1; slot <= 3; slot++) {
            if (slot != freeSlot) assertTrue(context.stand.getInventory().setItem(slot, namedWater("existing-" + slot)));
        }
        Item offered = namedWater("incoming");
        assertTrue(context.hopper.getInventory().setItem(0, offered));
        List<Item> before = snapshot(context.stand.getInventory());
        assertTrue(context.hopper.pushItems(), "an available bottle slot must remain usable");
        assertTrue(context.hopper.getInventory().isEmpty());
        assertTrue(offered.equalsExact(context.stand.getInventory().getItem(freeSlot)), "exact water NBT/count");
        for (int slot = 0; slot < before.size(); slot++) {
            if (slot != freeSlot) assertTrue(before.get(slot).equalsExact(context.stand.getInventory().getItem(slot)),
                    "unrelated brewing slot " + slot);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fullBottleSlotsRefuseWaterEvenWithEmptyIngredientOrFuel(boolean ingredientOccupied) {
        Context context = context(5);
        if (ingredientOccupied) context.stand.getInventory().setIngredient(Item.get(Item.NETHER_WART, 0, 12));
        for (int slot = 1; slot <= 3; slot++) {
            assertTrue(context.stand.getInventory().setItem(slot, namedWater("existing-" + slot)));
        }
        assertTrue(context.hopper.getInventory().setItem(0, namedWater("incoming")));
        List<Item> source = snapshot(context.hopper.getInventory());
        List<Item> target = snapshot(context.stand.getInventory());
        assertFalse(context.hopper.pushItems(), "water must not spill into ingredient/fuel");
        unchanged(context.hopper.getInventory(), source);
        unchanged(context.stand.getInventory(), target);
    }

    @Test
    void threeDistinctWaterBottlesFillThreeSlotsAndFourthRemainsInHopper() {
        Context context = context(5);
        for (int slot = 0; slot < 4; slot++) {
            assertTrue(context.hopper.getInventory().setItem(slot, namedWater("water-" + slot)));
        }
        for (int slot = 0; slot < 3; slot++) assertTrue(context.hopper.pushItems());
        assertFalse(context.hopper.pushItems());
        assertTrue(context.stand.getInventory().getIngredient().isNull());
        assertTrue(context.stand.getInventory().getFuel().isNull());
        for (int slot = 1; slot <= 3; slot++) {
            assertTrue(namedWater("water-" + (slot - 1)).equalsExact(context.stand.getInventory().getItem(slot)));
        }
        assertTrue(namedWater("water-3").equalsExact(context.hopper.getInventory().getItem(3)));
        assertEquals(4, count(context.hopper.getInventory()) + count(context.stand.getInventory()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    void explicitNonBottleSlotRefusesWater(int slot) {
        Context context = context(5);
        List<Item> source = snapshot(context.hopper.getInventory());
        List<Item> target = snapshot(context.stand.getInventory());
        assertFalse(NativeContainerTransaction.move(context.hopper, context.hopper.getInventory(), 0,
                context.stand.getInventory(), slot, () -> true));
        unchanged(context.hopper.getInventory(), source);
        unchanged(context.stand.getInventory(), target);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void explicitBottleSlotStillAcceptsWater(int slot) {
        Context context = context(5);
        Item offered = namedWater("explicit");
        assertTrue(context.hopper.getInventory().setItem(0, offered));
        assertTrue(NativeContainerTransaction.move(context.hopper, context.hopper.getInventory(), 0,
                context.stand.getInventory(), slot, () -> true));
        assertTrue(offered.equalsExact(context.stand.getInventory().getItem(slot)));
        assertConserved(context);
        assertTrue(context.hopper.getInventory().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    void callbackCannotSubstituteWaterIntoNonBottleSlot(int slot) {
        Context context = context(5);
        assertTrue(context.hopper.getInventory().setItem(0, Item.get(Item.NETHER_WART, 0, 12)));
        List<Item> source = snapshot(context.hopper.getInventory());
        List<Item> target = snapshot(context.stand.getInventory());
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof InventoryMoveItemEvent event) event.setItem(namedWater("approved"));
            return null;
        }).when(plugins).callEvent(any());
        assertFalse(NativeContainerTransaction.move(context.hopper, context.hopper.getInventory(), 0,
                context.stand.getInventory(), slot, () -> true));
        unchanged(context.hopper.getInventory(), source);
        unchanged(context.stand.getInventory(), target);
    }

    @Test
    void oversizedApprovedWaterCannotOverflowBottleSlot() {
        Context context = context(5);
        List<Item> source = snapshot(context.hopper.getInventory());
        List<Item> target = snapshot(context.stand.getInventory());
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof InventoryMoveItemEvent event) event.getItem().setCount(2);
            return null;
        }).when(plugins).callEvent(any());
        assertFalse(context.hopper.pushItems());
        unchanged(context.hopper.getInventory(), source);
        unchanged(context.stand.getInventory(), target);
    }

    @Test
    void cancelledNamedWaterKeepsBothInventoriesExact() {
        Context context = context(5);
        assertTrue(context.hopper.getInventory().setItem(0, namedWater("cancelled")));
        context.stand.getInventory().setIngredient(Item.get(Item.NETHER_WART, 0, 12));
        assertTrue(context.stand.getInventory().setItem(1, namedWater("existing")));
        List<Item> source = snapshot(context.hopper.getInventory());
        List<Item> target = snapshot(context.stand.getInventory());
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof InventoryMoveItemEvent event) event.setCancelled(true);
            return null;
        }).when(plugins).callEvent(any());
        assertFalse(context.hopper.pushItems());
        unchanged(context.hopper.getInventory(), source);
        unchanged(context.stand.getInventory(), target);
    }

    @Test
    void ordinaryIngredientCanStillReachIngredientSlot() {
        Context context = context(5);
        Item ingredient = Item.get(Item.NETHER_WART, 0, 12);
        assertTrue(context.hopper.getInventory().setItem(0, ingredient));
        assertTrue(context.hopper.pushItems());
        assertEquals(11, context.hopper.getInventory().getItem(0).getCount());
        Item expected = ingredient.clone();
        expected.setCount(1);
        assertTrue(expected.equalsExact(context.stand.getInventory().getIngredient()));
        for (int slot = 1; slot <= 4; slot++) assertTrue(context.stand.getInventory().getItem(slot).isNull());
    }

    @Test
    void waterStillTransfersIntoOrdinaryChest() {
        Context context = context(5);
        BlockEntityChest chest = new BlockEntityChest(context.hopper.chunk,
                tag(BlockEntity.CHEST, new Vector3(12, 64, 8)));
        Item offered = namedWater("chest-control");
        assertTrue(context.hopper.getInventory().setItem(0, offered));
        assertTrue(NativeContainerTransaction.move(context.hopper, context.hopper.getInventory(), 0,
                chest.getInventory(), -1, () -> true));
        assertTrue(context.hopper.getInventory().isEmpty());
        assertTrue(offered.equalsExact(chest.getInventory().getItem(0)));
    }

    // Feedback777 also covers ordinary potions after the water-only 0619 subset.
    // Their slot role is independent of metadata; sided and extraction rules are unchanged.
    static IntStream ordinaryPotionTypes() {
        return IntStream.rangeClosed(Potion.WATER, Potion.INFESTED);
    }

    @ParameterizedTest
    @MethodSource("ordinaryPotionTypes")
    void everyOrdinaryPotionUsesBottleSlotsOnly(int metadata) {
        Context context = context(5);
        Item offered = namedPotion(metadata, "ordinary-" + metadata);
        assertTrue(context.hopper.getInventory().setItem(0, offered));
        assertTrue(context.hopper.pushItems());
        assertTrue(context.hopper.getInventory().isEmpty());
        assertTrue(offered.equalsExact(context.stand.getInventory().getItem(1)));
        for (int slot : new int[]{0, 2, 3, 4}) {
            assertTrue(context.stand.getInventory().getItem(slot).isNull(), "untouched slot " + slot);
        }
    }

    @ParameterizedTest
    @CsvSource({"4,1", "16,2", "21,3", "46,1"})
    void nonWaterPotionUsesRemainingBottleSlotWithExactNbt(int metadata, int freeSlot) {
        Context context = context(5);
        context.stand.getInventory().setIngredient(Item.get(Item.NETHER_WART, 0, 12));
        context.stand.getInventory().setFuel(Item.get(Item.BLAZE_POWDER, 0, 5));
        for (int slot = 1; slot <= 3; slot++) {
            if (slot != freeSlot) assertTrue(context.stand.getInventory().setItem(slot,
                    namedPotion(metadata, "existing-" + slot)));
        }
        Item offered = namedPotion(metadata, "incoming");
        assertTrue(context.hopper.getInventory().setItem(0, offered));
        List<Item> before = snapshot(context.stand.getInventory());
        assertTrue(context.hopper.pushItems());
        assertTrue(context.hopper.getInventory().isEmpty());
        assertTrue(offered.equalsExact(context.stand.getInventory().getItem(freeSlot)));
        for (int slot = 0; slot < before.size(); slot++) {
            if (slot != freeSlot) assertTrue(before.get(slot).equalsExact(context.stand.getInventory().getItem(slot)));
        }
    }

    @ParameterizedTest
    @MethodSource("ordinaryPotionTypes")
    void fullBottleSlotsRefuseEveryOrdinaryPotionWithoutSpill(int metadata) {
        Context context = context(5);
        for (int slot = 1; slot <= 3; slot++) {
            assertTrue(context.stand.getInventory().setItem(slot, namedPotion(metadata, "existing-" + slot)));
        }
        assertTrue(context.hopper.getInventory().setItem(0, namedPotion(metadata, "incoming")));
        List<Item> source = snapshot(context.hopper.getInventory());
        List<Item> target = snapshot(context.stand.getInventory());
        assertFalse(context.hopper.pushItems());
        unchanged(context.hopper.getInventory(), source);
        unchanged(context.stand.getInventory(), target);
    }

    @ParameterizedTest
    @CsvSource({"4,0", "4,4", "16,0", "16,4"})
    void explicitNonBottleSlotRefusesNonWaterPotion(int metadata, int slot) {
        Context context = context(5);
        assertTrue(context.hopper.getInventory().setItem(0, namedPotion(metadata, "explicit")));
        List<Item> source = snapshot(context.hopper.getInventory());
        List<Item> target = snapshot(context.stand.getInventory());
        assertFalse(NativeContainerTransaction.move(context.hopper, context.hopper.getInventory(), 0,
                context.stand.getInventory(), slot, () -> true));
        unchanged(context.hopper.getInventory(), source);
        unchanged(context.stand.getInventory(), target);
    }

    @ParameterizedTest
    @CsvSource({"4,0", "4,4", "16,0", "16,4"})
    void callbackCannotSubstituteNonWaterPotionIntoNonBottleSlot(int metadata, int slot) {
        Context context = context(5);
        assertTrue(context.hopper.getInventory().setItem(0, Item.get(Item.NETHER_WART, 0, 12)));
        List<Item> source = snapshot(context.hopper.getInventory());
        List<Item> target = snapshot(context.stand.getInventory());
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof InventoryMoveItemEvent event) {
                event.setItem(namedPotion(metadata, "approved"));
            }
            return null;
        }).when(plugins).callEvent(any());
        assertFalse(NativeContainerTransaction.move(context.hopper, context.hopper.getInventory(), 0,
                context.stand.getInventory(), slot, () -> true));
        unchanged(context.hopper.getInventory(), source);
        unchanged(context.stand.getInventory(), target);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 16})
    void cancelledNonWaterPotionKeepsBothInventoriesExact(int metadata) {
        Context context = context(5);
        assertTrue(context.hopper.getInventory().setItem(0, namedPotion(metadata, "cancelled")));
        List<Item> source = snapshot(context.hopper.getInventory());
        List<Item> target = snapshot(context.stand.getInventory());
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof InventoryMoveItemEvent event) event.setCancelled(true);
            return null;
        }).when(plugins).callEvent(any());
        assertFalse(context.hopper.pushItems());
        unchanged(context.hopper.getInventory(), source);
        unchanged(context.stand.getInventory(), target);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 16})
    void oversizedApprovedNonWaterPotionCannotOverflowBottleSlot(int metadata) {
        Context context = context(5);
        assertTrue(context.hopper.getInventory().setItem(0, namedPotion(metadata, "oversized")));
        List<Item> source = snapshot(context.hopper.getInventory());
        List<Item> target = snapshot(context.stand.getInventory());
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof InventoryMoveItemEvent event) event.getItem().setCount(2);
            return null;
        }).when(plugins).callEvent(any());
        assertFalse(context.hopper.pushItems());
        unchanged(context.hopper.getInventory(), source);
        unchanged(context.stand.getInventory(), target);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 16})
    void nonWaterPotionStillTransfersIntoOrdinaryChest(int metadata) {
        Context context = context(5);
        BlockEntityChest chest = new BlockEntityChest(context.hopper.chunk,
                tag(BlockEntity.CHEST, new Vector3(12, 64, 8)));
        Item offered = namedPotion(metadata, "chest-control");
        assertTrue(context.hopper.getInventory().setItem(0, offered));
        assertTrue(NativeContainerTransaction.move(context.hopper, context.hopper.getInventory(), 0,
                chest.getInventory(), -1, () -> true));
        assertTrue(context.hopper.getInventory().isEmpty());
        assertTrue(offered.equalsExact(chest.getInventory().getItem(0)));
    }

    private static Item namedPotion(int metadata, String name) {
        Item potion = Item.get(Item.POTION, metadata, 1);
        assertInstanceOf(ItemPotion.class, potion, "registered ordinary potion, not a generic Item");
        assertEquals(1, potion.getMaxStackSize());
        potion.setCustomName(name);
        return potion;
    }

    private static Item namedWater(String name) {
        Item water = waterBottle();
        water.setCustomName(name);
        return water;
    }

    private static List<Item> snapshot(Inventory inventory) {
        return java.util.stream.IntStream.range(0, inventory.getSize()).mapToObj(inventory::getItem).toList();
    }

    private static void unchanged(Inventory inventory, List<Item> expected) {
        assertEquals(expected.size(), inventory.getSize());
        for (int slot = 0; slot < expected.size(); slot++) {
            assertTrue(expected.get(slot).equalsExact(inventory.getItem(slot)), "exact unchanged slot " + slot);
        }
    }

    private static Item waterBottle() {
        Item item = Item.get(Item.POTION, 0, 1);
        assertInstanceOf(ItemPotion.class, item, "bare Item fallback cannot prove this regression");
        return item;
    }

    private static Context context(int facing) {
        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        when(level.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);

        Vector3 position = new Vector3(8, 64, 8);
        Vector3 destination = position.getSide(BlockFace.fromIndex(facing));
        BlockEntityHopper hopper = new BlockEntityHopper(chunk, tag(BlockEntity.HOPPER, position));
        BlockEntityBrewingStand stand = new BlockEntityBrewingStand(chunk,
                tag(BlockEntity.BREWING_STAND, destination));
        when(level.getBlockDataAt(8, 64, 8)).thenReturn(facing);
        when(level.getBlock(any(Vector3.class))).thenReturn(Block.get(Block.BREWING_STAND_BLOCK));
        when(level.getBlockEntity(any(Vector3.class))).thenAnswer(invocation -> {
            Vector3 requested = invocation.getArgument(0);
            return requested.equals(position) ? hopper : requested.equals(destination) ? stand : null;
        });
        assertTrue(hopper.getInventory().setItem(0, waterBottle()));
        return new Context(hopper, stand);
    }

    private static CompoundTag tag(String type, Vector3 position) {
        return new CompoundTag().putString("id", type)
                .putInt("x", position.getFloorX()).putInt("y", position.getFloorY())
                .putInt("z", position.getFloorZ()).putList(new ListTag<CompoundTag>("Items"))
                .putShort("CookTime", BlockEntityBrewingStand.MAX_BREW_TIME)
                .putShort("FuelAmount", 0).putShort("FuelTotal", 0);
    }

    private static void assertConserved(Context context) {
        assertEquals(1, count(context.hopper.getInventory()) + count(context.stand.getInventory()),
                "single original water bottle must remain with exactly one holder");
    }

    private static int count(Inventory inventory) {
        int count = 0;
        for (Item item : inventory.getContents().values()) {
            if (item.isNull()) continue;
            assertInstanceOf(ItemPotion.class, item);
            assertEquals(Item.POTION, item.getId());
            assertEquals(0, item.getDamage());
            assertEquals(1, item.getMaxStackSize());
            count += item.getCount();
        }
        return count;
    }

    private record Context(BlockEntityHopper hopper, BlockEntityBrewingStand stand) { }
}
