package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.event.inventory.EnchantItemEvent;
import cn.nukkit.inventory.EnchantInventory;
import cn.nukkit.inventory.transaction.action.EnchantingAction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.network.protocol.PlayerEnchantOptionsPacket;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EnchantTransaction extends InventoryTransaction {
    private EnchantInventory inv;
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
        if (!(this.getSource().getWindowById(Player.ENCHANT_WINDOW_ID) instanceof final EnchantInventory inv)) {
            return false;
        }

        if (!this.getSource().isCreative()) {
            if (cost == -1 || !inv.getReagentSlot().equals(Item.get(Item.DYE, 4), true, false) || inv.getReagentSlot().count < cost) {
                return false;
            }
        }

        return inputItem != null && outputItem != null
            && inputItem.equals(inv.getInputSlot(), true, true)
            && this.checkEnchantValid(inv);
    }

    @Override
    public boolean execute() {
        // This will validate the enchant conditions
        if (this.hasExecuted || !this.canExecute()) {
            source.removeAllWindows(false);
            this.sendInventories();
            return false;
        }
        EnchantInventory inv = (EnchantInventory) this.getSource().getWindowById(Player.ENCHANT_WINDOW_ID);
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
            source.setEnchantmentSeed(source.generateEnchantmentSeed());
            source.setExperience(source.getExperience(), source.getExperienceLevel() - ev.getXpCost());
        }

        return true;
    }

    @Override
    public void addAction(InventoryAction action) {
        super.addAction(action);
        if (action instanceof EnchantingAction) {
            switch (((EnchantingAction) action).getType()) {
                case NetworkInventoryAction.SOURCE_TYPE_ENCHANT_INPUT ->
                    this.inputItem = action.getTargetItem(); // Input sent as newItem
                case NetworkInventoryAction.SOURCE_TYPE_ENCHANT_OUTPUT ->
                    this.outputItem = action.getSourceItem(); // Output sent as oldItem
                case NetworkInventoryAction.SOURCE_TYPE_ENCHANT_MATERIAL -> {
                    if (action.getTargetItem().equals(Item.get(Item.AIR), false, false)) {
                        this.cost = action.getSourceItem().count;
                    } else {
                        this.cost = action.getSourceItem().count - action.getTargetItem().count;
                    }
                }
            }
        }
    }

    public boolean checkForEnchantPart(List<InventoryAction> actions) {
        for (InventoryAction action : actions) {
            if (action instanceof EnchantingAction) return true;
        }
        return false;
    }

    public boolean checkEnchantValid(EnchantInventory inv) {
        for (final PlayerEnchantOptionsPacket.EnchantOptionData option : inv.getOptions()) {
            for (final Enchantment ench : option.enchantments()) {
                if (outputItem.hasEnchantment(ench.getId())) {
                    return outputItem.getId() == inputItem.getId() && outputItem.getDamage() == inputItem.getDamage();
                }
            }
        }

        System.out.println(inv.getOptions());

        return false;
    }
}
