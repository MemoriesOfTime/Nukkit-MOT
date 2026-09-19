package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockStone;
import cn.nukkit.block.custom.CustomBlockManager;
import cn.nukkit.block.custom.CustomBlockState;
import cn.nukkit.block.custom.container.BlockContainer;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CollisionHelperBlockOwnershipTest {
    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void vanillaMaterializationDoesNotAllocateAnotherClone() {
        Fixture f = new Fixture(Level.class);
        f.put(0, 0, 0, Block.STONE, 0);
        Block materialized = Block.get(Block.STONE, 0, f.level, 0, 0, 0, 0);
        try (MockedStatic<Block> blocks = mockStatic(Block.class, CALLS_REAL_METHODS)) {
            blocks.when(() -> Block.get(Block.STONE, 0, f.level, 0, 0, 0, 0)).thenReturn(materialized);
            assertSame(materialized, f.at(0, 0, 0));
        }
    }

    @Test
    void realVanillaReadsRemainIndependentAndKeepPositionAndLayer() {
        Fixture f = new Fixture(Level.class);
        f.put(2, 3, 4, Block.WATER, 0);
        Block first = f.at(2, 3, 4);
        Block second = f.at(2, 3, 4);
        assertNotSame(first, second);
        first.setDamage(5);
        first.x = 99;
        assertEquals(0, second.getDamage());
        assertEquals(2, second.x);
        assertEquals(3, second.y);
        assertEquals(4, second.z);
        assertSame(f.level, second.level);
        assertEquals(0, second.layer);
        assertEquals(0, f.at(2, 3, 4).getDamage());
    }

    @Test
    void extendedMetadataAndUnknownIdsAreNotPackedOrTruncated() {
        Fixture f = new Fixture(Level.class);
        f.put(1, 0, 0, Block.WATER, 8192);
        f.put(2, 0, 0, 9000, 9001);
        assertEquals(8192, f.at(1, 0, 0).getDamage());
        Block unknown = f.at(2, 0, 0);
        assertEquals(9000, unknown.getId());
        assertEquals(9001, unknown.getDamage());
    }

    @Test
    void customFactoryReturningCachedVanillaStillGetsSnapshot() throws Exception {
        assertCachedCustomSnapshot(false);
    }

    @Test
    void builtinAirProbeCannotHideCustomStateAtMaterialization() throws Exception {
        assertCachedCustomSnapshot(true);
    }

    @Test
    void nullCustomFactoryKeepsUnknownBlockFallbackAndMetadata() throws Exception {
        Fixture f = new Fixture(Level.class);
        int customId = CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID + 992;
        Field field = CustomBlockManager.class.getDeclaredField("legacy2CustomState");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Int2ObjectMap<CustomBlockState> states = (Int2ObjectMap<CustomBlockState>) field.get(CustomBlockManager.get());
        int key = customId << Block.DATA_BITS;
        CustomBlockState previous = states.put(key,
                new CustomBlockState("test:null_collision_factory", key, NbtMap.EMPTY, meta -> null));
        try {
            f.put(2, 0, 0, customId, 8192);
            Block block = f.at(2, 0, 0);
            assertEquals(customId, block.getId());
            assertEquals(8192, block.getDamage());
        } finally {
            if (previous == null) states.remove(key);
            else states.put(key, previous);
        }
    }

    @Test
    void stateChangedToAirAfterProbeRemainsExcluded() {
        Fixture f = new Fixture(Level.class);
        when(f.chunk.getBlockId(0, 0, 0, 0)).thenReturn(Block.STONE);
        assertEquals(0, f.helper.getBlocksInBoundingBox(point(0, 0, 0)).length);
        verify(f.chunk).getBlockState(0, 0, 0, 0);
    }

    private void assertCachedCustomSnapshot(boolean changedAfterProbe) throws Exception {
        Fixture f = new Fixture(Level.class);
        int customId = CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID + 991;
        CachedStone cached = new CachedStone();
        Field field = CustomBlockManager.class.getDeclaredField("legacy2CustomState");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Int2ObjectMap<CustomBlockState> states = (Int2ObjectMap<CustomBlockState>) field.get(CustomBlockManager.get());
        int key = customId << Block.DATA_BITS;
        CustomBlockState previous = states.put(key,
                new CustomBlockState("test:cached_collision_block", key, NbtMap.EMPTY, meta -> cached));
        try {
            f.put(2, 0, 0, customId, 0);
            f.put(3, 0, 0, customId, 0);
            if (changedAfterProbe) {
                when(f.chunk.getBlockId(anyInt(), anyInt(), anyInt(), eq(0))).thenReturn(Block.STONE);
            }
            Block first = f.at(2, 0, 0);
            Block second = f.at(3, 0, 0);
            assertEquals(Block.STONE, first.getId(), "Returned class/id does not prove factory ownership");
            assertNotSame(cached, first);
            assertNotSame(first, second);
            assertEquals(2, first.x);
            assertEquals(3, second.x);
            assertEquals(3, cached.x);
        } finally {
            if (previous == null) states.remove(key);
            else states.put(key, previous);
        }
    }

    @Test
    void levelOverrideReturningCachedBlockStillGetsSnapshot() {
        Fixture f = new Fixture(CustomLevel.class);
        f.put(2, 0, 0, Block.STONE, 0);
        f.put(3, 0, 0, Block.STONE, 0);
        Block cached = Block.get(Block.STONE);
        doAnswer(invocation -> {
                    cached.x = (int) invocation.getArgument(1);
                    return cached;
                }).when(f.level).getBlock(eq(f.chunk), anyInt(), anyInt(), anyInt(), eq(0), eq(false));
        Block first = f.at(2, 0, 0);
        Block second = f.at(3, 0, 0);
        assertNotSame(cached, first);
        assertNotSame(first, second);
        assertEquals(2, first.x);
        assertEquals(3, second.x);
    }

    @Test
    void nullAndAirFromLevelOverrideStayExcluded() {
        Fixture f = new Fixture(CustomLevel.class);
        f.put(0, 0, 0, Block.STONE, 0);
        doReturn(null, Block.get(Block.AIR)).when(f.level).getBlock(f.chunk, 0, 0, 0, 0, false);
        assertEquals(0, f.helper.getBlocksInBoundingBox(point(0, 0, 0)).length);
        assertEquals(0, f.helper.getBlocksInBoundingBox(point(0, 0, 0)).length);
    }

    @Test
    void airUnloadedAndOutOfHeightDoNotMaterializeBlocks() {
        Fixture f = new Fixture(Level.class);
        assertEquals(0, f.helper.getBlocksInBoundingBox(point(0, 0, 0)).length);
        assertEquals(0, f.helper.getBlocksInBoundingBox(point(32, 0, 0)).length);
        assertEquals(0, f.helper.getBlocksInBoundingBox(point(0, 320, 0)).length);
        assertEquals(0, f.helper.getBlocksInBoundingBox(point(0, -65, 0)).length);
        verify(f.chunk, never()).getBlockState(anyInt(), anyInt(), anyInt(), anyInt());
        verify(f.level, never()).getBlock(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
        verify(f.level, never()).getChunk(anyInt(), anyInt());
    }

    @Test
    void matchingHintIsReusedAndWrongColumnLoadsOnlyExistingChunk() {
        Fixture f = new Fixture(Level.class);
        f.put(2, 0, 0, Block.STONE, 0);
        f.entity.chunk = f.chunk;
        assertEquals(Block.STONE, f.at(2, 0, 0).getId());
        verify(f.level, never()).getChunkIfLoaded(anyInt(), anyInt());
        BaseFullChunk wrongColumn = mock(BaseFullChunk.class);
        when(wrongColumn.getX()).thenReturn(1);
        f.entity.chunk = wrongColumn;
        assertEquals(Block.STONE, f.at(2, 0, 0).getId());
        verify(f.level).getChunkIfLoaded(0, 0);
        verify(wrongColumn, never()).getBlockState(anyInt(), anyInt(), anyInt(), anyInt());
        verify(f.level, never()).getChunk(anyInt(), anyInt());
    }

    @Test
    void dynamicAndSolidCollisionResultsKeepExistingBounds() {
        Fixture f = new Fixture(Level.class);
        when(f.entity.getBoundingBox()).thenReturn(new SimpleAxisAlignedBB(8.2, 8.1, 8.2, 8.8, 8.9, 8.8));
        f.put(8, 8, 8, Block.STONE, 0);
        f.put(9, 8, 8, Block.FIRE, 0);
        f.put(8, 8, 9, Block.STONE, 0);
        Block[] blocks = f.helper.getCollisionBlocks();
        assertEquals(2, blocks.length);
        assertEquals(Block.STONE, blocks[0].getId());
        assertEquals(8, blocks[0].x);
        assertEquals(Block.FIRE, blocks[1].getId());
        assertEquals(9, blocks[1].x);
        assertTrue(blocks[1].hasDynamicCollision());
    }

    private static SimpleAxisAlignedBB point(int x, int y, int z) {
        return new SimpleAxisAlignedBB(x, y, z, x, y, z);
    }

    private record Cell(int x, int y, int z) {}

    private static final class Fixture {
        final Level level;
        final BaseFullChunk chunk = mock(BaseFullChunk.class);
        final Entity entity = mock(Entity.class);
        final CollisionHelper helper = new CollisionHelper(entity);
        final Map<Cell, int[]> states = new HashMap<>();

        Fixture(Class<? extends Level> levelType) {
            level = mock(levelType);
            assertEquals(levelType, level.getClass(), "Ownership guard must exercise exact runtime class");
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
            when(entity.getLevel()).thenReturn(level);
            when(chunk.getBlockId(anyInt(), anyInt(), anyInt(), eq(0)))
                    .thenAnswer(call -> state(call.getArgument(0), call.getArgument(1), call.getArgument(2))[0]);
            when(chunk.getBlockState(anyInt(), anyInt(), anyInt(), eq(0)))
                    .thenAnswer(call -> state(call.getArgument(0), call.getArgument(1), call.getArgument(2)).clone());
            when(level.getBlock(any(), anyInt(), anyInt(), anyInt(), eq(0), eq(false))).thenCallRealMethod();
            when(level.isYInRange(anyInt())).thenCallRealMethod();
        }

        void put(int x, int y, int z, int id, int meta) {
            states.put(new Cell(x, y, z), new int[]{id, meta});
        }

        int[] state(int x, int y, int z) {
            return states.getOrDefault(new Cell(x, y, z), new int[]{0, 0});
        }

        Block at(int x, int y, int z) {
            Block[] blocks = helper.getBlocksInBoundingBox(point(x, y, z));
            assertEquals(1, blocks.length);
            return blocks[0];
        }
    }

    private static class CustomLevel extends Level {
        CustomLevel() {
            super(null, "test", "test", null);
        }
    }

    private static class CachedStone extends BlockStone implements BlockContainer {
        @Override
        public String getIdentifier() {
            return "test:cached_collision_block";
        }

        @Override
        public int getNukkitId() {
            return Block.STONE;
        }
    }
}
