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
import cn.nukkit.item.*;
import cn.nukkit.level.Position;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.recipe.RecipeRegistry;
import cn.nukkit.recipe.impl.SmithingRecipe;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * @author joserobjr
 * @since 2020-09-28
 */
public class SmithingInventory extends FakeBlockUIComponent {
    private static final int EQUIPMENT = 0;
    private static final int INGREDIENT = 1;

    public static final int SMITHING_EQUIPMENT_UI_SLOT = 51;

    public static final int SMITHING_INGREDIENT_UI_SLOT = 52;

    private Item currentResult = Item.get(0);

    private static final IntSet ITEMS = new IntOpenHashSet(new int[]{
            Item.AIR,
            //材料 Material
            ItemID.NETHERITE_INGOT,
            //工具与护甲 Tools and Armor
            ItemID.DIAMOND_SWORD, ItemID.DIAMOND_SHOVEL, ItemID.DIAMOND_PICKAXE, ItemID.DIAMOND_AXE,
            ItemID.DIAMOND_HOE, ItemID.DIAMOND_HELMET, ItemID.DIAMOND_CHESTPLATE, ItemID.DIAMOND_LEGGINGS, ItemID.DIAMOND_BOOTS,
            ItemID.NETHERITE_SWORD, ItemID.NETHERITE_SHOVEL, ItemID.NETHERITE_PICKAXE, ItemID.NETHERITE_AXE, ItemID.NETHERITE_HOE,
            ItemID.NETHERITE_HELMET, ItemID.NETHERITE_CHESTPLATE, ItemID.NETHERITE_LEGGINGS, ItemID.NETHERITE_BOOTS
    });

    public SmithingInventory(PlayerUIInventory playerUI, Position position) {
        super(playerUI, InventoryType.SMITHING_TABLE, 51, position);
    }

    @Nullable
    public SmithingRecipe matchRecipe() {
        return RecipeRegistry.matchSmithingRecipe(Arrays.asList(getEquipment(), getIngredient()));
    }

    @Override
    public void onSlotChange(int index, Item before, boolean send) {
        if (index == EQUIPMENT || index == INGREDIENT) {
            updateResult();
        }
        super.onSlotChange(index, before, send);
    }

    public void updateResult() {
        setResult(this.getResult());
    }

    private void setResult(Item result) {
        this.currentResult = result;
    }

    public Item getResult() {
        SmithingRecipe recipe = matchRecipe();
        if (recipe == null) {
            return Item.AIR_ITEM.clone();
        }
        return recipe.getFinalResult(getEquipment());
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

    @Override
    public boolean allowedToAdd(Item item) {
        return ITEMS.contains(item.getId());
    }
}
