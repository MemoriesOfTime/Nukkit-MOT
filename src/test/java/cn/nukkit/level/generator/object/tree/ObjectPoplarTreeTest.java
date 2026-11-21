package cn.nukkit.level.generator.object.tree;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObjectPoplarTreeTest {

    private static final int ORIGIN_Y = 64;
    private static final int MAX_HEIGHT = 20;
    private static final Set<Integer> LEAF_IDS = Set.of(
            BlockID.ORANGE_POPLAR_LEAVES, BlockID.RED_POPLAR_LEAVES, BlockID.YELLOW_POPLAR_LEAVES);

    @BeforeAll
    static void initialize() {
        Block.init();
    }

    @Test
    void growthPutsAStraightTrunkAndOnlyItsOwnLeaves() {
        for (int leafId : LEAF_IDS) {
            for (int seed = 0; seed < 8; seed++) {
                World world = new World();
                Vector3 origin = new Vector3(0, ORIGIN_Y, 0);

                assertTrue(new ObjectPoplarTree(leafId).generate(world.manager, new NukkitRandom(seed), origin),
                        "seed " + seed + " leaf " + leafId);

                assertEquals(BlockID.POPLAR_LOG, world.idAt(0, ORIGIN_Y, 0), "trunk base must be a log");
                assertEquals(BlockID.DIRT, world.idAt(0, ORIGIN_Y - 1, 0), "trunk must sit on dirt");
                for (Map.Entry<Cell, Integer> entry : world.ids.entrySet()) {
                    Cell cell = entry.getKey();
                    int id = entry.getValue();
                    // 泥土只允许出现在树干正下方
                    int minY = cell.equals(new Cell(0, ORIGIN_Y - 1, 0)) ? ORIGIN_Y - 1 : ORIGIN_Y;
                    assertTrue(cell.y() >= minY && cell.y() <= ORIGIN_Y + MAX_HEIGHT,
                            "y out of envelope: " + cell + " = " + id);
                    assertTrue(id == BlockID.POPLAR_LOG || id == leafId || id == BlockID.DIRT,
                            "unexpected block at " + cell + ": " + id);
                }
                assertTrue(world.ids.containsValue(leafId), "seed " + seed + " placed no leaves");
            }
        }
    }

    @Test
    void foliageStaysAboveTheTrunkBase() {
        World world = new World();
        assertTrue(new ObjectPoplarTree(BlockID.RED_POPLAR_LEAVES)
                .generate(world.manager, new NukkitRandom(3), new Vector3(0, ORIGIN_Y, 0)));

        int lowestLeaf = Integer.MAX_VALUE;
        for (Map.Entry<Cell, Integer> entry : world.ids.entrySet()) {
            if (entry.getValue() == BlockID.RED_POPLAR_LEAVES) {
                lowestLeaf = Math.min(lowestLeaf, entry.getKey().y());
            }
        }
        assertTrue(lowestLeaf >= ORIGIN_Y + 3, "leaves started at y=" + lowestLeaf);
    }

    private record Cell(int x, int y, int z) {
    }

    /** 用 Mockito 桩出的最小 ChunkManager，只实现生成器用到的读写与高度范围。 */
    private static final class World {
        private final Map<Cell, Integer> ids = new HashMap<>();
        private final ChunkManager manager = mock(ChunkManager.class);

        World() {
            when(manager.getBlockIdAt(anyInt(), anyInt(), anyInt()))
                    .thenAnswer(invocation -> ids.getOrDefault(cell(invocation), BlockID.AIR));
            when(manager.getBlockDataAt(anyInt(), anyInt(), anyInt())).thenReturn(0);
            when(manager.getMinBlockY()).thenReturn(0);
            when(manager.getMaxBlockY()).thenReturn(255);
            org.mockito.Mockito.doAnswer(invocation -> {
                ids.put(new Cell(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)),
                        invocation.getArgument(3));
                return null;
            }).when(manager).setBlockAt(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        }

        int idAt(int x, int y, int z) {
            return ids.getOrDefault(new Cell(x, y, z), BlockID.AIR);
        }

        private static Cell cell(org.mockito.invocation.InvocationOnMock invocation) {
            return new Cell(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
        }
    }
}
