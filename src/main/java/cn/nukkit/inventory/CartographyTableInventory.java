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
        super(playerUI, InventoryType.CARTOGRAPHY, 2, position);
    }

    @Override
    public void onClose(Player who) {
        super.onClose(who);

        for (int i = 0; i < 2; i++) {
            Item item = this.getItem(i);
            if (item.isNull()) {
                continue;
            }
            Item[] drops = who.getInventory().addItem(item);
            for (Item drop : drops) {
                if (!who.dropItem(drop)) {
                    this.getHolder().getLevel().dropItem(this.getHolder().add(0.5, 0.5, 0.5), drop);
                }
            }
            this.clear(i);
        }

        who.resetCraftingGridType();
    }

    public Item getInput() {
        return this.getItem(0);
    }

    public Item getAdditional() {
        return this.getItem(1);
    }
}
