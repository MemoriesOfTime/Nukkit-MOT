package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemDoorCrimson extends Item {

    public ItemDoorCrimson() {
        this(0, 1);
    }

    public ItemDoorCrimson(Integer meta) {
        this(meta, 1);
    }

    public ItemDoorCrimson(Integer meta, int count) {
        super(CRIMSON_DOOR, 0, count, "Crimson Door");
        this.block = Block.get(BlockID.CRIMSON_DOOR_BLOCK);
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_16_0;
    }
}
