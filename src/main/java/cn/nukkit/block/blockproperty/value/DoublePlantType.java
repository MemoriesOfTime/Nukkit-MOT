package cn.nukkit.block.blockproperty.value;

import lombok.RequiredArgsConstructor;

import static io.sentry.util.StringUtils.capitalize;

/**
 * @author joserobjr
 * @since 2021-05-22
 */
@RequiredArgsConstructor
public enum DoublePlantType {
    SUNFLOWER,

    SYRINGA("Lilac", false),

    GRASS("Double Tallgrass", true),

    FERN("Large Fern", true),

    ROSE("Rose Bush", false),

    PAEONIA("Peony", false)
    ;
    private final String englishName;
    private final boolean replaceable;

    @SuppressWarnings("UnstableApiUsage")
    DoublePlantType() {
        englishName = capitalize(name());
        replaceable = false;
    }

    public String getEnglishName() {
        return englishName;
    }

    public boolean isReplaceable() {
        return replaceable;
    }
}
