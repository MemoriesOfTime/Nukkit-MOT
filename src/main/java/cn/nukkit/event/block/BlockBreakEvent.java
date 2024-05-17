package cn.nukkit.event.block;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;

/**
 * Event for Block being broken.
 *
 * @author MagicDroidX
 */
public class BlockBreakEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    protected final Player player;
    protected final Item item;
    protected final BlockFace face;
    protected boolean instaBreak;
    protected Item[] blockDrops;
    protected int blockXP;
    protected boolean fastBreak;

    /**
     * This event is called when a block is broken.
     *
     * @param player Player who broke the block.
     * @param block  Block that was broken.
     * @param item   Item used to break the block.
     * @param drops  Items dropped by the block.
     */
    public BlockBreakEvent(Player player, Block block, Item item, Item[] drops) {
        this(player, block, item, drops, false, false);
    }

    public BlockBreakEvent(Player player, Block block, Item item, Item[] drops, boolean instaBreak) {
        this(player, block, item, drops, instaBreak, false);
    }

    public BlockBreakEvent(Player player, Block block, Item item, Item[] drops, boolean instaBreak, boolean fastBreak) {
        this(player, block, null, item, drops, instaBreak, fastBreak);
    }

    public BlockBreakEvent(Player player, Block block, BlockFace face, Item item, Item[] drops, boolean instaBreak, boolean fastBreak) {
        super(block);
        this.face = face;
        this.item = item;
        this.player = player;
        this.instaBreak = instaBreak;
        this.blockDrops = drops;
        this.fastBreak = fastBreak;
        this.blockXP = block.getDropExp();
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public Player getPlayer() {
        return player;
    }

    public BlockFace getFace() {
        return face;
    }

    public Item getItem() {
        return item;
    }

    public boolean getInstaBreak() {
        return this.instaBreak;
    }

    public void setInstaBreak(boolean instaBreak) {
        this.instaBreak = instaBreak;
    }

    public Item[] getDrops() {
        return blockDrops;
    }

    public void setDrops(Item[] drops) {
        this.blockDrops = drops;
    }

    public int getDropExp() {
        return this.blockXP;
    }

    public void setDropExp(int xp) {
        this.blockXP = xp;
    }

    public boolean isFastBreak() {
        return this.fastBreak;
    }
}