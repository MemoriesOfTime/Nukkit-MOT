/*
 * https://PowerNukkit.org - The Nukkit you know but Powerful!
 * Copyright (C) 2021  José Roberto de Araújo Júnior
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.event.inventory.SmithingTableEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.SmithingInventory;
import cn.nukkit.inventory.transaction.action.CreativeInventoryAction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.inventory.transaction.action.SmithingItemAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;

import java.util.List;

/**
 * @author joserobjr
 * @since 2021-05-16
 */
public class SmithingTransaction extends InventoryTransaction {

    private Item equipmentItem;
    private Item ingredientItem;
    private Item templateItem;
    private Item outputItem;

    private boolean isError = false;

    public SmithingTransaction(Player source, List<InventoryAction> actions) {
        super(source, actions);
        //额外检查 保证在所有action处理完成后再检查
        boolean hasSlotChangeAction = false;
        for (InventoryAction action : actions) {
            if (action instanceof SlotChangeAction) {
                SlotChangeAction slotChangeAction = (SlotChangeAction) action;
                if (slotChangeAction.getInventory() instanceof PlayerInventory) { //真正给玩家背包物品的操作
                    if (hasSlotChangeAction) {
                        this.isError = true;
                        return;
                    }
                    hasSlotChangeAction = true;
                    this.outputItem = slotChangeAction.getTargetItem();
                }
            }
        }
    }

    @Override
    public void addAction(InventoryAction action) {
        super.addAction(action);
        if (action instanceof SmithingItemAction) {
            switch (((SmithingItemAction) action).getType()) {
                // input
                case 0:
                    this.equipmentItem = action.getTargetItem();
                    break;
                // result
                case 2:
                    this.outputItem = action.getSourceItem();
                    break;
                // ingredient
                case 1:
                    this.ingredientItem = action.getTargetItem();
                    break;
                // template
                case 3:
                    this.templateItem = action.getTargetItem();
                    break;
            }
        } else if (action instanceof CreativeInventoryAction) {
            CreativeInventoryAction creativeAction = (CreativeInventoryAction) action;
            if (creativeAction.getActionType() == 0
                    && creativeAction.getSourceItem().isNull()
                    && !creativeAction.getTargetItem().isNull() && creativeAction.getTargetItem().getId() == ItemID.NETHERITE_INGOT) {
                this.ingredientItem = action.getTargetItem();
            }
        }
    }

    @Override
    public boolean canExecute() {
        if (this.isError) {
            return false;
        }
        Inventory inventory = getSource().getWindowById(Player.SMITHING_WINDOW_ID);
        if (inventory == null) {
            return false;
        }
        SmithingInventory smithingInventory = (SmithingInventory) inventory;
        if (outputItem == null || outputItem.isNull() ||
                ((equipmentItem == null || equipmentItem.isNull()) && (ingredientItem == null || ingredientItem.isNull()) && (templateItem == null || templateItem.isNull()))) {
            return false;
        }

        Item air = Item.get(0);
        Item equipment = equipmentItem != null? equipmentItem : air;
        Item ingredient = ingredientItem != null? ingredientItem : air;
        Item template = templateItem != null? templateItem : air;

        return equipment.equals(smithingInventory.getEquipment(), true, true)
                && ingredient.equals(smithingInventory.getIngredient(), true, true)
                && template.equals(smithingInventory.getTemplate(), true, true)
                && outputItem.equals(smithingInventory.getResult(), true, true);
    }

    @Override
    public boolean execute() {
        if (this.hasExecuted() || !this.canExecute()) {
            this.source.removeAllWindows(false);
            this.sendInventories();
            return false;
        }
        SmithingInventory inventory = (SmithingInventory) getSource().getWindowById(Player.SMITHING_WINDOW_ID);
        Item air = Item.get(0);
        Item equipment = equipmentItem != null? equipmentItem : air;
        Item ingredient = ingredientItem != null? ingredientItem : air;
        Item template = templateItem != null? templateItem : air;
        SmithingTableEvent event = new SmithingTableEvent(inventory, equipment, outputItem, ingredient, templateItem, source);
        this.source.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            this.source.removeAllWindows(false);
            this.sendInventories();
            return true;
        }

        for (InventoryAction action : this.actions) {
            if (action.execute(this.source)) {
                action.onExecuteSuccess(this.source);
            } else {
                action.onExecuteFail(this.source);
            }
        }
        if (!source.isCreative()) {
            source.sendAllInventories();
            source.getCursorInventory().sendContents(source);
        }
        return true;
    }

    public Item getEquipmentItem() {
        return equipmentItem == null? null : equipmentItem.clone();
    }

    public Item getIngredientItem() {
        return ingredientItem == null? null : ingredientItem.clone();
    }

    public Item getOutputItem() {
        return outputItem == null? null : outputItem.clone();
    }

    public static boolean checkForItemPart(List<InventoryAction> actions) {
        return actions.stream().anyMatch(it-> it instanceof SmithingItemAction);
    }
}