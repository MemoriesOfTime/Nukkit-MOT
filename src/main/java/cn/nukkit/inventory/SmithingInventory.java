/*
 * https://PowerNukkit.org - The Nukkit you know but Powerful!
 * Copyright (C) 2020  José Roberto de Araújo Júnior
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

package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemArmor;
import cn.nukkit.item.ItemTrimMaterial;
import cn.nukkit.item.ItemTrimPattern;
import cn.nukkit.level.Position;
import cn.nukkit.network.protocol.ProtocolInfo;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * @author joserobjr
 * @since 2020-09-28
 */
public class SmithingInventory extends FakeBlockUIComponent {
    private static final int EQUIPMENT = 0;
    private static final int INGREDIENT = 1;
    private static final int TEMPLATE = 2;

    public static final int SMITHING_EQUIPMENT_UI_SLOT = 51;

    public static final int SMITHING_INGREDIENT_UI_SLOT = 52;
    public static final int SMITHING_TEMPLATE_UI_SLOT = 53;

    private Item currentResult = Item.get(0);


    public SmithingInventory(PlayerUIInventory playerUI, Position position) {
        super(playerUI, InventoryType.SMITHING_TABLE, 51, position);
    }

    @Nullable
    public SmithingRecipe matchRecipe() {
        return Server.getInstance().getCraftingManager().matchSmithingRecipe(ProtocolInfo.CURRENT_PROTOCOL, Arrays.asList(getEquipment(), getIngredient(), getTemplate()));
    }

    @Override
    public void onSlotChange(int index, Item before, boolean send) {
        if (index == EQUIPMENT || index == INGREDIENT || index == TEMPLATE) {
            updateResult();
        }
        super.onSlotChange(index, before, send);
    }

    public void updateResult() {
        Item result;
        SmithingRecipe recipe = matchRecipe();
        if (recipe == null) {
            result = Item.get(0);
        } else {
            result = recipe.getFinalResult(getEquipment(), getTemplate());
        }
        setResult(result);
    }

    private void setResult(Item result) {
        this.currentResult = result;
    }

    public Item getResult() {
        SmithingRecipe recipe = matchRecipe();
        if (recipe == null) {
            return Item.get(0);
        }
        return recipe.getFinalResult(getEquipment(), getTemplate());
    }

    public Item getTemplate() {
        return getItem(TEMPLATE);
    }

    public void setTemplate(Item template) {
        setItem(TEMPLATE, template);
    }

    public Item getEquipment() {
        return getItem(EQUIPMENT);
    }

    public void setEquipment(Item equipment) {
        setItem(EQUIPMENT, equipment);
    }

    public Item getIngredient() {
        return getItem(INGREDIENT);
    }

    public void setIngredient(Item ingredient) {
        setItem(INGREDIENT, ingredient);
    }

    @Override
    public void onOpen(Player who) {
        super.onOpen(who);
        who.craftingType = Player.CRAFTING_SMITHING;
    }

    @Override
    public void onClose(Player who) {
        super.onClose(who);
        who.craftingType = Player.CRAFTING_SMALL;

        who.giveItem(getItem(EQUIPMENT), getItem(INGREDIENT));

        this.clear(EQUIPMENT);
        this.clear(INGREDIENT);
        this.getHolder().getInventory().clear(CREATED_ITEM_OUTPUT_UI_SLOT);
    }

    public Item getCurrentResult() {
        return currentResult;
    }

    public Item getTrimOutPutItem(){
        Item input = this.getEquipment().clone();
        if(this.getIngredient() instanceof ItemTrimMaterial && this.getTemplate() instanceof ItemTrimPattern){
            if(!input.isNull() && input instanceof ItemArmor) {
                ItemArmor trimmedArmor = (ItemArmor) input.clone();
                ItemTrimMaterial material = (ItemTrimMaterial) this.getIngredient();
                ItemTrimPattern pattern = (ItemTrimPattern) this.getTemplate();
                trimmedArmor.setTrim(pattern.getPattern(), material.getMaterial());
                return trimmedArmor;
            }
        }
        return Item.AIR_ITEM;
    }
}
