package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.block.BlockGrowEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDye;
import cn.nukkit.level.Level;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.utils.Utils;

/**
 * Two-stage torchflower crop that matures into a torchflower block.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockTorchflowerCrop extends BlockCrops {

    private static final int MAX_GROWTH = 1;

    public BlockTorchflowerCrop() {
        this(0);
    }

    public BlockTorchflowerCrop(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return TORCHFLOWER_CROP;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:torchflower_crop";
    }

    @Override
    public String getName() {
        return "Torchflower Crop";
    }

    @Override
    public Item toItem() {
        return Item.fromString(Item.TORCHFLOWER_SEEDS);
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{Item.fromString(Item.TORCHFLOWER_SEEDS)};
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.getId() != Item.DYE || item.getDamage() != ItemDye.BONE_MEAL) {
            return false;
        }

        if (!this.growOneStage()) {
            return false;
        }

        this.level.addParticle(new BoneMealParticle(this));
        if (player != null && !player.isCreative()) {
            item.count--;
        }
        return true;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (this.down().getId() != FARMLAND) {
                this.getLevel().useBreakOn(this);
                return Level.BLOCK_UPDATE_NORMAL;
            }
        } else if (type == Level.BLOCK_UPDATE_RANDOM) {
            if (Utils.random.nextInt(2) == 1 && this.getLevel().getFullLight(this) >= MINIMUM_LIGHT_LEVEL) {
                if (!this.growOneStage()) {
                    return Level.BLOCK_UPDATE_RANDOM;
                }
            } else {
                return Level.BLOCK_UPDATE_RANDOM;
            }
        }
        return 0;
    }

    private boolean growOneStage() {
        int growth = this.getPropertyValue(GROWTH);
        Block newState;
        if (growth >= MAX_GROWTH) {
            newState = Block.get(TORCHFLOWER);
        } else {
            BlockTorchflowerCrop crop = (BlockTorchflowerCrop) this.clone();
            crop.setPropertyValue(GROWTH, Math.min(growth + 1, MAX_GROWTH));
            newState = crop;
        }

        BlockGrowEvent event = new BlockGrowEvent(this, newState);
        Server.getInstance().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        this.getLevel().setBlock(this, event.getNewState(), false, true);
        return true;
    }
}
