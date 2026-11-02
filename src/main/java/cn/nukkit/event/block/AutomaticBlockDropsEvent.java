package cn.nukkit.event.block;

import cn.nukkit.block.Block;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.item.Item;

import java.util.Objects;

/**
 * Called before a source-derived block drop is created. Interaction output preserves its source
 * without removing the block or completing a break chain. The terminal
 * event for a break runs after the block is removed; direct drops produced from inside
 * {@code Block.onBreak} run immediately
 * with {@link #isOutermostBreak()} false and an empty changed-block list.
 * Player-authored breaks already expose their primary batch through {@code BlockBreakEvent}; they
 * therefore emit an empty terminal batch only to complete secondary-cell bookkeeping, while any
 * direct secondary drops still use this event normally.
 *
 * <p>The source block is kept even if it has already been replaced or removed from the level. Plugins can
 * therefore distinguish real block drops from items thrown at the same coordinates. The whole
 * drop batch is exposed in one event so plugins can finish source-level bookkeeping exactly once,
 * including for blocks with no drops or worlds with tile drops disabled. Drops created by a block
 * entity's break hook are intentionally excluded: records, books and container contents belong to
 * the player rather than to the removed block.
 *
 * <p>{@link #getChangedBlocks()} contains snapshots of every main-layer block synchronously
 * replaced by this break chain. Nested automatic breaks report their own cells, while the
 * outermost event reports their union and is identified by {@link #isOutermostBreak()}.
 */
public class AutomaticBlockDropsEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public enum Cause {
        AUTOMATIC_BREAK,
        BLOCK_INTERACTION
    }

    private Item[] drops;
    private final Block[] changedBlocks;
    private final boolean outermostBreak;
    private final Cause cause;

    public AutomaticBlockDropsEvent(
            Block block, Item[] drops, Block[] changedBlocks, boolean outermostBreak) {
        this(block, drops, changedBlocks, outermostBreak, Cause.AUTOMATIC_BREAK);
    }

    public AutomaticBlockDropsEvent(
            Block block, Item[] drops, Block[] changedBlocks, boolean outermostBreak, Cause cause) {
        super(block);
        this.cause = Objects.requireNonNull(cause, "cause");
        if (cause == Cause.BLOCK_INTERACTION
                && (changedBlocks == null || changedBlocks.length != 0 || outermostBreak)) {
            throw new IllegalArgumentException("Interaction output cannot complete a block break");
        }
        this.drops = drops;
        this.changedBlocks = changedBlocks;
        this.outermostBreak = outermostBreak;
    }

    public Cause getCause() {
        return cause;
    }

    public Item[] getDrops() {
        return drops;
    }

    public void setDrops(Item[] drops) {
        this.drops = drops;
    }

    /** Main-layer blocks replaced while this break and any nested automatic breaks were running. */
    public Block[] getChangedBlocks() {
        return changedBlocks;
    }

    /** Whether this event closes the outermost synchronous chain of automatic breaks. */
    public boolean isOutermostBreak() {
        return outermostBreak;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
