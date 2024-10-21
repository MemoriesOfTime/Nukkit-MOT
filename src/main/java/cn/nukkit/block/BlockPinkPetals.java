package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.properties.BlockProperties;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.EnumBlockProperty;
import cn.nukkit.block.properties.IntBlockProperty;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class BlockPinkPetals extends BlockFlowable implements BlockPropertiesHelper {

    public enum MINECRAFT_CARDINAL_DIRECTION {
        SOUTH,
        WEST,
        NORTH,
        EAST
    }

    @Deprecated
    private static final IntBlockProperty DIRECTION = new IntBlockProperty("direction", false, 3, 0);

    private static final IntBlockProperty GROWTH = new IntBlockProperty("growth", false, 7, 0);

    private static final EnumBlockProperty<MINECRAFT_CARDINAL_DIRECTION> CARDINAL_DIRECTION = new EnumBlockProperty<>("minecraft:cardinal_direction", false, MINECRAFT_CARDINAL_DIRECTION.values());

    private static final BlockProperties PROPERTIES = new BlockProperties(GROWTH, CARDINAL_DIRECTION);


    public MINECRAFT_CARDINAL_DIRECTION cardinalDirection = MINECRAFT_CARDINAL_DIRECTION.values()[0];

    public BlockPinkPetals() {
            this(0);
        }

    public BlockPinkPetals(int meta) {
            super(meta);
        }

    @Override
    public String getName() {
        return "Pink Petals";
    }

    @Override
    public int getId() {
        return BlockID.PINK_PETALS;
    }

    public void setCardinalDirection(int cardinalDirectionIndex) {
        // 检查索引是否在枚举的范围内
        if (cardinalDirectionIndex >= 0 && cardinalDirectionIndex < MINECRAFT_CARDINAL_DIRECTION.values().length) {
            this.cardinalDirection = MINECRAFT_CARDINAL_DIRECTION.values()[cardinalDirectionIndex];
        } else {
            throw new IllegalArgumentException("Invalid cardinal direction index: " + cardinalDirectionIndex);
        }
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @Nullable Player player) {
        if (!isSupportValid(block.down()))
            return false;
        if (player != null)
            setCardinalDirection(player.getHorizontalFacing().getOpposite().getIndex());
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
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @Override
    public boolean onActivate(@NotNull Item item, @Nullable Player player) {
//        if (item.isFertilizer()) {
//            if (getPropertyValue(GROWTH) < 3) {
//                setPropertyValue(GROWTH, getPropertyValue(GROWTH) + 1);
//                getLevel().setBlock(this, this);
//            } else {
//                getLevel().dropItem(this, Block.get(Item.PINK_PETALS).toItem());
//            }
//            this.level.addParticle(new BoneMealParticle(this));
//            item.count--;
//            return true;
//        }
        if (item.getId() == Item.fromString("minecraft:pink_petals").getId() && getPropertyValue(GROWTH) < 3) {
            setPropertyValue(GROWTH, getPropertyValue(GROWTH) + 1);
            getLevel().setBlock(this, this);
            item.count--;
            return true;
        }
        return false;
    }
}