package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.inventory.StonecutterItemEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.StonecutterInventory;
import cn.nukkit.inventory.StonecutterRecipe;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.inventory.transaction.action.StonecutterItemAction;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class StonecutterTransaction extends InventoryTransaction {

    @Getter
    private Item inputItem;
    @Getter
    private Item outputItem;
    private final List<Item> outputItemCheck = new ArrayList<>();

    public StonecutterTransaction(Player source, List<InventoryAction> actions) {
        super(source, actions);

        for (InventoryAction action : actions) {
            if (action instanceof SlotChangeAction slotChangeAction) {
                if (!(slotChangeAction.getInventory() instanceof StonecutterInventory)) {
                    this.outputItemCheck.add(slotChangeAction.getTargetItemUnsafe());
                }
            }
        }
    }

    @Override
    public void addAction(InventoryAction action) {
        super.addAction(action);
        if (action instanceof StonecutterItemAction sta) {
            Server.getInstance().getLogger().warning("StonecutterTransaction.addAction: type=" + sta.getType()
                    + ", sourceItem=" + action.getSourceItem() + ", targetItem=" + action.getTargetItem());
            switch (sta.getType()) {
                case NetworkInventoryAction.SOURCE_TYPE_CRAFTING_USE_INGREDIENT:
                    this.inputItem = action.getTargetItem();
                    break;
                case NetworkInventoryAction.SOURCE_TYPE_CRAFTING_RESULT:
                    this.outputItem = action.getSourceItem();
                    break;
            }
        }
    }

    @Override
    public boolean canExecute() {
        if (!super.canExecute()) {
            source.getServer().getLogger().warning("StonecutterTransaction: super.canExecute() failed");
            return false;
        }

        Inventory inventory = getSource().getWindowById(Player.STONECUTTER_WINDOW_ID);
        if (!(inventory instanceof StonecutterInventory stonecutterInventory)) {
            source.getServer().getLogger().warning("StonecutterTransaction: no stonecutter inventory open");
            return false;
        }

        if (this.outputItem == null || this.outputItem.isNull() || this.inputItem == null || this.inputItem.isNull()) {
            source.getServer().getLogger().warning("StonecutterTransaction: inputItem=" + this.inputItem + ", outputItem=" + this.outputItem);
            return false;
        }

        // 验证客户端声称的输出与库存中其他 action 的一致性
        for (Item check : this.outputItemCheck) {
            if (check != null && !this.outputItem.equals(check)) {
                source.getServer().getLogger().debug("Illegal stonecutter output");
                return false;
            }
        }

        // 验证客户端声称的输入与库存中实际物品一致
        if (!inputItem.equals(stonecutterInventory.getInput(), true, true)) {
            source.getServer().getLogger().warning("StonecutterTransaction: input mismatch, claimed=" + inputItem + ", actual=" + stonecutterInventory.getInput());
            return false;
        }

        StonecutterRecipe recipe = Server.getInstance().getCraftingManager().matchStonecutterRecipe(this.inputItem, this.outputItem);
        if (recipe == null) {
            source.getServer().getLogger().warning("StonecutterTransaction: no matching recipe for input=" + this.inputItem + ", output=" + this.outputItem);
            return false;
        }

        // 验证输出数量与配方一致
        if (this.outputItem.getCount() != recipe.getResult().getCount()) {
            source.getServer().getLogger().debug("Stonecutter output count mismatch");
            return false;
        }

        return true;
    }

    @Override
    public boolean execute() {
        if (this.invalid || this.hasExecuted() || !this.canExecute()) {
            this.source.removeAllWindows(false);
            this.sendInventories();
            return false;
        }

        StonecutterInventory inventory = (StonecutterInventory) getSource().getWindowById(Player.STONECUTTER_WINDOW_ID);
        StonecutterItemEvent event = new StonecutterItemEvent(inventory, this.inputItem, this.outputItem, this.source);
        this.source.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            this.sendInventories();
            source.setNeedSendInventory(true);
            return true;
        }

        for (InventoryAction action : this.actions) {
            if (action.execute(this.source)) {
                action.onExecuteSuccess(this.source);
            } else {
                action.onExecuteFail(this.source);
            }
        }

        return true;
    }

    public static boolean isIn(List<InventoryAction> actions) {
        for (InventoryAction action : actions) {
            if (action instanceof StonecutterItemAction) return true;
        }
        return false;
    }

    @Override
    public boolean checkForItemPart(List<InventoryAction> actions) {
        return isIn(actions);
    }
}
