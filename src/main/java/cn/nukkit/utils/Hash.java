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
public class Hash {

    private static final int FNV1_32_INIT = 0x811c9dc5;
    private static final int FNV1_PRIME_32 = 0x01000193;
    private static final long FNV1_64_INIT = 0xcbf29ce484222325L;
    private static final long FNV1_PRIME_64 = 0x100000001b3L;

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

    public static int fnv1a_32(byte... data) {
        int hash = FNV1_32_INIT;
        for (byte datum : data) {
            hash ^= datum & 0xff;
            hash *= FNV1_PRIME_32;
        }
        return hash;
    }

    public static long fnv1_64(byte... data) {
        long hash = FNV1_64_INIT;
        for (byte datum : data) {
            hash *= FNV1_PRIME_64;
            hash ^= datum & 0xff;
        }
        return hash;
    }

    public static long hashIdentifier(String identifier) {
        return fnv1_64(identifier.getBytes(StandardCharsets.UTF_8));
    }

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
