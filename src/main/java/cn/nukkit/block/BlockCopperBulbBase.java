package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.event.redstone.RedstoneUpdateEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author glorydark
 */
public abstract class BlockCopperBulbBase extends BlockSolidMeta implements Oxidizable, Waxable {

    public static final int LIT_BIT = 0x01; // 0001

    public static final int POWERED_BIT = 0x02; // 0010

    public BlockCopperBulbBase() {
        super(0);
    }

    public BlockCopperBulbBase(int meta) {
        super(meta);
    }

    @Override
    public int onUpdate(int type) {
        Oxidizable.super.onUpdate(type);

        if (type == Level.BLOCK_UPDATE_REDSTONE) {
            RedstoneUpdateEvent ev = new RedstoneUpdateEvent(this);
            getLevel().getServer().getPluginManager().callEvent(ev);

            if (ev.isCancelled()) {
                return 0;
            }

            if ((this.level.isBlockPowered(this))) {
                this.setLit(!(isLit()));
                this.setPowered(true);
                this.getLevel().setBlock(this, this, true, true);
                return 1;
            }

            if(isPowered()) {
                this.setPowered(false);
                this.getLevel().setBlock(this, this, true, true);
                return 1;
            }
        }
        return 0;
    }

    @Override
    public boolean onActivate(@NotNull Item item, Player player) {
        return Waxable.super.onActivate(item, player)
                || Oxidizable.super.onActivate(item, player);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public double getHardness() {
        return 0.3D;
    }

    @Override
    public double getResistance() {
        return 1.5D;
    }

    public boolean isLit() {
        return this.getDamage(LIT_BIT) != 0;
    }

    public void setLit(boolean lit) {
        this.setDamage(LIT_BIT, lit ? 1 : 0);
    }

    public boolean isPowered() {
        return this.getDamage(POWERED_BIT) != 0;
    }

    public void setPowered(boolean lit) {
        this.setDamage(POWERED_BIT, lit ? 1 : 0);
    }

    @Override
    public boolean isWaxed() {
        return false;
    }

    @Override
    public Block getStateWithOxidizationLevel(@NotNull OxidizationLevel oxidizationLevel) {
        BlockCopperBulbBase bulb = (BlockCopperBulbBase) Block.get((getCopperId(isWaxed(), oxidizationLevel)));
        bulb.setDamage(getDamage());
        return bulb;
    }

    @Override
    public boolean setOxidizationLevel(@NotNull OxidizationLevel oxidizationLevel) {
        if (getOxidizationLevel().equals(oxidizationLevel)) {
            return true;
        }

        BlockCopperBulbBase bulb = (BlockCopperBulbBase) Block.get((getCopperId(isWaxed(), oxidizationLevel)));
        bulb.setDamage(getDamage());

        return getValidLevel().setBlock(this, bulb);
    }

    @Override
    public boolean setWaxed(boolean waxed) {
        if (isWaxed() == waxed) {
            return true;
        }

        BlockCopperBulbBase bulb = (BlockCopperBulbBase) Block.get((getCopperId(waxed, getOxidizationLevel())));
        bulb.setDamage(getDamage());

        return getValidLevel().setBlock(this, bulb);
    }

    protected int getCopperId(boolean waxed, @Nullable OxidizationLevel oxidizationLevel) {
        if (oxidizationLevel == null) {
            return getId();
        }
        switch (oxidizationLevel) {
            case UNAFFECTED:
                return waxed? WAXED_COPPER_BULB : COPPER_BULB;
            case EXPOSED:
                return waxed? WAXED_EXPOSED_COPPER_BULB : EXPOSED_COPPER_BULB;
            case WEATHERED:
                return waxed? WAXED_WEATHERED_COPPER_BULB : WEATHERED_COPPER_BULB;
            case OXIDIZED:
                return waxed? WAXED_OXIDIZED_COPPER_BULB : OXIDIZED_COPPER_BULB;
            default:
                return getId();
        }
    }
}
