package cn.nukkit.network.proxy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import lombok.extern.log4j.Log4j2;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Netty channel handler that decodes HAProxy Proxy Protocol v2 headers from incoming UDP datagrams,
 * replacing the sender address with the real client address.
 * <p>
 * Adapted from GeyserMC (<a href="https://github.com/GeyserMC/Geyser">GeyserMC</a>)
 */
@Log4j2
@ChannelHandler.Sharable
public class ProxyProtocolHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    // Cache proxy address → real client address, 31 min TTL (RakNet session timeout is ~30 min)
    private final Cache<InetSocketAddress, InetSocketAddress> addressCache = Caffeine.newBuilder()
            .expireAfterWrite(31, TimeUnit.MINUTES)
            .build();

    private final long[] whitelistCidrs;

    /**
     * @param whitelist list of allowed proxy IPs/CIDRs (e.g. "127.0.0.1", "10.0.0.0/8"). Empty means allow all.
     */
    public ProxyProtocolHandler(List<String> whitelist) {
        this.whitelistCidrs = parseCidrs(whitelist);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
        InetSocketAddress sender = packet.sender();

        // Check cache first
        InetSocketAddress cached = addressCache.getIfPresent(sender);
        if (cached != null) {
            ctx.fireChannelRead(new DatagramPacket(packet.content().retain(), packet.recipient(), cached));
            return;
        }

        // Try to detect PP v2 header
        int version = ProxyProtocolDecoder.findVersion(packet.content());
        if (version == -1) {
            // No PP header - pass through as-is (direct connection)
            ctx.fireChannelRead(new DatagramPacket(packet.content().retain(), packet.recipient(), sender));
            return;
        }

        // Whitelist check
        if (whitelistCidrs.length > 0 && !isWhitelisted(sender.getAddress())) {
            log.warn("Proxy Protocol packet from non-whitelisted address: {}", sender.getAddress().getHostAddress());
            ctx.fireChannelRead(new DatagramPacket(packet.content().retain(), packet.recipient(), sender));
            return;
        }

        // Decode PP v2 header
        HAProxyMessage message = ProxyProtocolDecoder.decode(packet.content(), version);
        if (message == null || message.sourceAddress() == null) {
            ctx.fireChannelRead(new DatagramPacket(packet.content().retain(), packet.recipient(), sender));
            return;
        }

        try {
            InetSocketAddress realAddress = new InetSocketAddress(
                    InetAddress.getByName(message.sourceAddress()),
                    message.sourcePort()
            );
            addressCache.put(sender, realAddress);

            if (log.isDebugEnabled()) {
                log.debug("Proxy Protocol: {} -> real client {}", sender, realAddress);
            }

            // Forward remaining content (after PP header) with real address
            ctx.fireChannelRead(new DatagramPacket(packet.content().retain(), packet.recipient(), realAddress));
        } catch (UnknownHostException e) {
            log.warn("Failed to resolve proxy protocol source address: {}", message.sourceAddress(), e);
            ctx.fireChannelRead(new DatagramPacket(packet.content().retain(), packet.recipient(), sender));
        } finally {
            message.release();
        }
    }

    @Override
    public boolean acceptInboundMessage(Object msg) {
        return msg instanceof DatagramPacket;
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
