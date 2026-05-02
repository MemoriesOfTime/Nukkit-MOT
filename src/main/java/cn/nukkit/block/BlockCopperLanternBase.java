package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Copper lantern base implementation.
 * <p>
 * Adapted from Lumi (<a href="https://github.com/KoshakMineDEV/Lumi">Lumi</a>)
 * and PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public abstract class BlockCopperLanternBase extends BlockLantern implements Oxidizable, Waxable {

    public BlockCopperLanternBase() {
        this(0);
    }

    public BlockCopperLanternBase(int meta) {
        super(meta);
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId()), 0, 1);
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public int onUpdate(int type) {
        int update = Oxidizable.super.onUpdate(type);
        return update != 0 ? update : super.onUpdate(type);
    }

    @Override
    public boolean onActivate(@NotNull Item item, Player player) {
        return Waxable.super.onActivate(item, player) || Oxidizable.super.onActivate(item, player);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public Block getStateWithOxidizationLevel(@NotNull OxidizationLevel oxidizationLevel) {
        return Block.get(this.getCopperId(this.isWaxed(), oxidizationLevel), this.getDamage());
    }

    @Override
    public boolean setOxidizationLevel(@NotNull OxidizationLevel oxidizationLevel) {
        if (this.getOxidizationLevel().equals(oxidizationLevel)) {
            return true;
        }
        return this.getValidLevel().setBlock(this, Block.get(this.getCopperId(this.isWaxed(), oxidizationLevel), this.getDamage()));
    }

    @Override
    public boolean setWaxed(boolean waxed) {
        if (this.isWaxed() == waxed) {
            return true;
        }
        return this.getValidLevel().setBlock(this, Block.get(this.getCopperId(waxed, this.getOxidizationLevel()), this.getDamage()));
    }

    @Override
    public boolean isWaxed() {
        return false;
    }

    protected int getCopperId(boolean waxed, @Nullable OxidizationLevel oxidizationLevel) {
        if (oxidizationLevel == null) {
            return this.getId();
        }
        return switch (oxidizationLevel) {
            case UNAFFECTED -> waxed ? WAXED_COPPER_LANTERN : COPPER_LANTERN;
            case EXPOSED -> waxed ? WAXED_EXPOSED_COPPER_LANTERN : EXPOSED_COPPER_LANTERN;
            case WEATHERED -> waxed ? WAXED_WEATHERED_COPPER_LANTERN : WEATHERED_COPPER_LANTERN;
            case OXIDIZED -> waxed ? WAXED_OXIDIZED_COPPER_LANTERN : OXIDIZED_COPPER_LANTERN;
        };
    }
}
