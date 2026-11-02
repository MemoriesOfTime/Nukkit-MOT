package cn.nukkit.network;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 共用端口模式的 SDP 改写：回环 host 候选换成可达地址 + RakNet 端口，offer 剥候选，ufrag 提取，地址筛选。
 * Shared-port SDP rewriting: loopback host candidates become reachable addresses on the RakNet
 * ports, offers lose their candidates, ufrag extraction and address selection.
 */
class NetherNetSharedPortSdpTest {

    private static final String ANSWER = "v=0\r\n"
            + "o=rtc 3276522793 0 IN IP4 127.0.0.1\r\n"
            + "s=-\r\n"
            + "t=0 0\r\n"
            + "a=group:BUNDLE 0\r\n"
            + "a=fingerprint:sha-256 E5:36:EF:D7:E8:A4:46:33:70:CD:28:08:C1:4F:24:FA:31:B2:17:48:66:47:29:F0:AE:3A:08:08:4F:DE:57:03\r\n"
            + "m=application 19134 UDP/DTLS/SCTP webrtc-datachannel\r\n"
            + "c=IN IP4 127.0.0.1\r\n"
            + "a=mid:0\r\n"
            + "a=setup:active\r\n"
            + "a=ice-ufrag:EkS2\r\n"
            + "a=ice-pwd:U6L845lAZWUnjy9DmYJtxe\r\n"
            + "a=candidate:1 1 UDP 2114977791 127.0.0.1 19134 typ host\r\n"
            + "a=end-of-candidates\r\n"
            + "\r\n";

    private static InetAddress address(String literal) throws Exception {
        return InetAddress.getByName(literal);
    }

    @Test
    void replacesTheLoopbackHostCandidateWithReachableAddressesOnTheRakNetPorts() throws Exception {
        List<InetAddress> addresses = List.of(address("192.168.1.5"), address("203.0.113.7"), address("2001:db8::1"));
        String out = NetherNetSharedPortSdp.rewriteAnswer(ANSWER, 19134, addresses,
                new NetherNetSharedPortSdp.ExternalPorts(19132, 19133));

        assertFalse(out.contains("127.0.0.1 19134"), "the loopback candidate never reaches the client");
        assertFalse(out.contains(" 19134 typ host"), "no candidate carries the internal port");
        assertTrue(out.contains(" 192.168.1.5 19132 typ host"), "IPv4 addresses publish the IPv4 listener port: " + out);
        assertTrue(out.contains(" 203.0.113.7 19132 typ host"));
        assertTrue(out.contains(" 2001:db8:0:0:0:0:0:1 19133 typ host"), "IPv6 addresses publish the IPv6 listener port: " + out);
        assertTrue(out.contains("m=application 19132 UDP/DTLS/SCTP webrtc-datachannel"), "the m= line follows the IPv4 port");
        assertTrue(out.contains("c=IN IP4 192.168.1.5"), "the c= line names the first IPv4 address");
        assertTrue(out.contains("a=ice-ufrag:EkS2"), "credentials stay untouched");
        assertTrue(out.contains("a=fingerprint:sha-256 E5:36"), "the fingerprint takes no part in the rewrite");
        assertTrue(out.indexOf("typ host") < out.indexOf("a=end-of-candidates"), "candidates precede end-of-candidates");
        assertFalse(out.endsWith("\r\n\r\n"), "the trailing empty line stays dropped");

        // 每行 foundation 与优先级各不相同 Every line carries its own foundation and priority
        long distinctFoundations = out.lines().filter(line -> line.startsWith("a=candidate:"))
                .map(line -> line.split(" ")[0]).distinct().count();
        long distinctPriorities = out.lines().filter(line -> line.startsWith("a=candidate:"))
                .map(line -> line.split(" ")[3]).distinct().count();
        assertEquals(3, distinctFoundations);
        assertEquals(3, distinctPriorities);
    }

    @Test
    void skipsIpv6AddressesWithoutAnIpv6Listener() throws Exception {
        List<InetAddress> addresses = List.of(address("192.168.1.5"), address("2001:db8::1"));
        String out = NetherNetSharedPortSdp.rewriteAnswer(ANSWER, 19134, addresses,
                new NetherNetSharedPortSdp.ExternalPorts(19132, -1));
        assertTrue(out.contains(" 192.168.1.5 19132 typ host"));
        assertFalse(out.contains("2001:db8"), "no IPv6 listener means no IPv6 candidate");
    }

    @Test
    void keepsTheLoopbackLineOnTheExternalPortWhenNothingIsReachable() {
        String out = NetherNetSharedPortSdp.rewriteAnswer(ANSWER, 19134, List.of(),
                new NetherNetSharedPortSdp.ExternalPorts(19132, -1));
        assertTrue(out.contains("127.0.0.1 19132 typ host"),
                "with no reachable address the loopback line stays as a translation base, on the external port: " + out);
        assertTrue(out.contains("c=IN IP4 0.0.0.0"), "the c= line falls back to the wildcard");
    }

