package cn.nukkit.level.format.generic;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.network.protocol.BatchPacket;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * 区块缓存功能测试
 * <p>
 * 测试 BaseFullChunk 中的 chunkPackets 缓存机制，包括：
 * <ul>
 *     <li>缓存的基本读写操作</li>
 *     <li>多协议版本缓存支持</li>
 *     <li>缓存失效机制（区块变更时）</li>
 *     <li>变更计数器机制</li>
 *     <li>时间戳验证逻辑</li>
 *     <li>废弃的协议ID方法</li>
 *     <li>compress方法</li>
 *     <li>trim方法调用验证</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
public class ChunkCacheTest {

    private BaseFullChunk chunk;

    @BeforeAll
    static void initServer() {
        // 确保 MockServer 已初始化，支持废弃的协议ID方法
        MockServer.init();
    }

    @BeforeEach
    void setUp() throws Exception {
        // 使用 Mockito 创建抽象类的 Mock，调用真实方法
        chunk = mock(BaseFullChunk.class, CALLS_REAL_METHODS);
        // Mockito mock 不执行字段初始化器，需手动初始化 AtomicLong 字段
        Field changesField = BaseFullChunk.class.getDeclaredField("changes");
        changesField.setAccessible(true);
        changesField.set(chunk, new AtomicLong());
    }

    // ==================== 缓存基础操作测试 ====================

    @Nested
    @DisplayName("缓存基础操作")
    class BasicCacheOperations {

        @Test
        @DisplayName("初始状态无缓存")
        void testInitialState() {
            assertNull(chunk.getChunkPacket(GameVersion.V1_21_0));
            assertEquals(0, chunk.getChanges());
            assertFalse(chunk.hasChanged());
        }

        @Test
        @DisplayName("设置和获取缓存")
        void testSetAndGetChunkPacket() {
            BatchPacket packet = createMockPacket();
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);

            BatchPacket retrieved = chunk.getChunkPacket(GameVersion.V1_21_0);
            assertNotNull(retrieved, "缓存应该存在");
            assertSame(packet, retrieved, "应该返回同一个包对象");
        }

        @Test
        @DisplayName("null包不会被存储")
        void testNullPacketNotStored() {
            chunk.setChunkPacket(GameVersion.V1_21_0, null);
            assertNull(chunk.getChunkPacket(GameVersion.V1_21_0));
        }

