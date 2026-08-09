package cn.nukkit.network.proxy;

import com.github.benmanes.caffeine.cache.Ticker;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ProxyProtocolHandlerTest {

    private static final byte[] PP_V2_SIGNATURE = {
            0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };

    private static int randomClientPort() {
        return ThreadLocalRandom.current().nextInt(1024, 65536);
    }

    @Test
    public void testHeaderlessPacketFromNonWhitelistedSenderRegistersDirectClient() {
        InetSocketAddress clientAddress = new InetSocketAddress("192.168.1.10", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        channel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x22, 0x33}), recipient, clientAddress));

        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(clientAddress, packet.sender());
        Assertions.assertEquals(0x22, packet.content().readUnsignedByte());
        Assertions.assertEquals(0x33, packet.content().readUnsignedByte());
        packet.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testHeaderlessPacketFromWhitelistedSenderWithoutMappingIsDropped() {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        channel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x22, 0x33}), recipient, proxyAddress));

        Assertions.assertNull(channel.readInbound());
        channel.finishAndReleaseAll();
    }

    @Test
    public void testHeaderlessPacketFromMappedProxyIsDelegatedToNetworkHandler() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress realAddress = new InetSocketAddress("8.8.8.8", randomClientPort());
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, realAddress.getAddress(), realAddress.getPort(), new byte[]{0x11}));
        DatagramPacket initial = channel.readInbound();
        Assertions.assertNotNull(initial);
        initial.release();

        channel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x22, 0x33}), recipient, proxyAddress));

        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(realAddress, packet.sender());
        Assertions.assertEquals(0x22, packet.content().readUnsignedByte());
        Assertions.assertEquals(0x33, packet.content().readUnsignedByte());
        packet.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testMappedProxySessionSurvivesShortIdleWindow() throws Exception {
        MutableTicker ticker = new MutableTicker();
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress realAddress = new InetSocketAddress("8.8.8.8", randomClientPort());
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32"), ticker));
        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, realAddress.getAddress(), realAddress.getPort(), new byte[]{0x11}));
        DatagramPacket initial = channel.readInbound();
        Assertions.assertNotNull(initial);
        initial.release();

        ticker.advance(11, TimeUnit.SECONDS);

        channel.writeOutbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x31}), realAddress, recipient));
        DatagramPacket outbound = channel.readOutbound();
        Assertions.assertNotNull(outbound);
        Assertions.assertEquals(proxyAddress, outbound.recipient());
        outbound.release();

        channel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x22, 0x33}), recipient, proxyAddress));
        DatagramPacket inbound = channel.readInbound();
        Assertions.assertNotNull(inbound);
        Assertions.assertEquals(realAddress, inbound.sender());
        Assertions.assertEquals(0x22, inbound.content().readUnsignedByte());
        Assertions.assertEquals(0x33, inbound.content().readUnsignedByte());
        inbound.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testClearedRealAddressMappingDropsHeaderlessPacketFromReusedWhitelistedProxy() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetAddress realIp = InetAddress.getByName("8.8.8.8");
        int realPort = randomClientPort();
        InetSocketAddress realAddress = new InetSocketAddress(realIp.getHostAddress(), realPort);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        ProxyProtocolHandler handler = new ProxyProtocolHandler(List.of("127.0.0.1/32"));
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, realIp, realPort, new byte[]{0x11}));

        DatagramPacket initial = channel.readInbound();
        Assertions.assertNotNull(initial);
        initial.release();

        handler.clearMappingByRealAddress(realAddress);
        channel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x22, 0x33}), recipient, proxyAddress));

        Assertions.assertNull(channel.readInbound());
        channel.finishAndReleaseAll();
    }

    @Test
    public void testValidProxyHeaderFromWhitelistedSenderIsDelegatedToNetworkHandler() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress realAddress = new InetSocketAddress("8.8.8.8", randomClientPort());
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient,
                realAddress.getAddress(), realAddress.getPort(), new byte[]{0x11}));

        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(realAddress, packet.sender());
        Assertions.assertEquals(0x11, packet.content().readUnsignedByte());
        packet.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testProxyHeadersFromSharedProxyUseRealSenderAndKeepOutboundProxyMappings() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress firstRealAddress = new InetSocketAddress("8.8.8.8", randomClientPort());
        InetSocketAddress secondRealAddress = new InetSocketAddress("1.1.1.1", randomClientPort());
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient,
                firstRealAddress.getAddress(), firstRealAddress.getPort(), new byte[]{0x11}));
        DatagramPacket firstInbound = channel.readInbound();
        Assertions.assertNotNull(firstInbound);
        Assertions.assertEquals(firstRealAddress, firstInbound.sender());
        Assertions.assertEquals(0x11, firstInbound.content().readUnsignedByte());
        firstInbound.release();

        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient,
                secondRealAddress.getAddress(), secondRealAddress.getPort(), new byte[]{0x22}));
        DatagramPacket secondInbound = channel.readInbound();
        Assertions.assertNotNull(secondInbound);
        Assertions.assertEquals(secondRealAddress, secondInbound.sender());
        Assertions.assertEquals(0x22, secondInbound.content().readUnsignedByte());
        secondInbound.release();

        channel.writeOutbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x31}), firstRealAddress, recipient));
        DatagramPacket firstOutbound = channel.readOutbound();
        Assertions.assertNotNull(firstOutbound);
        Assertions.assertEquals(proxyAddress, firstOutbound.recipient());
        firstOutbound.release();

        channel.writeOutbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x32}), secondRealAddress, recipient));
        DatagramPacket secondOutbound = channel.readOutbound();
        Assertions.assertNotNull(secondOutbound);
        Assertions.assertEquals(proxyAddress, secondOutbound.recipient());
        secondOutbound.release();

        channel.finishAndReleaseAll();
    }

    @Test
    public void testUdpIpv6ProxyHeaderFromWhitelistedSenderIsDelegatedToNetworkHandler() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress realAddress = new InetSocketAddress(Inet6Address.getByName("2001:4860:4860::8888"), randomClientPort());
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        channel.writeInbound(createUdpIpv6ProxyV2Datagram(proxyAddress, recipient,
                realAddress.getAddress(), realAddress.getPort(), new byte[]{0x11}));

        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(realAddress, packet.sender());
        Assertions.assertEquals(0x11, packet.content().readUnsignedByte());
        packet.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testProxyHeaderFromNonWhitelistedSenderIsDropped() throws Exception {
        InetSocketAddress clientAddress = new InetSocketAddress("192.168.1.10", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        channel.writeInbound(createUdpIpv4ProxyV2Datagram(clientAddress, recipient, InetAddress.getByName("8.8.8.8"), randomClientPort(), new byte[]{0x11}));

        Assertions.assertNull(channel.readInbound());
        channel.finishAndReleaseAll();
    }

    @Test
    public void testIpv4WildcardWhitelistDoesNotTrustIpv6Sender() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress(Inet6Address.getByName("2001:db8::1"), 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("0.0.0.0/0")));
        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, InetAddress.getByName("8.8.8.8"), randomClientPort(), new byte[]{0x11}));

        assertNoInbound(channel);
        channel.finishAndReleaseAll();
    }

    @Test
    public void testIpv6WhitelistAllowsIpv6ProxySender() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress(Inet6Address.getByName("2001:db8::1"), 63035);
        InetSocketAddress realAddress = new InetSocketAddress(Inet6Address.getByName("2001:4860:4860::8888"), randomClientPort());
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("2001:db8::/32")));
        channel.writeInbound(createUdpIpv6ProxyV2Datagram(proxyAddress, recipient,
                realAddress.getAddress(), realAddress.getPort(), new byte[]{0x11}));

        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(realAddress, packet.sender());
        Assertions.assertEquals(0x11, packet.content().readUnsignedByte());
        packet.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testBlankWhitelistEntryDoesNotTrustIpv4Sender() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("")));
        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, InetAddress.getByName("8.8.8.8"), randomClientPort(), new byte[]{0x11}));

        assertNoInbound(channel);
        channel.finishAndReleaseAll();
    }

    @Test
    public void testIpv6WhitelistHonorsPartialBytePrefix() throws Exception {
        InetSocketAddress matchingProxyAddress = new InetSocketAddress(Inet6Address.getByName("2001:db8:7fff::1"), 63035);
        InetSocketAddress nonMatchingProxyAddress = new InetSocketAddress(Inet6Address.getByName("2001:db8:8000::1"), 63036);
        InetSocketAddress realAddress = new InetSocketAddress(Inet6Address.getByName("2001:4860:4860::8888"), randomClientPort());
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("2001:db8::/33")));
        channel.writeInbound(createUdpIpv6ProxyV2Datagram(matchingProxyAddress, recipient,
                realAddress.getAddress(), realAddress.getPort(), new byte[]{0x11}));
        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(realAddress, packet.sender());
        packet.release();

        channel.writeInbound(createUdpIpv6ProxyV2Datagram(nonMatchingProxyAddress, recipient, Inet6Address.getByName("2001:4860:4860::8888"), randomClientPort(), new byte[]{0x22}));
        assertNoInbound(channel);
        channel.finishAndReleaseAll();
    }

    @Test
    public void testIpv6LoopbackSenderIsTrustedByDefault() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress(Inet6Address.getByName("::1"), 63035);
        InetSocketAddress realAddress = new InetSocketAddress(Inet6Address.getByName("2001:4860:4860::8888"), randomClientPort());
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("192.0.2.0/24")));
        channel.writeInbound(createUdpIpv6ProxyV2Datagram(proxyAddress, recipient,
                realAddress.getAddress(), realAddress.getPort(), new byte[]{0x11}));

        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(realAddress, packet.sender());
        packet.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testIpv6LinkLocalSenderIsTrustedByDefault() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress(Inet6Address.getByName("fe80::1"), 63035);
        InetSocketAddress realAddress = new InetSocketAddress(Inet6Address.getByName("2001:4860:4860::8888"), randomClientPort());
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("192.0.2.0/24")));
        channel.writeInbound(createUdpIpv6ProxyV2Datagram(proxyAddress, recipient,
                realAddress.getAddress(), realAddress.getPort(), new byte[]{0x11}));

        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(realAddress, packet.sender());
        packet.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testIpv6UniqueLocalSenderIsTrustedByDefault() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress(Inet6Address.getByName("fd00::1"), 63035);
        InetSocketAddress realAddress = new InetSocketAddress(Inet6Address.getByName("2001:4860:4860::8888"), randomClientPort());
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("192.0.2.0/24")));
        channel.writeInbound(createUdpIpv6ProxyV2Datagram(proxyAddress, recipient,
                realAddress.getAddress(), realAddress.getPort(), new byte[]{0x11}));

        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(realAddress, packet.sender());
        packet.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testInvalidProxyHeaderIsDropped() {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        ByteBuf buffer = Unpooled.buffer(16);
        buffer.writeBytes(PP_V2_SIGNATURE);
        buffer.writeByte(0x11);
        buffer.writeByte(0x12);
        buffer.writeShort(12);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        channel.writeInbound(new DatagramPacket(buffer, recipient, proxyAddress));

        Assertions.assertNull(channel.readInbound());
        channel.finishAndReleaseAll();
    }

    @Test
    public void testTcpIpv4ProxyHeaderFromWhitelistedSenderIsDropped() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        channel.writeInbound(createProxyV2Datagram(
                proxyAddress,
                recipient,
                (byte) 0x21,
                (byte) 0x11,
                createIpv4AddressBlock(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1"), randomClientPort(), recipient.getPort()),
                new byte[]{0x11}
        ));

        assertNoInbound(channel);

        channel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x22}), recipient, proxyAddress));
        assertNoInbound(channel);
        channel.finishAndReleaseAll();
    }

    @Test
    public void testAfUnixDgramProxyHeaderFromWhitelistedSenderIsDropped() {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        channel.writeInbound(createProxyV2Datagram(
                proxyAddress,
                recipient,
                (byte) 0x21,
                (byte) 0x32,
                createUnixAddressBlock("client.sock", "server.sock"),
                new byte[]{0x11}
        ));

        assertNoInbound(channel);
        channel.finishAndReleaseAll();
    }

    @Test
    public void testRepeatedProxyHeaderFromMappedProxyRefreshesMappingAndStripsHeader() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);
        InetAddress refreshedSourceIp = InetAddress.getByName("1.1.1.1");
        int refreshedSourcePort = randomClientPort();
        InetSocketAddress refreshedRealAddress = new InetSocketAddress(refreshedSourceIp.getHostAddress(), refreshedSourcePort);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, refreshedSourceIp, refreshedSourcePort, new byte[]{0x11}));

        DatagramPacket packet = channel.readInbound();
        Assertions.assertNotNull(packet);
        Assertions.assertEquals(refreshedRealAddress, packet.sender());
        Assertions.assertEquals(0x11, packet.content().readUnsignedByte());
        packet.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testTcpIpv4ProxyHeaderFromMappedProxyIsDropped() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress realAddress = new InetSocketAddress("8.8.8.8", randomClientPort());
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        channel.writeInbound(createProxyV2Datagram(
                proxyAddress,
                recipient,
                (byte) 0x21,
                (byte) 0x11,
                createIpv4AddressBlock(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("127.0.0.1"), randomClientPort(), recipient.getPort()),
                new byte[]{0x11}
        ));

        assertNoInbound(channel);
        channel.finishAndReleaseAll();
    }

    @Test
    public void testLocalProxyHeaderFromWhitelistedSenderIsDropped() {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        channel.writeInbound(createProxyV2Datagram(proxyAddress, recipient, (byte) 0x20, (byte) 0x00, new byte[0], new byte[]{0x22}));

        assertNoInbound(channel);
        channel.finishAndReleaseAll();
    }

    @Test
    public void testUnknownProtocolProxyHeaderFromWhitelistedSenderIsDropped() {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        channel.writeInbound(createProxyV2Datagram(proxyAddress, recipient, (byte) 0x21, (byte) 0x00, new byte[0], new byte[]{0x33}));

        assertNoInbound(channel);
        channel.finishAndReleaseAll();
    }

    @Test
    public void testMalformedTlvInRepeatedProxyHeaderIsDroppedWithoutThrowing() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress realAddress = new InetSocketAddress("8.8.8.8", randomClientPort());
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        byte[] addressBlock = createIpv4AddressBlock(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("127.0.0.1"),
                randomClientPort(),
                recipient.getPort()
        );
        byte[] malformedTlv = {0x04, 0x00, 0x04, 0x00};
        byte[] addressBlockWithMalformedTlv = new byte[addressBlock.length + malformedTlv.length];
        System.arraycopy(addressBlock, 0, addressBlockWithMalformedTlv, 0, addressBlock.length);
        System.arraycopy(malformedTlv, 0, addressBlockWithMalformedTlv, addressBlock.length, malformedTlv.length);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/32")));
        Assertions.assertDoesNotThrow(() -> channel.writeInbound(createProxyV2Datagram(
                proxyAddress,
                recipient,
                (byte) 0x21,
                (byte) 0x12,
                addressBlockWithMalformedTlv,
                new byte[]{0x44}
        )));

        Assertions.assertNull(channel.readInbound());
        channel.finishAndReleaseAll();
    }

    @Test
    public void testEmptyWhitelistAllowsDirectAndProxySources() throws Exception {
        InetSocketAddress sender = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress realAddress = new InetSocketAddress("8.8.8.8", randomClientPort());
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of()));
        channel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{0x22}), recipient, sender));
        DatagramPacket direct = channel.readInbound();
        Assertions.assertNotNull(direct);
        Assertions.assertEquals(sender, direct.sender());
        direct.release();

        channel.writeInbound(createUdpIpv4ProxyV2Datagram(sender, recipient,
                realAddress.getAddress(), realAddress.getPort(), new byte[]{0x11}));
        DatagramPacket proxied = channel.readInbound();
        Assertions.assertNotNull(proxied);
        Assertions.assertEquals(realAddress, proxied.sender());
        Assertions.assertEquals(0x11, proxied.content().readUnsignedByte());
        proxied.release();

        channel.finishAndReleaseAll();
    }

    @Test
    public void testInvalidWhitelistPrefixFailsClosedForProxyHeader() throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 63035);
        InetSocketAddress recipient = new InetSocketAddress("127.0.0.1", 19132);

        EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolHandler(List.of("127.0.0.1/33")));
        channel.writeInbound(createUdpIpv4ProxyV2Datagram(proxyAddress, recipient, InetAddress.getByName("8.8.8.8"), randomClientPort(), new byte[]{0x11}));

        Assertions.assertNull(channel.readInbound());
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

    private static DatagramPacket createProxyV2Datagram(InetSocketAddress sender,
                                                        InetSocketAddress recipient,
                                                        byte verCmd,
                                                        byte famTransport,
                                                        byte[] addressBlock,
                                                        byte[] payload) {
        ByteBuf buffer = Unpooled.buffer(PP_V2_SIGNATURE.length + 4 + addressBlock.length + payload.length);
        buffer.writeBytes(PP_V2_SIGNATURE);
        buffer.writeByte(verCmd);
        buffer.writeByte(famTransport);
        buffer.writeShort(addressBlock.length);
        buffer.writeBytes(addressBlock);
        buffer.writeBytes(payload);
        return new DatagramPacket(buffer, recipient, sender);
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

    private static byte[] createUnixAddressBlock(String sourcePath, String destinationPath) {
        ByteBuf buffer = Unpooled.buffer(216);
        writeUnixAddress(buffer, sourcePath);
        writeUnixAddress(buffer, destinationPath);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();
        return bytes;
    }

    private static void writeUnixAddress(ByteBuf buffer, String path) {
        byte[] pathBytes = path.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        buffer.writeBytes(pathBytes);
        buffer.writeZero(108 - pathBytes.length);
    }

    private static void assertNoInbound(EmbeddedChannel channel) {
        Object inbound = channel.readInbound();
        ReferenceCountUtil.release(inbound);
        Assertions.assertNull(inbound);
    }

    private static final class MutableTicker implements Ticker {

        private long nanos;

        @Override
        public long read() {
            return nanos;
        }

        private void advance(long duration, TimeUnit unit) {
            nanos += unit.toNanos(duration);
        }
    }
}
