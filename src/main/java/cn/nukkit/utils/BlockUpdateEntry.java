package cn.nukkit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.math.Vector3;

/**
 * Entry of a block update
 *
 * @author MagicDroidX
 * Nukkit Project
 */
public record BlockUpdateEntry(Vector3 pos, Block block, long delay, int priority) implements Comparable<BlockUpdateEntry> {

    private static final java.util.concurrent.atomic.AtomicLong entryID = new java.util.concurrent.atomic.AtomicLong(0);

    public static final long id = entryID.incrementAndGet();

    public BlockUpdateEntry {
        if (pos == null || block == null) {
            throw new IllegalArgumentException("Position and block cannot be null");
        }
    }

    public BlockUpdateEntry(Vector3 pos, Block block) {
        this(pos, block, 0, 0);
    }

    @Override
    public int compareTo(BlockUpdateEntry entry) {
        return this.delay < entry.delay ? -1 :
            (this.delay > entry.delay ? 1 :
             (this.priority != entry.priority ? this.priority - entry.priority :
                 Long.compare(this.id, entry.id)));
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof BlockUpdateEntry entry)) {
            if (object instanceof Vector3) {
                return pos.equals(object);
            }
            return false;
        }
        return this.pos.equals(entry.pos) && Block.equals(this.block, entry.block, false);
    }

    @Override
    public int hashCode() {
        return this.pos.hashCode();
    }
}
