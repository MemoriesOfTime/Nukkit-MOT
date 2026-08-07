package cn.nukkit.network.proxy;

import io.netty.buffer.ByteBuf;

/**
 * Detects HAProxy Proxy Protocol v2 headers without consuming packet content.
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

}
