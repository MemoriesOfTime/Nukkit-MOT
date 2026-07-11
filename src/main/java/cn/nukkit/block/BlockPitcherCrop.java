package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.event.block.BlockGrowEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDye;
import cn.nukkit.level.Level;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.utils.Utils;

/**
 * Pitcher pod crop with lower and upper states for the late growth stages.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockPitcherCrop extends BlockCrops implements BlockPropertiesHelper {

    private static final int MAX_GROWTH = 4;
    private static final int FIRST_DOUBLE_HEIGHT_GROWTH = 3;
    private static final BlockProperties PROPERTIES = new BlockProperties(GROWTH, VanillaProperties.UPPER_BLOCK);

    public BlockPitcherCrop() {
        this(0);
    }

    public BlockPitcherCrop(int meta) {
        super(meta);
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @Override
    public int getId() {
        return PITCHER_CROP;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:pitcher_crop";
    }

    @Override
    public String getName() {
        return "Pitcher Crop";
    }

    public boolean isUpper() {
        return this.getPropertyValue(VanillaProperties.UPPER_BLOCK);
    }

    private int getGrowthStage() {
        return Math.min(this.getPropertyValue(GROWTH), MAX_GROWTH);
    }

    private void setGrowthStage(int growth) {
        this.setPropertyValue(GROWTH, Math.max(0, Math.min(growth, MAX_GROWTH)));
    }

    @Override
    public Item toItem() {
        return Item.fromString(Item.PITCHER_POD);
    }

    @Override
    public Item[] getDrops(Item item) {
        if (this.isUpper()) {
            return Item.EMPTY_ARRAY;
        }
        if (this.getGrowthStage() >= MAX_GROWTH) {
            return new Item[]{new cn.nukkit.item.ItemBlock(Block.get(PITCHER_PLANT), 0, 1)};
        }
        return new Item[]{Item.fromString(Item.PITCHER_POD)};
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.getId() != Item.DYE || item.getDamage() != ItemDye.BONE_MEAL) {
            return false;
        }

        BlockPitcherCrop lower = this.getLowerCrop();
        if (lower == null || lower.getGrowthStage() >= MAX_GROWTH) {
            return false;
        }

        if (!lower.growToStage(lower.getGrowthStage() + 1)) {
            return false;
        }

        this.level.addParticle(new BoneMealParticle(lower));
        if (player != null && !player.isCreative()) {
            item.count--;
        }
        return true;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (this.isUpper()) {
                Block below = this.down();
                if (!(below instanceof BlockPitcherCrop crop) || crop.isUpper()) {
                    this.getLevel().setBlock(this, Block.get(AIR), false, true);
                    return Level.BLOCK_UPDATE_NORMAL;
                }
            } else if (this.down().getId() != FARMLAND || this.requiresUpperBlock() && !this.hasMatchingUpperBlock()) {
                this.getLevel().useBreakOn(this);
                return Level.BLOCK_UPDATE_NORMAL;
            }
        } else if (type == Level.BLOCK_UPDATE_RANDOM) {
            if (this.isUpper()) {
                return 0;
            }
            BlockPitcherCrop lower = this.getLowerCrop();
            if (lower != null && this.getLevel().getFullLight(this) >= MINIMUM_LIGHT_LEVEL && Utils.random.nextInt(5) == 0) {
                if (!lower.growToStage(lower.getGrowthStage() + 1)) {
                    return Level.BLOCK_UPDATE_RANDOM;
                }
            } else {
                return Level.BLOCK_UPDATE_RANDOM;
            }
        }
        return 0;
    }

    @Override
    public boolean onBreak(Item item) {
        if (this.isUpper()) {
            Block below = this.down();
            if (below instanceof BlockPitcherCrop crop && !crop.isUpper()) {
                this.getLevel().useBreakOn(crop, item, null, true);
            } else {
                this.getLevel().setBlock(this, Block.get(AIR), true, true);
            }
            return true;
        }

        Block above = this.up();
        if (above instanceof BlockPitcherCrop crop && crop.isUpper()) {
            this.getLevel().setBlock(above, Block.get(AIR), true, true);
        }
        this.getLevel().setBlock(this, Block.get(AIR), true, true);
        return true;
    }

    private BlockPitcherCrop getLowerCrop() {
        if (!this.isUpper()) {
            return this;
        }
        Block below = this.down();
        if (below instanceof BlockPitcherCrop crop && !crop.isUpper()) {
            return crop;
        }
        return null;
    }

    private boolean growToStage(int newStage) {
        if (newStage > MAX_GROWTH) {
            return false;
        }
        if (newStage >= FIRST_DOUBLE_HEIGHT_GROWTH && !this.up().isAir() && !this.hasMatchingUpperBlock()) {
            return false;
        }

        BlockPitcherCrop lower = (BlockPitcherCrop) this.clone();
        lower.setPropertyValue(VanillaProperties.UPPER_BLOCK, false);
        lower.setGrowthStage(newStage);

        BlockGrowEvent event = new BlockGrowEvent(this, lower);
        Server.getInstance().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        this.getLevel().setBlock(this, event.getNewState(), false, true);
        if (newStage >= FIRST_DOUBLE_HEIGHT_GROWTH) {
            this.updateUpperBlock(newStage);
        } else if (this.hasMatchingUpperBlock()) {
            this.getLevel().setBlock(this.up(), Block.get(AIR), true, true);
        }
        return true;
    }

    private boolean requiresUpperBlock() {
        return this.getGrowthStage() >= FIRST_DOUBLE_HEIGHT_GROWTH;
    }

    private boolean hasMatchingUpperBlock() {
        Block above = this.up();
        return above instanceof BlockPitcherCrop crop && crop.isUpper();
    }

    private void updateUpperBlock(int growth) {
        Block above = this.up();
        if (!above.isAir() && !(above instanceof BlockPitcherCrop crop && crop.isUpper())) {
            return;
        }

        BlockPitcherCrop upper = new BlockPitcherCrop();
        upper.position(above);
        upper.setLevel(this.getLevel());
        upper.setPropertyValue(VanillaProperties.UPPER_BLOCK, true);
        upper.setGrowthStage(growth);
        this.getLevel().setBlock(above, upper, true, true);
    }
}
