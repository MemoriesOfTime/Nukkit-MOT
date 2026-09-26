package cn.nukkit.network.process;

import cn.nukkit.PlayerHandle;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ItemStackRequestPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataPacketManagerTest {

    @Test
    void itemStackRequestProcessorStartsAtV116100() {
        DataPacketManager.registerDefaultProcessors();

        assertFalse(DataPacketManager.canProcess(ProtocolInfo.v1_16_0, ItemStackRequestPacket.class));
        assertFalse(DataPacketManager.canProcess(ProtocolInfo.v1_16_100_52, ItemStackRequestPacket.class));
        assertTrue(DataPacketManager.canProcess(ProtocolInfo.v1_16_100, ItemStackRequestPacket.class));
    }

    @Test
    void resolutionCacheInvalidatedByRegistration() {
        DataPacketManager.registerDefaultProcessors();

        // 先在未注册的包上建立负结果缓存
        assertFalse(DataPacketManager.canProcess(ProtocolInfo.v1_2_0, CacheTestPacket.class));
        // 再注册处理器：缓存须整体失效并解析到新处理器，而不是返回旧负结果
        DataPacketManager.registerProcessor(ProtocolInfo.v1_2_0, new CacheTestProcessor());
        assertTrue(DataPacketManager.canProcess(ProtocolInfo.v1_2_0, CacheTestPacket.class));
    }

    private static class CacheTestPacket extends DataPacket {
        @Override
        public void encode() {
        }

        @Override
        public void decode() {
        }

        @Override
        public byte pid() {
            return 0;
        }
    }

    private static class CacheTestProcessor extends DataPacketProcessor<CacheTestPacket> {
        @Override
        public void handle(PlayerHandle playerHandle, CacheTestPacket pk) {
        }

        @Override
        public int getPacketId() {
            return -255; // 测试专用，不与真实包 ID 冲突
        }

        @Override
        public Class<? extends DataPacket> getPacketClass() {
            return CacheTestPacket.class;
        }
    }
}
