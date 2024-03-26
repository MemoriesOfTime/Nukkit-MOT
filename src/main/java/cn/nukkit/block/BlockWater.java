package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.block.WaterFrostEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class BlockWater extends BlockLiquid {

    /**
     * Used to cache biome check for freezing
     * 1 = can't freeze, 2 = can freeze
     */
    private byte freezing;

    public BlockWater() {
        this(0);
    }

    public BlockWater(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WATER;
    }

    @Override
    public String getName() {
        return "Water";
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        boolean ret = this.getLevel().setBlock(this, this, true, false);
        this.getLevel().scheduleUpdate(this, this.tickRate());

        return ret;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WATER_BLOCK_COLOR;
    }

    @Override
    public BlockLiquid getBlock(int meta) {
        return (BlockLiquid) Block.get(BlockID.WATER, meta);
    }

    @Override
    public void onEntityCollide(Entity entity) {
        super.onEntityCollide(entity);

        if (entity.fireTicks > 0) {
            entity.extinguish();
        }
    }

    @Override
    public int tickRate() {
        return 5;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_RANDOM && this.getDamage() == 0) {
            if (freezing < 1) {
                freezing = Utils.freezingBiomes.contains(level.getBiomeId((int) this.x, (int) this.z)) ? (byte) 2 : (byte) 1;
            }
            if (freezing == 2) {
                if (ThreadLocalRandom.current().nextInt(10) == 0 && level.getBlockLightAt((int) this.x, (int) this.y, (int) this.z) < 12 && level.canBlockSeeSky(this)) {
                    WaterFrostEvent ev = new WaterFrostEvent(this);
                    level.getServer().getPluginManager().callEvent(ev);
                    if (!ev.isCancelled()) {
                        level.setBlock(this, Block.get(Block.ICE), true, true);
                    }
                }
            }
            return Level.BLOCK_UPDATE_RANDOM;
        }
        return super.onUpdate(type);
    }

    @Override
    public boolean usesWaterLogging() {
        return true;
    }

    @Override
    public boolean isWater() {
        return true;
    }

    @Override
    public boolean isWaterSource() {
        return isLiquidSource();
    }
}
