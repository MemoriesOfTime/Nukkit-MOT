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

package cn.nukkit.recipe.impl;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.recipe.Recipe;
import cn.nukkit.recipe.RecipeType;
import cn.nukkit.recipe.descriptor.DefaultDescriptor;
import cn.nukkit.recipe.descriptor.ItemDescriptor;
import cn.nukkit.recipe.impl.data.RecipeUnlockingRequirement;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author joserobjr
 * @since 2020-09-28
 */
@Getter
@ToString
public class SmithingRecipe extends ShapelessRecipe {

    private final Item equipment;
    private final Item ingredient;
    private final Item result;

    private final Collection<ItemDescriptor> ingredients;

    public SmithingRecipe(String recipeId, int priority, Collection<ItemDescriptor> ingredients, Item result) {
        super(recipeId, priority, result, ingredients);
        this.equipment = ((DefaultDescriptor) ingredients.toArray()[0]).getItem();
        this.ingredient = ((DefaultDescriptor) ingredients.toArray()[1]).getItem();
        this.result = result;

        this.ingredients = ingredients;
    }

    @Override
    public Item getResult() {
        return result;
    }

    public Item getFinalResult(Item equip) {
        Item finalResult = getResult().clone();

        if (equip.hasCompoundTag()) {
            finalResult.setCompoundTag(equip.getCompoundTag());
        }

        int maxDurability = finalResult.getMaxDurability();
        if (maxDurability <= 0 || equip.getMaxDurability() <= 0) {
            return finalResult;
        }

        int damage = equip.getDamage();
        if (damage <= 0) {
            return finalResult;
        }

        finalResult.setDamage(Math.min(maxDurability, damage));
        return finalResult;
    }

    @Override
    public RecipeType getType() {
        return RecipeType.SMITHING_TRANSFORM;
    }

    public Item getEquipment() {
        return equipment;
    }

    public Item getIngredient() {
        return ingredient;
    }

    public boolean matchItems(List<Item> inputList) {
        List<Item> haveInputs = new ArrayList<>();
        for (Item item : inputList) {
            if (item.isNull())
                continue;
            haveInputs.add(item.clone());
        }

        return Recipe.matchItemList(haveInputs, new ArrayList<>(ingredients));
    }

    @Override
    public boolean isValidRecipe(int protocol) {
        return true;
    }

    @Override
    public RecipeUnlockingRequirement getRequirement() {
        return RecipeUnlockingRequirement.ALWAYS_UNLOCKED;
    }
}
