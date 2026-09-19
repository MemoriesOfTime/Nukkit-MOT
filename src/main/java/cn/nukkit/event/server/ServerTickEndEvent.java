package cn.nukkit.event.server;

import cn.nukkit.event.HandlerList;

/**
 * Completion of one real server tick, even when its body throws.
 * Duration covers network, scheduler, worlds, players, autosave and tick bookkeeping.
 * Waiting and opportunistic GC in tickProcessor are outside this interval, as are end listeners.
 * The duration is uncapped monotonic nanoseconds, not TPS or the capped tick-usage estimate.
 */
public final class ServerTickEndEvent extends ServerEvent {
    private static final HandlerList handlers = new HandlerList();
    private final long tickId;
    private final long durationNanos;

    public ServerTickEndEvent(long tickId, long durationNanos) {
        this.tickId = tickId;
        this.durationNanos = durationNanos;
    }

    public long getTickId() {
        return tickId;
    }

    public long getDurationNanos() {
        return durationNanos;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
