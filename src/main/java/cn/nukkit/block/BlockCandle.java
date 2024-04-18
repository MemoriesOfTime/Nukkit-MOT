package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemID;
import cn.nukkit.math.BlockFace;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * @author Gabriel8579
 * @since 2021-07-14
 */

public class BlockCandle extends BlockFlowable {

    public static final int CANDLES_BIT = 0x03;
    public static final int LIT_BIT = 0x04;

    public BlockCandle() {
        super(0);
    }

    public BlockCandle(int meta) {
        super(meta);
    }

    protected Block toCakeForm() {
        return new BlockCandleCake();
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @Nullable Player player) {
        if (target.getId() == BlockID.CAKE_BLOCK && target.getDamage() == 0) {//必须是完整的蛋糕才能插蜡烛
            target.getLevel().addLevelSoundEvent(target, LevelSoundEventPacket.SOUND_CAKE_ADD_CANDLE);
            target.getLevel().setBlock(target, toCakeForm(), true, true);
            return true;
        }
        if (target.up().getId() == this.getId()) {
            target = target.up();
        }
        if (target.getId() == getId()) {
            if (target instanceof BlockMeta candle) {
                if (candle.getDamage(CANDLES_BIT) < 3) {
                    candle.setDamage(CANDLES_BIT, candle.getDamage(CANDLES_BIT) + 1);
                    this.getLevel().setBlock(target, target, true, true);
                    return true;
                }
            }
            return false;
        } else if (target instanceof BlockCandle) {
            return false;
        }

        this.setCandles(0);
        this.getLevel().setBlock(this, this, true, true);

        return true;
    }

    @Override
    public boolean onActivate(@NotNull Item item, Player player) {
        if (item.getId() != ItemID.FLINT_AND_STEEL && !item.isNull()) {
            return false;
        }
        if (this.isLit() && item.getId() != ItemID.FLINT_AND_STEEL) {
            this.setLit(false);
            this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_EXTINGUISH_CANDLE);
            this.getLevel().setBlock(this, this, true, true);
            return true;
        } else if (!this.isLit() && item.getId() == ItemID.FLINT_AND_STEEL) {
            this.setLit(true);
            this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_IGNITE);
            this.getLevel().setBlock(this, this, true, true);
            return true;
        }

        return false;
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{
                new ItemBlock(this, 0, this.getCandles() + 1)
        };
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public String getName() {
        return "Candle";
    }

    @Override
    public int getId() {
        return BlockID.CANDLE;
    }

    public boolean isLit() {
        return this.getDamage(LIT_BIT) != 0;
    }

    public void setLit(boolean lit) {
        this.setDamage(LIT_BIT, lit ? 1 : 0);
    }

    public int getCandles() {
        return this.getDamage(CANDLES_BIT);
    }

    public void setCandles(int candles) {
        this.setDamage(CANDLES_BIT, candles);
    }

    @Override
    public int getLightLevel() {
        return this.isLit() ? this.getCandles() * 3 : 0;
    }

    @Override
    public double getHardness() {
        return 0.1;
    }

    @Override
    public double getResistance() {
        return 0.1;
    }

}