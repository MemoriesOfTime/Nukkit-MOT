package cn.nukkit.utils;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.TreeMap;

/**
 * Hash utils
 */
public final class Hash {

    private static final int FNV1_32_INIT = 0x811c9dc5;
    private static final int FNV1_PRIME_32 = 0x01000193;
    private static final long FNV1_64_INIT = 0xcbf29ce484222325L;
    private static final long FNV1_PRIME_64 = 0x100000001b3L;

    private static final long XXH_PRIME64_1 = 0x9E3779B185EBCA87L;
    private static final long XXH_PRIME64_2 = 0x14DEF9DEA2F79CD6L;
    private static final long XXH_PRIME64_3 = 0x0000000165E84489L;
    private static final long XXH_PRIME64_4 = 0x27D4EB2F165B7D01L;
    private static final long XXH_PRIME64_5 = 0x0000000285EBCA87L;

    public static long xxh64(byte[] buffer) {
        return xxh64(buffer, 0, buffer.length);
    }

    public static long xxh64(byte[] buffer, int offset, int length) {
        long seed = 0;
        if (length >= 32) {
            long v1 = seed + XXH_PRIME64_1 + XXH_PRIME64_2;
            long v2 = seed + XXH_PRIME64_2;
            long v3 = seed;
            long v4 = seed - XXH_PRIME64_1;
            int remaining = length;
            int pos = offset;
            do {
                v1 = xxh64Round(v1, getLong(buffer, pos)); pos += 8;
                v2 = xxh64Round(v2, getLong(buffer, pos)); pos += 8;
                v3 = xxh64Round(v3, getLong(buffer, pos)); pos += 8;
                v4 = xxh64Round(v4, getLong(buffer, pos)); pos += 8;
                remaining -= 32;
            } while (remaining >= 32);
            long hash = Long.rotateLeft(v1, 1) + Long.rotateLeft(v2, 7) + Long.rotateLeft(v3, 12) + Long.rotateLeft(v4, 18);
            hash = xxh64MergeRound(hash, v1);
            hash = xxh64MergeRound(hash, v2);
            hash = xxh64MergeRound(hash, v3);
            hash = xxh64MergeRound(hash, v4);
            hash += length;
            return xxh64Finalize(hash, buffer, pos, remaining);
        } else {
            long hash = seed + XXH_PRIME64_5 + length;
            return xxh64Finalize(hash, buffer, offset, length);
        }
    }

    private static long xxh64Round(long acc, long input) {
        acc += input * XXH_PRIME64_2;
        acc = Long.rotateLeft(acc, 31);
        acc *= XXH_PRIME64_1;
        return acc;
    }

    private static long xxh64MergeRound(long hash, long val) {
        val = xxh64Round(0, val);
        hash ^= val;
        hash = hash * XXH_PRIME64_1 + XXH_PRIME64_4;
        return hash;
    }

    private static long xxh64Finalize(long hash, byte[] buffer, int pos, int remaining) {
        if (remaining >= 8) {
            long k1 = xxh64Round(0, getLong(buffer, pos));
            hash ^= k1;
            hash = Long.rotateLeft(hash, 27) * XXH_PRIME64_1 + XXH_PRIME64_4;
            pos += 8;
            remaining -= 8;
        }
        if (remaining >= 4) {
            hash ^= (getInt(buffer, pos) & 0xFFFFFFFFL) * XXH_PRIME64_1;
            hash = Long.rotateLeft(hash, 23) * XXH_PRIME64_2 + XXH_PRIME64_3;
            pos += 4;
            remaining -= 4;
        }
        while (remaining > 0) {
            hash ^= (buffer[pos] & 0xFFL) * XXH_PRIME64_5;
            hash = Long.rotateLeft(hash, 11) * XXH_PRIME64_1;
            pos++;
            remaining--;
        }
        hash ^= hash >>> 33;
        hash *= XXH_PRIME64_2;
        hash ^= hash >>> 29;
        hash *= XXH_PRIME64_3;
        hash ^= hash >>> 32;
        return hash;
    }

