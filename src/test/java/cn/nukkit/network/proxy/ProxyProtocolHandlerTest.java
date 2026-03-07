package cn.nukkit.network.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ProxyProtocolHandlerTest {

    private static final byte[] PP_V2_SIGNATURE = {
            0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };

    private static int randomClientPort() {
        return ThreadLocalRandom.current().nextInt(1024, 65536);
    }

    @Test
    public void testCachedSenderWithProxyHeaderIsDropped() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);
        InetAddress realIp = InetAddress.getByName("8.8.8.8");
        int realPort = randomClientPort();

        channel.writeInbound(createProxyV2Datagram(proxyAddress, recipient, realIp, realPort, new byte[]{0x01, 0x02}));
        DatagramPacket first = channel.readInbound();
        Assertions.assertNotNull(first);
        Assertions.assertEquals(realIp, first.sender().getAddress());
        Assertions.assertEquals(realPort, first.sender().getPort());
        Assertions.assertEquals(2, first.content().readableBytes());
        Assertions.assertEquals(0x01, first.content().readUnsignedByte());
        Assertions.assertEquals(0x02, first.content().readUnsignedByte());
        first.release();

        channel.writeInbound(createProxyV2Datagram(proxyAddress, recipient, realIp, realPort, new byte[]{0x03, 0x04, 0x05}));
        DatagramPacket second = channel.readInbound();
        Assertions.assertNull(second);

        channel.finishAndReleaseAll();
    }

    @Test
    public void testCachedSenderWithoutProxyHeaderStillMapsToRealAddress() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);
        InetAddress realIp = InetAddress.getByName("8.8.8.8");
        int realPort = randomClientPort();

        channel.writeInbound(createProxyV2Datagram(proxyAddress, recipient, realIp, realPort, new byte[]{0x11}));
        DatagramPacket first = channel.readInbound();
        Assertions.assertNotNull(first);
        first.release();

        channel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x22, 0x33}), recipient, proxyAddress));
        DatagramPacket second = channel.readInbound();
        Assertions.assertNotNull(second);
        Assertions.assertEquals(realIp, second.sender().getAddress());
        Assertions.assertEquals(realPort, second.sender().getPort());
        Assertions.assertEquals(2, second.content().readableBytes());
        Assertions.assertEquals(0x22, second.content().readUnsignedByte());
        Assertions.assertEquals(0x33, second.content().readUnsignedByte());
        second.release();

        channel.finishAndReleaseAll();
    }

    @Test
    public void testClearMappingByRealAddressRemovesBothDirections() throws Exception {
        ProxyProtocolHandler handler = new ProxyProtocolHandler(List.of("127.0.0.1/32"));
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);
        InetAddress realIp = InetAddress.getByName("8.8.8.8");
        int realPort = randomClientPort();

        channel.writeInbound(createProxyV2Datagram(proxyAddress, recipient, realIp, realPort, new byte[]{0x11}));
        DatagramPacket first = channel.readInbound();
        Assertions.assertNotNull(first);
        first.release();

        handler.clearMappingByRealAddress(InetSocketAddress.createUnresolved("8.8.8.8", realPort));

        channel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x22}), recipient, proxyAddress));
        DatagramPacket second = channel.readInbound();
        Assertions.assertNotNull(second);
        Assertions.assertEquals(proxyAddress, second.sender());
        second.release();

        channel.finishAndReleaseAll();
    }

    private static DatagramPacket createProxyV2Datagram(InetSocketAddress proxyAddress,
                                                        InetSocketAddress recipient,
                                                        InetAddress realSourceIp,
                                                        int realSourcePort,
                                                        byte[] payload) throws UnknownHostException {
        ByteBuf buffer = Unpooled.buffer(16 + 12 + payload.length);
        buffer.writeBytes(PP_V2_SIGNATURE);
        buffer.writeByte(0x21);
        buffer.writeByte(0x12);
        buffer.writeShort(12);
        buffer.writeBytes(realSourceIp.getAddress());
        buffer.writeBytes(InetAddress.getByName("127.0.0.1").getAddress());
        buffer.writeShort(realSourcePort);
        buffer.writeShort(recipient.getPort());
        buffer.writeBytes(payload);
        return new DatagramPacket(buffer, recipient, proxyAddress);
    }
}
