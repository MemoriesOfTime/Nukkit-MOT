package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockChestCopperOxidizedWaxed extends BlockChestCopper {

    public BlockChestCopperOxidizedWaxed() {
        this(0);
    }

    public BlockChestCopperOxidizedWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Oxidized Copper Chest";
    }

    @Override
    public int getId() {
        return WAXED_OXIDIZED_COPPER_CHEST;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.OXIDIZED;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }
}
