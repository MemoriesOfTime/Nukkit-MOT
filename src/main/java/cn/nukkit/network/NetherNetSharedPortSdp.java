package cn.nukkit.network;

import io.netty.util.NetUtil;
import lombok.extern.log4j.Log4j2;

import java.net.*;
import java.util.*;

/**
 * 共用端口模式下的 SDP 改写。libjuice 绑定在回环地址，应答里只有 {@code 127.0.0.1:<内部端口>} 的 host 候选，
 * 客户端够不到，这里换成服务器可达地址加 RakNet 监听端口；offer 侧则剥掉客户端候选——回环 socket 发不出去，
 * 留着只会让 libjuice 对每次连通性检查反复告警。
 * <p>
 * SDP rewriting for the shared-port mode. libjuice is bound to loopback, so the answer only
 * carries a {@code 127.0.0.1:<internal port>} host candidate no client can reach; it is replaced
 * with the server's reachable addresses on the RakNet listener ports. The offer loses its
 * candidates: a loopback socket cannot send to them, and libjuice would warn on every check.
 */
@Log4j2
final class NetherNetSharedPortSdp {

    private static final String CANDIDATE_PREFIX = "a=candidate:";
    private static final String UFRAG_PREFIX = "a=ice-ufrag:";
    /** RFC 8445 §5.1.2.1 host 类型偏好。 RFC 8445 §5.1.2.1 type preference for host candidates. */
    private static final long HOST_TYPE_PREFERENCE = 126;
    /** 与库内 SdpUtil 的 80000000/90000000 段错开。 Kept clear of SdpUtil's 80000000/90000000 ranges. */
    private static final int SYNTHESIZED_FOUNDATION_BASE = 70000000;
    static final int MAX_ADDRESSES_PER_FAMILY = 8;

    /** 每个地址族对外发布的端口，-1 表示该族没有 RakNet 监听、不发布候选。 External port per family, -1 means no listener for that family. */
    record ExternalPorts(int ipv4, int ipv6) {
        int forFamily(InetAddress address) {
            return address instanceof Inet6Address ? this.ipv6 : this.ipv4;
        }
    }

    private NetherNetSharedPortSdp() {
    }

