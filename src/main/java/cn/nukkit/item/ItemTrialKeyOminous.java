package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemTrialKeyOminous extends StringItemBase {

    public ItemTrialKeyOminous() {
        super("minecraft:ominous_trial_key", "Ominous Trial Key");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_0;
    }
}