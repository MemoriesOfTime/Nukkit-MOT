package cn.nukkit.level.explosion;

import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.blockentity.BlockEntityFurnace;
import cn.nukkit.event.block.NativeExplosionBlockProcessor;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.inventory.NativeContainerTransaction;
import cn.nukkit.item.Item;
import cn.nukkit.level.Explosion;
import cn.nukkit.math.Vector3;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Isolated;
import static cn.nukkit.level.explosion.ActualContainerRetirementFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Prospective source-only regression. No execution result is claimed by this packet. */
@Isolated
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
final class NativeContainerRetirementTest {
    @org.junit.jupiter.api.AfterAll static void restoreFixtureServer() throws Exception { NativeExplosionFixture.restoreServer(); }
    private static final NativeExplosionBlockProcessor NATIVE = original -> NativeExplosionBlockProcessor.Result.NATIVE;

    @BeforeAll static void init() throws Exception { boot(); }

    @Test @Order(1) void ordinaryRawWriteControlUsesNativeAcceptedHook() throws Exception {
        World world = world(Block.PLANKS);
        AtomicInteger accepted = new AtomicInteger();
        assertTrue(world.setBlock(new Vector3(0, 64, 0), Block.get(Block.AIR), false, false, accepted::incrementAndGet));
        assertTrue(world.getBlock(0, 64, 0).isAir());
        assertEquals(1, accepted.get());
    }

    @Test @Order(2) void unchangedNativeWriteKeepsActualTileRegistration() throws Exception {
        World world = world(Block.CHEST);
        BlockEntityChest chest = new BlockEntityChest(world.chunk, tileTag(BlockEntity.CHEST, 0));
        AtomicInteger accepted = new AtomicInteger();
        assertFalse(world.setBlock(new Vector3(0, 64, 0), world.getBlock(0, 64, 0).clone(), false, false,
                accepted::incrementAndGet));
        assertEquals(0, accepted.get());
        assertSame(chest, world.chunk.getTile(0, 64, 0));
        assertSame(chest, world.getBlockEntity(new Vector3(0, 64, 0)));
        assertSame(chest, world.getBlockEntityById(chest.getId()));
        assertFalse(chest.closed);
    }

    @Test @Order(3) void lawfulFurnaceOnBreakControlPublishesRealXpOnce() throws Exception {
        World world = world(Block.FURNACE);
        BlockEntityFurnace furnace = lawfulFurnace(world);
        assertEquals(0, physicalXp(world));
        furnace.onBreak();
        assertEquals(2, physicalXp(world));
        assertEquals(3, physicalItems(world, Item.IRON_INGOT));
        assertEquals(0f, furnace.getExperience());
        furnace.onBreak();
        assertEquals(2, physicalXp(world));
        assertEquals(3, physicalItems(world, Item.IRON_INGOT));
        furnace.close();
        assertTrue(furnace.closed);
        assertNull(world.getBlockEntity(new Vector3(0, 64, 0)));
        assertNull(world.getBlockEntityById(furnace.getId()));
    }

    @Test @Order(4) void protectedSourceRefusalKeepsRealTileInventoryAndXp() throws Exception {
        World world = world(Block.FURNACE);
        BlockEntityFurnace furnace = lawfulFurnace(world);
        float originalXp = furnace.getExperience();
        var inventory = NativeContainerTransaction.snapshot(furnace.getInventory());
        assertNotNull(inventory);
        var refused = new NativeExplosionBlockProcessor() {
            @Override public Result process(Block original) { return Result.NATIVE; }
            @Override public boolean canRemove(List<Block> original) { return false; }
        };
        Block original = world.getBlock(0, 64, 0);
        assertFalse(Explosion.removeSource(original, Item.get(Item.DIAMOND_PICKAXE), true,
                Explosion.canonicalOwnLoot(original, Item.get(Item.DIAMOND_PICKAXE)), refused));
        assertSame(furnace, world.getBlockEntity(new Vector3(0, 64, 0)));
        assertSame(furnace, world.chunk.getTile(0, 64, 0));
        assertFalse(furnace.closed);
        assertEquals(originalXp, furnace.getExperience());
        assertTrue(inventory.matches());
        assertEquals(0, physicalXp(world));
        assertEquals(0, physicalItems(world, Item.IRON_INGOT));
    }

