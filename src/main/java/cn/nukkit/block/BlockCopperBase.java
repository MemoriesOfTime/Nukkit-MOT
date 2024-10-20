package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.item.Item;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BlockCopperBase extends BlockSolid implements Oxidizable, Waxable {
    public BlockCopperBase() {
        // Does nothing
    }

    @Override
    public double getHardness() {
        return 3;
    }
    @Override
    public double getResistance() {
        return 6;
    }

    public boolean isWaxed() {
        return false;
    }

    @Override
    public int onUpdate(int type) {
        return Oxidizable.super.onUpdate(type);
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        return Waxable.super.onActivate(item, player)
                || Oxidizable.super.onActivate(item, player);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public Block getStateWithOxidizationLevel(@NotNull OxidizationLevel oxidizationLevel) {
        return Block.get((getCopperId(isWaxed(), oxidizationLevel)));
    }

    @Override
    public boolean setOxidizationLevel(@NotNull OxidizationLevel oxidizationLevel) {
        if (getOxidizationLevel().equals(oxidizationLevel)) {
            return true;
        }
        return getValidLevel().setBlock(this, Block.get(getCopperId(isWaxed(), oxidizationLevel)));
    }

    @Override
    public boolean setWaxed(boolean waxed) {
        if (isWaxed() == waxed) {
            return true;
        }
        return getValidLevel().setBlock(this, Block.get(getCopperId(waxed, getOxidizationLevel())));
    }

    protected int getCopperId(boolean waxed, @Nullable OxidizationLevel oxidizationLevel) {
        if (oxidizationLevel == null) {
            return getId();
        }
        switch (oxidizationLevel) {
            case UNAFFECTED:
                return waxed? WAXED_COPPER : COPPER_BLOCK;
            case EXPOSED:
                return waxed? WAXED_EXPOSED_COPPER : EXPOSED_COPPER;
            case WEATHERED:
                return waxed? WAXED_WEATHERED_COPPER : WEATHERED_COPPER;
            case OXIDIZED:
                return waxed? WAXED_OXIDIZED_COPPER : OXIDIZED_COPPER;
            default:
                return getId();
        }
    }
}