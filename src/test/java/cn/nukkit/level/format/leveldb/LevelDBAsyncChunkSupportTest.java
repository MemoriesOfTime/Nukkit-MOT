package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockStone;
import cn.nukkit.level.format.generic.BaseFullChunk;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

/**
 * LevelDB 异步区块加载持久化测试。
 * <p>
 * LevelDB async chunk-loading persistence tests.
 */
public class LevelDBAsyncChunkSupportTest {

    @BeforeAll
    public static void setUp() {
        MockServer.init();
    }

    @Test
    public void deferredBlockUpdatesAreSerializedBeforeChunkInitialization() {
        LevelDBProvider provider = Mockito.mock(LevelDBProvider.class, Mockito.CALLS_REAL_METHODS);
        BaseFullChunk.PendingBlockUpdate update = new BaseFullChunk.PendingBlockUpdate(
                new BlockStone(), 16, 64, 32, 5, 2);

        NbtMap ticks = provider.saveBlockTickingQueue(null, List.of(update), 100);

        Assertions.assertNotNull(ticks);
        Assertions.assertEquals(0, ticks.getInt("currentTick"));
        List<NbtMap> tickList = ticks.getList("tickList", NbtType.COMPOUND);
        Assertions.assertEquals(1, tickList.size());
        NbtMap saved = tickList.get(0);
        Assertions.assertEquals(16, saved.getInt("x"));
        Assertions.assertEquals(64, saved.getInt("y"));
        Assertions.assertEquals(32, saved.getInt("z"));
        Assertions.assertEquals(5L, saved.getLong("time"));
        Assertions.assertEquals(2, saved.getInt("p"));
    }
}
