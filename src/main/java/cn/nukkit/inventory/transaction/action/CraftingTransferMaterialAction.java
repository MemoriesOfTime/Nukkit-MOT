package cn.nukkit.inventory.transaction.action;

import cn.nukkit.Player;
import cn.nukkit.inventory.transaction.CraftingTransaction;
import cn.nukkit.inventory.transaction.InventoryTransaction;
import cn.nukkit.item.Item;

/**
 * @author CreeperFace
 */
public class CraftingTransferMaterialAction extends InventoryAction {

    public CraftingTransferMaterialAction(Item sourceItem, Item targetItem, int slot) {
        super(sourceItem, targetItem);
    }

    @Override
    public void onAddToTransaction(InventoryTransaction transaction) {
        if (transaction instanceof CraftingTransaction) {
            if (this.sourceItem.isNull()) {
                ((CraftingTransaction) transaction).setInput(this.targetItem);
            } else if (this.targetItem.isNull()) {
                ((CraftingTransaction) transaction).setExtraOutput(this.sourceItem);
            } else {
                if (!this.sourceItem.equals(this.targetItem, true, true)
                        || this.sourceItem.getCount() <= this.targetItem.getCount()) {
                    throw new RuntimeException("Invalid " + getClass().getName() + ", expected a partial consume of the same item, got source: " + this.sourceItem + ", target: " + this.targetItem);
                }
                int consumed = this.sourceItem.getCount() - this.targetItem.getCount();
                Item consumedItem = this.sourceItem.clone();
                consumedItem.setCount(consumed);
                ((CraftingTransaction) transaction).setInput(consumedItem);
            }
        } else {
            throw new RuntimeException(getClass().getName() + " can only be added to CraftingTransactions");
        }
    }

    @Override
    public boolean isValid(Player source) {
        return true;
    }

    @Override
    public boolean execute(Player source) {
        return true;
    }

    @Override
    public void onExecuteSuccess(Player $source) {
    }

    @Override
    public void onExecuteFail(Player source) {
    }
}
