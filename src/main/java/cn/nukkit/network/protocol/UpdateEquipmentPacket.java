package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

@ToString(exclude = "namedtag")
public class UpdateEquipmentPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.UPDATE_EQUIPMENT_PACKET;

    public int windowId;
    public int windowType;
    public int unknown;
    public long eid;
    public byte[] namedtag;


    @Override
    public byte pid() {
        if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.UPDATE_EQUIP_PACKET;
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        this.putByte((byte) this.windowId);
        this.putByte((byte) this.windowType);
        this.putEntityUniqueId(this.eid);
        this.put(this.namedtag);
    }
}
