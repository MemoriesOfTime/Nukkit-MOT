package cn.nukkit.block.properties.enums;

/**
 * Represents the liquid contained in a cauldron.
 * <p>
 * Note: vanilla also has {@code POWDER_SNOW}, but the runtime block palette
 * does not provide legacy id/data entries for it, so it is not supported here.
 */
public enum CauldronLiquid {
    WATER("water"),
    LAVA("lava");

    private final String name;

    CauldronLiquid(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
