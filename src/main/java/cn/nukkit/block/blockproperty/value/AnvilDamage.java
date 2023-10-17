package cn.nukkit.block.blockproperty.value;

import lombok.RequiredArgsConstructor;

/**
 * @author joserobjr
 * @since 2020-10-10
 */
@RequiredArgsConstructor
public enum AnvilDamage {
    UNDAMAGED("Anvil"),

    SLIGHTLY_DAMAGED("Slightly Damaged Anvil"),

    VERY_DAMAGED("Very Damaged Anvil"),

    BROKEN("Broken Anvil");
    private final String englishName;

    public String getEnglishName() {
        return englishName;
    }
}
