package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.inventory.transaction.action.TradeAction;

import java.util.List;

public class TradingTransaction extends InventoryTransaction {
    public TradingTransaction(Player source, List<InventoryAction> actions) {
        super(source, actions);
    }

    @Override
    public boolean canExecute() {
        for (var action : this.getActionList()) {
            if (action instanceof TradeAction tradeAction) {
                if (tradeAction.isValid(this.source)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean execute() {
        for (InventoryAction action : this.actions) {
            if (action instanceof SlotChangeAction slotChangeAction) {
                if (slotChangeAction.execute(this.source)) {
                    slotChangeAction.onExecuteSuccess(this.source);
                } else {
                    slotChangeAction.onExecuteFail(this.source);
                }
            }
        }
        return true;
    }
}
