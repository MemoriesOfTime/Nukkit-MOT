package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author glorydark
 */
public abstract class ItemHarness extends StringItemBase {

    public ItemHarness(@NotNull String namespaceId, @Nullable String name) {
        super(namespaceId, name);
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_90;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
