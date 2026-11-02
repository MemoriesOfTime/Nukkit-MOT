package cn.nukkit.utils;

/**
 * xxHash64 非流式实现，用于客户端区块缓存 blob 的内容寻址哈希（协议约定的算法）。
 * <p>
 * Non-streaming xxHash64 for content-addressing client chunk cache blobs (the
 * algorithm the cache protocol is specified to use).
 * Adapted from the xxHash reference implementation by Yann Collet
 * (<a href="https://github.com/Cyan4973/xxHash">xxHash</a>, BSD-2-Clause).
 */
public final class XXHash64 {

    private static final long PRIME1 = 0x9E3779B185EBCA87L;
    private static final long PRIME2 = 0xC2B2AE3D27D4EB4FL;
    private static final long PRIME3 = 0x165667B19E3779F9L;
    private static final long PRIME4 = 0x85EBCA77C2B2AE63L;
    private static final long PRIME5 = 0x27D4EB2F165667C5L;

    private XXHash64() {
    }

    public static long hash(byte[] data) {
        return hash(0, data, 0, data.length);
    }

    public static long hash(long seed, byte[] data) {
        return hash(seed, data, 0, data.length);
    }

    public static long hash(long seed, byte[] data, int off, int len) {
        if (data == null || off < 0 || len < 0 || off + len > data.length) {
            throw new IndexOutOfBoundsException("off=" + off + " len=" + len + " array=" + (data == null ? -1 : data.length));
        }
        long h;
        int end = off + len;
        int i = off;

        if (len >= 32) {
            long v1 = seed + PRIME1 + PRIME2;
            long v2 = seed + PRIME2;
            long v3 = seed;
            long v4 = seed - PRIME1;
            int limit = end - 32;

            do {
                v1 = round(v1, getLong(data, i));
                i += 8;
                v2 = round(v2, getLong(data, i));
                i += 8;
                v3 = round(v3, getLong(data, i));
                i += 8;
                v4 = round(v4, getLong(data, i));
                i += 8;
            } while (i <= limit);

            h = rotl(v1, 1) + rotl(v2, 7) + rotl(v3, 12) + rotl(v4, 18);
            h = mergeRound(h, v1);
            h = mergeRound(h, v2);
            h = mergeRound(h, v3);
            h = mergeRound(h, v4);
        } else {
            h = seed + PRIME5;
        }

        h += len;

        while (i + 8 <= end) {
            h ^= round(0, getLong(data, i));
            h = rotl(h, 27) * PRIME1 + PRIME4;
            i += 8;
        }
        if (i + 4 <= end) {
            h ^= ((data[i] & 0xFFL)
                    | (data[i + 1] & 0xFFL) << 8
                    | (data[i + 2] & 0xFFL) << 16
                    | (data[i + 3] & 0xFFL) << 24) * PRIME1;
            h = rotl(h, 23) * PRIME2 + PRIME3;
            i += 4;
        }
        while (i < end) {
            h ^= (data[i] & 0xFFL) * PRIME5;
            h = rotl(h, 11) * PRIME1;
            i++;
        }

        h ^= h >>> 33;
        h *= PRIME2;
        h ^= h >>> 29;
        h *= PRIME3;
        h ^= h >>> 32;
        return h;
    }

    private static long round(long acc, long value) {
        acc += value * PRIME2;
        acc = rotl(acc, 31);
        return acc * PRIME1;
    }

    private static long mergeRound(long acc, long value) {
        value = round(0, value);
        acc ^= value;
        return acc * PRIME1 + PRIME4;
    }

    private static long rotl(long value, int shift) {
        return (value << shift) | (value >>> (64 - shift));
    }

    private static long getLong(byte[] data, int off) {
        return (data[off] & 0xFFL)
                | (data[off + 1] & 0xFFL) << 8
                | (data[off + 2] & 0xFFL) << 16
                | (data[off + 3] & 0xFFL) << 24
                | (data[off + 4] & 0xFFL) << 32
                | (data[off + 5] & 0xFFL) << 40
                | (data[off + 6] & 0xFFL) << 48
                | (data[off + 7] & 0xFFL) << 56;
    }
}
