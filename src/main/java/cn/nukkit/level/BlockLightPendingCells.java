package cn.nukkit.level;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.HashCommon;

import java.util.Arrays;

/** Queue occupancy only: a dequeue clears the bit before relaxation can revisit it. */
final class BlockLightPendingCells {
    private final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();
    // Sparse worlds never allocate one bitmap per cell/section. Spill occupancy
    // into the original representation once this fixed metadata budget is full.
    private static final int MAX_SECTIONS = 256;
    private final LongOpenHashSet sparseOverflow = new LongOpenHashSet();
    // A zero counter proves that an overflow cell cannot belong to a bitmap.
    // Collisions only fall back to the map, and 256 owners cannot overflow short.
    private final short[] activeBuckets = new short[4096];
    private static final long LOCAL_MASK = (15L << 52) | (15L << 26) | 15L;
    private long lastKey;
    private Section lastSection;

    boolean add(long cell) {
        long key = cell & ~LOCAL_MASK;
        Section section;
        if (lastSection != null && lastKey == key) {
            section = lastSection;
        } else {
            if (sections.size() == MAX_SECTIONS && activeBuckets[bucket(key)] == 0)
                return sparseOverflow.add(cell);
            section = sections.get(key);
        }
        if (section == null) {
            if (sections.size() == MAX_SECTIONS) return sparseOverflow.add(cell);
            if (!sparseOverflow.isEmpty() && sparseOverflow.contains(cell)) return false;
            section = new Section(bucket(key));
            sections.put(key, section);
            activeBuckets[section.bucket]++;
        } else if (!sparseOverflow.isEmpty() && sparseOverflow.contains(cell)) {
            return false;
        }
        lastKey = key;
        lastSection = section;
        return section.add(local(cell));
    }

    void remove(long cell) {
        long key = cell & ~LOCAL_MASK;
        Section section = lastSection != null && lastKey == key ? lastSection
                : sections.isEmpty() || activeBuckets[bucket(key)] == 0 ? null : sections.get(key);
        if (section == null || !section.remove(local(cell))) {
            if (!sparseOverflow.remove(cell)) throw new IllegalStateException("Missing pending light cell");
            return;
        }
        if (section.size == 0) {
            sections.remove(key);
            activeBuckets[section.bucket]--;
            lastSection = null;
        } else {
            lastKey = key;
            lastSection = section;
        }
    }

    private static int local(long cell) {
        return (((int) (cell >>> 52) & 15) << 8)
                | (((int) (cell >>> 26) & 15) << 4) | ((int) cell & 15);
    }

    private static int bucket(long key) { return (int) HashCommon.mix(key) & 4095; }

    private static final class Section {
        final int bucket;
        private short[] sparse = new short[4];
        private long[] bits;
        private int size;

        Section(int bucket) { this.bucket = bucket; }

        boolean add(int cell) {
            if (bits == null) {
                for (int i = 0; i < size; i++) if (sparse[i] == cell) return false;
                if (size < sparse.length) {
                    sparse[size++] = (short) cell;
                    return true;
                }
                if (size < 32) {
                    sparse = Arrays.copyOf(sparse, size << 1);
                    sparse[size++] = (short) cell;
                    return true;
                }
                bits = new long[64];
                for (short existing : sparse) bits[existing >>> 6] |= 1L << existing;
                sparse = null;
            }
            int word = cell >>> 6;
            long bit = 1L << cell;
            if ((bits[word] & bit) != 0) return false;
            bits[word] |= bit;
            size++;
            return true;
        }

        boolean remove(int cell) {
            if (bits == null) {
                for (int i = 0; i < size; i++) if (sparse[i] == cell) {
                    sparse[i] = sparse[--size];
                    return true;
                }
                return false;
            }
            if ((bits[cell >>> 6] & (1L << cell)) == 0) return false;
            bits[cell >>> 6] &= ~(1L << cell);
            size--;
            return true;
        }
    }
}
