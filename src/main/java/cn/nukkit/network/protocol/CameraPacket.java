package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

@ToString
public class CameraPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CAMERA_PACKET;

    public long cameraUniqueId;
    public long playerUniqueId;

    @Override
    public byte pid() {
        if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.CAMERA_PACKET;
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.cameraUniqueId = this.getVarLong();
        this.playerUniqueId = this.getVarLong();
    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityUniqueId(this.cameraUniqueId);
        this.putEntityUniqueId(this.playerUniqueId);
    }
}
