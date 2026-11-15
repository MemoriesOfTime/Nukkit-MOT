package cn.nukkit.level;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.utils.Hash;

import java.util.*;

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
     *
     * <p>已被关闭的内层 scope 派发过的格子不再归因：内层终批在外层 scope 仍活跃时生成，
     * 重复归因会让同一次物理掉落派发两次事件。
     * <p>Cells dispatched by a closed inner scope are skipped: the inner terminal batch is spawned
     * while the outer scope is still active, and re-attributing those cells would dispatch the
     * same physical drop twice.
     */
    Block directDropSource(Block current) {
        if (scopes.isEmpty() || blockEntityDropDepth > 0 || current == null) {
            return null;
        }
        long key = Hash.hashBlock(current.getFloorX(), current.getFloorY(), current.getFloorZ());
        if (current.getId() != BlockID.AIR) {
            return anyDispatched(key) ? null : current;
        }
        for (Scope scope : scopes) {
            // 派发标记优先于同层 changed 中的旧记录，避免内层关闭后二次归因
            if (scope.dispatched.contains(key)) {
                return null;
            }
            Block changed = scope.changed.get(key);
            if (changed != null) {
                return changed;
            }
        }
        return null;
    }

    private boolean anyDispatched(long key) {
        for (Scope scope : scopes) {
            if (scope.dispatched.contains(key)) {
                return true;
            }
        }
        return false;
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
        private final Set<Long> dispatched = new HashSet<>();
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
            Scope parent = scopes.peek();
            if (parent != null) {
                // 内层终批随关闭派发，格子移交父层已派发集合
                parent.dispatched.addAll(changed.keySet());
            }
        }
    }
}
