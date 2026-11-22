package cn.nukkit.level.structure;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;

/**
 * 放置时的旋转、镜像、完整度与 void 语义。
 * <p>
 * Rotation, mirroring, integrity and void semantics during placement.
 */
class StructurePlacementTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    private static Structure twoByOne() {
        CompoundTag air = new CompoundTag().putString("name", "minecraft:air").putCompound("states", new CompoundTag());
        CompoundTag stone = new CompoundTag().putString("name", "minecraft:stone").putCompound("states", new CompoundTag());
        return new Structure(2, 1, 1, List.of(air, stone),
                new int[]{BlockID.AIR << Block.DATA_BITS, BlockID.STONE << Block.DATA_BITS},
                new int[]{0, 1}, new int[]{Structure.VOID, Structure.VOID},
                Map.of(), List.of(), new BlockVector3(0, 0, 0));
    }

    private static List<int[]> collectWrites(Structure structure, StructurePlacementOptions options) {
        Level level = Mockito.mock(Level.class);
        List<int[]> writes = new ArrayList<>();
        Mockito.when(level.setBlock(anyInt(), anyInt(), anyInt(), anyInt(), any(Block.class), anyBoolean(), anyBoolean()))
                .thenAnswer(invocation -> {
                    writes.add(new int[]{
                            invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                            invocation.getArgument(3), invocation.<Block>getArgument(4).getId()
                    });
                    return true;
                });
        StructurePlacement.place(structure, level, options);
        return writes;
    }

    @Test
    void placesBothLayersInSourceOrder() {
        List<int[]> writes = collectWrites(twoByOne(), new StructurePlacementOptions(new BlockVector3(10, 20, 30)));
        assertEquals(2, writes.size());
        assertEquals(BlockID.AIR, writes.get(0)[4]);
        assertEquals(10, writes.get(0)[0]);
        assertEquals(BlockID.STONE, writes.get(1)[4]);
        assertEquals(11, writes.get(1)[0]);
    }

    @Test
    void rotatesNinetyDegreesClockwise() {
        StructurePlacementOptions options = new StructurePlacementOptions(new BlockVector3(0, 0, 0));
        options.rotation = 1;
        List<int[]> writes = collectWrites(twoByOne(), options);
        assertEquals(2, writes.size());
        assertEquals(0, writes.get(0)[0]);
        assertEquals(0, writes.get(0)[2]);
        assertEquals(0, writes.get(1)[0]);
        assertEquals(1, writes.get(1)[2], "clockwise 90 must map east(+X) to south(+Z)");
    }

    @Test
    void mirrorsBeforeRotating() {
        StructurePlacementOptions options = new StructurePlacementOptions(new BlockVector3(0, 0, 0));
        options.rotation = 1;
        options.mirror = 1;
        List<int[]> writes = collectWrites(twoByOne(), options);
        assertEquals(2, writes.size());
        assertEquals(0, writes.get(0)[0]);
        assertEquals(1, writes.get(0)[2], "mirror X must flip the blocks before the 90° rotation");
        assertEquals(0, writes.get(1)[0]);
        assertEquals(0, writes.get(1)[2]);
    }

    @Test
    void mirrorsAlongXAxis() {
        StructurePlacementOptions options = new StructurePlacementOptions(new BlockVector3(0, 0, 0));
        options.mirror = 1;
        List<int[]> writes = collectWrites(twoByOne(), options);
        assertEquals(1, writes.get(0)[0]);
        assertEquals(0, writes.get(1)[0]);
    }

    @Test
    void skipsEverythingAtZeroIntegrity() {
        StructurePlacementOptions options = new StructurePlacementOptions(new BlockVector3(0, 0, 0));
        options.integrity = 0f;
        assertEquals(0, collectWrites(twoByOne(), options).size());
    }

    @Test
    void voidCellsAreNotWritten() {
        CompoundTag stone = new CompoundTag().putString("name", "minecraft:stone").putCompound("states", new CompoundTag());
        Structure structure = new Structure(1, 1, 1, List.of(stone),
                new int[]{BlockID.STONE << Block.DATA_BITS}, new int[]{Structure.VOID}, new int[]{0},
                Map.of(), List.of(), new BlockVector3(0, 0, 0));
        assertEquals(0, collectWrites(structure, new StructurePlacementOptions(new BlockVector3(0, 0, 0))).size());
    }

    @Test
    void ignoringBlocksWritesNothing() {
        StructurePlacementOptions options = new StructurePlacementOptions(new BlockVector3(0, 0, 0));
        options.ignoringBlocks = true;
        assertEquals(0, collectWrites(twoByOne(), options).size());
    }
}
