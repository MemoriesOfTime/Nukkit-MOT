package cn.nukkit.event.block;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntityItemFrame;
import cn.nukkit.event.Cancellable;
import cn.nukkit.item.Item;

/**
 * Event for Item Frame drops.
 * @author Pub4Game on 03.07.2016.
 */
@Deprecated
public class ItemFrameDropItemEvent extends ItemFrameUseEvent implements Cancellable {

    /**
     * Event for item being dropped from an item frame
     * @param player Player related to the event.
     * @param block Block (item frame) affected by change.
     * @param itemFrame Item frame block entity.
     * @param item Item that is dropped/contained in the item frame.
     */
    public ItemFrameDropItemEvent(Player player, Block block, BlockEntityItemFrame itemFrame, Item item) {
        super(player, block, itemFrame, item, Action.DROP);
    }

    public Player getPlayer() {
        return player;
    }

    public BlockEntityItemFrame getItemFrame() {
        return itemFrame;
    }

    public Item getItem() {
        return item;
    }
}