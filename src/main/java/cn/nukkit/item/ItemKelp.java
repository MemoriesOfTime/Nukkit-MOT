package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.BlockKelp;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemKelp extends Item {

    public ItemKelp() {
        this(0, 1);
    }

    public ItemKelp(Integer meta) {
        this(meta, 1);
    }

    public ItemKelp(Integer meta, int count) {
        super(KELP, meta, count, "Kelp");
        this.block = new BlockKelp();
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_4_0;
    }
}
