package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemArrow extends Item {

    public static final int NORMAL_ARROW = 0;
    public static final int TIPPED_ARROW = 1;

    public ItemArrow() {
        this(0, 1);
    }

    public ItemArrow(Integer meta) {
        this(meta, 1);
    }

    public ItemArrow(Integer meta, int count) {
        super(ARROW, meta, count, "Arrow");
    }

    public boolean isTipped() {
        return getDamage() > NORMAL_ARROW;
    }

    public int getPotionId() {
        return getDamage() - TIPPED_ARROW;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        int damage = this.getDamage();
        if (damage <= 42) {
            return true;
        }
        if (damage == 43) {
            return protocolId >= ProtocolInfo.v1_16_0;
        }
        return protocolId >= ProtocolInfo.v1_21_0;
    }
}
