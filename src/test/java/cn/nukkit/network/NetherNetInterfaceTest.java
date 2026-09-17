package cn.nukkit.network;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.utils.serverconfig.category.NetherNetSettings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;

/**
 * 回归测试：Network.sendPacket 把同一个 ByteBuf 依次交给所有 AdvancedSourceInterface，
 * RakNet 的 pipeline 写入是唯一一次引用消耗；NetherNet 无写方，不得再 release，
 * 否则默认配置下的查询应答会双重释放（IllegalReferenceCountException 且应答丢失）。
 * <p>
 * Regression: Network.sendPacket fans one buffer out to every interface; RakNet's
 * pipeline write is its only refcount consumer, so this no-writer interface must not
 * release, or the query reply gets double-freed under default settings.
 */
class NetherNetInterfaceTest {

    @TempDir
    Path tempDir;

    private NetherNetInterface netherNet;

    @BeforeEach
    void setUp() {
        Server server = MockServer.get();
        lenient().when(server.getIp()).thenReturn("127.0.0.1");
        lenient().when(server.getPort()).thenReturn(0);
        lenient().when(server.getDataPath()).thenReturn(tempDir.toString());
        lenient().when(server.getMotd()).thenReturn("NetherNetInterfaceTest");

        this.netherNet = new NetherNetInterface(server, new NetherNetSettings());
    }

    @AfterEach
    void tearDown() {
        if (this.netherNet != null) {
            this.netherNet.shutdown();
        }
        MockServer.reset();
    }

    @Test
    @Timeout(30)
    void sendRawPacketLeavesTheSharedBufferUnconsumed() {
        ByteBuf payload = PooledByteBufAllocator.DEFAULT.ioBuffer(8);
        payload.writeLong(0L);
        try {
            this.netherNet.sendRawPacket(new InetSocketAddress("127.0.0.1", 1), payload);
            assertEquals(1, payload.refCnt(),
                    "sendRawPacket must not consume the buffer: Network.sendPacket hands it to every interface and RakNet's write is the single owner");
        } finally {
            payload.release();
        }
    }

    @Test
    void handshakeBudgetFollowsTheLoginTimeout() {
        assertEquals(25, NetherNetInterface.handshakeTimeoutSeconds(25_000), "default login timeout maps to the same handshake budget");
        assertEquals(60, NetherNetInterface.handshakeTimeoutSeconds(60_000), "raising the login timeout extends the handshake budget");
        assertEquals(1, NetherNetInterface.handshakeTimeoutSeconds(1), "sub-second values clamp to one second");
        assertEquals(30, NetherNetInterface.handshakeTimeoutSeconds(0), "a disabled login timeout falls back so abandoned handshakes are reaped");
        assertEquals(30, NetherNetInterface.handshakeTimeoutSeconds(-1));
    }
}
