package cn.nukkit.network.protocol.types;

public enum InputMode {

    UNDEFINED(0),
    MOUSE(1),
    TOUCH(2),
    GAME_PAD(3),
    MOTION_CONTROLLER(4),
    COUNT(5);

    private static final InputMode[] VALUES = values();
    private final int ordinal;

    InputMode(int ordinal) {
        this.ordinal = ordinal;
    }

    public static InputMode fromOrdinal(int ordinal) {
        return VALUES[ordinal];
    }

    public int getOrdinal() {
        return ordinal;
    }
}