package cn.nukkit.level.explosion;

import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.block.NativeExplosionBlockProcessor;
import cn.nukkit.item.Item;
import cn.nukkit.level.Explosion;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Isolated;
import static cn.nukkit.level.explosion.ActualContainerRetirementFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** AFTER adjacent controls; the original seven tests and rollback2 stay unchanged. */
@Isolated
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
final class NativeContainerRetirementGuardsTest {
    @org.junit.jupiter.api.AfterAll static void restoreFixtureServer() throws Exception { NativeExplosionFixture.restoreServer(); }
    private static final int FOREIGN_CHEST = 1;
    private static final int FOREIGN_STONE = 2;
    private static final int MISSING_ORIGINAL_ID = 3;
    private static final int REPLACED_ORIGINAL_ID = 4;
    @BeforeAll static void init() throws Exception { boot(); }

    @Test @Order(1) void acceptedSourcePreservesLawfulForeignChestAtTerminalClose() throws Exception {
        exercise(FOREIGN_CHEST, "foreign-chest");
    }
    @Test @Order(2) void acceptedSourcePreservesLawfulForeignStoneAtTerminalClose() throws Exception {
        exercise(FOREIGN_STONE, "foreign-stone");
    }
    @Test @Order(3) void acceptedSourceRejectsControlledMissingOriginalIdOwnership() throws Exception {
        exercise(MISSING_ORIGINAL_ID, "missing-original-id");
    }
    @Test @Order(4) void acceptedSourcePreservesControlledReplacementOriginalIdEntry() throws Exception {
        exercise(REPLACED_ORIGINAL_ID, "replaced-original-id");
    }

    private static void exercise(int mode, String kind) throws Exception {
        World world = world(Block.CHEST);
        BlockEntityChest original = new BlockEntityChest(world.chunk, tileTag("Chest", 0));
        Item input = Item.get(Item.DIAMOND, 0, 7);
        input.setNamedTag(new CompoundTag().putString("guard-source", kind));
        assertTrue(original.getRealInventory().setItem(0, input));
        assertEquals(7, original.getRealInventory().getItem(0).getCount());
        assertSame(original, world.getBlockEntity(new Vector3(0, 64, 0)));
        assertSame(original, world.chunk.getTile(0, 64, 0));
        assertSame(original, world.getBlockEntityById(original.getId()));
        assertSame(original, world.chunk.getBlockEntities().get(original.getId()));
        assertFalse(original.closed);
        assertEquals(0, world.actors.size());
        AcceptedReplacement handler = new AcceptedReplacement(mode, kind, world, original, input);
        observeGuard(world, kind + "-before", original, handler);
        Block source = world.getBlock(0, 64, 0);
        boolean removed;
        try {
            removed = assertDoesNotThrow(() -> Explosion.removeSource(source,
                    Item.get(Item.DIAMOND_PICKAXE), true,
                    Explosion.canonicalOwnLoot(source, Item.get(Item.DIAMOND_PICKAXE)), handler));
        } finally { observeGuard(world, kind + "-after", original, handler); }
        assertTrue(removed, "Source was not actually accepted and funded");
        assertEquals(1, handler.callbacks);
        assertTrue(original.getRealInventory().getContents().isEmpty());
        assertFalse(original.closed, "Terminal guard closed an original it no longer owns");
        assertSame(world, original.getLevel());
        assertSame(world.chunk, original.chunk);
        assertNull(world.chunk.getBlockEntities().get(original.getId()));
        assertFundedGoods(world, input, false);
        if (mode == FOREIGN_CHEST) {
            assertEquals(Block.CHEST, world.getBlock(0, 64, 0).getId());
            assertSame(handler.foreign, world.getBlockEntity(new Vector3(0, 64, 0)));
            assertSame(handler.foreign, world.chunk.getTile(0, 64, 0));
            assertSame(handler.foreign, world.chunk.getBlockEntities().get(handler.foreign.getId()));
            assertSame(handler.foreign, world.getBlockEntityById(handler.foreign.getId()));
            assertSame(original, world.getBlockEntityById(original.getId()));
        } else if (mode == FOREIGN_STONE) {
            assertEquals(Block.STONE, world.getBlock(0, 64, 0).getId());
            assertNull(world.getBlockEntity(new Vector3(0, 64, 0)));
            assertNull(world.chunk.getTile(0, 64, 0));
            assertSame(original, world.getBlockEntityById(original.getId()));
        } else {
            assertTrue(world.getBlock(0, 64, 0).isAir());
            assertNull(world.getBlockEntity(new Vector3(0, 64, 0)));
            assertNull(world.chunk.getTile(0, 64, 0));
            if (mode == MISSING_ORIGINAL_ID) assertNull(world.getBlockEntityById(original.getId()));
            else {
                assertSame(handler.foreign, world.getBlockEntityById(original.getId()));
                assertSame(handler.foreign, world.getBlockEntity(new Vector3(1, 64, 0)));
                assertSame(handler.foreign, world.chunk.getTile(1, 64, 0));
                assertSame(handler.foreign, world.chunk.getBlockEntities().get(handler.foreign.getId()));
                assertSame(handler.foreign, world.getBlockEntityById(handler.foreign.getId()));
                assertEquals(Block.CHEST, world.getBlock(1, 64, 0).getId());
            }
        }
        if (handler.foreign != null) {
            assertFalse(handler.foreign.closed);
            assertSame(world, handler.foreign.getLevel());
            assertSame(world.chunk, handler.foreign.chunk);
            assertTrue(handler.foreign.getRealInventory().getContents().isEmpty());
            handler.foreign.saveNBT();
            assertEquals(handler.foreignNbt, handler.foreign.namedTag);
        }
    }

