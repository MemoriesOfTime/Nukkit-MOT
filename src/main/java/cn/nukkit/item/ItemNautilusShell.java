package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author PetteriM1
 */
public class ItemNautilusShell extends Item {

    public ItemNautilusShell() {
        this(0, 1);
    }

    public ItemNautilusShell(Integer meta) {
        this(meta, 1);
    }

    public ItemNautilusShell(Integer meta, int count) {
        super(NAUTILUS_SHELL, meta, count, "Nautilus Shell");
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_4_0;
    }
}
