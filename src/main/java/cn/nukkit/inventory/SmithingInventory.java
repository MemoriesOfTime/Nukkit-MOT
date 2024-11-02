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
    private static final int TEMPLATE = 2;

    public static final int SMITHING_EQUIPMENT_UI_SLOT = 51;

    public static final int SMITHING_INGREDIENT_UI_SLOT = 52;
    public static final int SMITHING_TEMPLATE_UI_SLOT = 53;

    private Item currentResult = Item.get(0);

    private static final IntSet ITEMS = new IntOpenHashSet(new int[]{
            Item.AIR,
            //材料 Material
            Item.IRON_INGOT, Item.GOLD_INGOT, Item.EMERALD, Item.REDSTONE, Item.NETHER_QUARTZ,
            ItemID.NETHERITE_INGOT, ItemID.AMETHYST_SHARD,
            //工具与护甲 Tools and Armor
            ItemID.DIAMOND_SWORD, ItemID.DIAMOND_SHOVEL, ItemID.DIAMOND_PICKAXE, ItemID.DIAMOND_AXE,
            ItemID.DIAMOND_HOE, ItemID.DIAMOND_HELMET, ItemID.DIAMOND_CHESTPLATE, ItemID.DIAMOND_LEGGINGS, ItemID.DIAMOND_BOOTS,
            ItemID.NETHERITE_SWORD, ItemID.NETHERITE_SHOVEL, ItemID.NETHERITE_PICKAXE, ItemID.NETHERITE_AXE, ItemID.NETHERITE_HOE,
            ItemID.NETHERITE_HELMET, ItemID.NETHERITE_CHESTPLATE, ItemID.NETHERITE_LEGGINGS, ItemID.NETHERITE_BOOTS
    });

    private static final ObjectSet<String> ITEMS_NAMESPACE = new ObjectOpenHashSet<>(new String[]{
            //材料 Material
            Item.COPPER_INGOT,
            //模板 Template
            Item.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Item.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, Item.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Item.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, Item.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, Item.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Item.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, Item.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, Item.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Item.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, Item.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, Item.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Item.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, Item.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, Item.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Item.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, Item.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, Item.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
            Item.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE
    });

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
        setResult(this.getResult());
    }

    private void setResult(Item result) {
        this.currentResult = result;
    }

    public Item getResult() {
        Item trimOutPutItem = this.getTrimOutPutItem();
        if (!trimOutPutItem.isNull()) {
            return trimOutPutItem;
        }
        SmithingRecipe recipe = matchRecipe();
        if (recipe == null) {
            return Item.AIR_ITEM.clone();
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

        who.giveItem(getItem(EQUIPMENT), getItem(INGREDIENT), getItem(TEMPLATE));

        this.clear(EQUIPMENT);
        this.clear(INGREDIENT);
        this.clear(TEMPLATE);
        this.getHolder().getInventory().clear(CREATED_ITEM_OUTPUT_UI_SLOT);
    }

    public Item getCurrentResult() {
        return currentResult;
    }

    public Item getTrimOutPutItem() {
        Item input = this.getEquipment().clone();
        if (this.getIngredient() instanceof ItemTrimMaterial && this.getTemplate() instanceof ItemTrimPattern) {
            if (!input.isNull() && input instanceof ItemArmor) {
                ItemArmor trimmedArmor = (ItemArmor) input.clone();
                ItemTrimMaterial material = (ItemTrimMaterial) this.getIngredient();
                ItemTrimPattern pattern = (ItemTrimPattern) this.getTemplate();
                trimmedArmor.setTrim(pattern.getPattern(), material.getMaterial());
                return trimmedArmor;
            }
        }
        return Item.AIR_ITEM.clone();
    }

    @Override
    public boolean allowedToAdd(Item item) {
        return ITEMS.contains(item.getId()) || ITEMS_NAMESPACE.contains(item.getNamespaceId());
    }
}
