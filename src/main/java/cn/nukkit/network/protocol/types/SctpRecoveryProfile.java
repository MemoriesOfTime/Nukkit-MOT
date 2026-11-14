package cn.nukkit.network.protocol.types;

/**
 * SCTP 恢复配置，随 NetworkSettings 下发。
 * <p>
 * SCTP recovery profile sent with NetworkSettings.
 *
 * @since v2259 1.26.70
 */
public enum SctpRecoveryProfile {
    DEFAULT(0),
    FAST(1),
    BALANCED(2);

    private final int id;

    SctpRecoveryProfile(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static SctpRecoveryProfile fromId(int id) {
        return switch (id) {
            case 0 -> DEFAULT;
            case 1 -> FAST;
            case 2 -> BALANCED;
            default -> DEFAULT;
        };
    }
}
