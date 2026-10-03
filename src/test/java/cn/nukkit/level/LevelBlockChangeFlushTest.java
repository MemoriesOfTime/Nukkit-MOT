package cn.nukkit.level;

import cn.nukkit.MockServer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 方块变更刷新判定测试:变更数超过缓存上限时必须整块重发,不能因哨兵 size() 恒为 0 而静默丢弃更新。
 * <p>
 * Block-change flush tests: an over-cap chunk must trigger a whole-chunk resend, and must not be
 * silently dropped because the sentinel map always reports size 0.
 *
 * @author LT_Name
 */
public class LevelBlockChangeFlushTest {

    private Level newLevel() throws Exception {
        MockServer.init();

        Level level = Mockito.mock(Level.class, Mockito.CALLS_REAL_METHODS);
        // CALLS_REAL_METHODS 不执行构造器,手动初始化本测试路径依赖的字段
        // CALLS_REAL_METHODS skips constructors; init the fields this test path depends on
        setField(level, "changedBlocks", new Long2ObjectOpenHashMap<SoftReference<Map<Integer, Object>>>());
        setField(level, "changeBlocksPresent", new Object());
        setField(level, "changeBlocksFullMap", new Int2ObjectOpenHashMap<>());
        setField(level, "dimensionData", DimensionEnum.OVERWORLD.getDimensionData());
        return level;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static boolean shouldResendWholeChunk(Level level, Map<Integer, Object> blocks) throws Exception {
        Method method = Level.class.getDeclaredMethod("shouldResendWholeChunk", Map.class);
        method.setAccessible(true);
        return (boolean) method.invoke(level, blocks);
    }

    private static void addBlockChange(Level level, long index, int x, int y, int z) throws Exception {
        Method method = Level.class.getDeclaredMethod("addBlockChange", long.class, int.class, int.class, int.class);
        method.setAccessible(true);
        method.invoke(level, index, x, y, z);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Object> changedBlocksFor(Level level, long index) throws Exception {
        Long2ObjectOpenHashMap<SoftReference<Map<Integer, Object>>> changed =
                (Long2ObjectOpenHashMap<SoftReference<Map<Integer, Object>>>) getField(level, "changedBlocks");
        return changed.get(index).get();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void sentinelIsEmptySoSizeCannotDetectIt() throws Exception {
        Level level = newLevel();
        Map<Integer, Object> sentinel = (Map<Integer, Object>) getField(level, "changeBlocksFullMap");

        Assertions.assertEquals(0, sentinel.size(), "sentinel is never populated");
        Assertions.assertTrue(shouldResendWholeChunk(level, sentinel), "sentinel must be detected by identity, not size");
    }

    @Test
    public void collectedReferenceResendsWholeChunk() throws Exception {
        Assertions.assertTrue(shouldResendWholeChunk(newLevel(), null));
    }

    @Test
    public void smallChangeSetSendsIndividualBlocks() throws Exception {
        Level level = newLevel();
        Map<Integer, Object> blocks = new HashMap<>();
        for (int i = 0; i < Level.MAX_BLOCK_CACHE; i++) {
            blocks.put(i, new Object());
        }
        Assertions.assertFalse(shouldResendWholeChunk(level, blocks));
    }

    @Test
    public void overflowingChunkStillDeliversUpdates() throws Exception {
        Level level = newLevel();
        long index = Level.chunkHash(0, 0);
        // 每个 i 对应唯一的 (x, z, y),确保 localBlockHash 不重复
        // Each i maps to a unique (x, z, y) so localBlockHash values never collide
        for (int i = 0; i <= Level.MAX_BLOCK_CACHE + 1; i++) {
            addBlockChange(level, index, i & 15, i >> 8, (i >> 4) & 15);
        }

        Map<Integer, Object> blocks = changedBlocksFor(level, index);
        Assertions.assertSame(getField(level, "changeBlocksFullMap"), blocks, "over-cap chunk must switch to the sentinel");
        Assertions.assertTrue(shouldResendWholeChunk(level, blocks), "sentinel must resend the chunk, otherwise this tick's updates are lost");
    }
}