    @Test @Order(5) void ordinaryRawRemovalObservesNativeTileDetachBeforeAcceptedHook() throws Exception {
        World world = world(Block.CHEST);
        BlockEntityChest chest = new BlockEntityChest(world.chunk, tileTag(BlockEntity.CHEST, 0));
        assertTrue(chest.getRealInventory().setItem(0, Item.get(Item.DIAMOND, 0, 7)));
        AtomicInteger accepted = new AtomicInteger();
        observe(world, "before", chest);
        var preparation = NativeContainerTransaction.tryPrepare(chest.getRealInventory());
        assertNotNull(preparation);
        try (preparation; var claim = InventoryMutationGuard.tryClaim(List.of(chest.getRealInventory()))) {
            assertNotNull(claim);
            assertTrue(world.setBlock(new Vector3(0, 64, 0), Block.get(Block.AIR), false, false, () -> {
                accepted.incrementAndGet();
                observe(world, "accepted-hook", chest);
                assertNull(world.getBlockEntity(new Vector3(0, 64, 0)));
                assertNull(world.chunk.getTile(0, 64, 0));
                assertSame(chest, world.getBlockEntityById(chest.getId()));
                assertFalse(chest.closed);
                assertEquals(7, chest.getRealInventory().getItem(0).getCount());
            }));
        } finally { observe(world, "after-hook-or-error", chest); }
        assertEquals(1, accepted.get());
        assertTrue(world.getBlock(0, 64, 0).isAir());
        assertNull(world.getBlockEntity(new Vector3(0, 64, 0)));
        assertNull(world.chunk.getTile(0, 64, 0));
        assertSame(chest, world.getBlockEntityById(chest.getId()));
        assertFalse(chest.closed);
        assertEquals(7, chest.getRealInventory().getItem(0).getCount());
    }

    @Test @Order(6) void nativeAcceptedFurnaceRemovalPaysXpAndRetiresOnce() throws Exception {
        World world = world(Block.FURNACE);
        BlockEntityFurnace furnace = lawfulFurnace(world);
        Block original = world.getBlock(0, 64, 0);
        observe(world, "before-native", furnace);
        try {
            assertTrue(Explosion.removeSource(original, Item.get(Item.DIAMOND_PICKAXE), true,
                    Explosion.canonicalOwnLoot(original, Item.get(Item.DIAMOND_PICKAXE)), NATIVE));
        } finally { observe(world, "after-native-or-error", furnace); }
        assertTrue(world.getBlock(0, 64, 0).isAir());
        assertTrue(furnace.closed);
        assertNull(world.getBlockEntity(new Vector3(0, 64, 0)));
        assertNull(world.getBlockEntityById(furnace.getId()));
        assertEquals(2, physicalXp(world));
        assertEquals(3, physicalItems(world, Item.IRON_INGOT));
        assertEquals(1, physicalItems(world, Block.FURNACE));
        assertEquals(0f, furnace.getExperience());
        // A terminal lifecycle replay cannot repay stored inventory or XP.
        furnace.close();
        assertEquals(2, physicalXp(world));
        assertEquals(3, physicalItems(world, Item.IRON_INGOT));
    }

    @Test @Order(7) void nativeAcceptedChestRemovalRetiresActualRegistrations() throws Exception {
        World world = world(Block.CHEST);
        BlockEntityChest chest = new BlockEntityChest(world.chunk, tileTag(BlockEntity.CHEST, 0));
        assertTrue(chest.getRealInventory().setItem(0, Item.get(Item.DIAMOND, 0, 7)));
        Block original = world.getBlock(0, 64, 0);
        observe(world, "before-native", chest);
        try {
            assertTrue(Explosion.removeSource(original, Item.get(Item.DIAMOND_PICKAXE), true,
                    Explosion.canonicalOwnLoot(original, Item.get(Item.DIAMOND_PICKAXE)), NATIVE));
        } finally { observe(world, "after-native-or-error", chest); }
        assertTrue(world.getBlock(0, 64, 0).isAir());
        assertTrue(chest.closed);
        assertNull(world.getBlockEntityById(chest.getId()));
        assertNull(world.getBlockEntity(new Vector3(0, 64, 0)));
        assertEquals(7, physicalItems(world, Item.DIAMOND));
        assertEquals(1, physicalItems(world, Block.CHEST));
    }
}
