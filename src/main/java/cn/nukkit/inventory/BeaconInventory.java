package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;

/**
 * @author Rover656
 */
public class BeaconInventory extends FakeBlockUIComponent {

    private int material;

    public BeaconInventory(PlayerUIInventory playerUI, Position position) {
        super(playerUI, InventoryType.BEACON, 27, position);
    }

    @Override
    public void onOpen(Player who) {
        super.onOpen(who);
        who.craftingType = Player.BEACON_WINDOW_ID;
    }

    @Override
    public void onClose(Player who) {
        super.onClose(who);

        // Drop item in slot if client doesn't automatically move it to player's inventory
        if (!who.isConnected()) {
            this.getHolder().getLevel().dropItem(this.getHolder().add(0.5, 0.5, 0.5), this.getItem(0));
        }
        this.clear(0);
    }

    public int useMaterial() {
        int usedMaterial = this.material;
        this.material = 0;
        return usedMaterial;
    }

    public void setMaterial() {
        this.material = this.getItemFast(0).getId();
        this.setItem(0, Item.get(Item.AIR));
    }
}
