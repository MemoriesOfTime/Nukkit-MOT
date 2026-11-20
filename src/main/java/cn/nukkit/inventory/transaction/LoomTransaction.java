package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.event.inventory.LoomItemEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.LoomInventory;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.LoomItemAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;

import java.util.List;

public class LoomTransaction extends InventoryTransaction {

    private Item outputItem;

    public LoomTransaction(Player source, List<InventoryAction> actions) {
        super(source, actions);
    }

    @Override
    public void addAction(InventoryAction action) {
        super.addAction(action);

        if (action instanceof LoomItemAction) {
            outputItem = action.getSourceItem();
        }
    }

    @Override
    public boolean canExecute() {
        if (!super.canExecute()) {
            return false;
        }

        Inventory inventory = getSource().getWindowById(Player.LOOM_WINDOW_ID);
        if (!(inventory instanceof LoomInventory loomInventory)) {
            return false;
        }

        if (outputItem == null) {
            return false;
        }

        Item banner = loomInventory.getBanner();
        Item dye = loomInventory.getDye();
        if (banner.getId() != Item.BANNER || dye.getId() != Item.DYE) {
            return false;
        }

        Item pattern = loomInventory.getPattern();
        if (pattern.getId() != 0 && (pattern.getId() != ItemID.BANNER_PATTERN && !pattern.getNamespaceId().equals(Item.GUSTER_BANNER_PATTERN) && !pattern.getNamespaceId().equals(Item.FLOW_BANNER_PATTERN))) {
            return false;
        }

        CompoundTag bannerTag = snapshotNamedTag(banner);
        if (bannerTag == null) {
            bannerTag = new CompoundTag();
        }
        if (bannerTag.contains("Patterns") && !(bannerTag.get("Patterns") instanceof ListTag<?>)) {
            return false;
        }
        ListTag<?> bannerPatterns = bannerTag.getList("Patterns");
        bannerTag.remove("Patterns");
        long outputCount = 0;
        for (InventoryAction action : this.actions) {
            if (!(action instanceof LoomItemAction)) {
                continue;
            }
            Item output = action.getSourceItem();
            if (output.getId() != Item.BANNER || output.isNull()
                    || banner.getDamage() != output.getDamage()) {
                return false;
            }
            outputCount += output.getCount();
            if (outputCount > banner.getCount() || outputCount > dye.getCount()) {
                return false;
            }
            CompoundTag outputTag = snapshotNamedTag(output);
            if (outputTag == null || !(outputTag.get("Patterns") instanceof ListTag<?> outputPatterns)
                    || outputPatterns.size() > 6 || bannerPatterns.size() + 1 != outputPatterns.size()) {
                return false;
            }
            for (int i = 0; i < outputPatterns.size(); i++) {
                if (!(outputPatterns.get(i) instanceof CompoundTag)
                        || i < bannerPatterns.size() && !bannerPatterns.get(i).equals(outputPatterns.get(i))) {
                    return false;
                }
            }
            // A loom may append one pattern; all other input metadata is authoritative.
            if (!bannerTag.equals(outputTag.remove("Patterns"))) {
                return false;
            }
        }
        return true;
    }

    private static CompoundTag snapshotNamedTag(Item item) {
        if (!item.hasCompoundTag()) {
            return null;
        }
        CompoundTag snapshot = item.getNamedTag().copy();
        snapshot.setName("");
        return snapshot;
    }

    @Override
    protected boolean callExecuteEvent() {
        LoomInventory inventory = (LoomInventory) getSource().getWindowById(Player.LOOM_WINDOW_ID);
        LoomItemEvent event = new LoomItemEvent(inventory, this.outputItem, this.source);
        event.call();
        if (event.isCancelled()) {
            this.sendInventories();
            source.setNeedSendInventory(true);
            return false;
        }
        return true;
    }

    public Item getOutputItem() {
        return this.outputItem;
    }

    public static boolean isIn(List<InventoryAction> actions) {
        return actions.stream().anyMatch(it-> it instanceof LoomItemAction);
    }

    @Override
    public boolean checkForItemPart(List<InventoryAction> actions) {
        return isIn(actions);
    }
}
