package cn.nukkit.level;

import cn.nukkit.level.generator.Generator;
import lombok.extern.log4j.Log4j2;

@Log4j2
public enum DimensionEnum {

    OVERWORLD("minecraft:overworld", new DimensionData(Level.DIMENSION_OVERWORLD, -64, 319), Generator.TYPE_INFINITE, -64, 319),
    NETHER("minecraft:nether", new DimensionData(Level.DIMENSION_NETHER, 0, 127), Generator.TYPE_NETHER, 0, 127),
    END("minecraft:the_end", new DimensionData(Level.DIMENSION_THE_END, 0, 255), Generator.TYPE_THE_END, 0, 255);

    private final String identifier;
    private DimensionData dimensionData;
    private final int generatorType;
    private final int vanillaMinHeight;
    private final int vanillaMaxHeight;

    DimensionEnum(String identifier, DimensionData dimensionData, int generatorType, int vanillaMinHeight, int vanillaMaxHeight) {
        this.identifier = identifier;
        this.dimensionData = dimensionData;
        this.generatorType = generatorType;
        this.vanillaMinHeight = vanillaMinHeight;
        this.vanillaMaxHeight = vanillaMaxHeight;
    }

    public String getIdentifier() {
        return this.identifier;
    }

    public DimensionData getDimensionData() {
        return this.dimensionData;
    }

    public int getGeneratorType() {
        return this.generatorType;
    }

    public boolean isVanillaBounds() {
        return this.dimensionData.getMinHeight() == this.vanillaMinHeight
                && this.dimensionData.getMaxHeight() == this.vanillaMaxHeight;
    }

    public static DimensionData getDataFromId(int dimension) {
        for (DimensionEnum value : values()) {
            if (value.getDimensionData().getDimensionId() == dimension) {
                return value.getDimensionData();
            }
        }
        return null;
    }

    /**
     * Sets the custom height range for this dimension.
     * <p>
     * Plugins should call this during {@code onLoad} to ensure it takes effect before worlds are loaded.
     *
     * @param minHeight minimum build height (must be a multiple of 16, range [-512, 512])
     * @param maxHeight maximum build height ((maxHeight+1) must be a multiple of 16, range [-512, 512])
     * @throws IllegalArgumentException if height parameters are invalid
     */
    public void setCustomHeight(int minHeight, int maxHeight) {
        validateHeight(minHeight, maxHeight);
        if (this != OVERWORLD) {
            log.warn("Custom dimension height for {}: [{}, {}] -> [{}, {}] - Note: the Bedrock client currently only accepts custom height for Overworld. Other dimensions will work server-side but may not render correctly on the client.",
                    this.name(), this.dimensionData.getMinHeight(), this.dimensionData.getMaxHeight(),
                    minHeight, maxHeight);
        } else {
            log.info("Custom dimension height for {}: [{}, {}] -> [{}, {}]",
                    this.name(), this.dimensionData.getMinHeight(), this.dimensionData.getMaxHeight(),
                    minHeight, maxHeight);
        }
        this.dimensionData = new DimensionData(this.dimensionData.getDimensionId(), minHeight, maxHeight);
    }

    /**
     * Resets this dimension to its vanilla default height.
     */
    public void resetToVanilla() {
        this.dimensionData = new DimensionData(this.dimensionData.getDimensionId(), this.vanillaMinHeight, this.vanillaMaxHeight);
    }

    /**
     * Resets all dimensions to their vanilla default heights.
     */
    public static void resetAllToVanilla() {
        for (DimensionEnum dim : values()) {
            dim.resetToVanilla();
        }
    }

    private static void validateHeight(int minHeight, int maxHeight) {
        if (minHeight > maxHeight) {
            throw new IllegalArgumentException("minHeight (" + minHeight + ") must be <= maxHeight (" + maxHeight + ")");
        }
        if (minHeight % 16 != 0) {
            throw new IllegalArgumentException("minHeight (" + minHeight + ") must be a multiple of 16");
        }
        if ((maxHeight + 1) % 16 != 0) {
            throw new IllegalArgumentException("(maxHeight + 1) (" + (maxHeight + 1) + ") must be a multiple of 16");
        }
        if (minHeight < -512 || minHeight > 512) {
            throw new IllegalArgumentException("minHeight (" + minHeight + ") must be in range [-512, 512]");
        }
        if (maxHeight < -512 || maxHeight > 512) {
            throw new IllegalArgumentException("maxHeight (" + maxHeight + ") must be in range [-512, 512]");
        }
        int totalHeight = maxHeight - minHeight + 1;
        if (totalHeight < 16 || totalHeight > 4096) {
            throw new IllegalArgumentException("Total height (" + totalHeight + ") must be in range [16, 4096]");
        }
    }
}
