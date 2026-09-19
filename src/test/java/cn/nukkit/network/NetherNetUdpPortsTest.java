package cn.nukkit.network;

import cn.nukkit.lang.BaseLang;
import io.netty.channel.EventLoop;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetServerSignaling;
import org.junit.jupiter.api.Test;
import tel.schich.libdatachannel.PeerConnectionConfiguration;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * server-udp-ports（BDS 同名语法）的解析与应答改写测试，官方示例取自 BDS server.properties 注释。
 * <p>
 * Parsing and answer rewriting for server-udp-ports (BDS syntax); the mapping examples
 * come from the BDS server.properties comments.
 */
class NetherNetUdpPortsTest {

    /** 解析错误消息经 BaseLang 本地化，测试用 eng 模板断言文案。 Parse errors localize through BaseLang; tests assert against the eng templates. */
    private static final BaseLang LANG = new BaseLang("eng");

    @Test
    void autoFormsReturnNull() {
        assertNull(NetherNetUdpPorts.parse(null, LANG), "a missing entry means system-assigned ports");
        assertNull(NetherNetUdpPorts.parse("  ", LANG), "a blank entry means system-assigned ports");
        assertNull(NetherNetUdpPorts.parse("", LANG), "an empty entry means system-assigned ports");
        assertNull(NetherNetUdpPorts.parse("0", LANG), "the default 0 means system-assigned ports");
    }

    @Test
    void windowOnlyForms() {
        NetherNetUdpPorts single = NetherNetUdpPorts.parse("50000", LANG);
        assertNotNull(single);
        assertEquals(50000, single.begin());
        assertEquals(50000, single.end());
        assertEquals(0, single.offset());
        assertTrue(single.advertisedAddresses().isEmpty());

        NetherNetUdpPorts range = NetherNetUdpPorts.parse(" 49152-49200 ", LANG);
        assertNotNull(range);
        assertEquals(49152, range.begin());
        assertEquals(49200, range.end());
        assertEquals(0, range.offset());
    }

    @Test
    void portMappingForms() {
        NetherNetUdpPorts mapping = NetherNetUdpPorts.parse("19132:32000", LANG);
        assertNotNull(mapping);
        assertEquals(32000, mapping.begin());
        assertEquals(32000, mapping.end());
        assertEquals(19132 - 32000, mapping.offset());
        assertTrue(mapping.advertisedAddresses().isEmpty());

        NetherNetUdpPorts withAddress = NetherNetUdpPorts.parse("203.0.113.10:19132-19140:32000-32008", LANG);
        assertNotNull(withAddress);
        assertEquals(32000, withAddress.begin());
        assertEquals(32008, withAddress.end());
        assertEquals(19132 - 32000, withAddress.offset());
        assertEquals(Set.of("203.0.113.10"), withAddress.advertisedAddresses());
    }

    @Test
    void ipv6PrefixAndCommaAccumulation() {
        NetherNetUdpPorts ports = NetherNetUdpPorts.parse("[2001:db8::1]:19132:32000,32000", LANG);
        assertNotNull(ports);
        assertEquals(32000, ports.begin());
        assertEquals(32000, ports.end());
        assertEquals(19132 - 32000, ports.offset());
        assertEquals(1, ports.advertisedAddresses().size(), "the IPv6 prefix is the only advertised address");
        assertTrue(ports.advertisedAddresses().iterator().next().startsWith("2001:db8:"),
                "the address is stored in its canonical literal form");
    }

    @Test
    void addressOnlyRemapKeepsPorts() {
        NetherNetUdpPorts ports = NetherNetUdpPorts.parse("203.0.113.10:32000", LANG);
        assertNotNull(ports);
        assertEquals(32000, ports.begin());
        assertEquals(32000, ports.end());
        assertEquals(0, ports.offset(), "no second range means no port shift");
        assertEquals(Set.of("203.0.113.10"), ports.advertisedAddresses());
    }

    @Test
    void commaEntriesAccumulateAddresses() {
        NetherNetUdpPorts ports = NetherNetUdpPorts.parse("1.2.3.4:32000-32010,5.6.7.8:32000-32010", LANG);
        assertNotNull(ports);
        assertEquals(2, ports.advertisedAddresses().size());
        assertEquals(32000, ports.begin());
        assertEquals(32010, ports.end());
    }

