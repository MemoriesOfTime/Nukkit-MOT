package cn.nukkit.level;

import cn.nukkit.block.Block;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import cn.nukkit.utils.BlockUpdateEntry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LevelPointAreaLoadedTest {
    private static Level level() {
        Level level = mock(Level.class);
        when(level.getMinBlockY()).thenReturn(-64);
        when(level.getMaxBlockY()).thenReturn(319);
        when(level.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(level.isAreaLoaded(any())).thenCallRealMethod();
        return level;
    }

    private static Block block(int id) {
        Block block = mock(Block.class);
        when(block.getId()).thenReturn(id);
        return block;
    }

    @Test
    void aPointFloorsEachHorizontalCoordinateOnce() {
        Level level = level();
        try (MockedStatic<NukkitMath> math = mockStatic(NukkitMath.class, CALLS_REAL_METHODS)) {
            assertTrue(level.isAreaLoaded(new SimpleAxisAlignedBB(-1.5, 64, -17.5, -1.5, 64, -17.5)));
            math.verify(() -> NukkitMath.floorDouble(-1.5), times(1));
            math.verify(() -> NukkitMath.floorDouble(-17.5), times(1));
        }
        verify(level).isChunkLoaded(-1, -2);
    }

    @Test
    void retainsHeightLimitsAndMissingChunkResult() {
        for (int y : new int[] {-65, -64, 318, 319}) {
            Level level = level();
            assertEquals(y >= -64 && y < 319,
                    level.isAreaLoaded(new SimpleAxisAlignedBB(0, y, 0, 0, y, 0)));
        }
        Level level = level();
        when(level.isChunkLoaded(1, 0)).thenReturn(false);
        assertFalse(level.isAreaLoaded(new SimpleAxisAlignedBB(16, 64, 0, 16, 64, 0)));
    }

    @Test
    void spanningAreasStillRequireAllTheirChunks() {
        Level level = level();
        AxisAlignedBB area = new SimpleAxisAlignedBB(-1.5, 64, -17.5, 16, 64, 0);
        assertTrue(level.isAreaLoaded(area));
        verify(level, times(9)).isChunkLoaded(anyInt(), anyInt());
        when(level.isChunkLoaded(0, -1)).thenReturn(false);
        assertFalse(level.isAreaLoaded(area));
        assertTrue(level.isAreaLoaded(new SimpleAxisAlignedBB(0, -65, 0, 0, -64, 0)),
                "Vertical columns retain the existing area intersection rule");
    }

    @Test
    void schedulerStillHonorsOverriddenAreaChecks() {
        Level level = level();
        doReturn(false).when(level).isAreaLoaded(any());
        Block block = block(1);
        Vector3 pos = new Vector3(0, 64, 0);
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(level, 0);
        scheduler.add(new BlockUpdateEntry(pos, block, 1, 0));
        scheduler.tick(1);
        verify(level).isAreaLoaded(any(AxisAlignedBB.class));
        verify(level).scheduleUpdate(block, pos, 0);
        verify(level, never()).isChunkLoaded(anyInt(), anyInt());
        verify(level, never()).getBlock(any(Vector3.class), anyInt());
    }

    @Test
    void schedulerStillDefersAnUnloadedPoint() {
        Level level = level();
        when(level.isChunkLoaded(1, 0)).thenReturn(false);
        Block block = block(1);
        Vector3 pos = new Vector3(16, 64, 0);
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(level, 0);
        scheduler.add(new BlockUpdateEntry(pos, block, 1, 0));
        scheduler.tick(1);
        verify(level).scheduleUpdate(block, pos, 0);
        verify(level, never()).getBlock(any(Vector3.class), anyInt());
    }

    @Test
    void schedulerPreservesLayerReplacementAndReentrantDelay() {
        Level level = level();
        Block block = block(1);
        block.layer = 1;
        Vector3 pos = new Vector3(0, 64, 0);
        when(level.getBlock(pos, 1)).thenReturn(block);
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(level, 0);
        doAnswer(invocation -> {
            scheduler.add(new BlockUpdateEntry(pos, block, 1, 0));
            return 0;
        }).when(block).onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        scheduler.add(new BlockUpdateEntry(pos, block, 1, 0));
        scheduler.tick(1);
        verify(block).onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        scheduler.tick(2);
        verify(block, times(2)).onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        Block replacement = block(2);
        when(level.getBlock(pos, 1)).thenReturn(replacement);
        scheduler.tick(3);
        verify(replacement, never()).onUpdate(anyInt());
    }
}
