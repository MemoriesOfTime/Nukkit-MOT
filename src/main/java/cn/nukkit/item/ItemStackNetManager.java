package cn.nukkit.item;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Allocator for {@link Item#getStackNetId() stack network ids}. Used by the
 * Server Authoritative Inventory (ItemStackRequest) flow to track a specific
 * item stack across client-server round trips. Ids are positive integers and
 * wrap back to 1 when {@link Integer#MAX_VALUE} is reached; 0 is reserved for
 * "not tracked". Unrelated to block / item runtime ids.
 */
public final class ItemStackNetManager {

    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    private ItemStackNetManager() {
    }

    /**
     * Allocates the next stack network id. Thread-safe; the counter wraps to 1
     * after reaching {@link Integer#MAX_VALUE} so the return value is always
     * positive.
     *
     * @return the freshly allocated stack network id (always {@code > 0})
     */
    public static int allocate() {
        int next = COUNTER.getAndIncrement();
        if (next == Integer.MAX_VALUE) {
            COUNTER.set(1);
        }
        return next;
    }

    /**
     * Resets the allocator back to 1. Intended for tests; production code must
     * not call this because previously-issued ids may still be referenced by
     * in-flight {@code ItemStackRequest} sessions.
     */
    public static void reset() {
        COUNTER.set(1);
    }
}
