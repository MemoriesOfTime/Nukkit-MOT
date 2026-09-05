package cn.nukkit.level;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockShulkerBox;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Identifies an automatic break caused synchronously by the block currently being placed.
 *
 * <p>The stack is thread-local because a placement can re-enter item use through a plugin. Every
 * placement pushes a scope, including non-creative ones, so an inner placement can never inherit
 * an outer creative scope.
 */
final class CreativePlacementDropGuard {

    private record Placement(Level level, boolean creative, int blockId, int x, int y, int z) {
    }

    private static final ThreadLocal<Deque<Placement>> ACTIVE =
            ThreadLocal.withInitial(ArrayDeque::new);

    private CreativePlacementDropGuard() {
    }

    static Scope enter(Level level, Player player, Block block) {
        return enter(
                level,
                player != null && player.isCreative(),
                block.getId(),
                block.getFloorX(),
                block.getFloorY(),
                block.getFloorZ());
    }

    static boolean suppresses(Level level, Player player, Block block) {
        return suppresses(
                level,
                player == null,
                block.getId(),
                block.getFloorX(),
                block.getFloorY(),
                block.getFloorZ(),
                block instanceof BlockShulkerBox);
    }

    static Scope enter(
            Level level, boolean creative, int blockId, int x, int y, int z) {
        Placement placement = new Placement(level, creative, blockId, x, y, z);
        ACTIVE.get().push(placement);
        return new Scope(placement);
    }

    static boolean suppresses(
            Level level,
            boolean automaticBreak,
            int blockId,
            int x,
            int y,
            int z,
            boolean shulkerBox) {
        if (!automaticBreak || shulkerBox) {
            return false;
        }
        Placement placement = ACTIVE.get().peek();
        return placement != null
                && placement.creative()
                && placement.level() == level
                && placement.blockId() == blockId
                && placement.x() == x
                && placement.y() == y
                && placement.z() == z;
    }

    static final class Scope implements AutoCloseable {

        private final Placement placement;
        private boolean closed;

        private Scope(Placement placement) {
            this.placement = placement;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            Deque<Placement> placements = ACTIVE.get();
            if (placements.peek() == placement) {
                placements.pop();
            } else {
                placements.removeFirstOccurrence(placement);
            }
            if (placements.isEmpty()) {
                ACTIVE.remove();
            }
        }
    }
}
