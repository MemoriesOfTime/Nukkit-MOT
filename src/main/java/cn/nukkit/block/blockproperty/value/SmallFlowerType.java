
package cn.nukkit.block.blockproperty.value;

import cn.nukkit.block.BlockID;
import cn.nukkit.utils.DyeColor;
import lombok.RequiredArgsConstructor;

/**
 * @author joserobjr
 * @since 2020-10-10
 */
@RequiredArgsConstructor
public enum SmallFlowerType {
    POPPY("Poppy", DyeColor.RED, BlockID.RED_FLOWER),

    ORCHID("Blue Orchid", DyeColor.LIGHT_BLUE, BlockID.RED_FLOWER),

    ALLIUM("Allium", DyeColor.MAGENTA, BlockID.RED_FLOWER),

    HOUSTONIA("Azure Bluet", DyeColor.LIGHT_GRAY, BlockID.RED_FLOWER),

    TULIP_RED("Red Tulip", DyeColor.RED, BlockID.RED_FLOWER),

    TULIP_ORANGE("Orange Tulip", DyeColor.ORANGE, BlockID.RED_FLOWER),

    TULIP_WHITE("White Tulip", DyeColor.LIGHT_GRAY, BlockID.RED_FLOWER),

    TULIP_PINK("Pink Tulip", DyeColor.PINK, BlockID.RED_FLOWER),

    OXEYE("Oxeye Daisy", DyeColor.LIGHT_GRAY, BlockID.RED_FLOWER),

    CORNFLOWER("Cornflower", DyeColor.BLUE, BlockID.RED_FLOWER),

    LILY_OF_THE_VALLEY("Lily of the Valley", DyeColor.WHITE, BlockID.RED_FLOWER),

    /**
     * 此种类的花没有networkTypeName
     */
    DANDELION("Dandelion", DyeColor.YELLOW, BlockID.DANDELION);

    /**
     * 此种类的花没有networkTypeName
     */
    //WITHER_ROSE("Wither Rose", DyeColor.BLACK, BlockID.WITHER_ROSE);

    private final String englishName;
    private final DyeColor dyeColor;
    private final int blockId;

    public DyeColor getDyeColor() {
        return dyeColor;
    }

    public String getEnglishName() {
        return englishName;
    }

    public int getBlockId() {
        return blockId;
    }

    /*public BlockFlower getBlock() {
        BlockFlower flower = (BlockFlower) Block.get(getBlockId());
        flower.setFlowerType(this);
        return flower;
    }*/
}
