package cn.nukkit.blockentity;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Container upkeep in the tick must never read a neighbouring chunk that is not in memory: the
 * loading getBlock/getBlockIdAt read it from disk on the main thread, where a slow disk turns
 * every such read into a tick spike. A hopper, comparator or beacon in an unloaded chunk does not
 * tick; it catches up when its chunk loads.
 */
class UnloadedNeighbourChunkReadsTest {

    @BeforeAll
    static void init() {
        MockServer.init();
        Block.init();
    }

    @Test
    void wakingHoppersNextToAnUnloadedChunkDoesNotLoadIt() {
        Level level = mock(Level.class);

        // A chest at the east edge of its chunk: (16, 64, 5) lives in the unloaded chunk (1, 0).
        BlockEntityHopper.wakeupHoppersAround(level, 15, 64, 5);

        verify(level, never()).getChunk(anyInt(), anyInt());
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        verify(level, never()).getBlockIdAt(anyInt(), anyInt(), anyInt());
        verify(level, never()).getBlockDataAt(anyInt(), anyInt(), anyInt());
        verify(level, never()).getBlockEntity(any(Vector3.class));
    }

    @Test
    void aLoadedHopperFacingTheContainerIsStillWoken() {
        Level level = mock(Level.class);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(level.getChunkIfLoaded(1, 0)).thenReturn(chunk);
        when(level.getBlockIdAt(chunk, 16, 64, 5)).thenReturn(Block.HOPPER_BLOCK);
        // Facing west, into the chest at (15, 64, 5)
        when(chunk.getBlockData(0, 64, 5)).thenReturn(BlockFace.WEST.getIndex());
        BlockEntityHopper hopper = mock(BlockEntityHopper.class);
        when(level.getBlockEntityIfLoaded(eq(chunk), any(Vector3.class))).thenReturn(hopper);

        BlockEntityHopper.wakeupHoppersAround(level, 15, 64, 5);

        verify(hopper).scheduleUpdate();
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void comparatorBehindASolidBlockInAnUnloadedChunkIsNotLoaded() {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        doReturn(true).when(level).isChunkLoaded(0, 0);
        doReturn(false).when(level).isChunkLoaded(1, 0);
        // A container at (14, 64, 5): a solid block east of it at (15, 64, 5), the comparator
        // spot behind that at (16, 64, 5) is in the unloaded chunk (1, 0).
        doReturn(Block.get(Block.AIR)).when(level).getBlock(any(Vector3.class));
        doReturn(Block.get(Block.STONE)).when(level).getBlock(argThat((Vector3 pos) -> pos != null && pos.x == 15));

        level.updateComparatorOutputLevelSelective(new Vector3(14, 64, 5), false);

        verify(level, never()).getBlock(argThat((Vector3 pos) -> pos != null && pos.x == 16));
    }

    @Test
    void beaconDoesNotLoadTheChunksUnderItsPyramid() {
        Level level = mock(Level.class);
        lenient().when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        lenient().when(level.isYInRange(anyInt())).thenReturn(true);
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().when(provider.getLevel()).thenReturn(level);
        // The beacon stands at the corner of chunk (0, 0); its pyramid reaches chunks that are unloaded.
        BlockEntityBeacon beacon = new BlockEntityBeacon(chunk, new CompoundTag()
                .putString("id", BlockEntity.BEACON)
                .putInt("x", 0).putInt("y", 64).putInt("z", 0));

        boolean keepTicking = beacon.onUpdate();

        assertTrue(keepTicking);
        verify(level, never()).getBlockIdAt(anyInt(), anyInt(), anyInt());
        verify(level, never()).getChunk(anyInt(), anyInt());
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }
}
