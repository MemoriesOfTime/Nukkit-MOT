package cn.nukkit.event.block;

import cn.nukkit.block.Block;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

/**
 * Event for Block growth.
 * @author MagicDroidX
 */
public class BlockGrowEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    private final Block newState;
    private final Block source;

    /**
     * Called on block grow.
     * @param block Block affected by event E.g Vine.
     * @param newState New state of the affected block.
     */
    public BlockGrowEvent(Block block, Block newState) {
        this(block, null, newState);
    }

    /**
     * Called when growth has an explicit source distinct from the affected block.
     * @param block Block affected by the event; existing target semantics are unchanged.
     * @param source Actual source of this growth, or null when the caller cannot provide it.
     * @param newState New state of the affected block.
     */
    public BlockGrowEvent(Block block, Block source, Block newState) {
        super(block);
        this.source = source;
        this.newState = newState;
    }

    public Block getSource() {
        return source;
    }

    public Block getNewState() {
        return newState;
    }
}
