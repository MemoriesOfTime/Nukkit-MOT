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

import cn.nukkit.inventory.data.RecipeUnlockingRequirement;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author joserobjr
 * @since 2020-09-28
 */
@ToString
public class SmithingRecipe extends ShapelessRecipe {

    private final Item template;
    private final Item equipment;
    private final Item ingredient;
    private final Item result;

    private final List<Item> ingredientsAggregate;

    public SmithingRecipe(String recipeId, int priority, Collection<Item> ingredients, Item result) {
        super(recipeId, priority, result, ingredients);
        this.equipment = (Item) ingredients.toArray()[0];
        this.ingredient = (Item) ingredients.toArray()[1];
        if (ingredients.size() >= 3) {
            this.template = (Item) ingredients.toArray()[2];
        } else {
            this.template = Item.AIR_ITEM.clone();
        }
        this.result = result;

        ArrayList<Item> aggregation = new ArrayList<>(3);

        for (Item item : new Item[]{equipment, ingredient, template}) {
            if (item.getId() != 0 && item.getCount() < 1) {
                throw new IllegalArgumentException("Recipe Ingredient amount was not 1 (value: " + item.getCount() + ")");
            }
            boolean found = false;
            for (Item existingIngredient : aggregation) {
                if (existingIngredient.equals(item, item.hasMeta(), item.hasCompoundTag())) {
                    existingIngredient.setCount(existingIngredient.getCount() + item.getCount());
                    found = true;
                    break;
                }
            }
            if (!found) {
                aggregation.add(item.clone());
            }
        }

        aggregation.trimToSize();
        aggregation.sort(CraftingManager.recipeComparator);
        this.ingredientsAggregate = Collections.unmodifiableList(aggregation);
    }

    @Override
    public Item getResult() {
        return result;
    }

    public Item getFinalResult(Item equip, Item template) {
        if (template.getNamespaceId(ProtocolInfo.CURRENT_PROTOCOL).equals("minecraft:netherite_upgrade_smithing_template")) { //We do not use the recipe check template for the time being
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
        return Item.AIR_ITEM.clone();
    }

    @Deprecated
    public Item getFinalResult(Item equip) {
        return getFinalResult(equip, template);
    }

    @Override
    public void registerToCraftingManager(CraftingManager manager) {
        manager.registerSmithingRecipe(ProtocolInfo.CURRENT_PROTOCOL, this);
    }

    @Override
    public RecipeType getType() {
        return RecipeType.SMITHING_TRANSFORM;
    }

    public Item getTemplate() {
        return template;
    }

    public Item getEquipment() {
        return equipment;
    }

    public Item getIngredient() {
        return ingredient;
    }

    @Override
    public List<Item> getIngredientsAggregate() {
        return ingredientsAggregate;
    }

    public boolean matchItems(List<Item> inputList) {
        return matchItems(inputList, 1);
    }

    public boolean matchItems(List<Item> inputList, int multiplier) {
        List<Item> haveInputs = new ArrayList<>();
        for (Item item : inputList) {
            if (item.isNull())
                continue;
            haveInputs.add(item.clone());
        }
        List<Item> needInputs = new ArrayList<>();
        if(multiplier != 1){
            for (Item item : ingredientsAggregate) {
                if (item.isNull())
                    continue;
                Item itemClone = item.clone();
                itemClone.setCount(itemClone.getCount() * multiplier);
                needInputs.add(itemClone);
            }
        } else {
            for (Item item : ingredientsAggregate) {
                if (item.isNull())
                    continue;
                needInputs.add(item.clone());
            }
        }

        return Recipe.matchItemList(haveInputs, needInputs);
    }

    @Override
    public RecipeUnlockingRequirement getRequirement() {
        return RecipeUnlockingRequirement.ALWAYS_UNLOCKED;
    }
}
