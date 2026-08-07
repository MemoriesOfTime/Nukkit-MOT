package cn.nukkit.inventory.transaction.action;

import cn.nukkit.Player;
import cn.nukkit.inventory.StonecutterInventory;
import cn.nukkit.item.Item;
import lombok.Getter;

public class StonecutterItemAction extends InventoryAction {

    @Getter
    private final int type;

    public StonecutterItemAction(Item sourceItem, Item targetItem, int type) {
        super(sourceItem, targetItem);
        this.type = type;
    }

    @Override
    public boolean isValid(Player source) {
        return source.getWindowById(Player.STONECUTTER_WINDOW_ID) instanceof StonecutterInventory;
    }

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
