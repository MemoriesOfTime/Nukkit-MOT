package cn.nukkit.block;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySculkShrieker;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Reacts to vibrations by shrieking, applying Darkness, and summoning a warden. The damage value
 * stores bit 0 = shrieking, bit 1 = can_summon (set by world gen). Adapted from PowerNukkitX.
 */
public class BlockSculkShrieker extends BlockTransparent implements BlockEntityHolder<BlockEntitySculkShrieker> {

    private static final int FLAG_SHRIEKING = 0x01;
    private static final int FLAG_CAN_SUMMON = 0x02;

    @Override
    public int getId() {
        return SCULK_SHRIEKER;
    }

    @Override
    public String getName() {
        return "Sculk Shrieker";
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_HOE;
    }

    @Override
    public double getHardness() {
        return 1;
    }

    @Override
    public double getResistance() {
        return 3;
    }

    @Override
    public int getLightLevel() {
        return 1;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BLACK_BLOCK_COLOR;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.hasEnchantment(Enchantment.ID_SILK_TOUCH)) {
            return new Item[]{
                    this.toItem()
            };
        }
        return Item.EMPTY_ARRAY;
    }

    @Override
    public int getDropExp() {
        return 5;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntitySculkShrieker> getBlockEntityClass() {
        return BlockEntitySculkShrieker.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.SCULK_SHRIEKER;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @Nullable cn.nukkit.Player player) {
        this.setDamage(0);
        this.getLevel().setBlock(block, this, true, true);
        this.createBlockEntity();
        return true;
    }

    /** Whether this shrieker is in the shrieking animation. */
    public boolean isShrieking() {
        return (this.getDamage() & FLAG_SHRIEKING) != 0;
    }

    /** Sets the shrieking state and updates the block. */
    public void setShrieking(boolean shrieking) {
        int damage = this.getDamage();
        if (shrieking) {
            damage |= FLAG_SHRIEKING;
        } else {
            damage &= ~FLAG_SHRIEKING;
        }
        this.setDamage(damage);
        this.level.setBlock(this, this, true, false);
    }

    /** Whether this shrieker can summon a warden (only naturally generated ones can). */
    public boolean canSummon() {
        return (this.getDamage() & FLAG_CAN_SUMMON) != 0;
    }

    /** Sets the can-summon flag. */
    public void setCanSummon(boolean canSummon) {
        int damage = this.getDamage();
        if (canSummon) {
            damage |= FLAG_CAN_SUMMON;
        } else {
            damage &= ~FLAG_CAN_SUMMON;
        }
        this.setDamage(damage);
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }
}
