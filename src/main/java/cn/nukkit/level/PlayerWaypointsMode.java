package cn.nukkit.level;

public enum PlayerWaypointsMode {
    OFF("off"),
    EVERYONE("everyone");

    private static final PlayerWaypointsMode[] VALUES = values();
    private final String name;

    PlayerWaypointsMode(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static PlayerWaypointsMode[] getValues() {
        return VALUES;
    }
}