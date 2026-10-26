package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;

import javax.annotation.Nonnull;

public class BlockCarvedPumpkin extends BlockPumpkin {

    public BlockCarvedPumpkin() {
        this(0);
    }

    public BlockCarvedPumpkin(int meta) {
        super(meta);
    }


    @Override
    public int getId() {
        return CARVED_PUMPKIN;
    }

    @Override
    public String getName() {
        return "Carved Pumpkin";
    }

    @Override
    public boolean canBeActivated() {
        return false;
    }

    @Override
    public boolean onActivate(@Nonnull Item item, Player player) {
        return false;
    }
}
