package cn.nukkit.blockentity;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/** Real native inventories and hopper transfers; only server/world infrastructure is mocked. */
class NativeBrewingHopperExtractionTest {
    private static Server server;
    private static PluginManager plugins;

    @BeforeAll
    static void initializeNativeRegistries() {
        MockServer.init();
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

    static Stream<Arguments> brewingInputs() {
        return Stream.of(Arguments.of(0, Item.NETHER_WART), Arguments.of(4, Item.BLAZE_POWDER));
    }

    @ParameterizedTest
    @MethodSource("brewingInputs")
    void inputCannotBeExtractedFromStandWithoutBottles(int slot, int itemId) {
        Context context = context();
        context.stand.getInventory().setItem(slot, named(itemId, 12, "input"));
        List<Item> before = snapshot(context.stand.getInventory());

        assertFalse(context.hopper.pullItems(), "ingredient and fuel are not output slots");

        unchanged(context.stand.getInventory(), before);
        assertTrue(context.hopper.getInventory().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("brewingInputs")
    void activeBrewInputCannotEscapeWhenOnlyThatInputFitsHopper(int slot, int itemId) {
        Context context = context();
        context.stand.getInventory().setIngredient(named(Item.NETHER_WART, 12, "ingredient"));
        context.stand.getInventory().setFuel(named(Item.BLAZE_POWDER, 12, "fuel"));
        context.stand.getInventory().setItem(1, named(Item.POTION, 1, "bottle"));
        Item matchingInput = context.stand.getInventory().getItem(slot);
        matchingInput.setCount(63);
        context.hopper.getInventory().setItem(0, matchingInput);
        for (int index = 1; index < context.hopper.getInventory().getSize(); index++) {
            context.hopper.getInventory().setItem(index, Item.get(Item.STONE, 0, 64));
        }
        context.stand.brewTime = BlockEntityBrewingStand.MAX_BREW_TIME / 2;
        List<Item> source = snapshot(context.stand.getInventory());
        List<Item> target = snapshot(context.hopper.getInventory());

        assertFalse(context.hopper.pullItems(), "input must stay even when bottles cannot fit");

        unchanged(context.stand.getInventory(), source);
        unchanged(context.hopper.getInventory(), target);
        assertEquals(BlockEntityBrewingStand.MAX_BREW_TIME / 2, context.stand.brewTime);
    }

    static Stream<Arguments> bottleSlots() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(slot -> Stream.of(
                Item.POTION, Item.SPLASH_POTION, Item.LINGERING_POTION, Item.GLASS_BOTTLE)
                .map(item -> Arguments.of(slot, item)));
    }

    @ParameterizedTest
    @MethodSource("bottleSlots")
    void eachBottleSlotRemainsExtractableWithExactItemAndInputs(int slot, int itemId) {
        Context context = context();
        context.stand.getInventory().setIngredient(named(Item.NETHER_WART, 12, "ingredient"));
        context.stand.getInventory().setFuel(named(Item.BLAZE_POWDER, 12, "fuel"));
        Item bottle = named(itemId, 1, "bottle");
        context.stand.getInventory().setItem(slot, bottle);
        List<Item> before = snapshot(context.stand.getInventory());

        assertTrue(context.hopper.pullItems());

        assertTrue(context.stand.getInventory().getItem(slot).isNull());
        assertTrue(bottle.equalsExact(context.hopper.getInventory().getItem(0)));
        for (int index = 0; index < before.size(); index++) {
            if (index != slot) assertTrue(before.get(index).equalsExact(context.stand.getInventory().getItem(index)));
        }
        assertEquals(1, count(context.hopper.getInventory()));
        assertEquals(25, count(context.hopper.getInventory()) + count(context.stand.getInventory()));
    }

    @Test
    void brewingTimerDoesNotAddAnOutputLock() {
        Context context = context();
        Item bottle = named(Item.POTION, 1, "active-bottle");
        context.stand.getInventory().setItem(2, bottle);
        context.stand.brewTime = BlockEntityBrewingStand.MAX_BREW_TIME / 2;

        assertTrue(context.hopper.pullItems());

        assertTrue(bottle.equalsExact(context.hopper.getInventory().getItem(0)));
        assertTrue(context.stand.getInventory().getItem(2).isNull());
    }

    @Test
    void cancelledExtractionKeepsBothInventoriesExact() {
        Context context = context();
        context.stand.getInventory().setItem(2, named(Item.POTION, 1, "cancelled"));
        List<Item> source = snapshot(context.stand.getInventory());
        List<Item> target = snapshot(context.hopper.getInventory());
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof InventoryMoveItemEvent event) event.setCancelled(true);
            return null;
        }).when(plugins).callEvent(any());

