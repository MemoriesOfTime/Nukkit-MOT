package cn.nukkit.blockentity;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.Event;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.ChestInventory;
import cn.nukkit.inventory.FurnaceInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.StringItemUnknown;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link BlockEntityHopper}.
 * <p>
 * Tests call real production code paths (pushItems, pullItems, onUpdate)
 * with mocked Level/Chunk/Server infrastructure.
 */
@ExtendWith(MockitoExtension.class)
public class BlockEntityHopperTest {

    private static Server serverMock;
    private static PluginManager pluginManagerMock;

    @BeforeAll
    static void init() throws Exception {
        Block.init();

        serverMock = mock(Server.class);
        pluginManagerMock = mock(PluginManager.class);
        lenient().when(serverMock.getPluginManager()).thenReturn(pluginManagerMock);

        // Set Server.instance for Event.call()
        Field instanceField = Server.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, serverMock);
    }

    @AfterAll
    static void cleanup() throws Exception {
        Field instanceField = Server.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @BeforeEach
    void resetMocks() {
        reset(pluginManagerMock);
    }

    // ===== Helper Methods =====

    /**
     * Creates a BlockEntityHopper with a mocked environment.
     * The hopper is at (0, 64, 0) with transferCooldown ready for transfer (set to 0).
     */
    private static HopperTestContext createHopper() {
        Level level = mock(Level.class);
        lenient().when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        lenient().when(level.getServer()).thenReturn(serverMock);

        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().when(provider.getLevel()).thenReturn(level);

        CompoundTag nbt = new CompoundTag()
                .putString("id", BlockEntity.HOPPER)
                .putInt("x", 0)
                .putInt("y", 64)
                .putInt("z", 0)
                .putList(new ListTag<CompoundTag>("Items"));

        BlockEntityHopper hopper = new BlockEntityHopper(chunk, nbt);
        // Ready for immediate transfer
        hopper.transferCooldown = 0;

        return new HopperTestContext(hopper, level, chunk);
    }

    /**
     * Creates a mock BlockEntityFurnace with a real FurnaceInventory.
     */
    private static BlockEntityFurnace createMockFurnace() {
        BlockEntityFurnace furnace = mock(BlockEntityFurnace.class);
        FurnaceInventory furnaceInv = new FurnaceInventory(furnace);
        when(furnace.getInventory()).thenReturn(furnaceInv);
        return furnace;
    }

    /**
     * Creates a mock BlockEntityChest with a real ChestInventory.
     */
    private static BlockEntityChest createMockChest() {
        BlockEntityChest chest = mock(BlockEntityChest.class);
        ChestInventory chestInv = new ChestInventory(chest);
        when(chest.getInventory()).thenReturn(chestInv);
        return chest;
    }

    /**
     * Sets up the level mock so that pushItems() sees the target block entity
     * at the expected facing direction.
     *
     * @param ctx     hopper test context
     * @param facing  block data value (0=down, 2=north, 3=south, 4=west, 5=east)
     * @param target  the target block entity (furnace, chest, etc.), or null
     * @param block   the block at the target position
     */
    private static void setupPushTarget(HopperTestContext ctx, int facing, BlockEntity target, Block block) {
        when(ctx.level.getBlockDataAt(0, 64, 0)).thenReturn(facing);
        lenient().when(ctx.level.getBlock(any(Vector3.class))).thenReturn(block != null ? block : Block.get(Block.AIR));
        lenient().when(ctx.level.getBlockEntity(any(Vector3.class))).thenReturn(target);
    }

    private record HopperTestContext(BlockEntityHopper hopper, Level level, FullChunk chunk) {
    }

    // ===== A. PushToFurnace — pushItems() targeting a furnace =====

    @Nested
    @DisplayName("A. PushToFurnace")
    class PushToFurnace {

        @Test
        @DisplayName("A1: facing down -> empty smelting slot")
        void pushToEmptySmeltingSlot() {
            HopperTestContext ctx = createHopper();
            BlockEntityFurnace furnace = createMockFurnace();
            setupPushTarget(ctx, 0, furnace, Block.get(Block.FURNACE));

            ctx.hopper.getInventory().setItem(0, Item.get(ItemID.IRON_INGOT, 0, 5));

            boolean result = ctx.hopper.pushItems();

            assertTrue(result);
            assertEquals(4, ctx.hopper.getInventory().getItem(0).getCount());
            Item smelting = furnace.getInventory().getSmelting();
            assertEquals(ItemID.IRON_INGOT, smelting.getId());
            assertEquals(1, smelting.getCount());
        }

        @Test
        @DisplayName("A2: facing down -> stack with same item in smelting slot")
        void pushToSmeltingSlotWithSameItem() {
            HopperTestContext ctx = createHopper();
            BlockEntityFurnace furnace = createMockFurnace();
            setupPushTarget(ctx, 0, furnace, Block.get(Block.FURNACE));

            ctx.hopper.getInventory().setItem(0, Item.get(ItemID.IRON_INGOT, 0, 5));
            furnace.getInventory().setSmelting(Item.get(ItemID.IRON_INGOT, 0, 10));

            boolean result = ctx.hopper.pushItems();

            assertTrue(result);
            assertEquals(4, ctx.hopper.getInventory().getItem(0).getCount());
            assertEquals(11, furnace.getInventory().getSmelting().getCount());
        }

        @Test
        @DisplayName("A3: facing down -> reject different item in smelting slot")
        void pushToSmeltingSlotWithDifferentItem() {
            HopperTestContext ctx = createHopper();
            BlockEntityFurnace furnace = createMockFurnace();
            setupPushTarget(ctx, 0, furnace, Block.get(Block.FURNACE));

            ctx.hopper.getInventory().setItem(0, Item.get(ItemID.IRON_INGOT, 0, 5));
            furnace.getInventory().setSmelting(Item.get(ItemID.GOLD_INGOT, 0, 10));

            boolean result = ctx.hopper.pushItems();

            assertFalse(result);
            assertEquals(5, ctx.hopper.getInventory().getItem(0).getCount());
            assertEquals(10, furnace.getInventory().getSmelting().getCount());
        }

        @Test
        @DisplayName("A4: facing down -> reject different StringItem merge (bugfix verification)")
        void pushDifferentStringItemToSmeltingSlotRejected() {
            HopperTestContext ctx = createHopper();
            BlockEntityFurnace furnace = createMockFurnace();
            setupPushTarget(ctx, 0, furnace, Block.get(Block.FURNACE));

            ctx.hopper.getInventory().setItem(0, new StringItemUnknown("minecraft:item_b"));
            Item smelting = new StringItemUnknown("minecraft:item_a");
            smelting.setCount(10);
            furnace.getInventory().setSmelting(smelting);

            boolean result = ctx.hopper.pushItems();

            assertFalse(result);
            assertEquals(1, ctx.hopper.getInventory().getItem(0).getCount());
            assertEquals(10, furnace.getInventory().getSmelting().getCount());
        }

        @Test
        @DisplayName("A5: facing down -> allow same StringItem merge (bugfix verification)")
        void pushSameStringItemToSmeltingSlotAccepted() {
            HopperTestContext ctx = createHopper();
            BlockEntityFurnace furnace = createMockFurnace();
            setupPushTarget(ctx, 0, furnace, Block.get(Block.FURNACE));

            Item hopperItem = new StringItemUnknown("minecraft:test_item");
            hopperItem.setCount(5);
            ctx.hopper.getInventory().setItem(0, hopperItem);

            Item smeltingItem = new StringItemUnknown("minecraft:test_item");
            smeltingItem.setCount(10);
            furnace.getInventory().setSmelting(smeltingItem);

            boolean result = ctx.hopper.pushItems();

            assertTrue(result);
            assertEquals(4, ctx.hopper.getInventory().getItem(0).getCount());
            assertEquals(11, furnace.getInventory().getSmelting().getCount());
        }

        @Test
        @DisplayName("A6: facing side -> fuel into empty fuel slot")
        void pushFuelToEmptyFuelSlot() {
            HopperTestContext ctx = createHopper();
            BlockEntityFurnace furnace = createMockFurnace();
            setupPushTarget(ctx, 2, furnace, Block.get(Block.FURNACE)); // 2 = NORTH

            ctx.hopper.getInventory().setItem(0, Item.get(ItemID.COAL, 0, 5));

            boolean result = ctx.hopper.pushItems();

            assertTrue(result);
            assertEquals(4, ctx.hopper.getInventory().getItem(0).getCount());
            Item fuel = furnace.getInventory().getFuel();
            assertEquals(ItemID.COAL, fuel.getId());
            assertEquals(1, fuel.getCount());
        }

        @Test
        @DisplayName("A7: facing side -> stack same fuel")
        void pushFuelToFuelSlotWithSameItem() {
            HopperTestContext ctx = createHopper();
            BlockEntityFurnace furnace = createMockFurnace();
            setupPushTarget(ctx, 2, furnace, Block.get(Block.FURNACE));

            ctx.hopper.getInventory().setItem(0, Item.get(ItemID.COAL, 0, 5));
            furnace.getInventory().setFuel(Item.get(ItemID.COAL, 0, 10));

            boolean result = ctx.hopper.pushItems();

            assertTrue(result);
            assertEquals(4, ctx.hopper.getInventory().getItem(0).getCount());
            assertEquals(11, furnace.getInventory().getFuel().getCount());
        }

        @Test
        @DisplayName("A8: facing side -> reject different fuel item")
        void pushDifferentStringItemFuelRejected() {
            HopperTestContext ctx = createHopper();
            BlockEntityFurnace furnace = createMockFurnace();
            setupPushTarget(ctx, 2, furnace, Block.get(Block.FURNACE));

            // Create a StringItem that hypothetically has fuel time
            // Since StringItemUnknown won't have fuel time, we use a real fuel item
            // to test the fuel slot merge logic, then separately test StringItem rejection
            Item hopperItem = Item.get(ItemID.COAL, 0, 5);
            ctx.hopper.getInventory().setItem(0, hopperItem);
            // Different fuel in the slot
            furnace.getInventory().setFuel(Item.get(ItemID.STICK, 0, 10));

            boolean result = ctx.hopper.pushItems();

            // Coal and Stick are different items, so merge should fail.
            // But pushedItem stays false for slot 0, then tries next slots (all empty).
            assertFalse(result);
            assertEquals(5, ctx.hopper.getInventory().getItem(0).getCount());
            assertEquals(10, furnace.getInventory().getFuel().getCount());
        }

        @Test
        @DisplayName("A9: facing side -> non-fuel item rejected from fuel slot")
        void pushNonFuelToFuelSlotRejected() {
            HopperTestContext ctx = createHopper();
            BlockEntityFurnace furnace = createMockFurnace();
            setupPushTarget(ctx, 2, furnace, Block.get(Block.FURNACE));

            ctx.hopper.getInventory().setItem(0, Item.get(ItemID.DIAMOND, 0, 5));

            boolean result = ctx.hopper.pushItems();

            assertFalse(result);
            assertEquals(5, ctx.hopper.getInventory().getItem(0).getCount());
        }

        @Test
        @DisplayName("A10: facing side -> empty bucket is not fuel")
        void pushEmptyBucketToFuelSlotRejected() {
            HopperTestContext ctx = createHopper();
            BlockEntityFurnace furnace = createMockFurnace();
            setupPushTarget(ctx, 2, furnace, Block.get(Block.FURNACE));

            ctx.hopper.getInventory().setItem(0, Item.get(ItemID.BUCKET, 0, 1));

            boolean result = ctx.hopper.pushItems();

            assertFalse(result);
            assertEquals(1, ctx.hopper.getInventory().getItem(0).getCount());
        }
    }

    // ===== B. PushToContainer — pushItems() targeting a generic container =====

    @Nested
    @DisplayName("B. PushToContainer")
    class PushToContainer {

        @Test
        @DisplayName("B1: push to container with space")
        void pushToContainerWithSpace() {
            HopperTestContext ctx = createHopper();
            BlockEntityChest chest = createMockChest();
            setupPushTarget(ctx, 0, chest, Block.get(Block.CHEST));

            ctx.hopper.getInventory().setItem(0, Item.get(ItemID.DIAMOND, 0, 5));

            boolean result = ctx.hopper.pushItems();

            assertTrue(result);
            assertEquals(4, ctx.hopper.getInventory().getItem(0).getCount());
            Item chestItem = chest.getInventory().getItem(0);
            assertEquals(ItemID.DIAMOND, chestItem.getId());
            assertEquals(1, chestItem.getCount());
        }

        @Test
        @DisplayName("B2: push to full container rejected")
        void pushToFullContainer() {
            HopperTestContext ctx = createHopper();
            BlockEntityChest chest = createMockChest();
            setupPushTarget(ctx, 0, chest, Block.get(Block.CHEST));

            // Fill every slot of the chest
            for (int i = 0; i < chest.getInventory().getSize(); i++) {
                Item item = Item.get(ItemID.IRON_INGOT, 0, 64);
                chest.getInventory().setItem(i, item);
            }

            ctx.hopper.getInventory().setItem(0, Item.get(ItemID.DIAMOND, 0, 5));

            boolean result = ctx.hopper.pushItems();

            assertFalse(result);
            assertEquals(5, ctx.hopper.getInventory().getItem(0).getCount());
        }
    }

    // ===== C. PullItems — pullItems() pulling items into hopper =====

    @Nested
    @DisplayName("C. PullItems")
    class PullItems {

        @Test
        @DisplayName("C1: pull from furnace result slot")
        void pullFromFurnaceResultSlot() {
            HopperTestContext ctx = createHopper();
            BlockEntityFurnace furnace = createMockFurnace();

            furnace.getInventory().setResult(Item.get(ItemID.IRON_INGOT, 0, 5));

            boolean result = ctx.hopper.pullItems(furnace, null);

            assertTrue(result);
            Item hopperItem = ctx.hopper.getInventory().getItem(0);
            assertEquals(ItemID.IRON_INGOT, hopperItem.getId());
            assertEquals(1, hopperItem.getCount());
            assertEquals(4, furnace.getInventory().getResult().getCount());
        }

        @Test
        @DisplayName("C2: pull from generic container")
        void pullFromContainer() {
            HopperTestContext ctx = createHopper();
            BlockEntityChest chest = createMockChest();

            chest.getInventory().setItem(0, Item.get(ItemID.DIAMOND, 0, 3));

            boolean result = ctx.hopper.pullItems(chest, null);

            assertTrue(result);
            Item hopperItem = ctx.hopper.getInventory().getItem(0);
            assertEquals(ItemID.DIAMOND, hopperItem.getId());
            assertEquals(1, hopperItem.getCount());
            assertEquals(2, chest.getInventory().getItem(0).getCount());
        }

        @Test
        @DisplayName("C3: pull rejected when hopper is full")
        void pullWhenHopperIsFull() {
            HopperTestContext ctx = createHopper();
            BlockEntityChest chest = createMockChest();

            // Fill hopper with max stacks
            for (int i = 0; i < ctx.hopper.getInventory().getSize(); i++) {
                ctx.hopper.getInventory().setItem(i, Item.get(ItemID.IRON_INGOT, 0, 64));
            }
            chest.getInventory().setItem(0, Item.get(ItemID.DIAMOND, 0, 3));

            boolean result = ctx.hopper.pullItems(chest, null);

            assertFalse(result);
            assertEquals(3, chest.getInventory().getItem(0).getCount());
        }
    }

    // ===== D. EventCancellation — InventoryMoveItemEvent cancellation =====

    @Nested
    @DisplayName("D. EventCancellation")
    class EventCancellation {

        @Test
        @DisplayName("D1: pushItems cancelled by InventoryMoveItemEvent")
        void pushItemsEventCancelled() {
            HopperTestContext ctx = createHopper();
            BlockEntityChest chest = createMockChest();
            setupPushTarget(ctx, 0, chest, Block.get(Block.CHEST));

            ctx.hopper.getInventory().setItem(0, Item.get(ItemID.DIAMOND, 0, 5));

            // Cancel all InventoryMoveItemEvents
            doAnswer(invocation -> {
                Event event = invocation.getArgument(0);
                if (event instanceof InventoryMoveItemEvent moveEvent) {
                    moveEvent.setCancelled(true);
                }
                return null;
            }).when(pluginManagerMock).callEvent(any(Event.class));

            boolean result = ctx.hopper.pushItems();

            assertFalse(result);
            // Items should not have moved
            assertEquals(5, ctx.hopper.getInventory().getItem(0).getCount());
            assertTrue(chest.getInventory().getItem(0).isNull());
        }

        @Test
        @DisplayName("D2: pullItems cancelled by InventoryMoveItemEvent")
        void pullItemsEventCancelled() {
            HopperTestContext ctx = createHopper();
            BlockEntityChest chest = createMockChest();

            chest.getInventory().setItem(0, Item.get(ItemID.DIAMOND, 0, 3));

            // Cancel all InventoryMoveItemEvents
            doAnswer(invocation -> {
                Event event = invocation.getArgument(0);
                if (event instanceof InventoryMoveItemEvent moveEvent) {
                    moveEvent.setCancelled(true);
                }
                return null;
            }).when(pluginManagerMock).callEvent(any(Event.class));

            boolean result = ctx.hopper.pullItems(chest, null);

            assertFalse(result);
            // Items should not have moved
            assertTrue(ctx.hopper.getInventory().getItem(0).isNull());
            assertEquals(3, chest.getInventory().getItem(0).getCount());
        }
    }

    // ===== E. OnUpdateGates — onUpdate() gate conditions =====

    @Nested
    @DisplayName("E. OnUpdateGates")
    class OnUpdateGates {

        @Test
        @DisplayName("E1: closed=true returns false")
        void closedReturnsFalse() {
            HopperTestContext ctx = createHopper();
            ctx.hopper.closed = true;

            boolean result = ctx.hopper.onUpdate();

            assertFalse(result);
        }

        @Test
        @DisplayName("E2: redstone powered sleeps (returns false)")
        void poweredSkipsTransfer() {
            HopperTestContext ctx = createHopper();
            ctx.hopper.transferCooldown = 1; // Will become 0 after decrement

            // getBlock() calls level.getBlock(this)
            when(ctx.level.getBlock(any(Vector3.class))).thenReturn(Block.get(Block.HOPPER_BLOCK));
            when(ctx.level.isBlockPowered(any(Vector3.class))).thenReturn(true);

            ctx.hopper.getInventory().setItem(0, Item.get(ItemID.DIAMOND, 0, 5));

            boolean result = ctx.hopper.onUpdate();

            assertFalse(result); // Returns false to sleep when powered
            assertEquals(5, ctx.hopper.getInventory().getItem(0).getCount());
        }

        @Test
        @DisplayName("E3: cooldown decrements without transfer")
        void cooldownDecrementsWithoutTransfer() {
            HopperTestContext ctx = createHopper();
            ctx.hopper.transferCooldown = 5;

            ctx.hopper.getInventory().setItem(0, Item.get(ItemID.DIAMOND, 0, 5));

            boolean result = ctx.hopper.onUpdate();

            assertTrue(result);
            // Cooldown should have decremented: 5 - 1 = 4
            assertEquals(4, ctx.hopper.transferCooldown);
            // Items should not have been transferred
            assertEquals(5, ctx.hopper.getInventory().getItem(0).getCount());
        }

        @Test
        @DisplayName("E4: idle hopper with container above sleeps (returns false)")
        void idleHopperWithContainerAboveSleeps() {
            HopperTestContext ctx = createHopper();
            ctx.hopper.transferCooldown = 0;

            // Empty hopper, empty chest above
            BlockEntityChest chest = createMockChest();
            when(ctx.level.getBlockEntity(any(Vector3.class))).thenReturn(chest);

            boolean result = ctx.hopper.onUpdate();

            // No items to transfer, container above → should sleep
            assertFalse(result);
        }

        @Test
        @DisplayName("E5: idle hopper without container above polls for ground items")
        void idleHopperWithoutContainerAbovePolls() {
            HopperTestContext ctx = createHopper();
            ctx.hopper.transferCooldown = 0;

            // Not powered
            lenient().when(ctx.level.getBlock(any(Vector3.class))).thenReturn(Block.get(Block.HOPPER_BLOCK));
            when(ctx.level.isBlockPowered(any())).thenReturn(false);
            // No block entity above, no composter, no entities
            when(ctx.level.getBlockEntity(any(Vector3.class))).thenReturn(null);
            lenient().when(ctx.level.getBlock(any(FullChunk.class), anyInt(), anyInt(), anyInt(), anyBoolean()))
                    .thenReturn(Block.get(Block.AIR));
            when(ctx.level.getCollidingEntities(any())).thenReturn(new cn.nukkit.entity.Entity[0]);

            boolean result = ctx.hopper.onUpdate();

            // No container above, not full → should stay awake polling for ground items
            assertTrue(result);
            assertEquals(8, ctx.hopper.transferCooldown);
        }

        @Test
        @DisplayName("E6: full hopper without container above sleeps")
        void fullHopperWithoutContainerAboveSleeps() {
            HopperTestContext ctx = createHopper();
            ctx.hopper.transferCooldown = 0;

            // Fill hopper
            for (int i = 0; i < ctx.hopper.getInventory().getSize(); i++) {
                ctx.hopper.getInventory().setItem(i, Item.get(ItemID.IRON_INGOT, 0, 64));
            }

            // Not powered
            lenient().when(ctx.level.getBlock(any(Vector3.class))).thenReturn(Block.get(Block.HOPPER_BLOCK));
            when(ctx.level.isBlockPowered(any())).thenReturn(false);
            // No block entity above, no composter
            when(ctx.level.getBlockEntity(any(Vector3.class))).thenReturn(null);
            lenient().when(ctx.level.getBlock(any(FullChunk.class), anyInt(), anyInt(), anyInt(), anyBoolean()))
                    .thenReturn(Block.get(Block.AIR));
            // No push target
            when(ctx.level.getBlockDataAt(anyInt(), anyInt(), anyInt())).thenReturn(0);

            boolean result = ctx.hopper.onUpdate();

            // Full, no container above, can't push → should sleep
            assertFalse(result);
        }
    }

    // ===== F. Boundary — Edge cases =====

    @Nested
    @DisplayName("F. Boundary")
    class Boundary {

        @Test
        @DisplayName("F1: empty hopper pushItems returns false")
        void emptyHopperPushReturnsFalse() {
            HopperTestContext ctx = createHopper();
            // Hopper is empty by default

            boolean result = ctx.hopper.pushItems();

            assertFalse(result);
        }

        @Test
        @DisplayName("F2: facing down to another hopper returns false")
        void pushDownToHopperReturnsFalse() {
            HopperTestContext ctx = createHopper();

            // Create a target hopper
            BlockEntityHopper targetHopper = createHopper().hopper;
            setupPushTarget(ctx, 0, targetHopper, Block.get(Block.HOPPER_BLOCK));

            ctx.hopper.getInventory().setItem(0, Item.get(ItemID.DIAMOND, 0, 5));

            boolean result = ctx.hopper.pushItems();

            // blockData == 0 && target is hopper → returns false
            assertFalse(result);
            assertEquals(5, ctx.hopper.getInventory().getItem(0).getCount());
        }
    }

    // ===== G. ItemBasics — Item API basic verification =====

    @Nested
    @DisplayName("G. ItemBasics")
    class ItemBasics {

        @Test
        @DisplayName("StringItems share the same numeric ID")
        void stringIdItemsShareSameNumericId() {
            Item a = new StringItemUnknown("minecraft:item_a");
            Item b = new StringItemUnknown("minecraft:item_b");

            assertEquals(ItemID.STRING_IDENTIFIED_ITEM, a.getId());
            assertEquals(ItemID.STRING_IDENTIFIED_ITEM, b.getId());
            assertEquals(a.getId(), b.getId());
        }

        @Test
        @DisplayName("numeric ID+damage comparison cannot distinguish different StringItems")
        void directIdAndDamageComparisonCannotDistinguishStringItems() {
            Item a = new StringItemUnknown("minecraft:item_a");
            Item b = new StringItemUnknown("minecraft:item_b");

            boolean oldBuggyResult = (a.getId() == b.getId()) && (a.getDamage() == b.getDamage());
            assertTrue(oldBuggyResult);
        }

        @Test
        @DisplayName("equals(checkDamage=true) distinguishes different namespaceIds")
        void equalsWithDamageCheckDistinguishesDifferentStringItems() {
            Item a = new StringItemUnknown("minecraft:item_a");
            Item b = new StringItemUnknown("minecraft:item_b");

            assertFalse(a.equals(b, true, false));
        }

        @Test
        @DisplayName("equals(checkDamage=true) matches same namespaceId")
        void equalsWithDamageCheckMatchesSameStringItems() {
            Item a = new StringItemUnknown("minecraft:test_item");
            Item b = new StringItemUnknown("minecraft:test_item");

            assertTrue(a.equals(b, true, false));
        }

        @Test
        @DisplayName("known fuel item returns non-null fuel time")
        void knownFuelItemReturnsNonNullFuelTime() {
            Item coal = Item.get(ItemID.COAL);
            assertNotNull(coal.getFuelTime());
            assertEquals((short) 1600, coal.getFuelTime());
        }

        @Test
        @DisplayName("non-fuel item returns null fuel time")
        void nonFuelItemReturnsNullFuelTime() {
            Item diamond = Item.get(ItemID.DIAMOND);
            assertNull(diamond.getFuelTime());
        }

        @Test
        @DisplayName("empty bucket is not fuel")
        void emptyBucketNotFuel() {
            Item emptyBucket = Item.get(ItemID.BUCKET, 0);
            assertNull(emptyBucket.getFuelTime());
        }

        @Test
        @DisplayName("lava bucket is fuel")
        void lavaBucketIsFuel() {
            Item lavaBucket = Item.get(ItemID.BUCKET, 10);
            assertNotNull(lavaBucket.getFuelTime());
            assertEquals((short) 20000, lavaBucket.getFuelTime());
        }
    }
}
