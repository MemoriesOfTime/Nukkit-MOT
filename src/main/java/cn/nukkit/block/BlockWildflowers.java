package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.custom.properties.IntBlockProperty;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockWildflowers extends BlockFlowable implements Faceable, BlockPropertiesHelper {

    private static final IntBlockProperty GROWTH = new IntBlockProperty("growth", false, 7, 0);

    private static final BlockProperties PROPERTIES = new BlockProperties(GROWTH, VanillaProperties.CARDINAL_DIRECTION);

    public BlockWildflowers() {
        this(0);
    }

    public BlockWildflowers(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Wildflowers";
    }

    @Override
    public int getId() {
        return BlockID.WILDFLOWERS;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:wildflowers";
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @Override
    public BlockFace getBlockFace() {
        return this.getPropertyValue(VanillaProperties.CARDINAL_DIRECTION);
    }

    @Override
    public void setBlockFace(BlockFace face) {
        this.setPropertyValue(VanillaProperties.CARDINAL_DIRECTION, face);
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @Nullable Player player) {
        if (!isSupportValid(block.down())) {
            return false;
        }
        if (player != null) {
            setBlockFace(player.getHorizontalFacing().getOpposite());
        }
        return this.getLevel().setBlock(this, this);
    }

    private static boolean isSupportValid(Block block) {
        switch (block.getId()) {
            case GRASS:
            case DIRT:
            case FARMLAND:
            case PODZOL:
            case DIRT_WITH_ROOTS:
            case MOSS_BLOCK:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(@NotNull Item item, @Nullable Player player) {
        if (item.getId() == Item.DYE && item.getDamage() == 0x0f) {
            if (this.getPropertyValue(GROWTH) < 3) {
                setPropertyValue(GROWTH, this.getPropertyValue(GROWTH) + 1);
                getLevel().setBlock(this, this);
            } else {
                getLevel().dropItem(this, this.toItem());
            }
            this.level.addParticle(new BoneMealParticle(this));
            item.count--;
            return true;
        }

        if (item.getId() == this.getItemId() && this.getPropertyValue(GROWTH) < 3) {
            setPropertyValue(GROWTH, this.getPropertyValue(GROWTH) + 1);
            getLevel().setBlock(this, this);
            item.count--;
            return true;
        }

        return false;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(this, 0, this.getPropertyValue(GROWTH) + 1);
    }
}