    @Test
    void leavesReflexiveCandidatesAndForeignHostLinesAlone() throws Exception {
        String sdp = "a=candidate:1 1 UDP 2114977791 127.0.0.1 19134 typ host\r\n"
                + "a=candidate:2 1 UDP 1694498815 203.0.113.99 40000 typ srflx raddr 127.0.0.1 rport 19134\r\n"
                + "a=candidate:3 1 UDP 2114977791 10.0.0.5 50000 typ host\r\n";
        String out = NetherNetSharedPortSdp.rewriteAnswer(sdp, 19134, List.of(address("192.168.1.5")),
                new NetherNetSharedPortSdp.ExternalPorts(19132, -1));
        assertTrue(out.contains("203.0.113.99 40000 typ srflx raddr 127.0.0.1 rport 19134"), "srflx lines are STUN discoveries and stay");
        assertTrue(out.contains("10.0.0.5 50000 typ host"), "a host line outside the internal port is untouched");
        assertTrue(out.contains("192.168.1.5 19132 typ host"));
    }

    @Test
    void stripsEveryCandidateFromTheOffer() {
        String offer = "v=0\r\n"
                + "a=ice-ufrag:abcd\r\n"
                + "a=candidate:1 1 udp 2130706431 192.168.1.100 12345 typ host\r\n"
                + "a=candidate:2 1 udp 1694498815 203.0.113.50 54321 typ srflx raddr 192.168.1.100 rport 12345\r\n"
                + "a=end-of-candidates\r\n"
                + "\r\n";
        String out = NetherNetSharedPortSdp.stripCandidates(offer);
        assertEquals("v=0\r\na=ice-ufrag:abcd\r\na=end-of-candidates\r\n", out);
    }

    @Test
    void readsTheIceUfrag() {
        assertEquals("EkS2", NetherNetSharedPortSdp.iceUfrag(ANSWER));
        assertNull(NetherNetSharedPortSdp.iceUfrag("v=0\r\na=ice-pwd:x\r\n"));
        assertNull(NetherNetSharedPortSdp.iceUfrag("a=ice-ufrag:   \r\n"));
    }

    @Test
    void selectsOnlyAddressesAPeerCouldReach() throws Exception {
        List<InetAddress> candidates = List.of(
                address("127.0.0.1"), address("169.254.10.1"), address("224.0.0.1"), address("0.0.0.0"),
                address("192.168.1.5"), address("192.168.1.5"), address("203.0.113.7"),
                address("::1"), address("fe80::1"), address("2001:db8::1"), address("fd00::5"));
        List<InetAddress> selected = NetherNetSharedPortSdp.selectAddresses(candidates, true);
        assertEquals(List.of(address("192.168.1.5"), address("203.0.113.7"), address("2001:db8::1"), address("fd00::5")), selected,
                "loopback, link-local, multicast and wildcard drop out, duplicates collapse, IPv4 leads");

        assertEquals(List.of(address("192.168.1.5"), address("203.0.113.7")),
                NetherNetSharedPortSdp.selectAddresses(candidates, false), "IPv6 is left out without an IPv6 listener");
        assertTrue(NetherNetSharedPortSdp.selectAddresses(List.of(address("127.0.0.1")), true).isEmpty());
    }

    @Test
    void capsAddressesPerFamily() throws Exception {
        List<InetAddress> many = new java.util.ArrayList<>();
        for (int i = 1; i <= NetherNetSharedPortSdp.MAX_ADDRESSES_PER_FAMILY + 3; i++) {
            many.add(address("10.0.0." + i));
        }
        assertEquals(NetherNetSharedPortSdp.MAX_ADDRESSES_PER_FAMILY, NetherNetSharedPortSdp.selectAddresses(many, false).size());
    }

    @Test
    void publishesTheBoundServerIpAlone() throws Exception {
        List<InetAddress> addresses = NetherNetSharedPortSdp.localAddresses("192.0.2.10", "::", false);
        assertEquals(List.of(address("192.0.2.10")), addresses, "a concrete server-ip is the only address published");
        assertEquals(List.of(address("127.0.0.1")), NetherNetSharedPortSdp.localAddresses("127.0.0.1", "::", false),
                "a loopback-only server is the operator's choice and is published as configured");
    }

    @Test
    void wildcardServerIpEnumeratesInterfacesWithoutLoopback() {
        List<InetAddress> addresses = NetherNetSharedPortSdp.localAddresses("0.0.0.0", "::", false);
        assertTrue(addresses.stream().noneMatch(InetAddress::isLoopbackAddress));
        assertTrue(addresses.stream().noneMatch(address -> address instanceof java.net.Inet6Address),
                "IPv6 stays out while the IPv6 listener is disabled");
    }
}
