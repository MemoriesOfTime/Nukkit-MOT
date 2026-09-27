package cn.nukkit.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetServerSignaling;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tel.schich.libdatachannel.*;

import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端：真实 libjuice 两端，服务端 ICE mux 绑回环内部端口，客户端只拿到改写后指向"RakNet 端口"的应答，
 * 全部媒体经 {@link NetherNetMediaRelay} 的回环 leg 往返，验证 ICE/DTLS/SCTP 打通且数据通道双向可用。
 * End to end with real libjuice on both sides: the server muxes on a loopback internal port, the
 * client only ever sees the rewritten answer pointing at the "RakNet port", and all media crosses
 * {@link NetherNetMediaRelay}'s loopback legs; ICE/DTLS/SCTP must complete and both channels carry data.
 */
class NetherNetSharedPortRelayIntegrationTest {

    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    private MultiThreadIoEventLoopGroup group;
    private Channel listener;
    private NetherNetMediaRelay relay;
    private PeerConnection server;
    private PeerConnection client;
    private int mediaPort;

    @BeforeEach
    void setUp() throws Exception {
        LibDataChannelArchDetect.initialize();
        this.group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        this.listener = new Bootstrap().group(this.group).channel(NioDatagramChannel.class)
                .handler(new ChannelInboundHandlerAdapter())
                .bind(new InetSocketAddress(LOOPBACK, 0)).syncUninterruptibly().channel();
        try (DatagramSocket probe = new DatagramSocket(new InetSocketAddress(LOOPBACK, 0))) {
            this.mediaPort = probe.getLocalPort();
        }
        this.relay = new NetherNetMediaRelay(this.mediaPort, 16, address -> false);
        this.relay.attach(this.listener);
    }

