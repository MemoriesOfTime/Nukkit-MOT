package cn.nukkit.level;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.utils.Hash;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 记录一条同步破坏链替换过的主层方块，并保留链的发起玩家。
 * <p>
 * 只做簿记，不做掉落归因：来源由 {@code Level.dropItemFromAutomaticBlock} 显式传入，
 * 避免把链内同坐标的无关掉落误判为方块掉落；嵌套作用域未显式传入玩家时继承外层发起者。
 * <p>Records every main-layer cell replaced by a synchronous break chain together with the
 * player that started it. Attribution itself stays explicit so unrelated drops at the same
 * coordinates are never misattributed; nested scopes without a player inherit the outer origin.
 */
final class AutomaticBlockBreakTrace {

    private final Deque<Scope> scopes = new ArrayDeque<>();

    boolean isActive() {
        return !scopes.isEmpty();
    }

    /**
     * 当前最内层作用域的发起玩家；无活跃作用域或纯物理链为 {@code null}。
     * <p>Origin of the innermost active scope, or {@code null} without one.
     */
    @Nullable
    Player currentOrigin() {
        Scope scope = scopes.peek();
        return scope == null ? null : scope.origin;
    }

    /**
     * 进入一层破坏作用域；无显式玩家时继承外层链的发起者。
     * <p>Enters a break scope; without an explicit player the enclosing chain's origin is inherited.
     */
    Scope enter(@Nullable Player player) {
        Scope parent = scopes.peek();
        Player origin = player != null ? player : (parent == null ? null : parent.origin);
        Scope scope = new Scope(scopes.isEmpty(), origin);
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
        private final Player origin;
        private final Map<Long, Block> changed = new LinkedHashMap<>();
        private boolean closed;

        private Scope(boolean outermost, @Nullable Player origin) {
            this.outermost = outermost;
            this.origin = origin;
        }

        boolean isOutermost() {
            return outermost;
        }

        /** 发起本链的玩家；纯物理链为 {@code null}。The player that started this chain, or {@code null}. */
        @Nullable
        Player getOrigin() {
            return origin;
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
