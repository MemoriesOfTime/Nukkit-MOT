package cn.nukkit.block.properties.enums;

import lombok.Getter;

@Getter
public enum DripleafTilt {
    NONE(true, -1, "none"),
    UNSTABLE(false, 10, "unstable"),
    PARTIAL_TILT(true, 10, "partial_tilt"),
    FULL_TILT(false, 100, "full_tilt");

    private final boolean stable;
    private final int netxStateDelay;
    private final String name;

    DripleafTilt(boolean stable, int netxStateDelay, String name) {
        this.stable = stable;
        this.netxStateDelay = netxStateDelay;
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
