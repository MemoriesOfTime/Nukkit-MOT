package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.math.Vector3;

/**
 * @author LT_Name
 */
public class ItemSpyglass extends Item {

    public ItemSpyglass() {
        this(0, 1);
    }

    public ItemSpyglass(Integer meta) {
        this(meta, 1);
    }

    public ItemSpyglass(Integer meta, int count) {
        super(SPYGLASS, 0, count, "Spyglass");
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        return true;
    }

    @Override
    public boolean onUse(Player player, int ticksUsed) {
        return true;
    }

    @Override
    public boolean onRelease(Player player, int ticksUsed) {
        return true;
    }

    @Override
    public boolean canRelease() {
        return true;
    }
}
