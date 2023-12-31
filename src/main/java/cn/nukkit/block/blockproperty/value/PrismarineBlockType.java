package cn.nukkit.block.blockproperty.value;

import cn.nukkit.utils.BlockColor;
import lombok.AllArgsConstructor;

/**
 * @author LoboMetalurgico
 * @since 09/06/2021
 */

@AllArgsConstructor
public enum PrismarineBlockType {
    DEFAULT("Prismarine", BlockColor.CYAN_BLOCK_COLOR),

    DARK("Dark Prismarine", BlockColor.DIAMOND_BLOCK_COLOR),

    BRICKS("Prismarine Bricks", BlockColor.DIAMOND_BLOCK_COLOR);

    private final String englishName;
    private final BlockColor color;

    public String getEnglishName() {
        return englishName;
    }

    public BlockColor getColor() {
        return color;
    }
}
