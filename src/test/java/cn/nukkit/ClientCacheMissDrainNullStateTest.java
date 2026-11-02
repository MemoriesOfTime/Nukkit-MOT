package cn.nukkit;

import cn.nukkit.level.PlayerSubChunkRequestState;
import cn.nukkit.network.process.processor.common.ClientCacheBlobStatusProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * drain 路径的空缓存状态防御：积压仍在而 blobCacheState 为 null（mock/异常构造；生产中
 * clearBlobCacheState 会原子清空两者）时必须静默返回，不得走到 sendMissBatchWithinBudget
 * 的无守卫解引用——积压哈希在仓库有载荷时 sendable 恒 ≥1，state.resolve 必然触发。
 * <p>
 * Null-blob-cache-state defense on the drain path: when a backlog survives while
 * blobCacheState is null (mocks/abnormal construction; production clears both
 * atomically), the drain must return silently instead of reaching the unguarded
 * dereferences in sendMissBatchWithinBudget — with a servable backlog entry,
 * sendable is always >= 1 and state.resolve fires unconditionally.
 */
public class ClientCacheMissDrainNullStateTest {

    @Test
    public void drainWithNullBlobCacheStateSkipsSilently() {
        PlayerHandle handle = mock(PlayerHandle.class);
        PlayerSubChunkRequestState flow = new PlayerSubChunkRequestState();
        assertTrue(flow.tryDeferMissBatch(new long[]{0x1234567890abcdefL}));
        when(handle.getSubChunkRequestState()).thenReturn(flow);
        when(handle.getBlobCacheState()).thenReturn(null);

        assertDoesNotThrow(() -> ClientCacheBlobStatusProcessor.drainDeferredMissResponses(handle));
        assertEquals(1, flow.deferredMissHashCount(), "a null cache state skips the backlog without draining it");
    }
}
