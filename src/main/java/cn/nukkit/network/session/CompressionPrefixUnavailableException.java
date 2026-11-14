package cn.nukkit.network.session;

import cn.nukkit.network.CompressionProvider;

/**
 * 编码器写不出 1.20.60+ 要求的批前缀，该批未写出即告失败。
 * <p>The codec cannot emit the 1.20.60+ batch prefix, so the batch fails without ever being written.
 */
class CompressionPrefixUnavailableException extends IllegalStateException {

    CompressionPrefixUnavailableException(CompressionProvider compression, int protocol) {
        super("compression " + compression + " has no batch prefix for protocol " + protocol);
    }
}
