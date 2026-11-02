package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.event.inventory.GrindItemEvent;
import cn.nukkit.inventory.GrindstoneInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.transaction.action.GrindstoneItemAction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class GrindstoneTransaction extends InventoryTransaction {

    @Getter
    private Item equipmentItem;
    @Getter
    private Item ingredientItem;
    @Getter
    private Item outputItem;
    @Getter
    private int experienceDropped;
    private final List<Item> outputItemCheck = new ArrayList<>();
    private boolean executionInProgress;

    public GrindstoneTransaction(Player source, List<InventoryAction> actions) {
        super(source, actions);

        for (InventoryAction action : actions) {
            if (action instanceof SlotChangeAction slotChangeAction) {
                if (!(slotChangeAction.getInventory() instanceof GrindstoneInventory)) {
                    this.outputItemCheck.add(slotChangeAction.getTargetItemUnsafe());
                }
            }
        }
    }

    @Override
    public void addAction(InventoryAction action) {
        super.addAction(action);
        if (action instanceof GrindstoneItemAction) {
            switch (((GrindstoneItemAction) action).getType()) {
                case NetworkInventoryAction.SOURCE_TYPE_ANVIL_INPUT:
                    this.equipmentItem = action.getTargetItem();
                    break;
                case NetworkInventoryAction.SOURCE_TYPE_ANVIL_MATERIAL:
                    this.ingredientItem = action.getTargetItem();
                    break;
                case NetworkInventoryAction.SOURCE_TYPE_ANVIL_RESULT:
                    this.outputItem = action.getSourceItem();
                    break;
            }
        }
    }

    @Override
    public boolean canExecute() {
        if (!super.canExecute()) {
            return false;
        }

        Inventory inventory = getSource().getWindowById(Player.GRINDSTONE_WINDOW_ID);
        if (!(inventory instanceof GrindstoneInventory grindstoneInventory)) {
            return false;
        }

        if (this.outputItem == null || this.outputItem.isNull()) {
            return false;
        }

        // equipment 或 ingredient 至少有一个非空
        boolean hasEquipment = this.equipmentItem != null && !this.equipmentItem.isNull();
        boolean hasIngredient = this.ingredientItem != null && !this.ingredientItem.isNull();
        if (!hasEquipment && !hasIngredient) {
            return false;
        }

        for (Item check : this.outputItemCheck) {
            if (check != null && !this.outputItem.equals(check)) {
                source.getServer().getLogger().debug("Illegal output");
                return false;
            }
        }

        Item equipmentOptional = hasEquipment ? equipmentItem : Item.get(Item.AIR);
        Item ingredientOptional = hasIngredient ? ingredientItem : Item.get(Item.AIR);

        return equipmentOptional.equals(grindstoneInventory.getEquipment(), true, true)
                && ingredientOptional.equals(grindstoneInventory.getIngredient(), true, true)
                && outputItem.equals(grindstoneInventory.getResult(), true, true);
    }

    @Override
    public boolean execute() {
        if (executionInProgress || this.hasExecuted() || !NativeRecipeTransaction.supports(this)) return false;
        executionInProgress = true;
        try {
            if (!this.canExecute()) {
                this.sendInventories();
                return false;
            }
            GrindstoneInventory inventory = (GrindstoneInventory) getSource().getWindowById(Player.GRINDSTONE_WINDOW_ID);
            Item equipment = this.equipmentItem == null ? null : this.equipmentItem.clone();
            Item ingredient = this.ingredientItem == null ? null : this.ingredientItem.clone();
            Item originalOutput = this.outputItem.clone();
            GrindItemEvent event = new GrindItemEvent(inventory, equipment == null ? null : equipment.clone(),
                    originalOutput.clone(), ingredient == null ? null : ingredient.clone(),
                    inventory.calculateExperience(), this.source);
            int[] approvedExperience = {0};
            boolean committed = NativeRecipeTransaction.execute(this, originalOutput, this::getOutputItem, () -> {
                this.source.getServer().getPluginManager().callEvent(event);
                if (event.isCancelled() || event.getExperienceDropped() < 0) return false;
                Item approved = applyEventOutputToActions(originalOutput, event.getNewItem());
                if (approved == null) return false;
                this.outputItem = approved;
                approvedExperience[0] = event.getExperienceDropped();
                return true;
            }, () -> sameNullableItem(this.equipmentItem, equipment) && sameNullableItem(this.ingredientItem, ingredient)
                    && this.source.getWindowById(Player.GRINDSTONE_WINDOW_ID) == inventory,
                    () -> this.experienceDropped = approvedExperience[0]);
            if (!committed) {
                this.sendInventories();
                source.setNeedSendInventory(true);
            }
            return committed;
        } finally {
            executionInProgress = false;
        }
    }

    private static boolean sameNullableItem(Item actual, Item expected) {
        return actual == null ? expected == null : expected != null && actual.equalsExact(expected);
    }

    public static boolean isIn(List<InventoryAction> actions) {
        for (InventoryAction action : actions) {
            if (action instanceof GrindstoneItemAction) return true;
        }
        return false;
    }

    @Override
    public boolean checkForItemPart(List<InventoryAction> actions) {
        return isIn(actions);
    }
}
