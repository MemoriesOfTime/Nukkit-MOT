package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.blockproperty.ArrayBlockProperty;
import cn.nukkit.block.blockproperty.BlockProperties;
import cn.nukkit.block.blockproperty.BlockProperty;
import cn.nukkit.block.blockproperty.value.SmallFlowerType;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Utils;
import cn.nukkit.utils.exception.InvalidBlockPropertyValueException;
import org.jetbrains.annotations.NotNull;

/**
 * Created on 2015/11/23 by xtypr.
 * Package cn.nukkit.block in project Nukkit .
 */
public class BlockFlower extends BlockFlowable {

    public static final BlockProperty<SmallFlowerType> RED_FLOWER_TYPE = new ArrayBlockProperty<>("flower_type", true, new SmallFlowerType[]{
            SmallFlowerType.POPPY,
            SmallFlowerType.ORCHID,
            SmallFlowerType.ALLIUM,
            SmallFlowerType.HOUSTONIA,
            SmallFlowerType.TULIP_RED,
            SmallFlowerType.TULIP_ORANGE,
            SmallFlowerType.TULIP_WHITE,
            SmallFlowerType.TULIP_PINK,
            SmallFlowerType.OXEYE,
            SmallFlowerType.CORNFLOWER,
            SmallFlowerType.LILY_OF_THE_VALLEY
    });

    public static final BlockProperties PROPERTIES = new BlockProperties(RED_FLOWER_TYPE);

    public static final int TYPE_POPPY = 0;
    public static final int TYPE_BLUE_ORCHID = 1;
    public static final int TYPE_ALLIUM = 2;
    public static final int TYPE_AZURE_BLUET = 3;
    public static final int TYPE_RED_TULIP = 4;
    public static final int TYPE_ORANGE_TULIP = 5;
    public static final int TYPE_WHITE_TULIP = 6;
    public static final int TYPE_PINK_TULIP = 7;
    public static final int TYPE_OXEYE_DAISY = 8;
    public static final int CORNFLOWER = 9;
    public static final int LILY_OF_THE_VALLEY = 10;

    //兼容nkx插件
    public static final int TYPE_CORNFLOWER = CORNFLOWER;
    public static final int TYPE_LILY_OF_THE_VALLEY = LILY_OF_THE_VALLEY;

    private static final String[] names = new String[]{
            "Poppy",
            "Blue Orchid",
            "Allium",
            "Azure Bluet",
            "Red Tulip",
            "Orange Tulip",
            "White Tulip",
            "Pink Tulip",
            "Oxeye Daisy",
            "Cornflower",
            "Lily of the Valley",
            "Unknown",
            "Unknown",
            "Unknown",
            "Unknown",
            "Unknown"
    };

    public BlockFlower() {
        this(0);
    }

    public BlockFlower(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return FLOWER;
    }

    @NotNull
    @Override
    public BlockProperties getProperties() {
        return PROPERTIES;
    }

    @Override
    public String getName() {
        return names[this.getDamage() & 0x0f];
    }

    public SmallFlowerType getFlowerType() {
        return getPropertyValue(RED_FLOWER_TYPE);
    }

    protected void setOnSingleFlowerType(SmallFlowerType acceptsOnly, SmallFlowerType attemptedToSet) {
        if (attemptedToSet == null || attemptedToSet == acceptsOnly) {
            return;
        }
        String persistenceName = getPersistenceName();
        throw new InvalidBlockPropertyValueException(
                new ArrayBlockProperty<>(persistenceName +"_type", false, new SmallFlowerType[]{acceptsOnly}),
                acceptsOnly,
                attemptedToSet,
                persistenceName+" only accepts "+acceptsOnly.name().toLowerCase()
        );
    }

    public void setFlowerType(SmallFlowerType flowerType) {
        setPropertyValue(RED_FLOWER_TYPE, flowerType);
    }

    public static boolean isSupportValid(Block block) {
        switch (block.getId()) {
            case GRASS:
            case DIRT:
            case FARMLAND:
            case PODZOL:
            case MYCELIUM:
                //TODO
                //case DIRT_WITH_ROOTS:
                //case MOSS_BLOCK:
                return true;
            default:
                return false;
        }
    }

    public boolean canPlantOn(Block block) {
        return isSupportValid(block);
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        Block down = this.down();
        if (this.canPlantOn(down)) {
            this.getLevel().setBlock(block, this, true);

            return true;
        }
        return false;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (this.down().isTransparent()) {
                this.getLevel().useBreakOn(this);

                return Level.BLOCK_UPDATE_NORMAL;
            }
        }

        return 0;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.FOLIAGE_BLOCK_COLOR;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.getId() == Item.DYE && item.getDamage() == 0x0f) { // Bone meal
            if (player != null && !player.isCreative()) {
                item.count--;
            }

            this.level.addParticle(new BoneMealParticle(this));

            for (int i = 0; i < 8; i++) {
                Vector3 vec = this.add(
                        Utils.random.nextInt(-3, 4),
                        Utils.random.nextInt(-1, 2),
                        Utils.random.nextInt(-3, 4));

                if (level.getBlock(vec).getId() == AIR && level.getBlock(vec.down()).getId() == GRASS && vec.getY() >= 0 && vec.getY() < 256) {
                    if (Utils.random.nextInt(10) == 0) {
                        this.level.setBlock(vec, this.getUncommonFlower(), true);
                    } else {
                        this.level.setBlock(vec, get(this.getId()), true);
                    }
                }
            }

            return true;
        }

        return false;
    }

    protected Block getUncommonFlower() {
        return get(DANDELION);
    }
}
