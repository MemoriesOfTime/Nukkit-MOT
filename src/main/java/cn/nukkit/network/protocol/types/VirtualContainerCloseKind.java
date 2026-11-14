package cn.nukkit.network.protocol.types;

/**
 * {@link cn.nukkit.network.protocol.VirtualContainerClosePacket} 的关闭阶段。
 * <p>
 * Close phase of {@link cn.nukkit.network.protocol.VirtualContainerClosePacket}.
 *
 * @since v2250 1.26.70.23
 */
public enum VirtualContainerCloseKind {
    REQUEST(0),
    CLOSED(1),
    CONFIRMED(2);

    private final int id;

    VirtualContainerCloseKind(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static VirtualContainerCloseKind fromId(int id) {
        return switch (id) {
            case 0 -> REQUEST;
            case 1 -> CLOSED;
            case 2 -> CONFIRMED;
            default -> throw new IllegalArgumentException("Unknown VirtualContainerCloseKind: " + id);
        };
    }
}
