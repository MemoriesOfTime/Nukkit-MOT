package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.DyeColor;

/**
 * Created by PetteriM1
 */
public class BlockHardGlassPaneStained extends BlockHardGlassPane {

    public BlockHardGlassPaneStained() {
        this(0);
    }

    public BlockHardGlassPaneStained(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return HARD_STAINED_GLASS_PANE;
    }

    @Override
    public String getName() {
        return getDyeColor().getName() + " Hardened Stained Glass Pane";
    }

    @Override
    public BlockColor getColor() {
        return getDyeColor().getColor();
    }

    public DyeColor getDyeColor() {
        return DyeColor.getByWoolData(this.getDamage() & 0xF);
    }
}
