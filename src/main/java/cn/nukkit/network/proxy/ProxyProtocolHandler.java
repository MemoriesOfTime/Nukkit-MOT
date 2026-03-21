package cn.nukkit.network.proxy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Netty channel handler that decodes HAProxy Proxy Protocol v2 headers from incoming UDP datagrams,
 * replacing the sender address with the real client address, and remaps outbound responses
 * back to the proxy address.
 * <p>
 * Adapted from GeyserMC (<a href="https://github.com/GeyserMC/Geyser">GeyserMC</a>)
 */
@Log4j2
@ChannelHandler.Sharable
public class ProxyProtocolHandler extends ChannelDuplexHandler {

    private static final int MAPPING_TTL_MINUTES = 31;

    // proxy address → real client address
    private final Cache<InetSocketAddress, InetSocketAddress> proxyToRealCache = Caffeine.newBuilder()
            .expireAfterAccess(MAPPING_TTL_MINUTES, TimeUnit.MINUTES)
            .build();

    // real client address → proxy address (reverse mapping for outbound)
    private final Cache<InetSocketAddress, InetSocketAddress> realToProxyCache = Caffeine.newBuilder()
            .expireAfterAccess(MAPPING_TTL_MINUTES, TimeUnit.MINUTES)
            .build();

    private final boolean whitelistConfigured;
    private final long[] whitelistCidrs;

