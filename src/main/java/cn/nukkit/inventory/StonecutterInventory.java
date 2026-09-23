package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;

public class StonecutterInventory extends FakeBlockUIComponent {

    public static final int STONECUTTER_INPUT_UI_SLOT = 3;

    public StonecutterInventory(PlayerUIInventory playerUI, Position position) {
        super(playerUI, InventoryType.STONECUTTER, 3, position);
    }

    @Override
    public void onOpen(Player who) {
        super.onOpen(who);
        who.craftingType = Player.STONECUTTER_WINDOW_ID;
    }

    @Override
    public void onClose(Player who) {
        super.onClose(who);
        who.craftingType = Player.CRAFTING_SMALL;

        who.returnUiItems(this.getItem(0));

        this.clear(0);
        who.resetCraftingGridType();
    }

    public Item getInput() {
        return this.getItem(0);
    }
}
