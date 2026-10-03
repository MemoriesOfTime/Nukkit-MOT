package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;

/**
 * Cartography Table 3-slot UI (input, additional, result). Used by the Server
 * Authoritative Inventory CraftRecipeOptional flow to resolve map operations such
 * as copying, scaling, adding a compass, or making a locator map.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
public class CartographyTableInventory extends FakeBlockUIComponent {

    public static final int CARTOGRAPHY_INPUT_UI_SLOT = 12;
    public static final int CARTOGRAPHY_ADDITIONAL_UI_SLOT = 13;

    public CartographyTableInventory(PlayerUIInventory playerUI, Position position) {
        super(playerUI, InventoryType.CARTOGRAPHY, CARTOGRAPHY_INPUT_UI_SLOT, position);
    }

    @Override
    public void onClose(Player who) {
        super.onClose(who);

        who.returnUiItems(this.getItem(0), this.getItem(1));

        this.clear(0);
        this.clear(1);

        who.resetCraftingGridType();
    }

    public Item getInput() {
        return this.getItem(0);
    }

    public Item getAdditional() {
        return this.getItem(1);
    }
}
