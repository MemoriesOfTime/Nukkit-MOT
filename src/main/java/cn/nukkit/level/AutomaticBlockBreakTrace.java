package cn.nukkit.level;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.utils.Hash;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects every main-layer block replaced by a synchronous automatic break chain.
 * <p>
 * 只做变更簿记，不做掉落归因：来源由 {@code Level.dropItemFromAutomaticBlock} 显式传入，
 * 避免把链内同坐标的无关掉落误判为方块掉落。
 * <p>Records replaced blocks only. Drop attribution is passed explicitly through
 * {@code Level.dropItemFromAutomaticBlock} so unrelated drops at the same coordinates are
 * never misattributed.
 */
final class AutomaticBlockBreakTrace {

    private final Deque<Scope> scopes = new ArrayDeque<>();

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
        // 隔离回调复用及内层事件修改，保留各层原始快照。
        // Keep each scope's original snapshot isolated from callback reuse and nested event edits.
        for (Scope scope : scopes) {
            scope.changed.computeIfAbsent(key, ignored -> previous.clone());
        }
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