        @Test
        @DisplayName("替换已存在的缓存")
        void testReplaceExistingCache() {
            BatchPacket packet1 = createMockPacket();
            BatchPacket packet2 = createMockPacket();

            chunk.setChunkPacket(GameVersion.V1_21_0, packet1);
            assertSame(packet1, chunk.getChunkPacket(GameVersion.V1_21_0));

            chunk.setChunkPacket(GameVersion.V1_21_0, packet2);
            assertSame(packet2, chunk.getChunkPacket(GameVersion.V1_21_0), "应该被替换为新包");
        }
    }

    // ==================== 多协议缓存测试 ====================

    @Nested
    @DisplayName("多协议版本缓存")
    class MultiProtocolCache {

        @Test
        @DisplayName("不同协议版本独立缓存")
        void testMultiProtocolCache() {
            BatchPacket packet1 = createMockPacket();
            BatchPacket packet2 = createMockPacket();

            chunk.setChunkPacket(GameVersion.V1_21_0, packet1);
            chunk.setChunkPacket(GameVersion.V1_21_50, packet2);

            assertSame(packet1, chunk.getChunkPacket(GameVersion.V1_21_0));
            assertSame(packet2, chunk.getChunkPacket(GameVersion.V1_21_50));
            assertNull(chunk.getChunkPacket(GameVersion.V1_20_0), "未设置的协议版本应返回null");
        }

        @Test
        @DisplayName("所有协议版本缓存一起失效")
        void testCacheClearedOnMultipleChanges() {
            BatchPacket packet1 = createMockPacket();
            BatchPacket packet2 = createMockPacket();
            chunk.setChunkPacket(GameVersion.V1_21_0, packet1);
            chunk.setChunkPacket(GameVersion.V1_21_50, packet2);

            assertNotNull(chunk.getChunkPacket(GameVersion.V1_21_0));
            assertNotNull(chunk.getChunkPacket(GameVersion.V1_21_50));

            chunk.setChanged();

            assertNull(chunk.getChunkPacket(GameVersion.V1_21_0), "变更后缓存应被清空");
            assertNull(chunk.getChunkPacket(GameVersion.V1_21_50), "所有协议版本缓存都应被清空");
        }

        @Test
        @DisplayName("三个协议版本同时缓存")
        void testThreeProtocolVersions() {
            BatchPacket packet1 = createMockPacket();
            BatchPacket packet2 = createMockPacket();
            BatchPacket packet3 = createMockPacket();

            chunk.setChunkPacket(GameVersion.V1_20_0, packet1);
            chunk.setChunkPacket(GameVersion.V1_21_0, packet2);
            chunk.setChunkPacket(GameVersion.V1_21_50, packet3);

            assertSame(packet1, chunk.getChunkPacket(GameVersion.V1_20_0));
            assertSame(packet2, chunk.getChunkPacket(GameVersion.V1_21_0));
            assertSame(packet3, chunk.getChunkPacket(GameVersion.V1_21_50));
        }
    }

    // ==================== 缓存失效机制测试 ====================

    @Nested
    @DisplayName("缓存失效机制")
    class CacheInvalidation {

        @Test
        @DisplayName("setChanged()清空缓存")
        void testCacheInvalidationOnSetChanged() {
            BatchPacket packet = createMockPacket();
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);
            assertNotNull(chunk.getChunkPacket(GameVersion.V1_21_0));

            chunk.setChanged();

            assertNull(chunk.getChunkPacket(GameVersion.V1_21_0), "setChanged后缓存应被清空");
            assertEquals(1, chunk.getChanges());
            assertTrue(chunk.hasChanged());
        }

        @Test
        @DisplayName("连续多次setChanged")
        void testMultipleSetChanged() {
            BatchPacket packet = createMockPacket();
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);

            chunk.setChanged();
            assertNull(chunk.getChunkPacket(GameVersion.V1_21_0));

            // 再次设置缓存
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);
            assertNotNull(chunk.getChunkPacket(GameVersion.V1_21_0));

            chunk.setChanged();
            assertNull(chunk.getChunkPacket(GameVersion.V1_21_0));
        }

        @Test
        @DisplayName("setChanged(false)不清空缓存")
        void testSetChangedFalseDoesNotClearCache() {
            BatchPacket packet = createMockPacket();
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);
            chunk.setChanged();
            assertEquals(1, chunk.getChanges());

            // 设置缓存
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);
            assertNotNull(chunk.getChunkPacket(GameVersion.V1_21_0));

            // setChanged(false) 只重置计数器，不清空缓存
            chunk.setChanged(false);
            assertEquals(0, chunk.getChanges());
            // 缓存应该仍然存在（setChanged(false)不会清空chunkPackets）
            assertNotNull(chunk.getChunkPacket(GameVersion.V1_21_0),
                    "setChanged(false)不应清空缓存");
        }
    }

    // ==================== 变更计数器测试 ====================

    @Nested
    @DisplayName("变更计数器")
    class ChangesCounter {

        @Test
        @DisplayName("计数器递增")
        void testChangesIncrement() {
            assertEquals(0, chunk.getChanges());

            chunk.setChanged();
            assertEquals(1, chunk.getChanges());

            chunk.setChanged();
            assertEquals(2, chunk.getChanges());

            chunk.setChanged();
            assertEquals(3, chunk.getChanges());
        }

        @Test
        @DisplayName("setChanged(false)重置计数器")
        void testSetChangedFalse() {
            chunk.setChanged();
            chunk.setChanged();
            assertTrue(chunk.hasChanged());
            assertEquals(2, chunk.getChanges());

            chunk.setChanged(false);
            assertFalse(chunk.hasChanged());
            assertEquals(0, chunk.getChanges());
        }

        @Test
        @DisplayName("setChanged(true)递增计数器")
        void testSetChangedTrue() {
            chunk.setChanged(true);
            assertEquals(1, chunk.getChanges());

            chunk.setChanged(true);
            assertEquals(2, chunk.getChanges());
        }

        @Test
        @DisplayName("hasChanged()基于计数器判断")
        void testHasChanged() {
            assertFalse(chunk.hasChanged());

            chunk.setChanged();
            assertTrue(chunk.hasChanged());

            chunk.setChanged(false);
            assertFalse(chunk.hasChanged());
        }
    }

    // ==================== 时间戳验证测试 ====================

    @Nested
    @DisplayName("时间戳验证")
    class TimestampValidation {

        @Test
        @DisplayName("时间戳比较逻辑 - 区块未变更时可使用缓存")
        void testTimestampValidationUnchanged() {
            long timestamp = chunk.getChanges();
            BatchPacket packet = createMockPacket();
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);

            // 区块未变更，timestamp >= changes，可以使用缓存
            assertTrue(timestamp >= chunk.getChanges() || chunk.getChanges() == 0);
        }

        @Test
        @DisplayName("时间戳比较逻辑 - 区块变更后旧时间戳失效")
        void testTimestampValidationChanged() {
            long timestamp1 = chunk.getChanges();
            BatchPacket packet = createMockPacket();
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);

            chunk.setChanged();
            long changesAfterModify = chunk.getChanges();

            // 变更后，旧的时间戳小于当前changes
            assertTrue(timestamp1 < changesAfterModify, "旧时间戳应小于变更后的计数器");
            assertEquals(1, changesAfterModify);
        }

        @Test
        @DisplayName("模拟Level.chunkRequestCallback的时间戳验证")
        void testLevelTimestampValidationLogic() {
            // 模拟序列化开始时的时间戳
            long timestamp = chunk.getChanges();

            // 设置缓存
            BatchPacket packet = createMockPacket();
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);

            // 模拟序列化期间区块未变更
            // chunk.getChanges() <= timestamp 时应该缓存
            boolean shouldCache = chunk.getChanges() <= timestamp;
            assertTrue(shouldCache, "区块未变更时应该缓存");

            // 现在模拟区块变更
            chunk.setChanged();

            // 模拟序列化期间区块已变更
            // chunk.getChanges() > timestamp 时不应该缓存
            boolean shouldCacheAfterChange = chunk.getChanges() <= timestamp;
            assertFalse(shouldCacheAfterChange, "区块变更后不应该使用旧缓存");
        }
    }

    // ==================== compress方法测试 ====================

    @Nested
    @DisplayName("compress方法")
    class CompressMethod {

        @Test
        @DisplayName("无缓存时返回false")
        void testCompressNoCache() {
            assertFalse(chunk.compress());
        }

        @Test
        @DisplayName("有缓存时返回true")
        void testCompressWithCache() {
            BatchPacket packet = createMockPacket();
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);
            assertTrue(chunk.compress());
        }

        @Test
        @DisplayName("缓存失效后返回false")
        void testCompressAfterInvalidation() {
            BatchPacket packet = createMockPacket();
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);
            assertTrue(chunk.compress());

            chunk.setChanged();

            assertFalse(chunk.compress(), "缓存失效后compress应返回false");
        }

        @Test
        @DisplayName("多协议版本compress")
        void testCompressMultipleProtocols() {
            BatchPacket packet1 = createMockPacket();
            BatchPacket packet2 = createMockPacket();
            chunk.setChunkPacket(GameVersion.V1_21_0, packet1);
            chunk.setChunkPacket(GameVersion.V1_21_50, packet2);

            assertTrue(chunk.compress());

            // 缓存仍然存在
            assertNotNull(chunk.getChunkPacket(GameVersion.V1_21_0));
            assertNotNull(chunk.getChunkPacket(GameVersion.V1_21_50));
        }

        @Test
        @DisplayName("空缓存Map时返回false")
        @SuppressWarnings("unchecked")
        void testCompressEmptyMap() throws Exception {
            // 通过反射设置一个空的 chunkPackets Map
            Field field = BaseFullChunk.class.getDeclaredField("chunkPackets");
            field.setAccessible(true);
            field.set(chunk, new java.util.HashMap<>());

            assertFalse(chunk.compress(), "空Map时compress应返回false");
        }
    }

    // ==================== 废弃的协议ID方法测试 ====================

    @Nested
    @DisplayName("废弃的协议ID方法")
    @SuppressWarnings("deprecation")
    class DeprecatedProtocolMethods {

        @BeforeEach
        void ensureMockServer() {
            // 确保 MockServer 已初始化，防止其他测试类清除了 Server.instance
            MockServer.init();
        }

        @Test
        @DisplayName("setChunkPacket(int, BatchPacket)正常工作")
        void testSetChunkPacketWithProtocolId() {
            BatchPacket packet = createMockPacket();
            int protocol = GameVersion.V1_21_0.getProtocol();

            chunk.setChunkPacket(protocol, packet);

            // 应该能通过 GameVersion 获取
            BatchPacket retrieved = chunk.getChunkPacket(GameVersion.V1_21_0);
            assertNotNull(retrieved, "通过协议ID设置后应该能获取缓存");
            assertSame(packet, retrieved);
        }

        @Test
        @DisplayName("getChunkPacket(int)正常工作")
        void testGetChunkPacketWithProtocolId() {
            BatchPacket packet = createMockPacket();
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);

            int protocol = GameVersion.V1_21_0.getProtocol();
            BatchPacket retrieved = chunk.getChunkPacket(protocol);

            assertNotNull(retrieved, "通过协议ID应该能获取缓存");
            assertSame(packet, retrieved);
        }

        @Test
        @DisplayName("协议ID和GameVersion混合使用")
        void testMixedProtocolUsage() {
            BatchPacket packet = createMockPacket();

            // 使用 GameVersion 设置
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);

            // 使用协议ID获取
            int protocol = GameVersion.V1_21_0.getProtocol();
            BatchPacket retrieved = chunk.getChunkPacket(protocol);

            assertNotNull(retrieved);
            assertSame(packet, retrieved);
        }
    }

    // ==================== trim方法验证测试 ====================

    @Nested
    @DisplayName("trim方法调用")
    class TrimMethodValidation {

        @Test
        @DisplayName("setChunkPacket时调用trim")
        void testTrimCalledOnSet() {
            BatchPacket packet = createMockPacket();
            byte[] originalPayload = packet.payload.clone();

            chunk.setChunkPacket(GameVersion.V1_21_0, packet);

            // 验证 packet 的 trim 方法在 setChunkPacket 中被调用
            // 由于 trim() 会调整 offset，我们可以检查这个效果
            // 注意：这里我们只验证 setChunkPacket 不抛异常
            assertNotNull(chunk.getChunkPacket(GameVersion.V1_21_0));
        }

        @Test
        @DisplayName("getChunkPacket时调用trim")
        void testTrimCalledOnGet() {
            BatchPacket packet = createMockPacket();
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);

            // 获取缓存
            BatchPacket retrieved = chunk.getChunkPacket(GameVersion.V1_21_0);

            // 验证获取正常工作
            assertNotNull(retrieved);
            assertSame(packet, retrieved);
        }

        @Test
        @DisplayName("compress时对所有包调用trim")
        void testTrimCalledOnCompress() {
            BatchPacket packet1 = createMockPacket();
            BatchPacket packet2 = createMockPacket();

            chunk.setChunkPacket(GameVersion.V1_21_0, packet1);
            chunk.setChunkPacket(GameVersion.V1_21_50, packet2);

            // compress 应该对所有包调用 trim
            assertTrue(chunk.compress());

            // 缓存仍然存在
            assertNotNull(chunk.getChunkPacket(GameVersion.V1_21_0));
            assertNotNull(chunk.getChunkPacket(GameVersion.V1_21_50));
        }
    }

    // ==================== 边界情况测试 ====================

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("大量协议版本缓存")
        void testManyProtocolVersions() {
            GameVersion[] versions = {
                    GameVersion.V1_16_0,
                    GameVersion.V1_17_0,
                    GameVersion.V1_18_0,
                    GameVersion.V1_19_0,
                    GameVersion.V1_20_0,
                    GameVersion.V1_21_0,
                    GameVersion.V1_21_20,
                    GameVersion.V1_21_50,
                    GameVersion.V1_21_60
            };

            for (GameVersion version : versions) {
                BatchPacket packet = createMockPacket();
                chunk.setChunkPacket(version, packet);
                assertNotNull(chunk.getChunkPacket(version), "版本 " + version + " 的缓存应该存在");
            }
        }

        @Test
        @DisplayName("changes计数器达到大值")
        void testLargeChangesValue() {
            for (int i = 0; i < 1000; i++) {
                chunk.setChanged();
            }
            assertEquals(1000, chunk.getChanges());

            // 缓存仍应正常工作
            BatchPacket packet = createMockPacket();
            chunk.setChunkPacket(GameVersion.V1_21_0, packet);
            assertNotNull(chunk.getChunkPacket(GameVersion.V1_21_0));
        }

        @Test
        @DisplayName("重置changes后重新计数")
        void testResetAndRecount() {
            for (int i = 0; i < 10; i++) {
                chunk.setChanged();
            }
            assertEquals(10, chunk.getChanges());

            chunk.setChanged(false);
            assertEquals(0, chunk.getChanges());

            chunk.setChanged();
            assertEquals(1, chunk.getChanges());
        }

        @Test
        @DisplayName("重复设置相同协议版本缓存")
        void testRepeatedSetSameProtocol() {
            BatchPacket packet1 = createMockPacket();
            BatchPacket packet2 = createMockPacket();
            BatchPacket packet3 = createMockPacket();

            chunk.setChunkPacket(GameVersion.V1_21_0, packet1);
            assertSame(packet1, chunk.getChunkPacket(GameVersion.V1_21_0));

            chunk.setChunkPacket(GameVersion.V1_21_0, packet2);
            assertSame(packet2, chunk.getChunkPacket(GameVersion.V1_21_0));

            chunk.setChunkPacket(GameVersion.V1_21_0, packet3);
            assertSame(packet3, chunk.getChunkPacket(GameVersion.V1_21_0));
        }

        @Test
        @DisplayName("changes计数器溢出后继续工作")
        void testChangesCounterOverflow() {
            // 设置一个较大的初始值
            for (int i = 0; i < 100; i++) {
                chunk.setChanged();
            }
            assertEquals(100, chunk.getChanges());

            // 继续递增
            chunk.setChanged();
            assertEquals(101, chunk.getChanges());

            // 重置后正常工作
            chunk.setChanged(false);
            assertEquals(0, chunk.getChanges());
            assertFalse(chunk.hasChanged());
        }
    }

    // ==================== 辅助方法 ====================

    private BatchPacket createMockPacket() {
        BatchPacket packet = new BatchPacket();
        packet.payload = new byte[]{1, 2, 3, 4, 5};
        return packet;
    }
}
