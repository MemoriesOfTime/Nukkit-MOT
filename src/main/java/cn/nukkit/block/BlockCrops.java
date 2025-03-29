package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.block.BlockGrowEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Utils;

import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.custom.properties.IntBlockProperty;
import cn.nukkit.block.properties.BlockPropertiesHelper;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class BlockCrops extends BlockFlowable implements BlockPropertiesHelper {

    protected static final IntBlockProperty GROWTH = new IntBlockProperty("growth", false, 13, 0);

    protected static final BlockProperties PROPERTIES = new BlockProperties(GROWTH);

    public static final int MINIMUM_LIGHT_LEVEL = 9;

    protected BlockCrops(int meta) {
        super(meta);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (block.down().getId() == FARMLAND) {
            this.getLevel().setBlock(block, this, true, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        // Bone meal
        if (item.getId() == Item.DYE && item.getDamage() == 0x0f) {
            if (this.getPropertyValue(GROWTH) < 7) {
                BlockCrops block = (BlockCrops) this.clone();
                block.setPropertyValue(GROWTH, block.getDamage() + Utils.random.nextInt(3) + 2);
                if (block.getPropertyValue(GROWTH) < 7) {
                    block.setPropertyValue(GROWTH, 7);
                }
                BlockGrowEvent ev = new BlockGrowEvent(this, block);
                Server.getInstance().getPluginManager().callEvent(ev);

                if (ev.isCancelled()) {
                    return false;
                }

                this.getLevel().setBlock(this, ev.getNewState(), false, true);

                this.level.addParticle(new BoneMealParticle(this));

                if (player != null && !player.isCreative()) {
                    item.count--;
                }
            }

            return true;
        }

        return false;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (this.down().getId() != FARMLAND) {
                this.getLevel().useBreakOn(this);
                return Level.BLOCK_UPDATE_NORMAL;
            }
        } else if (type == Level.BLOCK_UPDATE_RANDOM) {
            if (Utils.random.nextInt(2) == 1) {
                if (this.getPropertyValue(GROWTH) < 7) {

                    this.setPropertyValue(GROWTH, this.getPropertyValue(GROWTH) + 1);

                    BlockCrops block = (BlockCrops) this.clone();
                    BlockGrowEvent ev = new BlockGrowEvent(this, block);
                    Server.getInstance().getPluginManager().callEvent(ev);

                    if (!ev.isCancelled()) {
                        this.getLevel().setBlock(this, ev.getNewState(), false, true);
                    } else {
                        return Level.BLOCK_UPDATE_RANDOM;
                    }
                }
            } else {
                return Level.BLOCK_UPDATE_RANDOM;
            }
        }

        return 0;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.FOLIAGE_BLOCK_COLOR;
    }
}
