package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * @author joserobjr
 * @since 2021-06-12
 */
public abstract class ItemRawMaterial extends StringItemBase {

    public ItemRawMaterial(@NotNull String id, @Nullable String name) {
        super(id, name);
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_17_0;
    }
}
