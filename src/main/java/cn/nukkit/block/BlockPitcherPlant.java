package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import org.jetbrains.annotations.NotNull;

/**
 * Mature two-block pitcher plant.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockPitcherPlant extends BlockFlowable implements BlockPropertiesHelper {

    private static final BlockProperties PROPERTIES = new BlockProperties(VanillaProperties.UPPER_BLOCK);

    public BlockPitcherPlant() {
        this(0);
    }

    public BlockPitcherPlant(int meta) {
        super(meta);
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @Override
    public int getId() {
        return PITCHER_PLANT;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:pitcher_plant";
    }

    @Override
    public String getName() {
        return "Pitcher Plant";
    }

    public boolean isUpper() {
        return this.getPropertyValue(VanillaProperties.UPPER_BLOCK);
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        Block up = block.up();
        if (!up.isAir() || !isSupportValid(block.down())) {
            return false;
        }

        BlockPitcherPlant upper = new BlockPitcherPlant();
        upper.setPropertyValue(VanillaProperties.UPPER_BLOCK, true);
        this.getLevel().setBlock(up, upper, true, false);
        this.setPropertyValue(VanillaProperties.UPPER_BLOCK, false);
        this.getLevel().setBlock(block, this, true, true);
        return true;
    }

    @Override
    public int onUpdate(int type) {
        if (type != Level.BLOCK_UPDATE_NORMAL) {
            return 0;
        }

        if (this.isUpper()) {
            Block below = this.down();
            if (!(below instanceof BlockPitcherPlant plant) || plant.isUpper()) {
                this.getLevel().setBlock(this, Block.get(AIR), false, true);
                return Level.BLOCK_UPDATE_NORMAL;
            }
        } else if (!isSupportValid(this.down()) || !(this.up() instanceof BlockPitcherPlant plant) || !plant.isUpper()) {
            this.getLevel().useBreakOn(this);
            return Level.BLOCK_UPDATE_NORMAL;
        }
        return 0;
    }

    @Override
    public boolean onBreak(Item item) {
        if (this.isUpper()) {
            Block below = this.down();
            if (below instanceof BlockPitcherPlant plant && !plant.isUpper()) {
                this.getLevel().useBreakOn(plant, item, null, true);
            } else {
                this.getLevel().setBlock(this, Block.get(AIR), true, true);
            }
            return true;
        }

        Block above = this.up();
        if (above instanceof BlockPitcherPlant plant && plant.isUpper()) {
            this.getLevel().setBlock(above, Block.get(AIR), true, true);
        }
        this.getLevel().setBlock(this, Block.get(AIR), true, true);
        return true;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (this.isUpper()) {
            return Item.EMPTY_ARRAY;
        }
        return new Item[]{this.toItem()};
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(PITCHER_PLANT), 0, 1);
    }

    private static boolean isSupportValid(Block block) {
        return switch (block.getId()) {
            case GRASS, DIRT, FARMLAND, PODZOL, MYCELIUM, DIRT_WITH_ROOTS, MOSS_BLOCK -> true;
            default -> false;
        };
    }
}
