package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockChestCopperWaxed extends BlockChestCopper {

    public BlockChestCopperWaxed() {
        this(0);
    }

    public BlockChestCopperWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Copper Chest";
    }

    @Override
    public int getId() {
        return WAXED_COPPER_CHEST;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.UNAFFECTED;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }
}
