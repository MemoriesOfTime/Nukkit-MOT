package cn.nukkit.network.protocol.v70;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

public class RemoveBlockPacket  extends DataPacket {
    @Override
    public byte pid() {
        return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
    }

    public long eid;
    public int x;
    public int y;
    public int z;

    @Override
    public void decode() {
        if(this.protocol >= ProtocolInfo.v_0_16_0){
            BlockVector3 v = this.getBlockVector3();
            this.x = v.x;
            this.y = v.y;
            this.z = v.z;
            return;
        }
        if(this.protocol > ProtocolInfo.v_0_10_0){
            this.eid = this.getLong();
        }else{
            this.eid = this.getInt();
        }
        this.x = this.getInt();
        this.z = this.getInt();
        this.y = this.getByte();
    }

    @Override
    public void encode() {
        this.tryReset();
    }
}
