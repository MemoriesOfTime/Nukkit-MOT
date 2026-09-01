package cn.nukkit.event.block;

import cn.nukkit.block.Block;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;

/**
 * Called before a dispenser or a dropper releases an item.
 *
 * <p>Dispensing has no player behind it: the block fires from a lever, from a comparator, from
 * another machine. Without this event a plugin that guards land can only see the moment a player
 * breaks or places a block, so a dispenser standing one cell outside a protected area pours lava
 * into it, lights it, throws primed TNT at it, spawns mobs in it and places shulker boxes in it,
 * and none of the usual guards are ever asked.
 *
 * <p>The event carries the cell the block fires into ({@link #getTarget()}), because that is the
 * cell every dispense behavior actually changes, and the item that is about to leave the
 * container. Cancelling it stops the dispense completely: nothing is consumed, no behavior runs
 * and no sound or particle is sent, so a refused dispenser looks like a dispenser that has
 * nothing to fire.
 */
public class BlockDispenseEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    private final BlockFace face;
    private final Block target;
    private final Item item;

    public BlockDispenseEvent(Block block, BlockFace face, Block target, Item item) {
        super(block);
        this.face = face;
        this.target = target;
        this.item = item;
    }

    /** The direction the block is facing. */
    public BlockFace getFace() {
        return this.face;
    }

    /** The cell in front of the block, the one a dispense behavior changes. */
    public Block getTarget() {
        return this.target;
    }

    /** A copy of the item that is about to be dispensed. Changing it changes nothing. */
    public Item getItem() {
        return this.item;
    }
}
