package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class BlockEventPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.BLOCK_EVENT_PACKET;
    public int x;
    public int y;
    public int z;
    public int eventType;
    public int eventData;
    @Deprecated
    public int case1 = -1;
    @Deprecated
    public int case2 = -1;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        //兼容NK插件
        if (this.case1 != -1) {
            this.eventType = this.case1;
        }
        if (this.case2 != -1) {
            this.eventData = this.case2;
        }

        this.reset();
        this.putBlockVector3(this.x, this.y, this.z);
        this.putVarInt(this.eventType);
        this.putVarInt(this.eventData);
    }
}
