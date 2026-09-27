package cn.nukkit.event.server;

import cn.nukkit.event.HandlerList;

/**
 * Start of one real server tick, after the early-wakeup guard and before network processing.
 * Not cancellable. Paired with exactly one {@link ServerTickEndEvent}, including failed ticks.
 */
public final class ServerTickStartEvent extends ServerEvent {
    private static final HandlerList handlers = new HandlerList();
    private final long tickId;

    public ServerTickStartEvent(long tickId) {
        this.tickId = tickId;
    }

    public long getTickId() {
        return tickId;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
