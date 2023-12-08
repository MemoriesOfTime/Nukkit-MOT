package cn.nukkit.network.protocol.types;

import java.util.Objects;

public final class PropertySyncData {
    private final int[] intProperties;
    private final float[] floatProperties;

    public PropertySyncData(int[] intProperties, float[] floatProperties) {
        this.intProperties = intProperties;
        this.floatProperties = floatProperties;
    }

    public int[] intProperties() {
        return intProperties;
    }

    public float[] floatProperties() {
        return floatProperties;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        PropertySyncData that = (PropertySyncData) obj;
        return Objects.equals(this.intProperties, that.intProperties) &&
                Objects.equals(this.floatProperties, that.floatProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intProperties, floatProperties);
    }

    @Override
    public String toString() {
        return "PropertySyncData[" +
                "intProperties=" + intProperties + ", " +
                "floatProperties=" + floatProperties + ']';
    }

}
