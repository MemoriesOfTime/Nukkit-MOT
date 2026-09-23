package cn.nukkit.network;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.utils.serverconfig.category.NetherNetSettings;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    /** 与中继内部端口同一族别的回环地址（preferIPv6Addresses 下是 ::1）。 Loopback of the relay's family (::1 under preferIPv6Addresses). */
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

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
        // 构造器渲染本地化启动日志与媒体端口描述，统一在 setUp 提供语言
        // The constructor renders localized startup logs and media description, so setUp provides the language
        lenient().when(server.getLanguage()).thenReturn(new BaseLang("eng"));

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

    @Test
    void mediaUnreachableRequiresAttemptsFloorAndZeroIce() {
        assertTrue(NetherNetInterface.mediaUnreachable(snapshot(10, 0)),
                "attempts at the floor with zero ICE successes is the unreachable signature");
        assertFalse(NetherNetInterface.mediaUnreachable(snapshot(4, 0)),
                "few attempts may just be an idle server, not a fault");
        assertFalse(NetherNetInterface.mediaUnreachable(snapshot(10, 1)),
                "a single ICE success rules out the unreachable signature");
    }

    private static NetherNetTransportStats.TransportSnapshot snapshot(long peerConnecting, long iceConnected) {
        return new NetherNetTransportStats.TransportSnapshot(0, peerConnecting, iceConnected, 0, 0, 0, 0, 0, 0);
    }

    @Test
    @Timeout(30)
    void transportStatsAreWiredAndRenderLocalized() {
        Server server = MockServer.get();

        assertNotNull(this.netherNet.getTransportStats(), "the metrics instance is wired through the bootstrap");

        assertEquals(4, this.netherNet.buildStatusLines(10, true).size(), "full mode renders all four lines");
        var simple = this.netherNet.buildStatusLines(10, false);
        assertEquals(2, simple.size(), "simple mode renders two lines");
        assertTrue(simple.get(0).contains("NetherNet"), "the summary line renders through lang.ini, got: " + simple.get(0));
        assertTrue(simple.get(1).contains("ICE"), "the funnel line names ICE, got: " + simple.get(1));
    }

    @Test
    @Timeout(30)
    void configuredMediaPortStillStartsTheInterface() {
        Server server = MockServer.get();
        lenient().when(server.getPropertyString(eq("server-udp-ports"), anyString())).thenReturn("39000");
        NetherNetInterface pinned = new NetherNetInterface(server, new NetherNetSettings());
        // 媒体端口在对端接入时才随 peer connection 绑定，构造成功即验证解析与 bootstrap 选项接线无误
        // The media port binds with a peer connection, so constructing the interface already
        // proves the parsing and the bootstrap option wiring
        assertNotNull(pinned);
        pinned.shutdown();
    }

    @Test
    @Timeout(30)
    void configuredExternalMappingStillStartsTheInterface() {
        Server server = MockServer.get();
        lenient().when(server.getPropertyString(eq("server-udp-ports"), anyString())).thenReturn("203.0.113.10:19132:39000");
        NetherNetInterface mapped = new NetherNetInterface(server, new NetherNetSettings());
        assertNotNull(mapped);
        mapped.shutdown();
    }

    @Test
    @Timeout(30)
    void invalidMediaPortsAbortConstruction() {
        Server server = MockServer.get();
        lenient().when(server.getPropertyString(eq("server-udp-ports"), anyString())).thenReturn("70000");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new NetherNetInterface(server, new NetherNetSettings()));
        assertTrue(e.getMessage().contains("is not a valid port range"),
                "the abort carries the localized reason, got: " + e.getMessage());
    }

    @Test
    @Timeout(30)
    void mediaPortsCoveringServerPortAbortConstruction() {
        Server server = MockServer.get();
        lenient().when(server.getPropertyString(eq("server-udp-ports"), anyString())).thenReturn("19000-19200");
        lenient().when(server.getPort()).thenReturn(19132);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new NetherNetInterface(server, new NetherNetSettings()));
        assertTrue(e.getMessage().contains("server-port 19132"),
                "the abort names the RakNet-owned port, got: " + e.getMessage());
    }

    @Test
    @Timeout(30)
    void mediaPortsCoveringIpv6ListenerAbortConstruction() {
        Server server = MockServer.get();
        lenient().when(server.getPropertyString(eq("server-udp-ports"), anyString())).thenReturn("19000-19200");
        lenient().when(server.isIpv6Enabled()).thenReturn(true);
        lenient().when(server.getIpv6Port()).thenReturn(19133);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new NetherNetInterface(server, new NetherNetSettings()));
        assertTrue(e.getMessage().contains("IPv6 listener port 19133"),
                "the abort names the IPv6 listener port, got: " + e.getMessage());
    }

    @Test
    @Timeout(30)
    void sharedPortWindowIsRejected() {
        Server server = MockServer.get();
        lenient().when(server.getPropertyString(eq("server-udp-ports"), anyString())).thenReturn("19130-19140:39000-39010");
        lenient().when(server.getPort()).thenReturn(19132);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new NetherNetInterface(server, new NetherNetSettings()));
        assertTrue(e.getMessage().contains("single-port mapping such as 19132:39000"),
                "a window published over server-port must be narrowed to one port, got: " + e.getMessage());
    }

    @Test
    @Timeout(30)
    void sharedPortWindowCoveringOnlyTheIpv6ListenerIsRejected() {
        Server server = MockServer.get();
        lenient().when(server.getPropertyString(eq("server-udp-ports"), anyString())).thenReturn("19131-19135:39000-39004");
        lenient().when(server.isIpv6Enabled()).thenReturn(true);
        lenient().when(server.getIpv6Port()).thenReturn(19133);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new NetherNetInterface(server, new NetherNetSettings()));
        assertTrue(e.getMessage().contains("IPv6 listener port 19133"),
                "a window published over the IPv6 listener must be narrowed to one port, got: " + e.getMessage());
    }

    @Test
    @Timeout(30)
    void mappingOntoTheIpv6ListenerSharesIt() throws Exception {
        Server server = MockServer.get();
        int v4Port = freeTcpPort();
        int v6Port = freeTcpPort();
        int internal = freeUdpPort();
        lenient().when(server.getPort()).thenReturn(v4Port);
        lenient().when(server.isIpv6Enabled()).thenReturn(true);
        lenient().when(server.getIpv6Port()).thenReturn(v6Port);
        lenient().when(server.getPropertyString(eq("server-udp-ports"), anyString())).thenReturn(v6Port + ":" + internal);

        try (RakNetStandIn rakNet = new RakNetStandIn()) {
            NetherNetInterface shared = new NetherNetInterface(server, new NetherNetSettings(), rakNet.rakNet);
            try {
                assertNotNull(rakNet.listener.pipeline().get(NetherNetMediaRelay.HANDLER_NAME),
                        "a single-port mapping onto the IPv6 listener shares it like server-port");
                var lines = shared.buildStatusLines(10, true);
                assertEquals(5, lines.size(), "full mode gains the relay line");
                assertTrue(lines.get(0).contains("udp/" + v6Port + " shared with RakNet"),
                        "the summary names the shared IPv6 listener port, got: " + lines.get(0));
                assertEquals(internal, relayedPort(lines.get(0)), "the pinned internal port is the relay target");
                assertThrows(IOException.class, () -> bindUdp(internal).close(),
                        "the internal port is reserved until the first offer reaches the library");
            } finally {
                shared.shutdown();
            }
            rakNet.awaitDetached();
        }
    }

    @Test
    @Timeout(30)
    void plainIpv6ListenerPortSharesItWithAnAutoPickedInternalPort() throws Exception {
        Server server = MockServer.get();
        int v4Port = freeTcpPort();
        int v6Port = freeTcpPort();
        lenient().when(server.getPort()).thenReturn(v4Port);
        lenient().when(server.isIpv6Enabled()).thenReturn(true);
        lenient().when(server.getIpv6Port()).thenReturn(v6Port);
        lenient().when(server.getPropertyString(eq("server-udp-ports"), anyString())).thenReturn(String.valueOf(v6Port));

        try (RakNetStandIn rakNet = new RakNetStandIn()) {
            NetherNetInterface shared = new NetherNetInterface(server, new NetherNetSettings(), rakNet.rakNet);
            try {
                assertNotNull(rakNet.listener.pipeline().get(NetherNetMediaRelay.HANDLER_NAME),
                        "the bare IPv6 listener port shares like server-port does");
                var lines = shared.buildStatusLines(10, true);
                assertEquals(5, lines.size(), "full mode gains the relay line");
                assertTrue(lines.get(0).contains("udp/" + v6Port + " shared with RakNet"),
                        "the summary names the shared IPv6 listener port, got: " + lines.get(0));
                int internal = relayedPort(lines.get(0));
                assertNotEquals(v4Port, internal);
                assertThrows(IOException.class, () -> bindUdp(internal).close(),
                        "the picked port is reserved until the first offer reaches the library");
            } finally {
                shared.shutdown();
            }
            rakNet.awaitDetached();
        }
    }

    @Test
    @Timeout(30)
    void sharedPortWithoutARakNetListenerAbortsConstruction() {
        Server server = MockServer.get();
        lenient().when(server.getPropertyString(eq("server-udp-ports"), anyString())).thenReturn("19132:39000");
        lenient().when(server.getPort()).thenReturn(19132);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new NetherNetInterface(server, new NetherNetSettings(), null));
        assertTrue(e.getMessage().contains("server-port 19132"),
                "the abort explains there is nothing to relay from, got: " + e.getMessage());
    }

    @Test
    @Timeout(30)
    void sharedPortHooksTheRelayIntoTheRakNetListener() throws Exception {
        Server server = MockServer.get();
        // 信令绑定 TCP，媒体中继挂在 RakNet 的 UDP 监听上：两者用同一个探测到的空闲端口号
        // Signaling binds TCP and the relay hooks RakNet's UDP listener: both use one probed free port number
        int port = freeTcpPort();
        int internal = freeUdpPort();
        lenient().when(server.getPort()).thenReturn(port);
        lenient().when(server.getPropertyString(eq("server-udp-ports"), anyString())).thenReturn(port + ":" + internal);

        try (RakNetStandIn rakNet = new RakNetStandIn()) {
            NetherNetInterface shared = new NetherNetInterface(server, new NetherNetSettings(), rakNet.rakNet);
            try {
                assertNotNull(rakNet.listener.pipeline().get(NetherNetMediaRelay.HANDLER_NAME), "the relay demux sits on the RakNet listener");

                var lines = shared.buildStatusLines(10, true);
                assertEquals(5, lines.size(), "full mode gains the relay line while the port is shared");
                assertTrue(lines.get(0).contains("shared with RakNet"), "the summary describes the shared port, got: " + lines.get(0));
                assertEquals(internal, relayedPort(lines.get(0)), "the pinned internal port is the relay target");
                assertTrue(lines.get(4).contains("relay"), "the extra line reports the relay, got: " + lines.get(4));
                assertEquals(2, shared.buildStatusLines(10, false).size(), "simple mode stays at two lines");
                assertThrows(IOException.class, () -> bindUdp(internal).close(),
                        "the internal port is reserved until the first offer reaches the library");
            } finally {
                shared.shutdown();
            }
            rakNet.awaitDetached();
            assertNull(rakNet.listener.pipeline().get(NetherNetMediaRelay.HANDLER_NAME), "shutdown detaches the relay from RakNet");
            try (DatagramSocket free = bindUdp(internal)) {
                assertEquals(internal, free.getLocalPort(), "shutdown gives the reserved port back");
            }
        }
    }

    @Test
    @Timeout(30)
    void plainServerPortSharesItWithASystemAssignedInternalPort() throws Exception {
        Server server = MockServer.get();
        int port = freeTcpPort();
        lenient().when(server.getPort()).thenReturn(port);
        lenient().when(server.getPropertyString(eq("server-udp-ports"), anyString())).thenReturn(String.valueOf(port));

        try (RakNetStandIn rakNet = new RakNetStandIn()) {
            NetherNetInterface shared = new NetherNetInterface(server, new NetherNetSettings(), rakNet.rakNet);
            try {
                assertNotNull(rakNet.listener.pipeline().get(NetherNetMediaRelay.HANDLER_NAME), "server-port alone switches the relay on");
                var lines = shared.buildStatusLines(10, true);
                assertEquals(5, lines.size(), "full mode gains the relay line");
                assertTrue(lines.get(0).contains("shared with RakNet"), "the summary describes the shared port, got: " + lines.get(0));
                // 内部端口由系统在回环上分配：无固定窗口可耗尽，同机多实例天然错开
                // The internal port is OS-assigned on loopback: no fixed window to exhaust and
                // instances on one host spread out naturally
                int internal = relayedPort(lines.get(0));
                assertNotEquals(port, internal, "the internal port stays off the shared RakNet port");
                assertThrows(IOException.class, () -> bindUdp(internal).close(),
                        "the assigned port is reserved until the first offer reaches the library");
            } finally {
                shared.shutdown();
            }
        }
    }

    @Test
    @Timeout(30)
    void absentEntryDefaultsToTheServerPortAndSharesIt() throws Exception {
        Server server = MockServer.get();
        int port = freeTcpPort();
        lenient().when(server.getPort()).thenReturn(port);
        // 条目缺失时按代码内默认值回退：默认值必须是 server-port 本身，即默认共用
        // With the entry missing the in-code default applies: it must be server-port itself,
        // so the port is shared out of the box
        lenient().when(server.getPropertyString(eq("server-udp-ports"), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        try (RakNetStandIn rakNet = new RakNetStandIn()) {
            NetherNetInterface shared = new NetherNetInterface(server, new NetherNetSettings(), rakNet.rakNet);
            try {
                assertNotNull(rakNet.listener.pipeline().get(NetherNetMediaRelay.HANDLER_NAME),
                        "a missing entry falls back to server-port, switching the shared port on");
                var lines = shared.buildStatusLines(10, true);
                assertEquals(5, lines.size(), "full mode gains the relay line");
                assertTrue(lines.get(0).contains("shared with RakNet"), "the summary describes the shared port, got: " + lines.get(0));
                int internal = relayedPort(lines.get(0));
                assertThrows(IOException.class, () -> bindUdp(internal).close(),
                        "the picked internal port is reserved until the first offer reaches the library");
            } finally {
                shared.shutdown();
            }
            rakNet.awaitDetached();
        }
    }

    @Test
    @Timeout(30)
    void pinnedInternalPortAlreadyInUseAbortsConstruction() throws Exception {
        Server server = MockServer.get();
        int port = freeTcpPort();
        lenient().when(server.getPort()).thenReturn(port);
        try (DatagramSocket taken = bindUdp(0); RakNetStandIn rakNet = new RakNetStandIn()) {
            lenient().when(server.getPropertyString(eq("server-udp-ports"), anyString())).thenReturn(port + ":" + taken.getLocalPort());
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new NetherNetInterface(server, new NetherNetSettings(), rakNet.rakNet));
            assertTrue(e.getMessage().contains("already in use"),
                    "the abort names the occupied internal port instead of failing at the first join, got: " + e.getMessage());
            assertNull(rakNet.listener.pipeline().get(NetherNetMediaRelay.HANDLER_NAME), "nothing was attached before the abort");
        }
    }

    private static int freeTcpPort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return probe.getLocalPort();
        }
    }

    private static int freeUdpPort() throws IOException {
        try (DatagramSocket probe = bindUdp(0)) {
            return probe.getLocalPort();
        }
    }

    private static DatagramSocket bindUdp(int port) throws IOException {
        return new DatagramSocket(new InetSocketAddress(LOOPBACK, port));
    }

    /** 状态摘要里"relayed to loopback udp/N"的 N。 The N in the summary's "relayed to loopback udp/N". */
    private static int relayedPort(String summary) {
        Matcher matcher = Pattern.compile("relayed to loopback udp/(\\d+)").matcher(summary);
        assertTrue(matcher.find(), "the summary names the relay target, got: " + summary);
        return Integer.parseInt(matcher.group(1));
    }

    /** 一个真实的回环 UDP 监听充当 RakNet 的 datagram channel。 A real loopback UDP listener standing in for RakNet's datagram channel. */
    private static final class RakNetStandIn implements AutoCloseable {
        final MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        final Channel listener;
        final RakNetInterface rakNet = mock(RakNetInterface.class);

        RakNetStandIn() {
            this.listener = new Bootstrap().group(this.group).channel(NioDatagramChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(LOOPBACK, 0)).syncUninterruptibly().channel();
            when(this.rakNet.getDatagramChannels()).thenReturn(List.of(this.listener));
        }

        void awaitDetached() throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (this.listener.pipeline().get(NetherNetMediaRelay.HANDLER_NAME) != null && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
        }

        @Override
        public void close() {
            this.listener.close().awaitUninterruptibly();
            this.group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
        }
    }
}
