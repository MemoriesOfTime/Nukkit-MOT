package cn.nukkit.network.protocol.types;

import cn.nukkit.network.protocol.ProtocolInfo;

public enum InputMode {

    UNDEFINED(0),
    MOUSE(1),
    TOUCH(2),
    GAME_PAD(3),
    MOTION_CONTROLLER(4),
    /**
     * @since v2259 1.26.70
     */
    GAME_PAD_AND_MOUSE(5),
    /**
     * 模式数量哨兵；v2259 起线路上的 Count 为 6。
     * <p>
     * Mode-count sentinel; the wire Count value is 6 since v2259.
     */
    COUNT(5);

    private final int ordinal;

    InputMode(int ordinal) {
        this.ordinal = ordinal;
    }

    public int getOrdinal() {
        return ordinal;
    }

    /**
     * v2259 起 5 改为 GamePadAndMouse、遍历值 Count 移到 6，且不再有 MotionController。
     * <p>
     * Since v2259 the wire value 5 is GamePadAndMouse, the Count sentinel moves to 6 and
     * MotionController is gone.
     *
     * @param ordinal wire value
     * @param protocol target protocol
     */
    public static InputMode fromOrdinal(int ordinal, int protocol) {
        if (protocol >= ProtocolInfo.v1_26_70) {
            return switch (ordinal) {
                case 0 -> UNDEFINED;
                case 1 -> MOUSE;
                case 2 -> TOUCH;
                case 3 -> GAME_PAD;
                case 5 -> GAME_PAD_AND_MOUSE;
                case 6 -> COUNT;
                default -> UNDEFINED;
            };
        }
        return switch (ordinal) {
            case 0 -> UNDEFINED;
            case 1 -> MOUSE;
            case 2 -> TOUCH;
            case 3 -> GAME_PAD;
            case 4 -> MOTION_CONTROLLER;
            case 5 -> COUNT;
            default -> UNDEFINED;
        };
    }

    /**
     * 旧版一参数入口：按 v2259 之前的线值解析（5=Count）。
     * <p>
     * Legacy one-argument entry point: decodes the pre-v2259 wire values (5=Count).
     *
     * @deprecated 使用 {@link #fromOrdinal(int, int)} 传入实际协议版本
     * <p>
     * Use {@link #fromOrdinal(int, int)} with the actual protocol version
     */
    @Deprecated
    public static InputMode fromOrdinal(int ordinal) {
        return fromOrdinal(ordinal, ProtocolInfo.v1_26_60);
    }
}
