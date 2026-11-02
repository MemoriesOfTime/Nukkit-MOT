package cn.nukkit.level.explosion;

import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.blockentity.PersistentDataContainerBlockEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.block.NativeExplosionBlockProcessor;
import cn.nukkit.item.Item;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.persistence.PersistentDataContainer;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static cn.nukkit.level.explosion.ActualContainerRetirementFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real foreign metadata at provisional AIR; callback-to-terminal phase only, no later tick. */
@Isolated
final class NativeContainerForeignPdcGuardTest {
    @org.junit.jupiter.api.AfterAll static void restoreFixtureServer() throws Exception { NativeExplosionFixture.restoreServer(); }
    @BeforeAll static void init() throws Exception { boot(); }

    @Test void acceptedSourcePreservesDistinctNonemptyPdcAtTerminalClose() throws Exception {
        World world = world(Block.CHEST);
        BlockEntityChest original = new BlockEntityChest(world.chunk, tileTag(BlockEntity.CHEST, 0));
        assertTrue(original.getPersistentDataContainer().isEmpty());
        Item input = Item.get(Item.DIAMOND, 0, 7);
        input.setNamedTag(new CompoundTag().putString("pdc-source", "seven-original-diamonds"));
        assertTrue(original.getRealInventory().setItem(0, input));
        assertEquals(7, original.getRealInventory().getItem(0).getCount());
        assertSame(original, world.getBlockEntityById(original.getId()));
        assertSame(original, world.getBlockEntity(new Vector3(0, 64, 0)));
        assertSame(original, world.chunk.getTile(0, 64, 0));
        assertSame(original, world.chunk.getBlockEntities().get(original.getId()));
        assertFalse(original.closed);
        assertTrue(world.actors.isEmpty());
        ForeignMetadata handler = new ForeignMetadata(world, original, input);
        observePdc(world, "pdc-before", original, handler);
        Block source = world.getBlock(0, 64, 0);
        boolean removed;
        try {
            removed = assertDoesNotThrow(() -> Explosion.removeSource(source,
                    Item.get(Item.DIAMOND_PICKAXE), true,
                    Explosion.canonicalOwnLoot(source, Item.get(Item.DIAMOND_PICKAXE)), handler));
        } finally { observePdc(world, "pdc-after", original, handler); }
        assertTrue(removed);
        assertEquals(1, handler.callbacks);
        assertTrue(world.getBlock(0, 64, 0).isAir());
        assertSame(original, world.getBlockEntityById(original.getId()));
        assertNull(world.chunk.getBlockEntities().get(original.getId()));
        assertFalse(original.closed, "Foreign metadata means the terminal source is no longer owned");
        assertSame(world, original.getLevel());
        assertSame(world.chunk, original.chunk);
        assertEquals(0, original.x);
        assertEquals(64, original.y);
        assertEquals(0, original.z);
        assertTrue(original.getRealInventory().getContents().isEmpty());
        assertTrue(original.getPersistentDataContainer().isEmpty());
        PersistentDataContainerBlockEntity foreign = handler.foreign;
        assertNotNull(foreign);
        assertNotSame(original, foreign);
        assertNotEquals(original.getId(), foreign.getId());
        assertFalse(foreign.closed);
        assertSame(world, foreign.getLevel());
        assertSame(world.chunk, foreign.chunk);
        assertSame(foreign, world.getBlockEntity(new Vector3(0, 64, 0)));
        assertSame(foreign, world.chunk.getTile(0, 64, 0));
        assertSame(foreign, world.getBlockEntityById(foreign.getId()));
        assertSame(foreign, world.chunk.getBlockEntities().get(foreign.getId()));
        assertEquals(handler.expectedStorage, foreign.getStorage());
        assertEquals(handler.expectedNbt, foreign.namedTag);
        assertFalse(foreign.getStorage().isEmpty());
        // Native onUpdate would close this invalid AIR metadata holder; it is deliberately not invoked.
        assertFalse(foreign.isBlockEntityValid());
        assertGoods(world, input, false);
    }

