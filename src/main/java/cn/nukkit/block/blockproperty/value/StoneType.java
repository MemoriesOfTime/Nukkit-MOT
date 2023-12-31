
package cn.nukkit.block.blockproperty.value;

import cn.nukkit.utils.BlockColor;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;

import static cn.nukkit.utils.BlockColor.*;

/**
 * @author joserobjr
 * @since 2020-10-04
 */
@AllArgsConstructor
public enum StoneType {
    STONE("Stone", STONE_BLOCK_COLOR),

    GRANITE("Granite", DIRT_BLOCK_COLOR),
    
    GRANITE_SMOOTH("Polished Granite", DIRT_BLOCK_COLOR),

    DIORITE("Diorite", QUARTZ_BLOCK_COLOR),
    
    DIORITE_SMOOTH("Polished Diorite", QUARTZ_BLOCK_COLOR),

    ANDESITE("Andesite", STONE_BLOCK_COLOR),

    ANDESITE_SMOOTH("Polished Andesite", STONE_BLOCK_COLOR);
    
    private final String englishName;
    private final BlockColor color;

    @NotNull
    public String getEnglishName() {
        return englishName;
    }

    @NotNull
    public BlockColor getColor() {
        return color;
    }
}
