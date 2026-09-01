package cn.nukkit.network.proxy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProxiedProtocol;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Decodes UDP Proxy Protocol v2 packets before RakNet routing so child channels are keyed by real clients,
 * then remaps outbound datagrams back to the proxy socket.
 */
@Log4j2
public class ProxyProtocolHandler extends ChannelDuplexHandler {

    private static final int MAPPING_TTL_MINUTES = 31;

    private final boolean whitelistConfigured;
    private final CidrBlock[] whitelistCidrs;
    private final Cache<InetSocketAddress, InetSocketAddress> proxyToRealCache;
    private final Cache<InetSocketAddress, InetSocketAddress> realToProxyCache;

    /**
     * @param whitelist list of allowed proxy IPs/CIDRs (e.g. "127.0.0.1", "10.0.0.0/8", "2001:db8::/32"). Empty means allow all.
     */
    public ProxyProtocolHandler(List<String> whitelist) {
        this(whitelist, Ticker.systemTicker());
    }

    ProxyProtocolHandler(List<String> whitelist, Ticker ticker) {
        this.whitelistConfigured = whitelist != null && !whitelist.isEmpty();
        this.whitelistCidrs = parseCidrs(whitelist);
        this.proxyToRealCache = Caffeine.newBuilder()
                .expireAfterAccess(MAPPING_TTL_MINUTES, TimeUnit.MINUTES)
                .ticker(ticker)
                .build();
        this.realToProxyCache = Caffeine.newBuilder()
                .expireAfterAccess(MAPPING_TTL_MINUTES, TimeUnit.MINUTES)
                .ticker(ticker)
                .build();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof DatagramPacket packet)) {
            ctx.fireChannelRead(msg);
            return;
        }

        InetSocketAddress sender = packet.sender();
        ByteBuf content = packet.content();
        int version = ProxyProtocolDecoder.findVersion(content);
        InetSocketAddress activeAddress = this.proxyToRealCache.getIfPresent(sender);

        if (version == -1) {
            if (activeAddress != null) {
                forwardDatagram(ctx, packet, activeAddress);
                return;
            }

            if (!whitelistConfigured || !isWhitelisted(sender.getAddress())) {
                ctx.fireChannelRead(msg);
                return;
            }

            log.warn("[ProxyProtocol] Dropping headerless packet from whitelisted proxy address: {}", sender);
            ReferenceCountUtil.release(msg);
            return;
        }

        if (version == ProxyProtocolDecoder.INVALID_HEADER) {
            log.warn("[ProxyProtocol] Dropping invalid or incomplete PP v2 header from {}", sender);
            ReferenceCountUtil.release(msg);
            return;
        }

        if (whitelistConfigured && !isWhitelisted(sender.getAddress())) {
            log.warn("[ProxyProtocol] Packet from non-whitelisted address: {}", sender.getAddress().getHostAddress());
            ReferenceCountUtil.release(msg);
            return;
        }

        HAProxyMessage proxyMessage = decodeProxyHeader(content, version, sender);
        if (proxyMessage == null) {
            log.warn("[ProxyProtocol] Dropping invalid or incomplete PP v2 header from {}", sender);
            ReferenceCountUtil.release(msg);
            return;
        }

        try {
            if (!isSupportedProxiedProtocol(proxyMessage)) {
                log.warn("[ProxyProtocol] Dropping unsupported PP v2 proxied protocol {} from {}",
                        proxyMessage.proxiedProtocol(), sender);
                ReferenceCountUtil.release(msg);
                return;
            }
            InetSocketAddress clientAddress = getClientAddress(sender, proxyMessage);
            if (hasSourceAddress(proxyMessage)) {
                rememberMapping(sender, clientAddress);
            }
            forwardDatagram(ctx, packet, clientAddress);
        } finally {
            ReferenceCountUtil.release(proxyMessage);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof DatagramPacket packet)) {
            ctx.write(msg, promise);
            return;
        }

        InetSocketAddress proxyAddress = findProxyAddressByRealAddress(packet.recipient());
        if (proxyAddress == null) {
            ctx.write(msg, promise);
            return;
        }

        ctx.write(new DatagramPacket(packet.content().retain(), proxyAddress, packet.sender()), promise);
        ReferenceCountUtil.release(msg);
    }

    private static void forwardDatagram(ChannelHandlerContext ctx, DatagramPacket packet, InetSocketAddress sender) {
        try {
            ctx.fireChannelRead(new DatagramPacket(packet.content().retain(), packet.recipient(), sender));
        } finally {
            ReferenceCountUtil.release(packet);
        }
    }

    public void clearMappingByRealAddress(InetSocketAddress realAddress) {
        if (realAddress == null) {
            return;
        }

        InetSocketAddress proxyAddress = this.realToProxyCache.getIfPresent(realAddress);
        if (proxyAddress != null) {
            this.realToProxyCache.invalidate(realAddress);
            invalidateProxyMappingIfCurrent(proxyAddress, realAddress);
            return;
        }

        this.realToProxyCache.asMap().entrySet().removeIf(entry -> {
            if (!sameAddress(entry.getKey(), realAddress)) {
                return false;
            }
            invalidateProxyMappingIfCurrent(entry.getValue(), realAddress);
            return true;
        });
        this.proxyToRealCache.asMap().entrySet().removeIf(entry -> sameAddress(entry.getValue(), realAddress));
    }

    private void rememberMapping(InetSocketAddress proxyAddress, InetSocketAddress realAddress) {
        if (proxyAddress == null || realAddress == null) {
            return;
        }

        this.proxyToRealCache.put(proxyAddress, realAddress);
        this.realToProxyCache.put(realAddress, proxyAddress);
    }

    private InetSocketAddress findProxyAddressByRealAddress(InetSocketAddress realAddress) {
        InetSocketAddress proxyAddress = this.realToProxyCache.getIfPresent(realAddress);
        if (proxyAddress != null) {
            return proxyAddress;
        }

        return this.realToProxyCache.asMap().entrySet().stream()
                .filter(entry -> sameAddress(entry.getKey(), realAddress))
                .map(entry -> entry.getValue())
                .findFirst()
                .orElse(null);
    }

    private void invalidateProxyMappingIfCurrent(InetSocketAddress proxyAddress, InetSocketAddress realAddress) {
        InetSocketAddress currentRealAddress = this.proxyToRealCache.getIfPresent(proxyAddress);
        if (sameAddress(currentRealAddress, realAddress)) {
            this.proxyToRealCache.invalidate(proxyAddress);
        }
    }

    private static boolean sameAddress(InetSocketAddress first, InetSocketAddress second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.getPort() != second.getPort()) {
            return false;
        }
        InetAddress firstAddr = first.getAddress();
        InetAddress secondAddr = second.getAddress();
        if (firstAddr != null && secondAddr != null) {
            return firstAddr.equals(secondAddr);
        }
        return first.getHostString().equals(second.getHostString());
    }

    private static HAProxyMessage decodeProxyHeader(ByteBuf content, int version, InetSocketAddress sender) {
        try {
            return org.cloudburstmc.netty.util.ProxyProtocolDecoder.decode(content, version);
        } catch (RuntimeException e) {
            log.debug("[ProxyProtocol] Malformed PP v{} header from {}", version, sender, e);
            return null;
        }
    }

    private static InetSocketAddress getClientAddress(InetSocketAddress sender, HAProxyMessage proxyMessage) {
        String sourceAddress = proxyMessage.sourceAddress();
        if (sourceAddress == null || sourceAddress.isBlank()) {
            return sender;
        }
        return new InetSocketAddress(sourceAddress, proxyMessage.sourcePort());
    }

    private static boolean isSupportedProxiedProtocol(HAProxyMessage proxyMessage) {
        HAProxyProxiedProtocol proxiedProtocol = proxyMessage.proxiedProtocol();
        return proxiedProtocol == HAProxyProxiedProtocol.UDP4 || proxiedProtocol == HAProxyProxiedProtocol.UDP6;
    }

    private static boolean hasSourceAddress(HAProxyMessage proxyMessage) {
        String sourceAddress = proxyMessage.sourceAddress();
        return sourceAddress != null && !sourceAddress.isBlank();
    }

    private boolean isWhitelisted(InetAddress address) {
        if (address == null) {
            return false;
        }
        byte[] addrBytes = address.getAddress();
        if (isTrustedLocalIpv6Address(address, addrBytes)) {
            return true;
        }
        for (CidrBlock cidr : whitelistCidrs) {
            if (cidr.matches(addrBytes)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse IPv4/IPv6 CIDR strings into normalized network blocks.
     */
    private static CidrBlock[] parseCidrs(List<String> cidrs) {
        if (cidrs == null || cidrs.isEmpty()) {
            return new CidrBlock[0];
        }
        return cidrs.stream()
                .map(cidr -> {
                    if (cidr == null || cidr.trim().isEmpty()) {
                        log.warn("Invalid proxy protocol whitelist entry: {}", cidr);
                        return null;
                    }
                    String[] parts = cidr.trim().split("/", 2);
                    try {
                        InetAddress addr = InetAddress.getByName(parts[0].trim());
                        byte[] bytes = addr.getAddress();
                        int maxPrefix = bytes.length * Byte.SIZE;
                        int prefix = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : maxPrefix;
                        if (prefix < 0 || prefix > maxPrefix) {
                            log.warn("Invalid proxy protocol whitelist prefix length: {}", cidr);
                            return null;
                        }
                        return new CidrBlock(maskAddress(bytes, prefix), prefix);
                    } catch (UnknownHostException | NumberFormatException e) {
                        log.warn("Invalid proxy protocol whitelist entry: {}", cidr, e);
                        return null;
                    }
                })
                .filter(cidr -> cidr != null)
                .toArray(CidrBlock[]::new);
    }

    private static boolean isTrustedLocalIpv6Address(InetAddress address, byte[] bytes) {
        return bytes.length == 16
                && (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || isUniqueLocalIpv6Address(bytes));
    }

    private static boolean isUniqueLocalIpv6Address(byte[] bytes) {
        return (bytes[0] & 0xFE) == 0xFC;
    }

    private static byte[] maskAddress(byte[] address, int prefixLength) {
        byte[] network = address.clone();
        int fullBytes = prefixLength / Byte.SIZE;
        int remainingBits = prefixLength % Byte.SIZE;

        if (remainingBits != 0 && fullBytes < network.length) {
            int mask = (0xFF << (Byte.SIZE - remainingBits)) & 0xFF;
            network[fullBytes] = (byte) ((network[fullBytes] & 0xFF) & mask);
            fullBytes++;
        }

        for (int i = fullBytes; i < network.length; i++) {
            network[i] = 0;
        }
        return network;
    }

    private static final class CidrBlock {

        private final byte[] network;
        private final int prefixLength;

        private CidrBlock(byte[] network, int prefixLength) {
            this.network = network;
            this.prefixLength = prefixLength;
        }

        private boolean matches(byte[] address) {
            if (address.length != this.network.length) {
                return false;
            }

            int fullBytes = this.prefixLength / Byte.SIZE;
            int remainingBits = this.prefixLength % Byte.SIZE;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != this.network[i]) {
                    return false;
                }
            }

            if (remainingBits == 0) {
                return true;
            }

            int mask = (0xFF << (Byte.SIZE - remainingBits)) & 0xFF;
            return ((address[fullBytes] & 0xFF) & mask) == ((this.network[fullBytes] & 0xFF) & mask);
        }
    }
}