    /** Runs in real native batch settlement, after source debit and before terminal close. */
    private static final class AcceptedReplacement implements NativeExplosionBlockProcessor {
        private final int mode;
        private final String kind;
        private final World world;
        private final BlockEntityChest original;
        private final Item input;
        private int callbacks;
        private BlockEntityChest foreign;
        private CompoundTag foreignNbt;
        AcceptedReplacement(int mode, String kind, World world, BlockEntityChest original, Item input) {
            this.mode = mode; this.kind = kind; this.world = world; this.original = original; this.input = input;
        }
        @Override public Result process(Block affected) { return Result.NATIVE; }
        @Override public void removed(List<Block> cells) {
            callbacks++;
            assertEquals(1, callbacks);
            assertEquals(1, cells.size());
            assertEquals(Block.CHEST, cells.get(0).getId());
            assertSame(world, cells.get(0).getLevel());
            assertTrue(world.getBlock(0, 64, 0).isAir());
            assertTrue(original.getRealInventory().getContents().isEmpty());
            assertNull(world.getBlockEntity(new Vector3(0, 64, 0)));
            assertNull(world.chunk.getTile(0, 64, 0));
            assertNull(world.chunk.getBlockEntities().get(original.getId()));
            assertSame(original, world.getBlockEntityById(original.getId()));
            assertFalse(original.closed);
            assertFundedGoods(world, input, true);
            observeGuard(world, kind + "-accepted-before-replacement", original, this);
            if (mode == FOREIGN_CHEST || mode == REPLACED_ORIGINAL_ID) {
                int x = mode == FOREIGN_CHEST ? 0 : 1;
                assertTrue(world.setBlock(new Vector3(x, 64, 0), Block.get(Block.CHEST), false, false));
                foreign = new BlockEntityChest(world.chunk, tileTag("Chest", x));
                assertNotSame(original, foreign);
                assertNotEquals(original.getId(), foreign.getId());
                assertSame(foreign, world.chunk.getTile(x, 64, 0));
                assertSame(foreign, world.getBlockEntity(new Vector3(x, 64, 0)));
                assertSame(foreign, world.getBlockEntityById(foreign.getId()));
                assertFalse(foreign.closed);
                foreign.saveNBT();
                foreignNbt = foreign.namedTag.clone();
                if (mode == REPLACED_ORIGINAL_ID) {
                    // Controlled trusted-extension registry corruption, not a player path.
                    assertSame(original, world.getBlockEntities().put(original.getId(), foreign));
                }
            } else if (mode == FOREIGN_STONE) {
                assertTrue(world.setBlock(new Vector3(0, 64, 0), Block.get(Block.STONE), false, false));
            } else if (mode == MISSING_ORIGINAL_ID) {
                // Controlled trusted-extension registry mutation, not a player exploit.
                assertTrue(world.getBlockEntities().remove(original.getId(), original));
            } else fail("Unknown guard control");
            observeGuard(world, kind + "-accepted-after-replacement", original, this);
        }
    }

    private static void assertFundedGoods(World world, Item input, boolean deferred) {
        int diamonds = 0, shells = 0;
        for (Entity actor : world.actors.values()) {
            assertTrue(actor instanceof EntityItem, "Unexpected XP or unrelated actor in isolated world");
            EntityItem item = (EntityItem) actor;
            assertFalse(item.closed);
            assertTrue(item.isAlive());
            assertEquals(deferred, item.isPublicationDeferred());
            assertSame(world, item.getLevel());
            assertSame(item, world.getEntity(item.getId()));
            assertSame(item, world.chunk.getEntities().get(item.getId()));
            Item goods = item.getItem();
            if (goods.getId() == Item.DIAMOND) {
                diamonds += goods.getCount();
                assertEquals(input.getNamedTag(), goods.getNamedTag());
            } else {
                assertEquals(Block.CHEST, goods.getId());
                shells += goods.getCount();
            }
        }
        assertEquals(7, diamonds);
        assertEquals(1, shells);
        assertEquals(world.actors.size(), world.chunk.getEntities().size());
        assertEquals(0, physicalXp(world));
    }

    private static void observeGuard(World world, String phase, BlockEntityChest original, AcceptedReplacement handler) {
        observe(world, phase, original);
        System.out.println("TERMINAL_GUARD phase=" + phase + " originalId=" + original.getId()
                + " sourceChunkIdSame=" + (world.chunk.getBlockEntities().get(original.getId()) == original)
                + " sourceLevelIdSame=" + (world.getBlockEntityById(original.getId()) == original)
                + " foreignId=" + (handler.foreign == null ? -1 : handler.foreign.getId())
                + " foreignClosed=" + (handler.foreign != null && handler.foreign.closed)
                + " callbacks=" + handler.callbacks);
    }
}
