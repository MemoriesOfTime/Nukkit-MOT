package cn.nukkit.network.protocol;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

@ToString
public class BlockPickRequestPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.BLOCK_PICK_REQUEST_PACKET;

    public int x;
    public int y;
    public int z;
    public boolean addUserData;
    public int selectedSlot;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.BLOCK_PICK_REQUEST_PACKET;
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            BlockVector3 v = this.getSignedBlockPosition();
            this.x = v.x;
            this.y = v.y;
            this.z = v.z;
            this.addUserData = this.getBoolean();
        }else{
            BlockVector3 v = this.getBlockVector3();
            this.x = v.x;
            this.y = v.y;
            this.z = v.z;
        }
        this.selectedSlot = this.getByte();
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }
}
