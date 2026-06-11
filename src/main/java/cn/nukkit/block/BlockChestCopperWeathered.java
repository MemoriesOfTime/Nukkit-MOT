package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockChestCopperWeathered extends BlockChestCopper {

    public BlockChestCopperWeathered() {
        this(0);
    }

    public BlockChestCopperWeathered(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Weathered Copper Chest";
    }

    @Override
    public int getId() {
        return WEATHERED_COPPER_CHEST;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }
}
