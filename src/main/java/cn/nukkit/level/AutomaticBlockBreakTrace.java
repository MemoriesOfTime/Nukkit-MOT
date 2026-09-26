package cn.nukkit.level;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.utils.Hash;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Collects every main-layer block replaced by a synchronous automatic break chain. */
final class AutomaticBlockBreakTrace {

    private final Deque<Scope> scopes = new ArrayDeque<>();
    private int blockEntityDropDepth;

    boolean isActive() {
        return !scopes.isEmpty();
    }

    Scope enter() {
        Scope scope = new Scope(scopes.isEmpty());
        scopes.push(scope);
        return scope;
    }

    void record(Block previous, Block current) {
        if (scopes.isEmpty()
                || previous == null
                || current == null
                || previous.layer != 0
                || previous.getId() == BlockID.AIR
                || previous.getId() == current.getId()) {
            return;
        }
        long key = Hash.hashBlock(previous.getFloorX(), previous.getFloorY(), previous.getFloorZ());
        for (Scope scope : scopes) {
            scope.changed.putIfAbsent(key, previous);
        }
    }

    /**
     * Returns the exact block snapshot responsible for a direct Level.dropItem call made from
     * Block.onBreak. Block-entity onBreak hooks are excluded because those drops are container
     * contents, records and books rather than copies of the removed block.
     */
    Block directDropSource(Block current) {
        if (scopes.isEmpty() || blockEntityDropDepth > 0 || current == null) {
            return null;
        }
        if (current.getId() != BlockID.AIR) {
            return current;
        }
        long key = Hash.hashBlock(current.getFloorX(), current.getFloorY(), current.getFloorZ());
        for (Scope scope : scopes) {
            Block changed = scope.changed.get(key);
            if (changed != null) {
                return changed;
            }
        }
        return null;
    }

    void enterBlockEntityDrops() {
        blockEntityDropDepth++;
    }

    void leaveBlockEntityDrops() {
        if (blockEntityDropDepth <= 0) {
            throw new IllegalStateException("Block-entity drop scopes closed out of order");
        }
        blockEntityDropDepth--;
    }

    final class Scope implements AutoCloseable {

        private final boolean outermost;
        private final Map<Long, Block> changed = new LinkedHashMap<>();
        private boolean closed;

        private Scope(boolean outermost) {
            this.outermost = outermost;
        }

        boolean isOutermost() {
            return outermost;
        }

        Block[] changedBlocks() {
            return changed.values().toArray(Block.EMPTY_ARRAY);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (scopes.peek() != this) {
                throw new IllegalStateException("Automatic block break scopes closed out of order");
            }
            scopes.pop();
            closed = true;
        }
    }
}
