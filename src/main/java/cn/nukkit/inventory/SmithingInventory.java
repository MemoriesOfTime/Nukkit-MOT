
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
}
