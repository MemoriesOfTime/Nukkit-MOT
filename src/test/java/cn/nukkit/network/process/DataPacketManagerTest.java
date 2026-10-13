package cn.nukkit.network.process;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.network.process.processor.common.ItemStackRequestProcessor;
import cn.nukkit.network.process.processor.common.MoveEntityAbsoluteProcessor;
import cn.nukkit.network.protocol.ItemStackRequestPacket;
import cn.nukkit.network.protocol.MoveEntityAbsolutePacket;
import cn.nukkit.network.protocol.netease.PyRpcPacket;
import cn.nukkit.network.protocol.netease.SyncSkinPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataPacketManagerTest {

    @BeforeAll
    static void setup() {
        MockServer.init();
        DataPacketManager.registerDefaultProcessors();
    }

    @Test
    void itemStackRequestProcessorStartsAtV116100() {
        assertFalse(DataPacketManager.canProcess(GameVersion.V1_16_0, ItemStackRequestPacket.class));
        assertFalse(DataPacketManager.canProcess(GameVersion.V1_16_100_52, ItemStackRequestPacket.class));
        assertTrue(DataPacketManager.canProcess(GameVersion.V1_16_100, ItemStackRequestPacket.class));
    }

    @Test
    void netEaseProcessorsAreIsolatedFromStandardClients() {
        // 标准客户端不应解析网易专属包处理器（即使协议号已达到门槛）
        assertFalse(DataPacketManager.canProcess(GameVersion.V1_21_93, SyncSkinPacket.class));
        assertFalse(DataPacketManager.canProcess(GameVersion.V1_21_130, SyncSkinPacket.class));
        assertFalse(DataPacketManager.canProcess(GameVersion.V1_20_50, PyRpcPacket.class));
        assertFalse(DataPacketManager.canProcess(GameVersion.V1_26_50, PyRpcPacket.class));

        // 网易客户端按门槛正常解析
        assertTrue(DataPacketManager.canProcess(GameVersion.V1_21_93_NETEASE, SyncSkinPacket.class));
        assertFalse(DataPacketManager.canProcess(GameVersion.V1_21_50_NETEASE, SyncSkinPacket.class));
        assertTrue(DataPacketManager.canProcess(GameVersion.V1_20_50_NETEASE, PyRpcPacket.class));

        // 通用包对网易客户端不受影响；协议区间门控在网易空间同样生效
        assertTrue(DataPacketManager.canProcess(GameVersion.V1_21_130_NETEASE, ItemStackRequestPacket.class));
        assertTrue(DataPacketManager.canProcess(GameVersion.V1_20_50_NETEASE, ItemStackRequestPacket.class));
        assertTrue(DataPacketManager.canProcess(GameVersion.V1_20_50_NETEASE, MoveEntityAbsolutePacket.class));
        assertFalse(DataPacketManager.canProcess(GameVersion.V1_21_93_NETEASE, MoveEntityAbsolutePacket.class));
    }

    @Test
    void negativeCacheDoesNotLeakAcrossNetEaseAndStandard() {
        // 标准 898 先查（写入标准桶负缓存），网易 898 解析不受污染
        assertFalse(DataPacketManager.canProcess(GameVersion.V1_21_130, SyncSkinPacket.class));
        assertTrue(DataPacketManager.canProcess(GameVersion.V1_21_130_NETEASE, SyncSkinPacket.class));

        // 反向：网易 630 先查，标准 630 的通用包解析不受影响
        assertFalse(DataPacketManager.canProcess(GameVersion.V1_20_50_NETEASE, SyncSkinPacket.class));
        assertFalse(DataPacketManager.canProcess(GameVersion.V1_20_50, PyRpcPacket.class));
        assertTrue(DataPacketManager.canProcess(GameVersion.V1_20_50, ItemStackRequestPacket.class));
    }

    @SuppressWarnings("deprecation")
    @Test
    void deprecatedIntBridgeMatchesGameVersionPath() {
        // 弃用 int 桥接对协议纯比较的处理器在全部版本（含网易）判定一致；
        // 网易门控处理器（SyncSkin/PyRpc）在混合模式下按标准空间解析，属已文档化的桥接限制
        for (GameVersion version : GameVersion.getValues()) {
            assertEquals(ItemStackRequestProcessor.INSTANCE.isSupported(version),
                    ItemStackRequestProcessor.INSTANCE.isSupported(version.getProtocol()), version::toString);
            assertEquals(MoveEntityAbsoluteProcessor.INSTANCE.isSupported(version),
                    MoveEntityAbsoluteProcessor.INSTANCE.isSupported(version.getProtocol()), version::toString);
        }
    }
}