    @Test
    void conflictingWindowsAreRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> NetherNetUdpPorts.parse("32000,33000", LANG),
                "entries carrying different internal windows are a config error");
        assertTrue(e.getMessage().contains("different internal window"),
                "the rejection carries the localized reason, got: " + e.getMessage());
    }

    @Test
    void conflictingOffsetsAreRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> NetherNetUdpPorts.parse("19132:32000,18000:32000", LANG),
                "entries mapping different external offsets are a config error");
        assertTrue(e.getMessage().contains("different external offset"),
                "the rejection carries the localized reason, got: " + e.getMessage());
    }

    @Test
    void invalidFormsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> NetherNetUdpPorts.parse("19132-19140:32000-32009", LANG),
                "range lengths on each side of ':' must match");
        assertThrows(IllegalArgumentException.class, () -> NetherNetUdpPorts.parse("50010-50000", LANG),
                "a reversed range is invalid");
        assertThrows(IllegalArgumentException.class, () -> NetherNetUdpPorts.parse("0-50010", LANG),
                "the range must start at a real port");
        assertThrows(IllegalArgumentException.class, () -> NetherNetUdpPorts.parse("70000", LANG),
                "ports above 65535 are invalid");
        assertThrows(IllegalArgumentException.class, () -> NetherNetUdpPorts.parse("abc", LANG),
                "non-numeric values are invalid");
        assertThrows(IllegalArgumentException.class, () -> NetherNetUdpPorts.parse("50000-abc", LANG),
                "a non-numeric range end is invalid");
        assertThrows(IllegalArgumentException.class, () -> NetherNetUdpPorts.parse("example.com:32000", LANG),
                "hostnames are not accepted, only IP literals");
        assertThrows(IllegalArgumentException.class, () -> NetherNetUdpPorts.parse("999.1.1.1:32000", LANG),
                "an invalid IPv4 literal is rejected");
        assertThrows(IllegalArgumentException.class, () -> NetherNetUdpPorts.parse("[2001:db8::1:32000", LANG),
                "an unclosed IPv6 bracket is rejected");
        assertThrows(IllegalArgumentException.class, () -> NetherNetUdpPorts.parse("1.2.3.4:32000:19132:40000", LANG),
                "too many colon groups are rejected");
        assertThrows(IllegalArgumentException.class, () -> NetherNetUdpPorts.parse("abc,def", LANG),
                "every entry invalid is still a config error, not silent auto-assignment");
    }

    @Test
    void rejectionMessagesAreLocalized() {
        IllegalArgumentException eng = assertThrows(IllegalArgumentException.class,
                () -> NetherNetUdpPorts.parse("70000", LANG));
        assertTrue(eng.getMessage().contains("is not a valid port range"),
                "the eng message comes from lang.ini, got: " + eng.getMessage());
        assertTrue(eng.getMessage().contains("'70000'"), "the message names the offending value");

        IllegalArgumentException chs = assertThrows(IllegalArgumentException.class,
                () -> NetherNetUdpPorts.parse("70000", new BaseLang("chs")));
        assertTrue(chs.getMessage().contains("端口范围"),
                "the chs message comes from lang.ini, got: " + chs.getMessage());
    }

    @Test
    void containsCoversTheWholeWindow() {
        NetherNetUdpPorts ports = NetherNetUdpPorts.parse("50000-50010", LANG);
        assertNotNull(ports);
        assertTrue(ports.contains(50000), "the window's lower bound is included");
        assertTrue(ports.contains(50005), "ports inside the window are included");
        assertTrue(ports.contains(50010), "the window's upper bound is included");
        assertFalse(ports.contains(49999), "ports below the window are excluded");
        assertFalse(ports.contains(50011), "ports above the window are excluded");
    }

    @Test
    void publishesOnRequiresASinglePortMappingOntoThatPort() {
        assertTrue(NetherNetUdpPorts.parse("19132:19134", LANG).publishesOn(19132), "a single-port mapping onto server-port shares it");
        assertTrue(NetherNetUdpPorts.parse("203.0.113.10:19132:19134", LANG).publishesOn(19132), "an address prefix does not change that");
        assertFalse(NetherNetUdpPorts.parse("19132:19134", LANG).publishesOn(25565), "another server-port is a plain external mapping");
        assertFalse(NetherNetUdpPorts.parse("19134", LANG).publishesOn(19134), "no mapping means nothing is published elsewhere");
        assertFalse(NetherNetUdpPorts.parse("19132-19140:32000-32008", LANG).publishesOn(19132), "a window is not a single-port mapping");
    }

    @Test
    void peerConfigPinsTheWindowWithMux() {
        NetherNetUdpPorts ports = NetherNetUdpPorts.parse("19132-19140:32000-32008", LANG);
        assertNotNull(ports, "equal-length ranges are a valid mapping");
        PeerConnectionConfiguration config = ports.peerConfig(new InetSocketAddress("0.0.0.0", 19132));
        assertTrue(config.enableIceUdpMux(), "mux keeps every peer on one port from the window");
        assertEquals(32000, config.portRangeBegin());
        assertEquals(32008, config.portRangeEnd());
    }

    @Test
    void rewriteShiftsOnlyHostUdpCandidates() {
        NetherNetUdpPorts ports = NetherNetUdpPorts.parse("19132:32000", LANG);
        assertNotNull(ports);
        String sdp = "v=0\r\n" +
                "a=fingerprint:sha-256 00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF\r\n" +
                "a=candidate:1 1 UDP 2130706431 192.168.1.5 32000 typ host\r\n" +
                "a=candidate:2 1 udp 1694498815 203.0.113.99 40000 typ srflx raddr 192.168.1.5 rport 32000\r\n" +
                "a=candidate:3 1 TCP 1518280447 192.168.1.5 9 typ host tcptype active\r\n" +
                "\r\n";
        String out = ports.rewriteSdp(sdp);

        assertTrue(out.contains("192.168.1.5 19132 typ host"), "the host UDP candidate carries the external port");
        assertTrue(out.contains("203.0.113.99 40000 typ srflx raddr 192.168.1.5 rport 32000"),
                "reflexive candidates are STUN discoveries and stay untouched");
        assertTrue(out.contains("TCP 1518280447 192.168.1.5 9 typ host"), "TCP candidates stay untouched");
        assertTrue(out.contains("a=fingerprint:sha-256 00:11:22"), "the fingerprint line takes no part in the rewrite");
        assertFalse(out.endsWith("\r\n\r\n"), "the trailing empty line stays dropped");
        assertTrue(out.endsWith("\r\n"));
    }

    @Test
    void rewriteLeavesCandidatesOutsideTheWindowAlone() {
        NetherNetUdpPorts ports = NetherNetUdpPorts.parse("19132:32000", LANG);
        assertNotNull(ports);
        String sdp = "a=candidate:1 1 UDP 2130706431 192.168.1.5 54321 typ host\r\n";
        assertTrue(ports.rewriteSdp(sdp).contains(" 54321 typ host"),
                "a gathered port outside the configured window is left unchanged");
    }

    @Test
    void zeroOffsetReturnsTheSdpUntouched() {
        NetherNetUdpPorts ports = NetherNetUdpPorts.parse("32000", LANG);
        assertNotNull(ports);
        String sdp = "a=candidate:1 1 UDP 2130706431 192.168.1.5 32000 typ host\r\n";
        assertSame(sdp, ports.rewriteSdp(sdp), "without a port mapping there is nothing to rewrite");
    }

    @Test
    void decorateReturnsTheDelegateWithoutAShift() {
        NetherNetUdpPorts ports = NetherNetUdpPorts.parse("32000", LANG);
        assertNotNull(ports);
        RecordingSignaling signaling = new RecordingSignaling();
        assertSame(signaling, ports.decorate(signaling), "no offset means no wrapper is needed");
    }

    @Test
    void decoratedSignalingRewritesTheAnswer() {
        NetherNetUdpPorts ports = NetherNetUdpPorts.parse("19132:32000", LANG);
        assertNotNull(ports);
        RecordingSignaling signaling = new RecordingSignaling();
        NetherNetServerSignaling decorated = ports.decorate(signaling);

        assertNotNull(decorated);
        assertNotSame(signaling, decorated);
        decorated.sendFullSdp("peer", "a=candidate:1 1 UDP 2130706431 192.168.1.5 32000 typ host\r\n");
        assertNotNull(signaling.lastSdp, "the wrapper delegates the answer onward");
        assertTrue(signaling.lastSdp.contains(" 19132 typ host"), "the delegated answer carries the external port");
    }

    @Test
    void toStringDescribesTheMapping() {
        assertEquals("udp/32000", NetherNetUdpPorts.parse("32000", LANG).toString());
        assertEquals("udp/32000-32008", NetherNetUdpPorts.parse("32000-32008", LANG).toString());
        assertEquals("udp/32000 published as 19132", NetherNetUdpPorts.parse("19132:32000", LANG).toString());
        assertEquals("udp/32000-32008 published as 19132-19140 via [203.0.113.10]",
                NetherNetUdpPorts.parse("203.0.113.10:19132-19140:32000-32008", LANG).toString());
    }

    private static class RecordingSignaling implements NetherNetServerSignaling {
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
