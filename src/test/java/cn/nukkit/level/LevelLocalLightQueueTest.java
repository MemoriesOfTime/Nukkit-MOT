package cn.nukkit.level;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.utils.Hash;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LevelLocalLightQueueTest {
    @Test
    void removingOneSourcePreservesTheOtherAndRemovingBothClearsTheLight() {
        Block.init();
        Level level = mock(Level.class);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        Map<Long, Integer> light = new HashMap<>();
        Set<Long> sources = new HashSet<>();
        int y = 64;
        when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
        when(level.getChunk(anyInt(), anyInt(), eq(false))).thenReturn(chunk);
        when(level.getBlockIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
            sources.contains(Hash.hashBlock(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2))) ? BlockID.GLOWSTONE : BlockID.AIR);
        when(level.getBlockLightAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
            light.getOrDefault(Hash.hashBlock(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)), 0));
        doAnswer(inv -> { long key = Hash.hashBlock(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2));
            int value = inv.getArgument(3); if (value == 0) light.remove(key); else light.put(key, value); return null;
        }).when(level).setBlockLightAt(anyInt(), anyInt(), anyInt(), anyInt());
        when(chunk.getBlockLight(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
            light.getOrDefault(Hash.hashBlock(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)), 0));
        when(chunk.getBlockId(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
            sources.contains(Hash.hashBlock(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2))) ? BlockID.GLOWSTONE : BlockID.AIR);
        doAnswer(inv -> { level.setBlockLightAt(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)); return null;
        }).when(chunk).setBlockLight(anyInt(), anyInt(), anyInt(), anyInt());
        doCallRealMethod().when(level).updateBlockLight(anyMap());
        sources.add(Hash.hashBlock(7, y, 7)); sources.add(Hash.hashBlock(9, y, 7));
        update(level, 7, y, 7); update(level, 9, y, 7);
        assertEquals(15, light.get(Hash.hashBlock(7, y, 7)));
        assertEquals(15, light.get(Hash.hashBlock(9, y, 7)));
        assertEquals(14, light.get(Hash.hashBlock(8, y, 7)));
        sources.remove(Hash.hashBlock(7, y, 7)); update(level, 7, y, 7);
        assertEquals(15, light.get(Hash.hashBlock(9, y, 7)));
        assertEquals(14, light.get(Hash.hashBlock(8, y, 7)));
        assertEquals(13, light.get(Hash.hashBlock(7, y, 7)));
        sources.clear(); update(level, 9, y, 7);
        assertTrue(light.isEmpty(), "removal FIFO must keep each position paired with its old light level");
    }
    private static void update(Level level, int x, int y, int z) {
        Map<Long, Set<Integer>> changes = new LinkedHashMap<>();
        changes.put(Level.chunkHash(x >> 4, z >> 4), new HashSet<>(Set.of(Level.localBlockHash(x, y, z, DimensionEnum.OVERWORLD.getDimensionData()))));
        level.updateBlockLight(changes);
        assertTrue(changes.isEmpty());
    }
}
