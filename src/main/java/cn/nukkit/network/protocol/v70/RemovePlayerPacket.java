package cn.nukkit.network.protocol.v70;

import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

import java.util.UUID;

public class RemovePlayerPacket extends DataPacket {
    @Override
    public byte pid() {
        return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
    }

    public long eid;
    public long clientID;
    public UUID uuid;

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.tryReset();
        if(this.protocol <= ProtocolInfo.v_0_10_0){
            this.putInt((int) this.eid);
        }else{
            this.putLong(this.eid);
        }
        if(this.protocol <= ProtocolInfo.v_0_11_0){
            this.putLong(this.clientID);
        }else {
            this.putUUID(this.uuid);
        }
    }
}
