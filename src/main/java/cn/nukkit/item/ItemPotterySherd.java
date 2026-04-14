package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author glorydark
 * @date {2024/1/10} {12:08}
 */
public abstract class ItemPotterySherd extends StringItemBase {

    public ItemPotterySherd(@NotNull String id, @Nullable String name) {
        super(id, name);
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_20_0_23;
    }
}
