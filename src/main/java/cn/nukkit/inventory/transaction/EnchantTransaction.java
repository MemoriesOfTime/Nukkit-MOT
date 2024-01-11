package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.event.inventory.EnchantItemEvent;
import cn.nukkit.inventory.EnchantInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.inventory.transaction.action.EnchantingAction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class EnchantTransaction extends InventoryTransaction {

    private Item inputItem;
    private Item outputItem;
    private int cost = -1;

    public EnchantTransaction(Player source, List<InventoryAction> actions) {
        super(source, actions);
        for (InventoryAction action : actions) {
            if (action instanceof SlotChangeAction slotChangeAction) {
                if (slotChangeAction.getInventory() instanceof EnchantInventory && slotChangeAction.getSlot() == 0) {
                    this.outputItem = slotChangeAction.getTargetItem();
                }
            }
        }
    }

    @Override
    public boolean canExecute() {
        Inventory inv = getSource().getWindowById(Player.ENCHANT_WINDOW_ID);
        if (inv == null) return false;
        EnchantInventory eInv = (EnchantInventory) inv;
        if (!getSource().isCreative()) {
            if (cost == -1 || !eInv.getReagentSlot().equals(Item.get(Item.DYE, 4), true, false) || eInv.getReagentSlot().count < cost)
                return false;
        }
        return inputItem != null && outputItem != null
                && inputItem.equals(eInv.getInputSlot(), true, true)
                && this.checkEnchantValid();
    }

    @Override
    public boolean execute() {
        // This will validate the enchant conditions
        if (this.hasExecuted || !this.canExecute()) {
            source.removeAllWindows(false);
            this.sendInventories();
            return false;
        }
        EnchantInventory inv = (EnchantInventory) getSource().getWindowById(Player.ENCHANT_WINDOW_ID);
        EnchantItemEvent ev = new EnchantItemEvent(inv, inputItem, outputItem, cost, source);
        source.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            source.removeAllWindows(false);
            this.sendInventories();
            // Cancelled by plugin, means handled OK
            return true;
        }
        // This will process all the slot changes
        for (InventoryAction a : this.actions) {
            if (a.execute(source)) {
                a.onExecuteSuccess(source);
            } else {
                a.onExecuteFail(source);
            }
        }

        if (!ev.getNewItem().equals(this.outputItem, true, true)) {
            // Plugin changed item, so the previous slot change is going to be invalid
            // Send the replaced item to the enchant inventory manually
            inv.setItem(0, ev.getNewItem(), true);
        }

        if (!source.isCreative()) {
            source.setExperience(source.getExperience(), source.getExperienceLevel() - ev.getXpCost());
        }
        return true;
    }

    @Override
    public void addAction(InventoryAction action) {
        super.addAction(action);
        if (action instanceof EnchantingAction) {
            switch (((EnchantingAction) action).getType()) {
                case NetworkInventoryAction.SOURCE_TYPE_ENCHANT_INPUT:
                    this.inputItem = action.getTargetItem(); // Input sent as newItem
                    break;
                case NetworkInventoryAction.SOURCE_TYPE_ENCHANT_OUTPUT:
                    this.outputItem = action.getSourceItem(); // Output sent as oldItem
                    break;
                case NetworkInventoryAction.SOURCE_TYPE_ENCHANT_MATERIAL:
                    if (action.getTargetItem().equals(Item.get(Item.AIR), false, false)) {
                        this.cost = action.getSourceItem().count;
                    } else {
                        this.cost = action.getSourceItem().count - action.getTargetItem().count;
                    }
                    break;
            }

        }
    }

    public boolean checkForEnchantPart(List<InventoryAction> actions) {
        for (InventoryAction action : actions) {
            if (action instanceof EnchantingAction) return true;
        }
        return false;
    }

    /**
     * 检查并完成 从附魔台取出附魔书操作
     */
    @Nullable
    public List<SlotChangeAction> checkForSlotChange(List<InventoryAction> actions) {
        if (actions.size() != 2) {
            return null;
        }
        List<SlotChangeAction> slotChangeActions = new ArrayList<>();
        Item sourceItem = null;
        Item targetItem = null;
        boolean isSource = true; //正常的数据包第一个为源物品
        for (InventoryAction action : actions) {
            if (action instanceof SlotChangeAction slotChangeAction) {
                if (slotChangeAction.getInventory() instanceof EnchantInventory
                        || slotChangeAction.getInventory() instanceof PlayerUIInventory
                        || slotChangeAction.getInventory() instanceof PlayerInventory) {
                    slotChangeActions.add(slotChangeAction);
                    if (isSource) {
                        sourceItem = slotChangeAction.getSourceItem();
                    } else {
                        targetItem = slotChangeAction.getTargetItem();
                    }
                    isSource = false;
                }
            }
        }
        if (sourceItem != null && sourceItem.equals(targetItem)) {
            for (SlotChangeAction action : slotChangeActions) {
                action.execute(source);
            }
            return slotChangeActions;
        }
        return null;
    }

    public boolean checkEnchantValid() {
        if (this.inputItem.getId() != this.outputItem.getId()
                || this.inputItem.getCount() != this.outputItem.getCount()) {
            return false;
        }

        //TODO 检查附魔

        return true;
    }
}
