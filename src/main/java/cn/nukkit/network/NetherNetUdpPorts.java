package cn.nukkit.network;

import cn.nukkit.lang.BaseLang;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetServerSignaling;
import org.cloudburstmc.netty.util.nethernet.EndpointAddress;
import tel.schich.libdatachannel.PeerConnectionConfiguration;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * server.properties 的 server-udp-ports（BDS 同名语法）解析结果：
 * 0=系统自动分配；{@code [ip:]internal[-internal]}=钉住本地端口/窗口；
 * {@code [ip:]external[-external]:internal[-internal]}=另发布对端可达的外部映射，
 * 冒号两侧范围长度必须一致。逗号分隔多条目：IP 累计，窗口与偏移须一致。
 * 钉住的单个端口正是某个 RakNet 监听端口（server-port 或 IPv6，{@link #pinsOnly}）或单端口映射到它
 * （{@link #publishesOn}）时，媒体与 RakNet 共用该端口，前者的内部回环端口由操作系统分配。
 * <p>
 * Parses server-udp-ports from server.properties (BDS syntax): 0=system-assigned;
 * {@code [ip:]internal[-internal]} pins the local port/window;
 * {@code [ip:]external[-external]:internal[-internal]} additionally publishes an
 * externally reachable mapping, range lengths on each side of the colon must match.
 * Comma-separated entries accumulate IPs while window and offset must agree.
 * A single port pinned exactly on a RakNet listener port (server-port or the IPv6 listener,
 * {@link #pinsOnly}) or a single-port mapping onto one ({@link #publishesOn}) shares that
 * port with RakNet, the former leaving the internal loopback port for the OS to assign.
 */
@Log4j2
public final class NetherNetUdpPorts {

    private final int begin;
    private final int end;
    /** 外部端口 = 本地端口 + offset，0 表示无端口映射。 External port = local port + offset, 0 means no port mapping. */
    private final int offset;
    /** 发布给对端的外部地址（裸 IP 字面量）。 External addresses (bare IP literals) announced to peers. */
    private final Set<String> advertisedAddresses;

    private NetherNetUdpPorts(int begin, int end, int offset, Set<String> advertisedAddresses) {
        this.begin = begin;
        this.end = end;
        this.offset = offset;
        this.advertisedAddresses = advertisedAddresses;
    }

    /**
     * 解析配置值，0/空返回 null 表示自动分配；任何无效条目抛出携带本地化消息的
     * {@link IllegalArgumentException}——server.properties 是关键配置，错误即停而非回退。
     * Parses the configured value; null means system-assigned (0 or blank); any invalid
     * entry throws an {@link IllegalArgumentException} carrying a localized message —
     * server.properties is critical config, so errors abort instead of falling back.
     */
    public static NetherNetUdpPorts parse(String value, BaseLang lang) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if ("0".equals(trimmed)) {
            return null;
        }

        int begin = -1;
        int end = -1;
        int offset = 0;
        Set<String> addresses = new LinkedHashSet<>();
        for (String entryText : trimmed.split(",")) {
            Entry entry = parseEntry(entryText, value, lang);
            if (begin < 0) {
                begin = entry.internalBegin;
                end = entry.internalEnd;
                offset = entry.offset;
            } else if (entry.internalBegin != begin || entry.internalEnd != end) {
                throw invalid(lang, "nukkit.nethernet.udpPorts.windowMismatch", value, entryText.trim(), begin, end);
            } else if (entry.hasMapping && entry.offset != offset) {
                throw invalid(lang, "nukkit.nethernet.udpPorts.offsetMismatch", value, entryText.trim(), offset);
            }
            if (entry.address != null) {
                addresses.add(entry.address);
            }
        }
        return begin < 0 ? null : new NetherNetUdpPorts(begin, end, offset, addresses);
    }

    private static IllegalArgumentException invalid(BaseLang lang, String key, Object... params) {
        return new IllegalArgumentException(lang.translateString(key, params));
    }

    /**
     * 单条目语法 {@code [ip:]range[:range]}，IPv6 必须带方括号；含点的首组按 IPv4 前缀处理（端口不含点）。
     * Single entry {@code [ip:]range[:range]}; IPv6 requires brackets, a dotted first group is
     * an IPv4 prefix (ports never carry dots).
     */
    private static Entry parseEntry(String text, String whole, BaseLang lang) {
        String t = text.trim();
        String address = null;

        if (t.startsWith("[")) {
            int close = t.indexOf(']');
            if (close < 0 || close + 1 >= t.length() || t.charAt(close + 1) != ':') {
                throw invalid(lang, "nukkit.nethernet.udpPorts.badIpv6", whole, t);
            }
            address = canonicalAddress(t.substring(1, close), whole, t, lang);
            t = t.substring(close + 2);
        }

        String[] groups = t.split(":", -1);
        String externalText = null;
        String internalText;
        switch (groups.length) {
            case 1 -> internalText = groups[0];
            case 2 -> {
                if (groups[0].contains(".")) {
                    address = canonicalAddress(groups[0], whole, t, lang);
                    internalText = groups[1];
                } else {
                    externalText = groups[0];
                    internalText = groups[1];
                }
            }
            case 3 -> {
                address = canonicalAddress(groups[0], whole, t, lang);
                externalText = groups[1];
                internalText = groups[2];
            }
            default -> throw invalid(lang, "nukkit.nethernet.udpPorts.badShape", whole, t);
        }

        int[] internal = parseRange(internalText, whole, t, lang);
        if (externalText == null) {
            return new Entry(internal[0], internal[1], 0, false, address);
        }

        int[] external = parseRange(externalText, whole, t, lang);
        int externalSpan = external[1] - external[0];
        int internalSpan = internal[1] - internal[0];
        if (externalSpan != internalSpan) {
            throw invalid(lang, "nukkit.nethernet.udpPorts.rangeLengthMismatch", whole, t);
        }
        return new Entry(internal[0], internal[1], external[0] - internal[0], true, address);
    }

    private static String canonicalAddress(String text, String whole, String entry, BaseLang lang) {
        try {
            return EndpointAddress.parse(text).getHostAddress();
        } catch (UnknownHostException e) {
            throw invalid(lang, "nukkit.nethernet.udpPorts.notIpLiteral", whole, text, entry);
        }
    }

    private static int[] parseRange(String text, String whole, String entry, BaseLang lang) {
        int dash = text.indexOf('-');
        try {
            if (dash >= 0) {
                return validatedRange(Integer.parseInt(text.substring(0, dash).trim()),
                        Integer.parseInt(text.substring(dash + 1).trim()), text, whole, entry, lang);
            }
            int port = Integer.parseInt(text.trim());
            return validatedRange(port, port, text, whole, entry, lang);
        } catch (NumberFormatException e) {
            throw invalid(lang, "nukkit.nethernet.udpPorts.notPortRange", whole, text, entry);
        }
    }

    private static int[] validatedRange(int begin, int end, String text, String whole, String entry, BaseLang lang) {
        if (begin < 1 || end < 1 || begin > 65535 || end > 65535 || begin > end) {
            throw invalid(lang, "nukkit.nethernet.udpPorts.invalidRange", whole, text, entry);
        }
        return new int[]{begin, end};
    }

    private record Entry(int internalBegin, int internalEnd, int offset, boolean hasMapping, String address) {
    }

    public boolean contains(int port) {
        return port >= this.begin && port <= this.end;
    }

    int begin() {
        return this.begin;
    }

    int end() {
        return this.end;
    }

    int offset() {
        return this.offset;
    }

    public Set<String> advertisedAddresses() {
        return this.advertisedAddresses;
    }

    /**
     * 钉端口的 peer connection 配置：mux + 端口窗口；绑定到具体 IP 时同步约束 ICE，
     * 通配地址保持全接口收集（与库内 bindIce 的钉端口路径同规则）。
     * The pinning peer connection config: mux + the window; a concrete bind address also
     * constrains ICE, a wildcard one keeps gathering on every interface (same rule the
     * library's own bindIce pinning path applies).
     */
    public PeerConnectionConfiguration peerConfig(InetSocketAddress bindAddress) {
        PeerConnectionConfiguration config = PeerConnectionConfiguration.DEFAULT
                .withEnableIceUdpMux(true)
                .withPortRangeBegin(this.begin)
                .withPortRangeEnd(this.end);
        InetAddress host = bindAddress.getAddress();
        if (host != null && !host.isAnyLocalAddress()) {
            config = config.withBindAddress(host);
        }
        return config;
    }

    /**
     * 把 host/UDP 候选行的端口平移到外部窗口（offset 为 0 原样返回）。
     * srflx/relay 候选是 STUN/TURN 实测结果，保持不动；候选行不参与身份签名
     * （签名只覆盖 a=fingerprint），库自身的地址改写同样发生在签名之后。
     * Shifts the port of host/UDP candidate lines into the external window
     * (returns the input unchanged when the offset is 0). srflx/relay candidates
     * are STUN/TURN discoveries and stay; candidate lines take no part in the
     * identity signature (it covers a=fingerprint only), and the library's own
     * address rewriting happens after signing just the same.
     */
    public String rewriteSdp(String sdp) {
        if (this.offset == 0) {
            return sdp;
        }
        StringBuilder out = new StringBuilder(sdp.length());
        for (String line : sdp.split("\r\n|\n")) {
            // 末尾空行会使 libwebrtc 拒绝整个应答，与 SdpUtil 一并裁掉
            // A trailing empty line makes libwebrtc reject the whole answer, drop it like SdpUtil does
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("a=candidate:")) {
                String rewritten = rewriteCandidate(line);
                if (rewritten != null) {
                    line = rewritten;
                }
            }
            out.append(line).append("\r\n");
        }
        return out.toString();
    }

    /**
     * 候选行按空格分词后端口在第六列（foundation/component/transport/priority/address/port）。
     * Candidate tokens split on spaces put the port sixth
     * (foundation/component/transport/priority/address/port).
     */
    private String rewriteCandidate(String line) {
        String[] parts = line.split(" ");
        if (parts.length < 8 || !"udp".equalsIgnoreCase(parts[2]) || !"typ".equals(parts[6]) || !"host".equals(parts[7])) {
            return null;
        }
        int port;
        try {
            port = Integer.parseInt(parts[5]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (!this.contains(port)) {
            log.warn("A gathered host candidate port {} sits outside the configured window {}-{}, leaving it unchanged", port, this.begin, this.end);
            return null;
        }
        parts[5] = Integer.toString(port + this.offset);
        return String.join(" ", parts);
    }

    public NetherNetServerSignaling decorate(NetherNetServerSignaling delegate) {
        return this.offset == 0 ? delegate : new PortRewritingSignaling(delegate);
    }

    /**
     * 单端口映射且外部端口正是 {@code port}（某个 RakNet 监听端口）：媒体要在该端口的 UDP 侧对外发布，
     * 由 {@link NetherNetMediaRelay} 在进程内转发，而不是指望外部 NAT。
     * A single-port mapping whose external port is {@code port} (a RakNet listener port): media is
     * published on that port's UDP side and {@link NetherNetMediaRelay} forwards it in-process
     * instead of relying on an external NAT.
     */
    public boolean publishesOn(int port) {
        return this.offset != 0 && this.begin == this.end && this.begin + this.offset == port;
    }

    /**
     * 未映射的单个端口正是 {@code port}（某个 RakNet 监听端口）：同样表示与 RakNet 共用该端口，只是内部
     * 回环端口留待系统分配，再经 {@link #relayedThrough} 变成 {@link #publishesOn} 形式的单端口映射。
     * A single unmapped port that is exactly {@code port} (a RakNet listener port): sharing with
     * RakNet as well, only the internal loopback port is left for the OS to assign, after which
     * {@link #relayedThrough} turns this into the {@link #publishesOn} single-port mapping.
     */
    public boolean pinsOnly(int port) {
        return this.offset == 0 && this.begin == this.end && this.begin == port;
    }

    /**
     * 与监听在 {@code port} 上的 RakNet 共用端口的两种单端口形态（钉住或映射），IPv4/IPv6 监听一视同仁。
     * Either single-port shape sharing the RakNet listener on {@code port} (pinned or mapped),
     * treating the IPv4 and IPv6 listeners alike.
     */
    public boolean sharesPort(int port) {
        return this.pinsOnly(port) || this.publishesOn(port);
    }

    /**
     * 把 {@link #pinsOnly} 的单端口改成经 {@code internalPort} 中继到它的映射（即 {@code ext:internalPort}），地址前缀保留。
     * Turns a {@link #pinsOnly} single port into the mapping relayed through {@code internalPort}
     * ({@code ext:internalPort}), keeping any address prefix.
     */
    NetherNetUdpPorts relayedThrough(int internalPort) {
        if (this.offset != 0 || this.begin != this.end) {
            throw new IllegalStateException("only a single unmapped port can be relayed, got " + this);
        }
        return new NetherNetUdpPorts(internalPort, internalPort, this.begin - internalPort, this.advertisedAddresses);
    }

    /** 仅拦截非 trickle 应答的唯一出口 sendFullSdp。 Intercepts only the single non-trickle answer exit, sendFullSdp. */
    private final class PortRewritingSignaling extends NetherNetDelegatingSignaling {

        private PortRewritingSignaling(NetherNetServerSignaling delegate) {
            super(delegate);
        }

        @Override
        public void sendFullSdp(String targetNetworkId, String sdp) {
            this.delegate.sendFullSdp(targetNetworkId, NetherNetUdpPorts.this.rewriteSdp(sdp));
        }
    }

    @Override
    public String toString() {
        String window = this.begin == this.end ? String.valueOf(this.begin) : this.begin + "-" + this.end;
        StringBuilder out = new StringBuilder("udp/").append(window);
        if (this.offset != 0) {
            int externalBegin = this.begin + this.offset;
            int externalEnd = this.end + this.offset;
            out.append(" published as ").append(externalBegin == externalEnd
                    ? String.valueOf(externalBegin) : externalBegin + "-" + externalEnd);
        }
        if (!this.advertisedAddresses.isEmpty()) {
            out.append(" via ").append(this.advertisedAddresses);
        }
        return out.toString();
    }
}
