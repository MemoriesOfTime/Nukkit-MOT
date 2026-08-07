package cn.nukkit.network.protocol.v70;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ReplaceSelectedItemPacket  extends DataPacket {

    public Item item;


    @Override
    public byte pid() {
        return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
    }

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.tryReset();
        if(this.protocol > ProtocolInfo.v_0_15_10 && this.protocol < ProtocolInfo.v_1_0_0){
            this.putSlot(this.protocol, this.item);
            return;
        }
    }
}
