package cn.nukkit.block.blockproperty.value;

import cn.nukkit.block.blockproperty.ArrayBlockProperty;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum StoneSlab4Type {
    MOSSY_STONE_BRICK(BlockColor.STONE_BLOCK_COLOR),
    SMOOTH_QUARTZ(BlockColor.QUARTZ_BLOCK_COLOR),
    STONE(BlockColor.STONE_BLOCK_COLOR),
    CUT_SANDSTONE(BlockColor.SAND_BLOCK_COLOR),
    CUT_RED_SANDSTONE(BlockColor.ORANGE_BLOCK_COLOR);
    public static final ArrayBlockProperty<StoneSlab4Type> PROPERTY = new ArrayBlockProperty<>("stone_slab_type_4", true, values());
    private final BlockColor color;

    private final String englishName;

    StoneSlab4Type(BlockColor color) {
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
