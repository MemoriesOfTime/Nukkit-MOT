package cn.nukkit.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 共用端口中继：RFC 7983 分流、ufrag 门禁、回环 leg 往返与地址映射，用真实回环 socket 验证。
 * Shared-port relay: RFC 7983 demux, the ufrag gate, loopback leg round trips and address
 * mapping, exercised over real loopback sockets.
 */
class NetherNetMediaRelayTest {

    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();
    private static final int STUN_MAGIC_COOKIE = 0x2112A442;

    private MultiThreadIoEventLoopGroup group;
    private Channel listener;
    private DatagramSocket media;
    private DatagramSocket client;
    private NetherNetMediaRelay relay;
    /** 分流器放行给"RakNet"的数据报。 Datagrams the demux passed on to "RakNet". */
    private final BlockingQueue<byte[]> passedThrough = new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() throws Exception {
        this.group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        this.listener = new Bootstrap()
                .group(this.group)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        channel.pipeline().addLast("raknet-stand-in", new SimpleChannelInboundHandler<DatagramPacket>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                                passedThrough.add(bytes(packet.content()));
                            }
                        });
                    }
                })
                .bind(new InetSocketAddress(LOOPBACK, 0))
                .syncUninterruptibly()
                .channel();
        this.media = new DatagramSocket(new InetSocketAddress(LOOPBACK, 0));
        this.media.setSoTimeout(3000);
        this.client = new DatagramSocket(new InetSocketAddress(LOOPBACK, 0));
        this.client.setSoTimeout(3000);
        this.relay = new NetherNetMediaRelay(this.media.getLocalPort(), 4, address -> false,
                TimeUnit.MILLISECONDS.toNanos(200));
        this.relay.attach(this.listener);
    }

    @AfterEach
    void tearDown() {
        if (this.relay != null) {
            this.relay.shutdown();
        }
        if (this.listener != null) {
            this.listener.close().awaitUninterruptibly();
        }
        if (this.media != null) {
            this.media.close();
        }
        if (this.client != null) {
            this.client.close();
        }
        if (this.group != null) {
            this.group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void classifiesByFirstByteAndMagicCookie() {
        assertEquals(NetherNetMediaRelay.Kind.STUN, NetherNetMediaRelay.classify(Unpooled.wrappedBuffer(stunBindingRequest("srv:cli"))));
        byte[] rakNetPing = new byte[25];
        rakNetPing[0] = 0x01;
        assertEquals(NetherNetMediaRelay.Kind.OTHER, NetherNetMediaRelay.classify(Unpooled.wrappedBuffer(rakNetPing)),
                "a RakNet unconnected ping shares STUN's first byte but carries no magic cookie");
        byte[] dtls = new byte[13];
        dtls[0] = 0x16;
        assertEquals(NetherNetMediaRelay.Kind.DTLS, NetherNetMediaRelay.classify(Unpooled.wrappedBuffer(dtls)));
        byte[] rakNetDatagram = new byte[10];
        rakNetDatagram[0] = (byte) 0x84;
        assertEquals(NetherNetMediaRelay.Kind.OTHER, NetherNetMediaRelay.classify(Unpooled.wrappedBuffer(rakNetDatagram)));
        assertEquals(NetherNetMediaRelay.Kind.OTHER, NetherNetMediaRelay.classify(Unpooled.EMPTY_BUFFER));
        byte[] shortStun = new byte[8];
        assertEquals(NetherNetMediaRelay.Kind.OTHER, NetherNetMediaRelay.classify(Unpooled.wrappedBuffer(shortStun)),
                "anything shorter than a STUN header cannot be STUN");
    }

    @Test
    void readsTheLocalUfragOutOfABindingRequest() {
        assertEquals("srv", NetherNetMediaRelay.stunLocalUfrag(Unpooled.wrappedBuffer(stunBindingRequest("srv:cli"))));
        assertNull(NetherNetMediaRelay.stunLocalUfrag(Unpooled.wrappedBuffer(stunBindingRequest("nocolon"))));
        byte[] response = stunBindingRequest("srv:cli");
        response[0] = 0x01; // Binding Success Response 0x0101
        assertNull(NetherNetMediaRelay.stunLocalUfrag(Unpooled.wrappedBuffer(response)), "only requests open a leg");
        byte[] truncated = Arrays.copyOf(stunBindingRequest("srv:cli"), 22);
        assertNull(NetherNetMediaRelay.stunLocalUfrag(Unpooled.wrappedBuffer(truncated)), "a truncated attribute block is rejected");
    }

    @Test
    @Timeout(30)
    void relaysBothWaysOverALoopbackLegAndMapsTheClientBack() throws Exception {
        this.relay.expectUfrag("srv");
        byte[] request = stunBindingRequest("srv:cli");
        send(this.client, request, this.listener.localAddress());

        java.net.DatagramPacket atMedia = receive(this.media);
        assertArrayEquals(request, Arrays.copyOf(atMedia.getData(), atMedia.getLength()), "the payload reaches libjuice untouched");
        assertTrue(atMedia.getAddress().isLoopbackAddress(), "libjuice sees a loopback leg as the peer");
        assertNotEquals(this.client.getLocalPort(), atMedia.getPort(), "the leg has its own port");
        assertEquals(1, this.relay.legCount());

        InetSocketAddress legAddress = new InetSocketAddress(atMedia.getAddress(), atMedia.getPort());
        assertEquals(new InetSocketAddress(LOOPBACK, this.client.getLocalPort()), this.relay.clientFor(legAddress),
                "the leg address maps back to the real client");
        assertNull(this.relay.clientFor(new InetSocketAddress("203.0.113.5", 40000)), "non-loopback addresses are never legs");

        byte[] reply = new byte[40];
        reply[0] = 0x16; // a DTLS record heading back
        this.media.send(new java.net.DatagramPacket(reply, reply.length, legAddress));
        java.net.DatagramPacket atClient = receive(this.client);
        assertArrayEquals(reply, Arrays.copyOf(atClient.getData(), atClient.getLength()));
        assertEquals(((InetSocketAddress) this.listener.localAddress()).getPort(), atClient.getPort(),
                "replies leave through the RakNet socket, so the client sees the shared port");

        // 已有 leg 的来源后续 DTLS 直接转发 Follow-up DTLS from a known source rides the same leg
        byte[] dtls = new byte[30];
        dtls[0] = 0x17;
        send(this.client, dtls, this.listener.localAddress());
        java.net.DatagramPacket dtlsAtMedia = receive(this.media);
        assertEquals(legAddress.getPort(), dtlsAtMedia.getPort(), "the same leg carries the whole session");
        assertEquals(1, this.relay.legCount());

        NetherNetMediaRelay.Snapshot snapshot = this.relay.snapshot();
        assertEquals(2, snapshot.packetsIn());
        assertEquals(1, snapshot.packetsOut());
        assertEquals(1, snapshot.legsOpened());
        assertTrue(this.passedThrough.isEmpty(), "nothing NetherNet-shaped reaches RakNet");
    }

    @Test
    @Timeout(30)
    void gatesNewLegsOnAnExpectedUfragAndDropsStrays() throws Exception {
        this.relay.expectUfrag("srv");
        send(this.client, stunBindingRequest("stranger:cli"), this.listener.localAddress());
        byte[] dtls = new byte[30];
        dtls[0] = 0x16;
        send(this.client, dtls, this.listener.localAddress());
        assertThrows(SocketTimeoutException.class, () -> {
            this.media.setSoTimeout(300);
            this.media.receive(new java.net.DatagramPacket(new byte[64], 64));
        }, "neither an unknown ufrag nor DTLS from an unknown source opens a leg");
        assertEquals(0, this.relay.legCount());
        NetherNetMediaRelay.Snapshot snapshot = this.relay.snapshot();
        assertEquals(1, snapshot.rejectedUfrag());
        assertEquals(1, snapshot.droppedNoLeg());
    }

    @Test
    @Timeout(30)
    void passesRakNetDatagramsDownThePipeline() throws Exception {
        byte[] ping = new byte[25];
        ping[0] = 0x01;
        ping[9] = (byte) 0xff; // RakNet magic starts here, no STUN cookie at offset 4
        send(this.client, ping, this.listener.localAddress());
        byte[] passed = this.passedThrough.poll(3, TimeUnit.SECONDS);
        assertNotNull(passed, "a RakNet-shaped datagram continues to the next handler");
        assertArrayEquals(ping, passed);
        assertEquals(0, this.relay.legCount());
    }

    @Test
    @Timeout(30)
    void reapsIdleLegsButKeepsTheUfragWindow() throws Exception {
        this.relay.expectUfrag("srv");
        send(this.client, stunBindingRequest("srv:cli"), this.listener.localAddress());
        receive(this.media);
        assertEquals(1, this.relay.legCount());

        Thread.sleep(300);
        this.relay.sweep();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (this.relay.legCount() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(0, this.relay.legCount(), "an idle leg is closed by the sweep");
        assertTrue(this.relay.isExpected("srv"), "the ufrag window outlives one idle leg");
    }

    @Test
    @Timeout(30)
    void shutdownDetachesTheDemuxAndClosesLegs() throws Exception {
        this.relay.expectUfrag("srv");
        send(this.client, stunBindingRequest("srv:cli"), this.listener.localAddress());
        receive(this.media);
        this.relay.shutdown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((this.relay.legCount() > 0 || this.listener.pipeline().get(NetherNetMediaRelay.HANDLER_NAME) != null)
                && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(0, this.relay.legCount());
        assertNull(this.listener.pipeline().get(NetherNetMediaRelay.HANDLER_NAME), "the demux leaves the RakNet pipeline");

        // 卸载后 STUN 也只是普通数据报，落到 RakNet 手里 Once detached STUN is just another datagram for RakNet
        byte[] request = stunBindingRequest("srv:cli");
        send(this.client, request, this.listener.localAddress());
        assertArrayEquals(request, this.passedThrough.poll(3, TimeUnit.SECONDS));
    }

    /** RFC 5389 Binding Request，仅带 USERNAME 属性。 An RFC 5389 Binding Request carrying just USERNAME. */
    static byte[] stunBindingRequest(String username) {
        byte[] user = username.getBytes(StandardCharsets.UTF_8);
        int padded = (user.length + 3) & ~3;
        ByteBuffer buffer = ByteBuffer.allocate(20 + 4 + padded);
        buffer.putShort((short) 0x0001);
        buffer.putShort((short) (4 + padded));
        buffer.putInt(STUN_MAGIC_COOKIE);
        buffer.put(new byte[12]);
        buffer.putShort((short) 0x0006);
        buffer.putShort((short) user.length);
        buffer.put(user);
        buffer.put(new byte[padded - user.length]);
        return buffer.array();
    }

    private static void send(DatagramSocket socket, byte[] payload, java.net.SocketAddress to) throws IOException {
        socket.send(new java.net.DatagramPacket(payload, payload.length, to));
    }

    private static java.net.DatagramPacket receive(DatagramSocket socket) throws IOException {
        java.net.DatagramPacket packet = new java.net.DatagramPacket(new byte[2048], 2048);
        socket.receive(packet);
        return packet;
    }

    private static byte[] bytes(ByteBuf buf) {
        byte[] out = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), out);
        return out;
    }
}
