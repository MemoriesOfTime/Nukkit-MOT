package cn.nukkit.level.explosion;

import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.blockentity.BlockEntityFurnace;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.event.block.NativeExplosionBlockProcessor;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.NativeContainerTransaction;
import cn.nukkit.item.Item;
import cn.nukkit.level.Explosion;
import cn.nukkit.math.Vector3;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Isolated;
import static cn.nukkit.level.explosion.ActualContainerRetirementFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** AFTER acceptance controls only. The original seven-test source is unchanged. */
@Isolated
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
final class NativeContainerRollbackRejectionTest {
    @org.junit.jupiter.api.AfterAll static void restoreFixtureServer() throws Exception { NativeExplosionFixture.restoreServer(); }
    @BeforeAll static void init() throws Exception { boot(); }

    @Test @Order(1) void chestFinalAirPolicyRefusalRestoresOriginalSource() throws Exception {
        World world = world(Block.CHEST);
        BlockEntityChest chest = new BlockEntityChest(world.chunk, tileTag(BlockEntity.CHEST, 0));
        assertTrue(chest.getRealInventory().setItem(0, Item.get(Item.DIAMOND, 0, 7)));
        assertEquals(Block.CHEST, world.getBlock(0, 64, 0).getId());
        assertEquals(Item.DIAMOND, chest.getRealInventory().getItem(0).getId());
        assertEquals(7, chest.getRealInventory().getItem(0).getCount());
        exercise("chest", world, chest, chest.getRealInventory(), Item.DIAMOND, 7);
    }

    @Test @Order(2) void lawfulFurnaceFinalAirPolicyRefusalRetainsAccruedXp() throws Exception {
        World world = world(Block.FURNACE);
        BlockEntityFurnace furnace = lawfulFurnace(world);
        // The unchanged fixture performs 600 real onUpdate calls with ore and coal.
        assertEquals(Block.BURNING_FURNACE, world.getBlock(0, 64, 0).getId());
        assertEquals(Item.IRON_INGOT, furnace.getInventory().getResult().getId());
        assertEquals(3, furnace.getInventory().getResult().getCount());
        assertTrue(furnace.getExperience() >= 2f && furnace.getExperience() < 3f);
        assertEquals(2, furnace.namedTag.getShort("StoredXpInt"));
        exercise("furnace", world, furnace, furnace.getInventory(), Item.IRON_INGOT, 3);
    }

    private static void exercise(String kind, World world, BlockEntity tile, Inventory inventory,
                                 int itemId, int itemCount) {
        Block original = world.getBlock(0, 64, 0);
        assertEquals(0, original.layer);
        assertEquals(0, tile.x);
        assertEquals(64, tile.y);
        assertEquals(0, tile.z);
        assertSame(world, tile.getLevel());
        assertSame(world.chunk, tile.chunk);
        assertSame(world.chunk, world.getChunkIfLoaded(0, 0));
        assertSame(tile, world.getBlockEntity(new Vector3(0, 64, 0)));
        assertSame(tile, world.chunk.getTile(0, 64, 0));
        assertSame(tile, world.getBlockEntityById(tile.getId()));
        assertSame(tile, world.chunk.getBlockEntities().get(tile.getId()));
        assertFalse(tile.closed);
        assertEquals(itemCount, inventory.getContents().values().stream().mapToInt(Item::getCount).sum());
        assertTrue(inventory.getContents().values().stream().allMatch(item -> item.getId() == itemId));
        tile.saveNBT();
        var originalNbt = tile.namedTag.clone();
        var source = NativeContainerTransaction.snapshot(inventory);
        assertNotNull(source);
        assertTrue(source.matches());
        float originalXp = tile instanceof BlockEntityFurnace furnace ? furnace.getExperience() : 0f;
        assertNoPublishedGoods(world);
        assertNoRegisteredGoods(world);
        FinalAirVeto veto = new FinalAirVeto(kind, world, tile, inventory, original, originalXp);
        observeRollback(world, kind + "-before-final-veto", tile);
        boolean removed;
        try {
            removed = assertDoesNotThrow(() -> Explosion.removeSource(original,
                    Item.get(Item.DIAMOND_PICKAXE), true,
                    Explosion.canonicalOwnLoot(original, Item.get(Item.DIAMOND_PICKAXE)), veto));
        } finally { observeRollback(world, kind + "-after-refusal-or-error", tile); }
        assertFalse(removed);
        assertTrue(veto.earlierAllows > 0, "No pre-write policy call was observed");
        assertEquals(1, veto.finalRefusals);
        assertEquals(veto.earlierAllows + 1, veto.calls);
        assertEquals(0, veto.removedCalls);
        assertEquals(original.getFullId(), world.getBlock(0, 64, 0).getFullId());
        assertSame(tile, world.getBlockEntity(new Vector3(0, 64, 0)));
        assertSame(tile, world.chunk.getTile(0, 64, 0));
        assertSame(tile, world.getBlockEntityById(tile.getId()));
        assertSame(tile, world.chunk.getBlockEntities().get(tile.getId()));
        assertFalse(tile.closed);
        assertSame(world, tile.getLevel());
        assertSame(world.chunk, tile.chunk);
        assertTrue(source.matches(), "Exact original inventory receipt was not restored");
        assertEquals(itemCount, inventory.getContents().values().stream().mapToInt(Item::getCount).sum());
        if (tile instanceof BlockEntityFurnace furnace) assertEquals(originalXp, furnace.getExperience());
        tile.saveNBT();
        assertEquals(originalNbt, tile.namedTag);
        assertNoPublishedGoods(world);
        assertNoRegisteredGoods(world);
        assertEquals(0, physicalXp(world));
        assertEquals(0, physicalItems(world, itemId));
    }

