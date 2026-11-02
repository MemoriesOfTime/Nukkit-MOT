package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockFence;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * 旧世界连接位迁移的区块扫描：fixLegacyBlockConnections 必须真的扫到非 (0,0) 区块里的目标方块。
 * 曾把 chunk 局部坐标传给期望世界坐标的 getBlockIdAt，守卫恒假，除 (0,0) 外所有区块空转
 * 扫描后照常清标志——旧世界栅栏/玻璃板/楼梯永不重算且不再重试。
 * <p>
 * Chunk scan of the legacy connection migration: fixLegacyBlockConnections must actually reach target
 * blocks in chunks other than (0,0). Chunk-local coords were once passed to getBlockIdAt (world coords),
 * so its guard failed and every chunk silently scanned nothing before clearing the flag — pre-1.26.50
 * fences/panes/stairs were never recomputed and never retried.
 */
class LevelLegacyConnectionFixTest {

    private static final int CHUNK_X = 3;
    private static final int CHUNK_Z = 7;
    private static final int FENCE_LOCAL_X = 5;
    private static final int FENCE_LOCAL_Z = 9;
    private static final int Y = 65;

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @Test
    void fenceNextToFenceGateInNonZeroChunkRegainsConnection() throws Exception {
        Chunk chunk = new Chunk(Anvil.class);
        chunk.setPosition(CHUNK_X, CHUNK_Z);
        // 旧世界内容：栅栏与栅栏门均无连接位（meta=0）
        // pre-1.26.50 content: neither fence nor gate carries connection bits (meta=0)
        chunk.setBlockAtLayer(FENCE_LOCAL_X, Y, FENCE_LOCAL_Z, 0, BlockID.FENCE, 0);
        chunk.setBlockAtLayer(FENCE_LOCAL_X, Y, FENCE_LOCAL_Z + 1, 0, BlockID.FENCE_GATE_OAK, 0);
        chunk.setNeedsLegacyConnectionFix(true);

        Assertions.assertEquals(BlockID.FENCE, chunk.getBlockId(FENCE_LOCAL_X, Y, FENCE_LOCAL_Z));
        Assertions.assertEquals(BlockID.FENCE_GATE_OAK, chunk.getBlockId(FENCE_LOCAL_X, Y, FENCE_LOCAL_Z + 1));

        Level level = Mockito.mock(Level.class, Mockito.CALLS_REAL_METHODS);
        Field serverField = Level.class.getDeclaredField("server");
        serverField.setAccessible(true);
        // final 字段在 mock（未跑构造器）上直接可写 / final field is writable on a mock (constructor never ran)
        serverField.set(level, Server.getInstance());
        List<Throwable> scanErrors = new ArrayList<>();
        cn.nukkit.utils.MainLogger logger = Server.getInstance().getLogger();
        Mockito.doAnswer(inv -> {
            if (inv.getArguments().length > 1 && inv.getArgument(1) instanceof Throwable t) {
                scanErrors.add(t);
            }
            return null;
        }).when(logger).error(Mockito.anyString(), Mockito.any(Throwable.class));

        // Anvil 区块段覆盖 Y 0..255，维度范围须与之配对 / anvil sections cover Y 0..255
        Mockito.doReturn(0).when(level).getMinBlockY();
        Mockito.doReturn(255).when(level).getMaxBlockY();
        Mockito.doAnswer(inv -> inv.getArgument(0).equals(CHUNK_X) && inv.getArgument(1).equals(CHUNK_Z)
                        ? chunk : populatedNeighbourMock())
                .when(level).getChunk(anyInt(), anyInt(), anyBoolean());
        Mockito.doAnswer(inv -> inv.getArgument(0).equals(CHUNK_X) && inv.getArgument(1).equals(CHUNK_Z)
                        ? chunk : populatedNeighbourMock())
                .when(level).getChunkIfLoaded(anyInt(), anyInt());

        List<Block> written = new ArrayList<>();
        Mockito.doAnswer(inv -> {
            Block reconnected = inv.getArgument(2);
            written.add(reconnected);
            chunk.setBlockAtLayer(reconnected.getFloorX() & 0xF, reconnected.getFloorY(),
                    reconnected.getFloorZ() & 0xF, 0, reconnected.getId(), reconnected.getDamage());
            return true;
        }).when(level).setBlock(Mockito.any(), Mockito.anyInt(), Mockito.any(Block.class),
                Mockito.anyBoolean(), Mockito.anyBoolean());

        // canConnect 必须认得栅栏门：手动重算应得到指向门的南向位
        // canConnect must recognize fence gates: a manual recompute derives the south bit
        Block raw = level.getBlock((CHUNK_X << 4) + FENCE_LOCAL_X, Y, (CHUNK_Z << 4) + FENCE_LOCAL_Z, 0);
        Assertions.assertInstanceOf(BlockFence.class, raw);
        Assertions.assertTrue(((BlockFence) raw).updateConnections());
        Assertions.assertEquals(BlockFence.FLAG_CONNECTION_SOUTH, raw.getDamage() & BlockFence.CONNECTION_FLAGS);

        Method fix = Level.class.getDeclaredMethod("fixLegacyBlockConnections", int.class, int.class, BaseFullChunk.class);
        fix.setAccessible(true);
        fix.invoke(level, CHUNK_X, CHUNK_Z, chunk);

        Assertions.assertTrue(scanErrors.isEmpty(), "scan must not throw: " + (scanErrors.isEmpty() ? "" : scanErrors.get(0)));
        Assertions.assertEquals(1, written.size(), "the fence must be written back once");
        Block fence = written.get(0);
        Assertions.assertInstanceOf(BlockFence.class, fence);
        Assertions.assertEquals(BlockFence.FLAG_CONNECTION_SOUTH, fence.getDamage() & BlockFence.CONNECTION_FLAGS,
                "only the south arm (towards the gate) may be set");
        Assertions.assertEquals(BlockFence.FLAG_CONNECTION_SOUTH,
                chunk.getBlockData(FENCE_LOCAL_X, Y, FENCE_LOCAL_Z) & BlockFence.CONNECTION_FLAGS,
                "write-back must land in the chunk");
        Assertions.assertFalse(chunk.isNeedsLegacyConnectionFix(), "flag clears after a successful scan");
    }

    /**
     * 四邻门控要求邻居已 populate：mock 默认 isPopulated()=false 会卡门控 / the four-neighbour
     * gate requires populated neighbours; a bare mock's isPopulated()=false would stall it.
     */
    private static BaseFullChunk populatedNeighbourMock() {
        BaseFullChunk neighbour = Mockito.mock(BaseFullChunk.class);
        Mockito.doReturn(true).when(neighbour).isPopulated();
        return neighbour;
    }
}