    /** Real accepted batch callback, not an injected tile getter or acceptance marker. */
    private static final class ForeignMetadata implements NativeExplosionBlockProcessor {
        private final World world;
        private final BlockEntityChest original;
        private final Item input;
        private int callbacks;
        private PersistentDataContainerBlockEntity foreign;
        private CompoundTag expectedStorage;
        private CompoundTag expectedNbt;
        ForeignMetadata(World world, BlockEntityChest original, Item input) {
            this.world = world; this.original = original; this.input = input;
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
            assertTrue(original.getPersistentDataContainer().isEmpty());
            assertSame(original, world.getBlockEntityById(original.getId()));
            assertNull(world.getBlockEntity(new Vector3(0, 64, 0)));
            assertNull(world.chunk.getTile(0, 64, 0));
            assertNull(world.chunk.getBlockEntities().get(original.getId()));
            assertFalse(original.closed);
            assertGoods(world, input, true);
            observePdc(world, "pdc-accepted-before-foreign", original, this);
            // Actual native constructor assigns its own ID and registers both native maps.
            foreign = new PersistentDataContainerBlockEntity(world.chunk,
                    BlockEntity.getDefaultCompound(new Vector3(0, 64, 0), BlockEntity.PERSISTENT_CONTAINER));
            foreign.setStorage(new CompoundTag(PersistentDataContainer.STORAGE_TAG)
                    .putString("foreign-owner", "distinct-live-pdc")
                    .putCompound("payload", new CompoundTag("payload").putInt("revision", 37)));
            assertEquals("distinct-live-pdc", foreign.getStorage().getString("foreign-owner"));
            assertEquals(37, foreign.getStorage().getCompound("payload").getInt("revision"));
            assertFalse(foreign.getStorage().isEmpty());
            assertNotEquals(original.getId(), foreign.getId());
            assertSame(foreign, world.getBlockEntity(new Vector3(0, 64, 0)));
            assertSame(foreign, world.chunk.getTile(0, 64, 0));
            assertSame(foreign, world.getBlockEntityById(foreign.getId()));
            assertSame(foreign, world.chunk.getBlockEntities().get(foreign.getId()));
            assertSame(original, world.getBlockEntityById(original.getId()));
            expectedStorage = foreign.getStorage().copy();
            expectedNbt = foreign.namedTag.clone();
            observePdc(world, "pdc-accepted-after-foreign", original, this);
        }
    }

    private static void assertGoods(World world, Item input, boolean deferred) {
        int diamonds = 0, shells = 0;
        for (Entity actor : world.actors.values()) {
            assertTrue(actor instanceof EntityItem, "Unexpected XP or unrelated actor");
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
            } else { assertEquals(Block.CHEST, goods.getId()); shells += goods.getCount(); }
        }
        assertEquals(7, diamonds);
        assertEquals(1, shells);
        assertEquals(world.actors.size(), world.chunk.getEntities().size());
        assertEquals(0, physicalXp(world));
    }

    private static void observePdc(World world, String phase, BlockEntityChest original, ForeignMetadata handler) {
        observe(world, phase, original);
        PersistentDataContainerBlockEntity foreign = handler.foreign;
        System.out.println("PDC_GUARD phase=" + phase + " originalId=" + original.getId()
                + " originalIdSame=" + (world.getBlockEntityById(original.getId()) == original)
                + " originalClosed=" + original.closed + " callbacks=" + handler.callbacks
                + " foreignId=" + (foreign == null ? -1 : foreign.getId())
                + " foreignClosed=" + (foreign != null && foreign.closed)
                + " foreignLevelIdSame=" + (foreign != null && world.getBlockEntityById(foreign.getId()) == foreign)
                + " foreignChunkIdSame=" + (foreign != null && world.chunk.getBlockEntities().get(foreign.getId()) == foreign)
                + " foreignPositionSame=" + (foreign != null && world.getBlockEntity(new Vector3(0, 64, 0)) == foreign)
                + " foreignStoragePresent=" + (foreign != null && foreign.namedTag.contains(PersistentDataContainer.STORAGE_TAG))
                + " foreignOwner=" + (foreign == null ? "none" : foreign.namedTag.getCompound(PersistentDataContainer.STORAGE_TAG).getString("foreign-owner"))
                + " foreignRevision=" + (foreign == null ? -1 : foreign.namedTag.getCompound(PersistentDataContainer.STORAGE_TAG).getCompound("payload").getInt("revision")));
    }
}
