package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author PetteriM1
 */
public class ItemBannerPattern extends Item {

    public ItemBannerPattern() {
        this(0, 1);
    }

    public ItemBannerPattern(Integer meta) {
        this(meta, 1);
    }

    public ItemBannerPattern(Integer meta, int count) {
        super(BANNER_PATTERN, meta, count, "Banner Pattern");
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        int meta = this.getDamage();
        return switch (meta) {
            case 0, 1, 2, 3, 4, 5, 6 -> protocolId.getProtocol() >= ProtocolInfo.v1_16_100;
            case 7 -> protocolId.getProtocol() >= ProtocolInfo.v1_18_10;
            default -> false;
        };
    }
}