    /**
     * 把回环 host 候选换成 {@code addresses} 上的合成候选，其余在内部端口上的 host 候选平移到对外端口；
     * {@code c=} 与 {@code m=} 行同步改写。找不到可达地址时保留回环候选（改到对外端口），
     * 让库内的 advertised-address 翻译仍有同族 host 行可作为基准。
     * Swaps the loopback host candidate for synthesized ones on {@code addresses} and shifts any
     * other host candidate on the internal port to the external port; {@code c=} and {@code m=}
     * follow. With no reachable address the loopback line stays (on the external port) so the
     * library's advertised-address translation still has a same-family host line to build on.
     */
    static String rewriteAnswer(String sdp, int internalPort, List<InetAddress> addresses, ExternalPorts ports) {
        List<String> synthesized = synthesizedCandidates(addresses, ports);
        StringBuilder out = new StringBuilder(sdp.length() + synthesized.size() * 64);
        boolean replaced = false;
        for (String line : sdp.split("\r\n|\n")) {
            // 末尾空行会使 libwebrtc 拒绝整个应答，与 SdpUtil 一并裁掉
            // A trailing empty line makes libwebrtc reject the whole answer, drop it like SdpUtil does
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith(CANDIDATE_PREFIX)) {
                String[] parts = line.split(" ");
                if (isHostUdp(parts)) {
                    InetAddress address = NetUtil.createInetAddressFromIpAddressString(parts[4]);
                    if (address != null && address.isLoopbackAddress()) {
                        if (!synthesized.isEmpty()) {
                            if (!replaced) {
                                appendAll(out, synthesized);
                                replaced = true;
                            }
                            continue;
                        }
                        // 无可达地址：回环行改到对外端口后保留（见方法注释）
                        // No reachable address: keep the loopback line on the external port (see above)
                    }
                    if (address != null && parsePort(parts[5]) == internalPort) {
                        int external = ports.forFamily(address);
                        if (external <= 0) {
                            continue;
                        }
                        parts[5] = Integer.toString(external);
                        line = String.join(" ", parts);
                    }
                }
            } else if (line.startsWith("c=IN IP4 ") || line.startsWith("c=IN IP6 ")) {
                line = rewriteConnectionLine(line, addresses);
            } else if (line.startsWith("m=application " + internalPort + " ") && ports.ipv4() > 0) {
                line = "m=application " + ports.ipv4() + line.substring(("m=application " + internalPort).length());
            }
            out.append(line).append("\r\n");
        }
        return out.toString();
    }

    private static boolean isHostUdp(String[] parts) {
        return parts.length >= 8 && "udp".equalsIgnoreCase(parts[2]) && "typ".equals(parts[6]) && "host".equals(parts[7]);
    }

    private static int parsePort(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 每个地址一行 host 候选，本地偏好按顺序递减（RFC 8445 §5.1.2.1），foundation 各不相同。
     * One host candidate per address, local preference descending in list order
     * (RFC 8445 §5.1.2.1), each with its own foundation.
     */
    static List<String> synthesizedCandidates(List<InetAddress> addresses, ExternalPorts ports) {
        List<String> lines = new ArrayList<>(addresses.size());
        int index = 0;
        for (InetAddress address : addresses) {
            int port = ports.forFamily(address);
            if (port <= 0) {
                continue;
            }
            long priority = (HOST_TYPE_PREFERENCE << 24) | ((long) (65535 - index) << 8) | 255;
            lines.add(CANDIDATE_PREFIX + (SYNTHESIZED_FOUNDATION_BASE + index) + " 1 UDP " + priority + " "
                    + address.getHostAddress() + " " + port + " typ host");
            index++;
        }
        return lines;
    }

    /** 回环的 c= 行换成首个同族可达地址，没有则用通配地址。 A loopback c= line takes the first same-family address, else the wildcard. */
    private static String rewriteConnectionLine(String line, List<InetAddress> addresses) {
        boolean ipv6 = line.startsWith("c=IN IP6 ");
        String prefix = ipv6 ? "c=IN IP6 " : "c=IN IP4 ";
        InetAddress current = NetUtil.createInetAddressFromIpAddressString(line.substring(prefix.length()).trim());
        if (current == null || !current.isLoopbackAddress()) {
            return line;
        }
        for (InetAddress address : addresses) {
            if ((address instanceof Inet6Address) == ipv6) {
                return prefix + address.getHostAddress();
            }
        }
        return prefix + (ipv6 ? "::" : "0.0.0.0");
    }

    /**
     * 剥掉全部候选行。服务端 socket 绑在回环，任何客户端候选都发不到；对端地址由中继 leg 以 prflx 形式带入。
     * Drops every candidate line: the loopback-bound socket cannot reach any of them, the relay leg
     * brings the peer in as a prflx candidate instead.
     */
    static String stripCandidates(String sdp) {
        StringBuilder out = new StringBuilder(sdp.length());
        for (String line : sdp.split("\r\n|\n")) {
            if (line.isEmpty() || line.startsWith(CANDIDATE_PREFIX)) {
                continue;
            }
            out.append(line).append("\r\n");
        }
        return out.toString();
    }

    /** 应答里的本端 ice-ufrag，用作中继放行首个 STUN 请求的凭据。 The local ice-ufrag of an answer, which the relay admits the first STUN request by. */
    static String iceUfrag(String sdp) {
        for (String line : sdp.split("\r\n|\n")) {
            if (line.startsWith(UFRAG_PREFIX)) {
                String ufrag = line.substring(UFRAG_PREFIX.length()).trim();
                return ufrag.isEmpty() ? null : ufrag;
            }
        }
        return null;
    }

    /**
     * 对外发布的地址：server-ip 绑了具体地址就只发布它（哪怕是回环，那是管理员的选择，如本机代理前置），
     * 否则枚举本机网卡；IPv6 仅在有 IPv6 监听时参与，同样优先其绑定地址。枚举失败返回空表，由调用方决定回退。
     * Addresses to publish: a concrete server-ip is published alone (even loopback, that is the
     * operator's call, say behind a local proxy), otherwise the host's interfaces are enumerated;
     * IPv6 only joins while an IPv6 listener exists, likewise preferring its bound address.
     * Enumeration failures yield an empty list for the caller to fall back on.
     */
    static List<InetAddress> localAddresses(String serverIp, String ipv6Address, boolean ipv6Enabled) {
        InetAddress boundV4 = concreteAddress(serverIp);
        InetAddress boundV6 = ipv6Enabled ? concreteAddress(ipv6Address) : null;
        List<InetAddress> configured = new ArrayList<>(2);
        if (boundV4 != null && !(boundV4 instanceof Inet6Address)) {
            configured.add(boundV4);
        }
        if (boundV6 != null) {
            configured.add(boundV6);
        }
        boolean needV4 = configured.stream().noneMatch(address -> address instanceof Inet4Address);
        boolean needV6 = ipv6Enabled && boundV6 == null;
        List<InetAddress> enumerated = new ArrayList<>();
        if (needV4 || needV6) {
            for (InetAddress address : interfaceAddresses()) {
                boolean v6 = address instanceof Inet6Address;
                if ((v6 && needV6) || (!v6 && needV4)) {
                    enumerated.add(address);
                }
            }
        }
        List<InetAddress> published = new ArrayList<>(configured);
        for (InetAddress address : selectAddresses(enumerated, ipv6Enabled)) {
            if (!published.contains(address)) {
                published.add(address);
            }
        }
        return published;
    }

    private static InetAddress concreteAddress(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        InetAddress address = NetUtil.createInetAddressFromIpAddressString(text.trim());
        return address == null || address.isAnyLocalAddress() ? null : address;
    }

    private static List<InetAddress> interfaceAddresses() {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return addresses;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> ifaceAddresses = iface.getInetAddresses();
                while (ifaceAddresses.hasMoreElements()) {
                    addresses.add(ifaceAddresses.nextElement());
                }
            }
        } catch (SocketException e) {
            log.warn("Unable to enumerate network interfaces for the NetherNet shared-port answer", e);
        }
        return addresses;
    }

    /**
     * 只保留对端可能到达的地址：去掉回环、链路本地、组播、通配，去重并保持顺序，IPv4 在前，每族最多
     * {@value #MAX_ADDRESSES_PER_FAMILY} 个（每个候选都要花客户端一轮连通性检查）。
     * Keeps only addresses a peer could reach: no loopback, link-local, multicast or wildcard,
     * de-duplicated in order, IPv4 first, at most {@value #MAX_ADDRESSES_PER_FAMILY} per family
     * (every candidate costs the client a round of connectivity checks).
     */
    static List<InetAddress> selectAddresses(Collection<InetAddress> candidates, boolean includeIpv6) {
        Set<InetAddress> v4 = new LinkedHashSet<>();
        Set<InetAddress> v6 = new LinkedHashSet<>();
        for (InetAddress address : candidates) {
            if (address == null || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isMulticastAddress() || address.isAnyLocalAddress()) {
                continue;
            }
            if (address instanceof Inet6Address) {
                if (includeIpv6 && v6.size() < MAX_ADDRESSES_PER_FAMILY) {
                    v6.add(address);
                }
            } else if (v4.size() < MAX_ADDRESSES_PER_FAMILY) {
                v4.add(address);
            }
        }
        if (v4.isEmpty() && v6.isEmpty()) {
            return Collections.emptyList();
        }
        List<InetAddress> selected = new ArrayList<>(v4.size() + v6.size());
        selected.addAll(v4);
        selected.addAll(v6);
        return selected;
    }

    private static void appendAll(StringBuilder out, List<String> lines) {
        for (String line : lines) {
            out.append(line).append("\r\n");
        }
    }
}
