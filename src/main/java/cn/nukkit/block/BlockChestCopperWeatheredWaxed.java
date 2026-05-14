package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockChestCopperWeatheredWaxed extends BlockChestCopper {

    public BlockChestCopperWeatheredWaxed() {
        this(0);
    }

    public BlockChestCopperWeatheredWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Weathered Copper Chest";
    }

    @Override
    public int getId() {
        return WAXED_WEATHERED_COPPER_CHEST;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }
}
