package cn.nukkit.network.protocol.types;

import lombok.Getter;

public enum DisplaySlot {

    SIDEBAR("sidebar"),
    LIST("list"),
    BELOW_NAME("belowname");

    @Getter
    private final String slotName;

    DisplaySlot(String slotName) {
        this.slotName = slotName;
    }
}
