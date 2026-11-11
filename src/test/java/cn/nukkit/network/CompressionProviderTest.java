package cn.nukkit.network;

import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Prefix capability and log-facing names of the built-in codecs. */
class CompressionProviderTest {

    @Test
    void neteaseAutoKeepsTheRawDeflatePrefixForm() {
        assertTrue(CompressionProvider.NETEASE_UNKNOWN.supportsPrefix(),
                "its compress() delegates to ZLIB_RAW, so the prefixed wire form exists");
        assertEquals((byte) 0x00, CompressionProvider.NETEASE_UNKNOWN.getPrefix());
    }

    @Test
    void headerZlibHasNoPrefixedForm() {
        assertFalse(CompressionProvider.ZLIB.supportsPrefix());
    }

    @Test
    void builtInsKeepPrefixCapabilities() {
        assertTrue(CompressionProvider.NONE.supportsPrefix());
        assertTrue(CompressionProvider.ZLIB_RAW.supportsPrefix());
        assertTrue(CompressionProvider.SNAPPY.supportsPrefix());
    }

    @Test
    void toStringNamesCodecsForLogs() {
        assertEquals("none", CompressionProvider.NONE.toString());
        assertEquals("zlib-header", CompressionProvider.ZLIB.toString());
        assertEquals("zlib-raw", CompressionProvider.ZLIB_RAW.toString());
        assertEquals("snappy", CompressionProvider.SNAPPY.toString());
        assertEquals("netease-auto", CompressionProvider.NETEASE_UNKNOWN.toString());
    }

    @Test
    void legacyCustomProviderStaysPrefixCapable() {
        // 新增能力方法之前，能在 1.20.60+ 上工作的自定义实现只需实现 getPrefix()；默认值不能把它们判死
        // Before this capability method existed, custom codecs usable on 1.20.60+ only had to implement getPrefix()
        CompressionProvider legacy = new CompressionProvider() {
            @Override
            public byte[] compress(BinaryStream packet, int level) throws Exception {
                return CompressionProvider.ZLIB_RAW.compress(packet, level);
            }

            @Override
            public byte[] decompress(byte[] compressed) throws Exception {
                return CompressionProvider.ZLIB_RAW.decompress(compressed);
            }

            @Override
            public byte getPrefix() {
                return CompressionProvider.ZLIB_RAW.getPrefix();
            }
        };

        assertTrue(legacy.supportsPrefix(), "defaulting to false would disconnect existing plugin codecs");
        assertEquals((byte) 0x00, legacy.getPrefix());
    }
}
