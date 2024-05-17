package cn.nukkit.item.trim;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author glorydark
 * @date {2023/8/9} {17:26}
 */
public enum ItemTrimMaterialType {

    MATERIAL_QUARTZ("quartz"),
    MATERIAL_IRON("iron"),
    MATERIAL_NETHERITE("netherite"),
    MATERIAL_REDSTONE("redstone"),
    MATERIAL_COPPER("copper"),
    MATERIAL_GOLD("gold"),
    MATERIAL_EMERALD("emerald"),
    MATERIAL_LAPIS("lapis"),
    MATERIAL_AMETHYST("amethyst");

    private final String materialName;

    ItemTrimMaterialType(@NotNull String input) {
        this.materialName = input;
    }

    @Nullable
    public static ItemTrimMaterialType fromMaterialName(@NotNull String materialName) {
        for (ItemTrimMaterialType value : ItemTrimMaterialType.values()) {
            if (value.getMaterialName().equals(materialName)) {
                return value;
            }
        }
        return null;
    }

    @NotNull
    public String getMaterialName() {
        return materialName;
    }
}