    /**
     * @param whitelist list of allowed proxy IPs/CIDRs (e.g. "127.0.0.1", "10.0.0.0/8"). Empty means allow all.
     */
    public ProxyProtocolHandler(List<String> whitelist) {
        this.whitelistConfigured = whitelist != null && !whitelist.isEmpty();
        this.whitelistCidrs = parseCidrs(whitelist);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof DatagramPacket packet)) {
            ctx.fireChannelRead(msg);
            return;
        }

        InetSocketAddress sender = packet.sender();
        ByteBuf content = packet.content();

        try {
            InetSocketAddress cached = proxyToRealCache.getIfPresent(sender);
            int version = ProxyProtocolDecoder.findVersion(content);
            if (version == -1) {
                if (cached != null) {
                    ctx.fireChannelRead(new DatagramPacket(content.retain(), packet.recipient(), cached));
                } else if (!whitelistConfigured || !isWhitelisted(sender.getAddress())) {
                    ctx.fireChannelRead(new DatagramPacket(content.retain(), packet.recipient(), sender));
                } else {
                    log.warn("[ProxyProtocol] Dropping headerless packet from whitelisted proxy address: {}", sender);
                }
                return;
            }

            if (version == ProxyProtocolDecoder.INVALID_HEADER) {
                log.warn("[ProxyProtocol] Dropping invalid or incomplete PP v2 header from {}", sender);
                return;
            }
            if (whitelistConfigured && !isWhitelisted(sender.getAddress())) {
                log.warn("[ProxyProtocol] Packet from non-whitelisted address: {}", sender.getAddress().getHostAddress());
                return;
            }

            HAProxyMessage message = null;
            try {
                message = ProxyProtocolDecoder.decode(content, version);
                if (message == null || message.sourceAddress() == null) {
                    ctx.fireChannelRead(new DatagramPacket(content.retain(), packet.recipient(), sender));
                    return;
                }

                InetSocketAddress realAddress = new InetSocketAddress(
                        InetAddress.getByName(message.sourceAddress()),
                        message.sourcePort()
                );
                // Clean up stale reverse mapping if real address changed
                if (cached != null && !sameAddress(cached, realAddress)) {
                    realToProxyCache.invalidate(cached);
                    log.debug("[ProxyProtocol] Updated mapping proxy {} -> real client {} (was {})",
                            sender, realAddress, cached);
                } else if (cached == null) {
                    log.debug("[ProxyProtocol] Mapped proxy {} -> real client {} (remaining bytes: {})",
                            sender, realAddress, content.readableBytes());
                }

                proxyToRealCache.put(sender, realAddress);
                realToProxyCache.put(realAddress, sender);

                ctx.fireChannelRead(new DatagramPacket(content.retain(), packet.recipient(), realAddress));
            } catch (UnknownHostException e) {
                log.warn("[ProxyProtocol] Failed to resolve source address: {}", message != null ? message.sourceAddress() : "unknown", e);
            } catch (RuntimeException e) {
                log.warn("[ProxyProtocol] Exception decoding PP v2 header from {}", sender, e);
            } finally {
                if (message != null) {
                    message.release();
                }
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof DatagramPacket packet)) {
            ctx.write(msg, promise);
            return;
        }

        InetSocketAddress recipient = packet.recipient();
        // Remap real client address back to proxy address for outbound packets
        InetSocketAddress proxyAddress = realToProxyCache.getIfPresent(recipient);
        if (proxyAddress != null) {
            ctx.write(new DatagramPacket(packet.content().retain(), proxyAddress, packet.sender()), promise);
            ReferenceCountUtil.release(msg);
        } else {
            ctx.write(msg, promise);
        }
    }

    public void clearMappingByRealAddress(InetSocketAddress realAddress) {
        if (realAddress == null) {
            return;
        }

        InetSocketAddress proxyAddress = realToProxyCache.getIfPresent(realAddress);
        if (proxyAddress != null) {
            realToProxyCache.invalidate(realAddress);
            proxyToRealCache.invalidate(proxyAddress);
            return;
        }

        // Fallback for potential address object mismatch (resolved/unresolved variants).
        realToProxyCache.asMap().entrySet().removeIf(entry -> {
            if (!sameAddress(entry.getKey(), realAddress)) {
                return false;
            }
            proxyToRealCache.invalidate(entry.getValue());
            return true;
        });
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

    private boolean isWhitelisted(InetAddress address) {
        byte[] addrBytes = address.getAddress();
        long addrLong = bytesToLong(addrBytes);
        for (long cidr : whitelistCidrs) {
            long network = (cidr >> 8) & 0xFFFFFFFFL;
            int prefixLen = (int) (cidr & 0xFF);
            long mask = prefixLen == 0 ? 0L : (0xFFFFFFFFL << (32 - prefixLen)) & 0xFFFFFFFFL;
            if ((addrLong & mask) == (network & mask)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse CIDR strings into packed longs (network << 8 | prefix length).
     * Only supports IPv4.
     */
    private static long[] parseCidrs(List<String> cidrs) {
        if (cidrs == null || cidrs.isEmpty()) {
            return new long[0];
        }
        return cidrs.stream()
                .mapToLong(cidr -> {
                    String[] parts = cidr.split("/");
                    try {
                        InetAddress addr = InetAddress.getByName(parts[0].trim());
                        byte[] bytes = addr.getAddress();
                        if (bytes.length != 4) {
                            log.warn("Proxy protocol whitelist only supports IPv4, skipping: {}", cidr);
                            return -1;
                        }
                        long network = bytesToLong(bytes);
                        int prefix = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 32;
                        if (prefix < 0 || prefix > 32) {
                            log.warn("Invalid proxy protocol whitelist prefix length: {}", cidr);
                            return -1;
                        }
                        return (network << 8) | prefix;
                    } catch (UnknownHostException | NumberFormatException e) {
                        log.warn("Invalid proxy protocol whitelist entry: {}", cidr, e);
                        return -1;
                    }
                })
                .filter(v -> v != -1)
                .toArray();
    }

    private static long bytesToLong(byte[] bytes) {
        if (bytes.length != 4) return 0;
        return ((long) (bytes[0] & 0xFF) << 24) |
                ((long) (bytes[1] & 0xFF) << 16) |
                ((long) (bytes[2] & 0xFF) << 8) |
                ((long) (bytes[3] & 0xFF));
    }
}
