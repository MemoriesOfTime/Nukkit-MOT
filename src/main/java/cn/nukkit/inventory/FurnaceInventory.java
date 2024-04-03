package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityFurnace;
import cn.nukkit.item.Item;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class FurnaceInventory extends ContainerInventory {

    public FurnaceInventory(BlockEntityFurnace furnace) {
        this(furnace, InventoryType.FURNACE);
    }

    public FurnaceInventory(BlockEntityFurnace furnace, InventoryType type) {
        super(furnace, type);
    }

    @Override
    public BlockEntityFurnace getHolder() {
        return (BlockEntityFurnace) this.holder;
    }

    public Item getResult() {
        return this.getItem(2);
    }

    public Item getFuel() {
        return this.getItem(1);
    }

    public Item getSmelting() {
        return this.getItem(0);
    }

    public boolean setResult(Item item) {
        return this.setItem(2, item);
    }

    public boolean setFuel(Item item) {
        return this.setItem(1, item);
    }

    public boolean setSmelting(Item item) {
        return this.setItem(0, item);
    }

    @Override
    public void onSlotChange(int index, Item before, boolean send) {
        super.onSlotChange(index, before, send);

        this.getHolder().scheduleUpdate();
    }

    public boolean setItemByPlayer(Player player, int index, Item item, boolean send) {
        if (index == 2 && (item.getId() == 0 || item.getCount() == 0)) {
            BlockEntityFurnace holder = getHolder();
            holder.releaseExperience();
        }
        return setItem(index, item, send);
    }
}
