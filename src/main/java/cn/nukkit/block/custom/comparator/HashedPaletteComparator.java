package cn.nukkit.block.custom.comparator;


import cn.nukkit.utils.Hash;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;

public class HashedPaletteComparator implements Comparator<String> {
    public static final HashedPaletteComparator INSTANCE = new HashedPaletteComparator();

    private static final long FNV1_64_INIT = 0xcbf29ce484222325L;
    private static final long FNV1_PRIME_64 = 0x100000001b3L;

    @Override
    public int compare(String o1, String o2) {
        byte[] b1 = o1.getBytes(StandardCharsets.UTF_8);
        byte[] b2 = o2.getBytes(StandardCharsets.UTF_8);
        long hash1 = Hash.fnv1_64(b1);
        long hash2 = Hash.fnv1_64(b2);
        return Long.compareUnsigned(hash1, hash2);
    }
}
