package cn.nukkit.level;

public enum DimensionEnum {

    //NK-MOT暂时不支持小于0和大于255的高度，但为了区块编码正常 不修改这里
    OVERWORLD(new DimensionData(Level.DIMENSION_OVERWORLD, -64, 319)),
    NETHER(new DimensionData(Level.DIMENSION_NETHER, 0, 127)),
    END(new DimensionData(Level.DIMENSION_THE_END, 0, 255));

    private final DimensionData dimensionData;

    DimensionEnum(DimensionData dimensionData) {
        this.dimensionData = dimensionData;
    }

    public DimensionData getDimensionData() {
        return this.dimensionData;
    }

    public static DimensionData getDataFromId(int dimension) {
        for (DimensionEnum value : values()) {
            if (value.getDimensionData().getDimensionId() == dimension) {
                return value.getDimensionData();
            }
        }
        return null;
    }
}