        assertFalse(context.hopper.pullItems());

        unchanged(context.stand.getInventory(), source);
        unchanged(context.hopper.getInventory(), target);
    }

    @Test
    void fullHopperLeavesEverySourceSlotExact() {
        Context context = context();
        context.stand.getInventory().setIngredient(named(Item.NETHER_WART, 12, "ingredient"));
        context.stand.getInventory().setItem(3, named(Item.POTION, 1, "bottle"));
        context.stand.getInventory().setFuel(named(Item.BLAZE_POWDER, 12, "fuel"));
        for (int slot = 0; slot < context.hopper.getInventory().getSize(); slot++) {
            context.hopper.getInventory().setItem(slot, Item.get(Item.STONE, 0, 64));
        }
        List<Item> source = snapshot(context.stand.getInventory());
        List<Item> target = snapshot(context.hopper.getInventory());

        assertFalse(context.hopper.pullItems());

        unchanged(context.stand.getInventory(), source);
        unchanged(context.hopper.getInventory(), target);
    }

    private static Context context() {
        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        when(level.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        Vector3 position = new Vector3(8, 64, 8);
        Vector3 above = position.up();
        BlockEntityHopper hopper = new BlockEntityHopper(chunk, tag(BlockEntity.HOPPER, position));
        BlockEntityBrewingStand stand = new BlockEntityBrewingStand(chunk, tag(BlockEntity.BREWING_STAND, above));
        when(level.getBlock(any(Vector3.class))).thenReturn(Block.get(Block.BREWING_STAND_BLOCK));
        when(level.getBlockEntity(any(Vector3.class))).thenAnswer(invocation -> {
            Vector3 requested = invocation.getArgument(0);
            return requested.equals(position) ? hopper : requested.equals(above) ? stand : null;
        });
        return new Context(hopper, stand);
    }

    private static CompoundTag tag(String type, Vector3 position) {
        return new CompoundTag().putString("id", type)
                .putInt("x", position.getFloorX()).putInt("y", position.getFloorY())
                .putInt("z", position.getFloorZ()).putList(new ListTag<CompoundTag>("Items"))
                .putShort("CookTime", BlockEntityBrewingStand.MAX_BREW_TIME)
                .putShort("FuelAmount", 0).putShort("FuelTotal", 0);
    }

    private static Item named(int itemId, int count, String name) {
        Item item = Item.get(itemId, 0, count);
        item.setCustomName(name);
        return item;
    }

    private static List<Item> snapshot(Inventory inventory) {
        return IntStream.range(0, inventory.getSize()).mapToObj(inventory::getItem).toList();
    }

    private static void unchanged(Inventory inventory, List<Item> before) {
        for (int slot = 0; slot < before.size(); slot++) {
            assertTrue(before.get(slot).equalsExact(inventory.getItem(slot)), "slot " + slot);
        }
    }

    private static int count(Inventory inventory) {
        return inventory.getContents().values().stream().mapToInt(Item::getCount).sum();
    }

    private record Context(BlockEntityHopper hopper, BlockEntityBrewingStand stand) { }
}
