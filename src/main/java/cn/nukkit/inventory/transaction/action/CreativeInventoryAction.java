package cn.nukkit.inventory.transaction.action;

import cn.nukkit.Player;
import cn.nukkit.item.Item;

/**
 * @author CreeperFace
 */
public class CreativeInventoryAction extends InventoryAction {
    /**
     * Player put an item into the creative window to destroy it.
     */
    public static final int TYPE_DELETE_ITEM = 0;
    /**
     * Player took an item from the creative window.
     */
    public static final int TYPE_CREATE_ITEM = 1;

    protected int actionType;

    public CreativeInventoryAction(Item source, Item target, int action) {
        super(source, target);
        this.actionType = action;
    }

    /**
     * Validates the creative credit/sink direction and exact session catalog membership for item creation.
     *
     * @param source player
     * @return valid
     */
    @Override
    public boolean isValid(Player source) {
        if (!source.isCreative() || this.sourceItem == null || this.targetItem == null) {
            return false;
        }
        return switch (this.actionType) {
            case TYPE_CREATE_ITEM -> this.sourceItem.getId() != Item.AIR
                    && this.sourceItem.getCount() > 0
                    && this.sourceItem.getCount() <= this.sourceItem.getMaxStackSize()
                    && this.targetItem.getId() == Item.AIR
                    && isCatalogItem(source);
            case TYPE_DELETE_ITEM -> this.sourceItem.getId() == Item.AIR
                    && (this.targetItem.getId() == Item.AIR || this.targetItem.getCount() > 0);
            default -> false;
        };
    }

    private boolean isCatalogItem(Player source) {
        var version = source.getGameVersion();
        if (version == null) {
            return false;
        }
        for (Item item : Item.getCreativeItemsAndGroups().getItems(version)) {
            if (this.sourceItem.equals(item, true, true)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onPreExecute(Player source) {
        // Plugins may change the mode, catalog or action items after initial validation.
        return this.isValid(source);
    }

    /**
     * Returns the type of the action.
     *
     * @return action type
     */
    public int getActionType() {
        return actionType;
    }

    /**
     * No need to do anything extra here: this type just provides a place for items to disappear or appear from.
     *
     * @param source playere
     * @return successfully executed
     */
    @Override
    public boolean execute(Player source) {
        return true;
    }

    @Override
    public void onExecuteSuccess(Player source) {
    }

    @Override
    public void onExecuteFail(Player source) {
    }
}
