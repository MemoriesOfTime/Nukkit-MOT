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
        Map<Long, Integer> light = new HashMap<>();
        Set<Long> sources = new HashSet<>();
        int y = 64;
        when(level.isYInRange(anyInt())).thenCallRealMethod();
        when(level.getMinBlockY()).thenReturn(-64);
        when(level.getMaxBlockY()).thenReturn(319);
        when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
        Map<Long, BaseFullChunk> chunks = new HashMap<>();
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) {
            int baseX = cx << 4, baseZ = cz << 4;
            BaseFullChunk chunk = mock(BaseFullChunk.class);
            chunks.put(Level.chunkHash(cx, cz), chunk);
            when(chunk.getBlockLight(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
                light.getOrDefault(Hash.hashBlock(baseX + (int) inv.getArgument(0), inv.getArgument(1), baseZ + (int) inv.getArgument(2)), 0));
            when(chunk.getBlockId(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
                sources.contains(Hash.hashBlock(baseX + (int) inv.getArgument(0), inv.getArgument(1), baseZ + (int) inv.getArgument(2))) ? BlockID.GLOWSTONE : BlockID.AIR);
            doAnswer(inv -> {
                long key = Hash.hashBlock(baseX + (int) inv.getArgument(0), inv.getArgument(1), baseZ + (int) inv.getArgument(2));
                int value = inv.getArgument(3);
                if (value == 0) light.remove(key); else light.put(key, value);
                return null;
            }).when(chunk).setBlockLight(anyInt(), anyInt(), anyInt(), anyInt());
        }
        when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(inv ->
            chunks.get(Level.chunkHash(inv.getArgument(0), inv.getArgument(1))));
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
