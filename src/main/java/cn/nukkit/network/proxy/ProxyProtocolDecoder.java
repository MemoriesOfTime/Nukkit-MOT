package cn.nukkit.network.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import io.netty.handler.codec.haproxy.HAProxyProxiedProtocol;

/**
 * Decodes HAProxy Proxy Protocol v2 headers from raw UDP datagrams.
 * <p>
 * Adapted from GeyserMC (<a href="https://github.com/GeyserMC/Geyser">GeyserMC</a>)
 */
public final class ProxyProtocolDecoder {

    public static final int INVALID_HEADER = 0;

    // PP v2 signature: 12 bytes
    private static final int PP_V2_SIGNATURE_LENGTH = 12;
    private static final byte[] PP_V2_SIGNATURE = {
            0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };
    // Minimum header length: 12 (signature) + 4 (ver/cmd, fam, len) = 16
    private static final int PP_V2_MIN_LENGTH = 16;

    private ProxyProtocolDecoder() {
    }

    /**
     * Detect Proxy Protocol version from the ByteBuf.
     *
     * @return 2 if PP v2 header detected, -1 otherwise
     */
    public static int findVersion(ByteBuf buf) {
        if (buf.readableBytes() < PP_V2_SIGNATURE_LENGTH) {
            return -1;
        }
        int readerIndex = buf.readerIndex();
        for (int i = 0; i < PP_V2_SIGNATURE_LENGTH; i++) {
            if (buf.getByte(readerIndex + i) != PP_V2_SIGNATURE[i]) {
                return -1;
            }
        }

        if (buf.readableBytes() < PP_V2_MIN_LENGTH) {
            return INVALID_HEADER;
        }

        int version = (buf.getByte(readerIndex + PP_V2_SIGNATURE_LENGTH) & 0xF0) >> 4;
        if (version != 0x2) {
            return INVALID_HEADER;
        }

        return 2;
    }

    /**
     * Decode a PP v2 header from the ByteBuf and advance the readerIndex past the header.
     *
     * @param buf     the ByteBuf containing the PP v2 header followed by RakNet data
     * @param version must be 2
     * @return the decoded HAProxyMessage, or null if version is not 2
     */
    public static HAProxyMessage decode(ByteBuf buf, int version) {
        if (version != 2) {
            return null;
        }
        if (buf.readableBytes() < PP_V2_MIN_LENGTH) {
            throw new IllegalArgumentException("Incomplete Proxy Protocol v2 header");
        }

        // Skip signature
        buf.skipBytes(PP_V2_SIGNATURE_LENGTH);

        // Byte 13: version (high nibble) and command (low nibble)
        byte verCmd = buf.readByte();
        // Byte 14: address family and transport protocol
        byte famTransport = buf.readByte();
        // Bytes 15-16: address length
        int addrLen = buf.readUnsignedShort();

        if (buf.readableBytes() < addrLen) {
            throw new IllegalArgumentException("Incomplete Proxy Protocol v2 address block");
        }

        HAProxyProtocolVersion ppVersion = HAProxyProtocolVersion.valueOf(verCmd);
        HAProxyCommand command = HAProxyCommand.valueOf(verCmd);
        HAProxyProxiedProtocol proxiedProtocol = HAProxyProxiedProtocol.valueOf(famTransport);

        if (command == HAProxyCommand.LOCAL || proxiedProtocol == HAProxyProxiedProtocol.UNKNOWN) {
            // LOCAL command or UNKNOWN protocol: skip address data, no real address info
            buf.skipBytes(addrLen);
            return new HAProxyMessage(ppVersion, command, proxiedProtocol, null, null, 0, 0);
        }

        String sourceAddress;
        String destAddress;
        int sourcePort;
        int destPort;

        switch (proxiedProtocol.addressFamily()) {
            case AF_IPv4 -> {
                // 4 bytes src, 4 bytes dst, 2 bytes src port, 2 bytes dst port = 12 bytes
                if ((famTransport & 0x0F) != 0x02) {
                    throw new IllegalArgumentException("Proxy Protocol v2 UDP handler only accepts DGRAM over IPv4");
                }
                if (addrLen < 12) {
                    throw new IllegalArgumentException("Proxy Protocol v2 IPv4 address block too short");
                }
                sourceAddress = readIPv4(buf);
                destAddress = readIPv4(buf);
                sourcePort = buf.readUnsignedShort();
                destPort = buf.readUnsignedShort();
                // Skip any remaining TLVs
                int remaining = addrLen - 12;
                if (remaining > 0) {
                    buf.skipBytes(remaining);
                }
            }
            case AF_IPv6 -> {
                // 16 bytes src, 16 bytes dst, 2 bytes src port, 2 bytes dst port = 36 bytes
                if ((famTransport & 0x0F) != 0x02) {
                    throw new IllegalArgumentException("Proxy Protocol v2 UDP handler only accepts DGRAM over IPv6");
                }
                if (addrLen < 36) {
                    throw new IllegalArgumentException("Proxy Protocol v2 IPv6 address block too short");
                }
                sourceAddress = readIPv6(buf);
                destAddress = readIPv6(buf);
                sourcePort = buf.readUnsignedShort();
                destPort = buf.readUnsignedShort();
                // Skip any remaining TLVs
                int remaining = addrLen - 36;
                if (remaining > 0) {
                    buf.skipBytes(remaining);
                }
            }
            default -> {
                throw new IllegalArgumentException("Unsupported Proxy Protocol v2 address family for UDP handler");
            }
        }

        return new HAProxyMessage(ppVersion, command, proxiedProtocol, sourceAddress, destAddress, sourcePort, destPort);
    }

    private static String readIPv4(ByteBuf buf) {
        return (buf.readUnsignedByte()) + "." +
                (buf.readUnsignedByte()) + "." +
                (buf.readUnsignedByte()) + "." +
                (buf.readUnsignedByte());
    }

    private static String readIPv6(ByteBuf buf) {
        StringBuilder sb = new StringBuilder(39);
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format("%x", buf.readUnsignedShort()));
        }
        return sb.toString();
    }
}
