package cn.nukkit.network.protocol.v70;

import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ExplodePacket  extends DataPacket {
    @Override
    public byte pid() {
        return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
    }

    public float x;
    public float y;
    public float z;
    public float radius;
    public Vector3[] records = new Vector3[0];

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.tryReset();
        this.putFloat(this.x);
        this.putFloat(this.y);
        this.putFloat(this.z);
        this.putFloat(this.radius);
        this.putInt(this.records.length);
        if (this.records.length > 0) {
            for (Vector3 record : records) {
                this.putByte((byte) record.x);
                this.putByte((byte) record.y);
                this.putByte((byte) record.z);
            }
        }
    }
}