    /** Only the final pure policy call refuses, after native AIR and inventory debit. */
    private static final class FinalAirVeto implements NativeExplosionBlockProcessor {
        private final String kind;
        private final World world;
        private final BlockEntity tile;
        private final Inventory inventory;
        private final Block original;
        private final float originalXp;
        private int calls;
        private int earlierAllows;
        private int finalRefusals;
        private int removedCalls;
        FinalAirVeto(String kind, World world, BlockEntity tile, Inventory inventory,
                     Block original, float originalXp) {
            this.kind = kind; this.world = world; this.tile = tile; this.inventory = inventory;
            this.original = original; this.originalXp = originalXp;
        }
        @Override public Result process(Block affected) { return Result.NATIVE; }
        @Override public boolean canRemove(List<Block> cells) {
            calls++;
            assertEquals(1, cells.size());
            assertEquals(original.getFullId(), cells.get(0).getFullId());
            assertSame(world, cells.get(0).getLevel());
            if (!world.getBlock(0, 64, 0).isAir()) {
                assertEquals(original.getFullId(), world.getBlock(0, 64, 0).getFullId());
                assertSame(tile, world.chunk.getTile(0, 64, 0));
                assertSame(tile, world.getBlockEntity(new Vector3(0, 64, 0)));
                assertFalse(inventory.getContents().isEmpty());
                earlierAllows++;
                return true;
            }
            finalRefusals++;
            observeRollback(world, kind + "-final-veto-after-debit", tile);
            assertTrue(inventory.getContents().isEmpty(), "Policy veto did not reach the real inventory debit");
            assertNull(world.chunk.getTile(0, 64, 0));
            assertNull(world.getBlockEntity(new Vector3(0, 64, 0)));
            assertNull(world.chunk.getBlockEntities().get(tile.getId()));
            assertSame(tile, world.getBlockEntityById(tile.getId()));
            assertFalse(tile.closed);
            if (tile instanceof BlockEntityFurnace furnace) assertEquals(originalXp, furnace.getExperience());
            assertNoPublishedGoods(world);
            // Prepared deferred actors may be registered here; observe them, do not reject them.
            return false;
        }
        @Override public void removed(List<Block> cells) {
            removedCalls++;
            fail("blocksRemoved is forbidden for a refused source");
        }
    }

    private static void observeRollback(World world, String phase, BlockEntity tile) {
        observe(world, phase, tile);
        BlockEntity chunkIdEntry = world.chunk.getBlockEntities().get(tile.getId());
        System.out.println("ROLLBACK phase=" + phase + " tileId=" + tile.getId()
                + " chunkIdSame=" + (chunkIdEntry == tile) + " chunkIdAbsent=" + (chunkIdEntry == null));
    }

    private static void assertNoPublishedGoods(World world) {
        for (Entity actor : world.actors.values()) {
            if (actor instanceof EntityItem || actor instanceof EntityXPOrb) {
                assertFalse(!actor.closed && actor.isAlive() && !actor.isPublicationDeferred(),
                        "Refused source published a native goods actor");
            }
        }
    }

    private static void assertNoRegisteredGoods(World world) {
        for (Entity actor : world.actors.values()) {
            assertFalse(actor instanceof EntityItem || actor instanceof EntityXPOrb,
                    "Refused source retained a registered item/XP ticket");
        }
        for (Entity actor : world.chunk.getEntities().values()) {
            assertFalse(actor instanceof EntityItem || actor instanceof EntityXPOrb,
                    "Refused source retained an item/XP ticket in the native chunk");
        }
    }
}
