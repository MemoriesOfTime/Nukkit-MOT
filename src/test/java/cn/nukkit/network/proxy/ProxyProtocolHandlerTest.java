package cn.nukkit.network.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.Inet6Address;
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
    public void testValidUdpIpv4ProxyHeaderMapsToRealAddress() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);
        InetAddress realIp = InetAddress.getByName("8.8.8.8");
        int realPort = randomClientPort();

        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, realIp, realPort, new byte[]{0x11, 0x22}));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(realIp, packet.sender().getAddress());
        Assertions.assertEquals(realPort, packet.sender().getPort());
        Assertions.assertEquals(2, packet.content().readableBytes());
        Assertions.assertEquals(0x11, packet.content().readUnsignedByte());
        Assertions.assertEquals(0x22, packet.content().readUnsignedByte());
        packet.release();

        channel.finishAndReleaseAll();
    }

    @Test
    public void testRepeatedProxyHeadersAreAcceptedAndRefreshMapping() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);
        InetAddress firstRealIp = InetAddress.getByName("8.8.8.8");
        InetAddress secondRealIp = InetAddress.getByName("1.1.1.1");
        int firstPort = randomClientPort();
        int secondPort = randomClientPort();

        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, firstRealIp, firstPort, new byte[]{0x01}));
        DatagramPacket first = channel.readInbound();
        Assertions.assertNotNull(first);
        Assertions.assertEquals(firstRealIp, first.sender().getAddress());
        Assertions.assertEquals(firstPort, first.sender().getPort());
        first.release();

        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, secondRealIp, secondPort, new byte[]{0x02, 0x03}));
        DatagramPacket second = channel.readInbound();
        Assertions.assertNotNull(second);
        Assertions.assertEquals(secondRealIp, second.sender().getAddress());
        Assertions.assertEquals(secondPort, second.sender().getPort());
        Assertions.assertEquals(2, second.content().readableBytes());
        Assertions.assertEquals(0x02, second.content().readUnsignedByte());
        Assertions.assertEquals(0x03, second.content().readUnsignedByte());
        second.release();

        channel.finishAndReleaseAll();
    }

    @Test
    public void testHeaderlessPacketFromNonWhitelistedSenderIsForwardedWithoutProxyParsing() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress clientAddress = new InetSocketAddress("192.168.1.10", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        channel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x22, 0x33}), recipient, clientAddress));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(clientAddress, packet.sender());
        Assertions.assertEquals(2, packet.content().readableBytes());
        Assertions.assertEquals(0x22, packet.content().readUnsignedByte());
        Assertions.assertEquals(0x33, packet.content().readUnsignedByte());
        packet.release();

        channel.finishAndReleaseAll();
    }

    @Test
    public void testHeaderlessPacketFromWhitelistedSenderIsDropped() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        channel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x22, 0x33}), recipient, proxyAddress));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNull(packet);

        channel.finishAndReleaseAll();
    }

    @Test
    public void testHeaderlessPacketFromWhitelistedSenderWithCachedMappingUsesRealAddress() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);
        InetAddress realIp = InetAddress.getByName("8.8.8.8");
        int realPort = randomClientPort();

        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, realIp, realPort, new byte[]{0x11}));
        DatagramPacket first = channel.readInbound();
        Assertions.assertNotNull(first);
        Assertions.assertEquals(realIp, first.sender().getAddress());
        Assertions.assertEquals(realPort, first.sender().getPort());
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
    public void testOutboundPacketsAreRemappedToProxyAddress() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);
        InetAddress realIp = InetAddress.getByName("8.8.8.8");
        int realPort = randomClientPort();
        InetSocketAddress realAddress = new InetSocketAddress(realIp, realPort);
        InetSocketAddress serverAddress = new InetSocketAddress("127.0.0.1", 19133);

        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, realIp, realPort, new byte[]{0x11}));
        DatagramPacket inbound = channel.readInbound();
        Assertions.assertNotNull(inbound);
        inbound.release();

        channel.writeOutbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x55}), realAddress, serverAddress));
        DatagramPacket outbound = channel.readOutbound();
        Assertions.assertNotNull(outbound);
        Assertions.assertEquals(proxyAddress, outbound.recipient());
        Assertions.assertEquals(serverAddress, outbound.sender());
        Assertions.assertEquals(1, outbound.content().readableBytes());
        Assertions.assertEquals(0x55, outbound.content().readUnsignedByte());
        outbound.release();

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

        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, realIp, realPort, new byte[]{0x11}));
        DatagramPacket first = channel.readInbound();
        Assertions.assertNotNull(first);
        first.release();

        handler.clearMappingByRealAddress(InetSocketAddress.createUnresolved("8.8.8.8", realPort));

        channel.writeOutbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x22}), new InetSocketAddress(realIp, realPort), recipient));
        DatagramPacket outbound = channel.readOutbound();
        Assertions.assertNotNull(outbound);
        Assertions.assertEquals(new InetSocketAddress(realIp, realPort), outbound.recipient());
        outbound.release();

        channel.finishAndReleaseAll();
    }

    @Test
    public void testLocalCommandUsesProxyAddressAndStripsHeader() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        channel.writeInbound(createProxyV2Datagram(proxyAddress, recipient, (byte) 0x20, (byte) 0x00, new byte[0], new byte[]{0x55, 0x66}));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(proxyAddress, packet.sender());
        Assertions.assertEquals(2, packet.content().readableBytes());
        Assertions.assertEquals(0x55, packet.content().readUnsignedByte());
        Assertions.assertEquals(0x66, packet.content().readUnsignedByte());
        packet.release();

        channel.finishAndReleaseAll();
    }

    @Test
    public void testUnknownProtocolUsesProxyAddressAndStripsHeader() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        channel.writeInbound(createProxyV2Datagram(proxyAddress, recipient, (byte) 0x21, (byte) 0x00, new byte[0], new byte[]{0x44}));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(proxyAddress, packet.sender());
        Assertions.assertEquals(1, packet.content().readableBytes());
        Assertions.assertEquals(0x44, packet.content().readUnsignedByte());
        packet.release();

        channel.finishAndReleaseAll();
    }

    @Test
    public void testUdpIpv6ProxyHeaderMapsToRealAddress() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);
        InetAddress realIp = Inet6Address.getByName("2001:db8::1");
        int realPort = randomClientPort();

        channel.writeInbound(createUdpIpv6ProxyV2Datagram(proxyAddress, recipient, realIp, realPort, new byte[]{0x33}));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(realIp, packet.sender().getAddress());
        Assertions.assertEquals(realPort, packet.sender().getPort());
        Assertions.assertEquals(1, packet.content().readableBytes());
        Assertions.assertEquals(0x33, packet.content().readUnsignedByte());
        packet.release();

        channel.finishAndReleaseAll();
    }

    @Test
    public void testTruncatedProxyHeaderIsDropped() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        ByteBuf buffer = Unpooled.buffer(18);
        buffer.writeBytes(PP_V2_SIGNATURE);
        buffer.writeByte(0x21);
        buffer.writeByte(0x12);
        buffer.writeShort(12);
        buffer.writeBytes(new byte[]{0x55, 0x66});

        channel.writeInbound(new DatagramPacket(buffer, recipient, proxyAddress));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNull(packet);

        channel.finishAndReleaseAll();
    }

    @Test
    public void testInvalidVersionIsDropped() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        channel.writeInbound(createProxyV2Datagram(proxyAddress, recipient, (byte) 0x11, (byte) 0x12, new byte[12], new byte[]{0x10}));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNull(packet);

        channel.finishAndReleaseAll();
    }

    @Test
    public void testNonDatagramTransportIsDropped() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);
        InetAddress realIp = InetAddress.getByName("8.8.8.8");
        byte[] addressBlock = createIpv4AddressBlock(realIp, InetAddress.getByName("127.0.0.1"), randomClientPort(), recipient.getPort());

        channel.writeInbound(createProxyV2Datagram(proxyAddress, recipient, (byte) 0x21, (byte) 0x11, addressBlock, new byte[]{0x21}));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNull(packet);

        channel.finishAndReleaseAll();
    }

    @Test
    public void testInvalidWhitelistPrefixFailsClosed() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/33")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);
        InetAddress realIp = InetAddress.getByName("8.8.8.8");
        int realPort = randomClientPort();

        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, realIp, realPort, new byte[]{0x11}));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNull(packet);

        channel.finishAndReleaseAll();
    }

    @Test
    public void testTlvDataAfterAddressBlockIsSkipped() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);
        InetAddress realIp = InetAddress.getByName("8.8.8.8");
        int realPort = randomClientPort();

        byte[] ipv4Addr = createIpv4AddressBlock(realIp, InetAddress.getByName("127.0.0.1"), realPort, recipient.getPort());
        // TLV: type=0x04 (PP2_TYPE_NOOP), length=0x0003, value={0x00, 0x00, 0x00}
        byte[] tlv = {0x04, 0x00, 0x03, 0x00, 0x00, 0x00};
        byte[] addressBlockWithTlv = new byte[ipv4Addr.length + tlv.length];
        System.arraycopy(ipv4Addr, 0, addressBlockWithTlv, 0, ipv4Addr.length);
        System.arraycopy(tlv, 0, addressBlockWithTlv, ipv4Addr.length, tlv.length);

        channel.writeInbound(createProxyV2Datagram(proxyAddress, recipient, (byte) 0x21, (byte) 0x12, addressBlockWithTlv, new byte[]{0x77}));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(realIp, packet.sender().getAddress());
        Assertions.assertEquals(realPort, packet.sender().getPort());
        Assertions.assertEquals(1, packet.content().readableBytes());
        Assertions.assertEquals(0x77, packet.content().readUnsignedByte());
        packet.release();

        channel.finishAndReleaseAll();
    }

    @Test
    public void testInvalidCommandIsDropped() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        // verCmd=0x22 → version 2, command 0x2 (invalid, only 0x0 LOCAL and 0x1 PROXY are valid per spec)
        channel.writeInbound(createProxyV2Datagram(proxyAddress, recipient, (byte) 0x22, (byte) 0x12, new byte[12], new byte[]{0x11}));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNull(packet);

        channel.finishAndReleaseAll();
    }

    @Test
    public void testAfUnixFamilyIsDropped() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        // famTransport=0x32 → AF_UNIX DGRAM (216 bytes address block per spec)
        channel.writeInbound(createProxyV2Datagram(proxyAddress, recipient, (byte) 0x21, (byte) 0x32, new byte[216], new byte[]{0x11}));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNull(packet);

        channel.finishAndReleaseAll();
    }

    @Test
    public void testPortBoundaryValues() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);
        InetAddress realIp = InetAddress.getByName("8.8.8.8");

        // Port 0
        InetSocketAddress proxyAddr1 = new InetSocketAddress("127.0.0.1", 63035);
        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddr1, recipient, realIp, 0, new byte[]{0x11}));
        DatagramPacket packet0 = channel.readInbound();
        Assertions.assertNotNull(packet0);
        Assertions.assertEquals(0, packet0.sender().getPort());
        packet0.release();

        // Port 65535
        InetSocketAddress proxyAddr2 = new InetSocketAddress("127.0.0.1", 63036);
        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddr2, recipient, realIp, 65535, new byte[]{0x22}));
        DatagramPacket packet65535 = channel.readInbound();
        Assertions.assertNotNull(packet65535);
        Assertions.assertEquals(65535, packet65535.sender().getPort());
        packet65535.release();

        channel.finishAndReleaseAll();
    }

    @Test
    public void testEmptyPayloadAfterHeader() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);
        InetAddress realIp = InetAddress.getByName("8.8.8.8");
        int realPort = randomClientPort();

        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, realIp, realPort, new byte[0]));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(realIp, packet.sender().getAddress());
        Assertions.assertEquals(realPort, packet.sender().getPort());
        Assertions.assertEquals(0, packet.content().readableBytes());
        packet.release();

        channel.finishAndReleaseAll();
    }

    @Test
    public void testAddressLengthShorterThanExpectedIsDropped() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        // famTransport=0x12 (IPv4/UDP) requires 12 bytes address block, but only 8 provided
        channel.writeInbound(createProxyV2Datagram(proxyAddress, recipient, (byte) 0x21, (byte) 0x12, new byte[8], new byte[]{0x11}));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNull(packet);

        channel.finishAndReleaseAll();
    }

    private static DatagramPacket createUdpIpv4ProxyV2Datagram(InetSocketAddress proxyAddress,
                                                                InetSocketAddress recipient,
                                                                InetAddress realSourceIp,
                                                                int realSourcePort,
                                                                byte[] payload) throws UnknownHostException {
        return createProxyV2Datagram(
                proxyAddress,
                recipient,
                (byte) 0x21,
                (byte) 0x12,
                createIpv4AddressBlock(realSourceIp, InetAddress.getByName("127.0.0.1"), realSourcePort, recipient.getPort()),
                payload
        );
    }

    private static DatagramPacket createUdpIpv6ProxyV2Datagram(InetSocketAddress proxyAddress,
                                                                InetSocketAddress recipient,
                                                                InetAddress realSourceIp,
                                                                int realSourcePort,
                                                                byte[] payload) throws UnknownHostException {
        return createProxyV2Datagram(
                proxyAddress,
                recipient,
                (byte) 0x21,
                (byte) 0x22,
                createIpv6AddressBlock(realSourceIp, Inet6Address.getByName("2001:db8::2"), realSourcePort, recipient.getPort()),
                payload
        );
    }

    private static byte[] createIpv4AddressBlock(InetAddress sourceIp,
                                                 InetAddress destinationIp,
                                                 int sourcePort,
                                                 int destinationPort) {
        ByteBuf buffer = Unpooled.buffer(12);
        buffer.writeBytes(sourceIp.getAddress());
        buffer.writeBytes(destinationIp.getAddress());
        buffer.writeShort(sourcePort);
        buffer.writeShort(destinationPort);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();
        return bytes;
    }

    private static byte[] createIpv6AddressBlock(InetAddress sourceIp,
                                                 InetAddress destinationIp,
                                                 int sourcePort,
                                                 int destinationPort) {
        ByteBuf buffer = Unpooled.buffer(36);
        buffer.writeBytes(sourceIp.getAddress());
        buffer.writeBytes(destinationIp.getAddress());
        buffer.writeShort(sourcePort);
        buffer.writeShort(destinationPort);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();
        return bytes;
    }

    private static DatagramPacket createProxyV2Datagram(InetSocketAddress proxyAddress,
                                                        InetSocketAddress recipient,
                                                        byte verCmd,
                                                        byte famTransport,
                                                        byte[] addressBlock,
                                                        byte[] payload) {
        ByteBuf buffer = Unpooled.buffer(16 + addressBlock.length + payload.length);
        buffer.writeBytes(PP_V2_SIGNATURE);
        buffer.writeByte(verCmd);
        buffer.writeByte(famTransport);
        buffer.writeShort(addressBlock.length);
        buffer.writeBytes(addressBlock);
        buffer.writeBytes(payload);
        return new DatagramPacket(buffer, recipient, proxyAddress);
    }
}
