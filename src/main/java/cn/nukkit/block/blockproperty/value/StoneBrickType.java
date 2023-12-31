package cn.nukkit.block.blockproperty.value;

import lombok.AllArgsConstructor;

/**
 * @author LoboMetalurgico
 * @since 09/06/2021
 */

@AllArgsConstructor
public enum StoneBrickType {
    DEFAULT("Stone Bricks"),

    MOSSY("Mossy Stone Bricks"),

    CRACKED("Cracked Stone Bricks"),

    CHISELED("Chiseled Stone Bricks"),

    SMOOTH("Smooth Stone Bricks");

    private final String englishName;

    public String getEnglishName() {
        return englishName;
    }
}
