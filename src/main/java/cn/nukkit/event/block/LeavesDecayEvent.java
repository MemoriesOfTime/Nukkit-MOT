package cn.nukkit.event.block;

import cn.nukkit.block.Block;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

/**
 * Event for Leaves decay.
 *
 * @author MagicDroidX
 */
public class LeavesDecayEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    /**
     * Event for leaves decaying / disappearing.
     *
     * @param block Leaves block.
     */
    public LeavesDecayEvent(Block block) {
        super(block);
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
