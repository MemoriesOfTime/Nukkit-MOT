package cn.nukkit.block.blockproperty.value;

import lombok.AllArgsConstructor;

/**
 * @author LoboMetalurgico
 * @since 09/06/2021
 */

@AllArgsConstructor
public enum SandStoneType {
    DEFAULT("Sandstone"),

    HEIROGLYPHS("Chiseled Sandstone"),

    CUT("Cut Sandstone"),

    SMOOTH("Smooth Sandstone");

    private final String englishName;

    public String getEnglishName() {
        return englishName;
    }
}
