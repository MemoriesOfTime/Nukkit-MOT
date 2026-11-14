package cn.nukkit.network.protocol.types;

/**
 * 虚拟容器关闭原因。
 * <p>
 * Why a virtual container screen is closing.
 *
 * @since v2250 1.26.70.23
 */
public enum VirtualContainerCloseCause {
    UNKNOWN(0),
    USER_CLOSED(1),
    SERVER_REQUESTED(2),
    USER_BUSY(3),
    CLIENT_NOT_READY(4),
    INVALID_CONTENTS(5),
    UI_CREATION_FAILED(6),
    UI_CANCELLED(7);

    private final int id;

    VirtualContainerCloseCause(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static VirtualContainerCloseCause fromId(int id) {
        return switch (id) {
            case 0 -> UNKNOWN;
            case 1 -> USER_CLOSED;
            case 2 -> SERVER_REQUESTED;
            case 3 -> USER_BUSY;
            case 4 -> CLIENT_NOT_READY;
            case 5 -> INVALID_CONTENTS;
            case 6 -> UI_CREATION_FAILED;
            case 7 -> UI_CANCELLED;
            default -> throw new IllegalArgumentException("Unknown VirtualContainerCloseCause: " + id);
        };
    }
}