    @AfterEach
    void tearDown() {
        if (this.client != null) {
            this.client.close();
        }
        if (this.server != null) {
            this.server.close();
        }
        if (this.relay != null) {
            this.relay.shutdown();
        }
        if (this.listener != null) {
            this.listener.close().awaitUninterruptibly();
        }
        if (this.group != null) {
            this.group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(60)
    void dataChannelsOpenThroughTheRelay() throws Exception {
        int listenerPort = ((InetSocketAddress) this.listener.localAddress()).getPort();
        NetherNetUdpPorts ports = NetherNetUdpPorts.parse(listenerPort + ":" + this.mediaPort, new cn.nukkit.lang.BaseLang("eng"));
        assertNotNull(ports);
        assertTrue(ports.publishesOn(listenerPort));

        // 与 NetherNetInterface 共用端口时的接线一致：mux + 内部端口 + 回环绑定，应答改写走装饰器；
        // 对外端口跟随监听（即回环）的族别，IPv6 偏好下发布在 v6 一侧
        // Wired like NetherNetInterface in shared mode: mux + internal port + loopback bind, answers
        // through the decorator; the external port follows the listener's (the loopback's) family,
        // so under IPv6 preference it publishes on the v6 side
        boolean loopbackIsV6 = LOOPBACK instanceof Inet6Address;
        PeerConnectionConfiguration serverConfig = ports.peerConfig(this.relay.mediaEndpoint()).withDisableAutoNegotiation(true);
        assertEquals(LOOPBACK, serverConfig.bindAddress().orElseThrow());
        this.server = PeerConnection.createPeer(serverConfig);
        RecordingSignaling signaling = new RecordingSignaling();
        NetherNetServerSignaling decorated = this.relay.decorate(signaling, sdp -> NetherNetSharedPortSdp.rewriteAnswer(
                sdp, this.mediaPort, List.of(LOOPBACK),
                new NetherNetSharedPortSdp.ExternalPorts(loopbackIsV6 ? -1 : listenerPort, loopbackIsV6 ? listenerPort : -1)));
        CompletableFuture<String> offerForServer = new CompletableFuture<>();
        decorated.setNewConnectionHandler((connectionId, remoteNetworkId, payload, clientAddress, player) -> offerForServer.complete(payload));

        this.client = PeerConnection.createPeer(PeerConnectionConfiguration.DEFAULT
                .withBindAddress(LOOPBACK).withPortRangeBegin(40100).withPortRangeEnd(40110).withDisableAutoNegotiation(true));
        Side clientSide = observe(this.client);
        Side serverSide = observe(this.server);
        DataChannel reliable = this.client.createDataChannel("ReliableDataChannel");
        DataChannel unreliable = this.client.createDataChannel("UnreliableDataChannel");
        for (DataChannel channel : List.of(reliable, unreliable)) {
            channel.onMessage.register(DataChannelCallback.Message.handleText((c, text) -> clientSide.messages.add(c.label() + ":" + text)));
        }

        this.client.setLocalDescription("offer");
        clientSide.gathered.get(10, TimeUnit.SECONDS);
        signaling.handler.onConnect(1L, "peer", this.client.localDescription(), null, null);
        String strippedOffer = offerForServer.get(5, TimeUnit.SECONDS);
        assertFalse(strippedOffer.contains("a=candidate:"), "the server never learns the client's own candidates");

        this.server.setRemoteDescription(strippedOffer, SessionDescriptionType.OFFER);
        this.server.setLocalDescription("answer");
        serverSide.gathered.get(10, TimeUnit.SECONDS);
        decorated.sendFullSdp("peer", this.server.localDescription());
        String answer = signaling.lastSdp;
        assertNotNull(answer);
        assertTrue(answer.contains(" " + LOOPBACK.getHostAddress() + " " + listenerPort + " typ host"),
                "the client is pointed at the RakNet port: " + answer);
        assertFalse(answer.contains(" " + this.mediaPort + " typ host"), "the internal media port never leaks: " + answer);
        assertTrue(this.relay.isExpected(NetherNetSharedPortSdp.iceUfrag(answer)), "the answer's ufrag opens the relay gate");

        this.client.setRemoteDescription(answer, SessionDescriptionType.ANSWER);

        clientSide.connected.get(20, TimeUnit.SECONDS);
        serverSide.connected.get(20, TimeUnit.SECONDS);
        List<DataChannel> serverChannels = serverSide.channels.get(20, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while ((!reliable.isOpen() || !unreliable.isOpen()) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(reliable.isOpen() && unreliable.isOpen(), "both client channels report open");

        reliable.sendMessage("ping reliable");
        unreliable.sendMessage("ping unreliable");
        for (DataChannel channel : serverChannels) {
            channel.sendMessage("pong " + channel.label());
        }
        assertEquals(2, drain(serverSide.messages).size(), "the server received both pings");
        assertEquals(2, drain(clientSide.messages).size(), "the client received both pongs");

        // 服务端看到的对端是回环 leg，中继能换回真实客户端；客户端看到的是 RakNet 端口
        // The server's peer is the loopback leg, which the relay maps back; the client's peer is the RakNet port
        InetSocketAddress serverPeer = this.server.remoteAddress();
        InetSocketAddress mapped = this.relay.clientFor(new InetSocketAddress(serverPeer.getHostString(), serverPeer.getPort()));
        assertNotNull(mapped, "the server-side peer address is a relay leg: " + serverPeer);
        assertTrue(mapped.getPort() >= 40100 && mapped.getPort() <= 40110,
                "the leg maps back to the client's media socket in its configured window, got " + mapped);
        // 客户端从服务端的 STUN 应答学到的映射地址就是 leg，所以它的本地候选也变成了 leg 地址（prflx）
        // The mapped address the client learns from the server's STUN responses is the leg, so its
        // local candidate turned into the leg address (prflx)
        assertEquals(serverPeer.getPort(), this.client.localAddress().getPort());
        assertEquals(listenerPort, this.client.remoteAddress().getPort(), "the client talks to the RakNet port only");
        assertEquals(1, this.relay.legCount());
        NetherNetMediaRelay.Snapshot snapshot = this.relay.snapshot();
        assertTrue(snapshot.packetsIn() > 0 && snapshot.packetsOut() > 0, "media flowed both ways through the relay: " + snapshot);
    }

    private static List<String> drain(BlockingQueue<String> queue) throws InterruptedException {
        List<String> out = new java.util.ArrayList<>(2);
        for (int i = 0; i < 2; i++) {
            String message = queue.poll(10, TimeUnit.SECONDS);
            if (message != null) {
                out.add(message);
            }
        }
        return out;
    }

    private record Side(CompletableFuture<Void> gathered, CompletableFuture<Void> connected,
                        CompletableFuture<List<DataChannel>> channels, BlockingQueue<String> messages) {
    }

    private static Side observe(PeerConnection peer) {
        Side side = new Side(new CompletableFuture<>(), new CompletableFuture<>(), new CompletableFuture<>(), new LinkedBlockingQueue<>());
        List<DataChannel> received = new CopyOnWriteArrayList<>();
        peer.onGatheringStateChange.register((p, state) -> {
            if (state == GatheringState.RTC_GATHERING_COMPLETE) {
                side.gathered.complete(null);
            }
        });
        peer.onStateChange.register((p, state) -> {
            if (state == PeerState.RTC_CONNECTED) {
                side.connected.complete(null);
            } else if (state == PeerState.RTC_FAILED || state == PeerState.RTC_CLOSED) {
                side.connected.completeExceptionally(new IllegalStateException("peer " + state));
                side.channels.completeExceptionally(new IllegalStateException("peer " + state));
            }
        });
        peer.onDataChannel.register((p, channel) -> {
            channel.onMessage.register(DataChannelCallback.Message.handleText((c, text) -> side.messages.add(c.label() + ":" + text)));
            received.add(channel);
            if (received.size() == 2) {
                side.channels.complete(List.copyOf(received));
            }
        });
        return side;
    }

    private static final class RecordingSignaling implements NetherNetServerSignaling {
        NewConnectionHandler handler;
        String lastSdp;

        @Override
        public void sendFullSdp(String targetNetworkId, String sdp) {
            this.lastSdp = sdp;
        }

        @Override
        public void bind(SocketAddress localAddress, EventLoop eventLoop) {
        }

        @Override
        public void setNewConnectionHandler(NewConnectionHandler handler) {
            this.handler = handler;
        }

        @Override
        public void setAdvertisementData(PongData pongData) {
        }

        @Override
        public void setSignalHandler(long connectionId, SignalHandler handler) {
        }

        @Override
        public void removeSignalHandler(long connectionId) {
        }

        @Override
        public String getLocalNetworkId() {
            return "test";
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void close() {
        }
    }
}
