package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemSuspiciousStew extends ItemEdible {

    private static final int MAX_SUPPORTED_META = 12;

    private static final int TORCHFLOWER_META = 10;
    private static final int OPEN_EYEBLOSSOM_META = 11;
    private static final int CLOSED_EYEBLOSSOM_META = 12;

    public ItemSuspiciousStew() {
        this(0, 1);
    }

    public ItemSuspiciousStew(Integer meta) {
        this(meta, 1);
    }

    public ItemSuspiciousStew(Integer meta, int count) {
        super(SUSPICIOUS_STEW, meta, count, "Suspicious Stew");
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        int meta = this.getDamage();
        if (protocolId.getProtocol() < ProtocolInfo.v1_13_0) {
            return false;
        }

        return switch (meta) {
            case TORCHFLOWER_META -> protocolId.getProtocol() >= ProtocolInfo.v1_20_0;
            case OPEN_EYEBLOSSOM_META, CLOSED_EYEBLOSSOM_META -> protocolId.getProtocol() >= ProtocolInfo.v1_21_50;
            default -> true;
        };
    }
}
