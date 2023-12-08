package cn.nukkit.network.protocol.types;

import java.util.Objects;

public final class TrimPattern {
    private final String itemName;
    private final String patternId;

    public TrimPattern(String itemName, String patternId) {
        this.itemName = itemName;
        this.patternId = patternId;
    }

    public String itemName() {
        return itemName;
    }

    public String patternId() {
        return patternId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        TrimPattern that = (TrimPattern) obj;
        return Objects.equals(this.itemName, that.itemName) &&
                Objects.equals(this.patternId, that.patternId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemName, patternId);
    }

    @Override
    public String toString() {
        return "TrimPattern[" +
                "itemName=" + itemName + ", " +
                "patternId=" + patternId + ']';
    }

}
