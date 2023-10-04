package cn.nukkit.block.blockproperty.value;

import cn.nukkit.block.blockproperty.ArrayBlockProperty;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum StoneSlab3Type {
    END_STONE_BRICK(BlockColor.SAND_BLOCK_COLOR),
    SMOOTH_RED_SANDSTONE(BlockColor.ORANGE_BLOCK_COLOR),
    POLISHED_ANDESITE(BlockColor.STONE_BLOCK_COLOR),
    ANDESITE(BlockColor.STONE_BLOCK_COLOR),
    DIORITE(BlockColor.QUARTZ_BLOCK_COLOR),
    POLISHED_DIORITE(BlockColor.QUARTZ_BLOCK_COLOR),
    GRANITE(BlockColor.DIRT_BLOCK_COLOR),
    POLISHED_GRANITE(BlockColor.DIRT_BLOCK_COLOR);
    public static final ArrayBlockProperty<StoneSlab3Type> PROPERTY = new ArrayBlockProperty<>("stone_slab_type_3", true, values());
    private final BlockColor color;

    private final String englishName;

    StoneSlab3Type(BlockColor color) {
        this.color = color;
        englishName = Arrays.stream(name().split("_")).map(name-> name.substring(0, 1) + name.substring(1).toLowerCase()).collect(Collectors.joining(" "));
    }

    @NotNull
    public BlockColor getColor() {
        return this.color;
    }

    @NotNull
    public String getEnglishName() {
        return this.englishName;
    }
}