    private static long getLong(byte[] buf, int off) {
        return (buf[off] & 0xFFL)
                | ((buf[off + 1] & 0xFFL) << 8)
                | ((buf[off + 2] & 0xFFL) << 16)
                | ((buf[off + 3] & 0xFFL) << 24)
                | ((buf[off + 4] & 0xFFL) << 32)
                | ((buf[off + 5] & 0xFFL) << 40)
                | ((buf[off + 6] & 0xFFL) << 48)
                | ((buf[off + 7] & 0xFFL) << 56);
    }

    private static int getInt(byte[] buf, int off) {
        return (buf[off] & 0xFF)
                | ((buf[off + 1] & 0xFF) << 8)
                | ((buf[off + 2] & 0xFF) << 16)
                | ((buf[off + 3] & 0xFF) << 24);
    }

    public static long hashBlock(int x, int y, int z) {
        return ((long) y << 52) + (((long) z & 0x3ffffff) << 26) + ((long) x & 0x3ffffff);
    }

    public static int hashBlockX(long triple) {
        return (int) (((triple & 0x3ffffff) << 38) >> 38);
    }

    public static int hashBlockY(long triple) {
        return (int) (triple >> 52);
    }

    public static int hashBlockZ(long triple) {
        return (int) ((((triple >> 26) & 0x3ffffff) << 38) >> 38);
    }

    /**
     * 使用FNV-1a算法计算32位哈希值
     * Calculate 32-bit hash value using FNV-1a algorithm
     *
     * @param data 要计算哈希的字节数组 / byte array to calculate hash
     * @return 32位哈希值 / 32-bit hash value
     */
    public static int fnv1a_32(byte... data) {
        int hash = FNV1_32_INIT;
        for (byte datum : data) {
            hash ^= datum & 0xff;
            hash *= FNV1_PRIME_32;
        }
        return hash;
    }

    /**
     * 使用FNV-1算法计算64位哈希值
     * Calculate 64-bit hash value using FNV-1 algorithm
     *
     * @param data 要计算哈希的字节数组 / byte array to calculate hash
     * @return 64位哈希值 / 64-bit hash value
     */
    public static long fnv1_64(byte... data) {
        long hash = FNV1_64_INIT;
        for (byte datum : data) {
            hash *= FNV1_PRIME_64;
            hash ^= datum & 0xff;
        }
        return hash;
    }

    /**
     * 计算字符串标识符的64位哈希值
     * Calculate 64-bit hash value of a string identifier
     * <p>
     * 使用UTF-8编码将字符串转换为字节后使用FNV-1算法
     * Converts the string to bytes using UTF-8 encoding then applies FNV-1 algorithm
     *
     * @param identifier 要哈希的字符串标识符 / string identifier to hash
     * @return 64位哈希值 / 64-bit hash value
     */
    public static long hashIdentifier(String identifier) {
        return fnv1_64(identifier.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算方块状态的哈希值
     * Calculate hash value of a block state
     * <p>
     * 通过序列化方块的名称和状态标签（按字典序排序）后计算FNV-1a哈希
     * Calculates FNV-1a hash after serializing the block's name and state tags (sorted lexicographically)
     * <p>
     * 用于生成方块的网络ID或用于方块状态比较
     * Used to generate block network IDs or for block state comparison
     *
     * @param block 包含方块名称和状态的NBT标签 / NBT tag containing block name and states
     * @return 方块状态的32位哈希值，如果是未知方块则返回-2 / 32-bit hash value of the block state, returns -2 if unknown block
     */
    public static int hashBlock(CompoundTag block) {
        String name = block.getString("name");

        if ("minecraft:unknown".equals(name)) {
            return -2;
        }

        try {
            return fnv1a_32(NBTIO.write(new CompoundTag(new LinkedHashMap<>())
                    .putString("name", name)
                    .putCompound("states", new CompoundTag(new TreeMap<>(block.getCompound("states").getTags()))), ByteOrder.LITTLE_ENDIAN));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
