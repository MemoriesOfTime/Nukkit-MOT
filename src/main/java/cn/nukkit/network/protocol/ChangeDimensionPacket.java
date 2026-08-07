package cn.nukkit.network.protocol;

import cn.nukkit.utils.BinaryStream;
import lombok.ToString;

/**
 * Created on 2016/1/5 by xtypr.
 * Package cn.nukkit.network.protocol in project nukkit .
 */
@ToString
public class ChangeDimensionPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CHANGE_DIMENSION_PACKET;

    public int dimension;

    public float x;
    public float y;
    public float z;

    public boolean respawn;

    /**
     * Will be serialized as optional not present if null
     * @since v712
     */
    public Integer loadingScreenId = null;

    @Override
    public void decode() {
        this.dimension = this.getVarInt();
        var pos = this.getVector3f();
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.respawn = this.getBoolean();
        if (protocol >= ProtocolInfo.v1_21_20) {
            this.loadingScreenId = this.getOptional(null, BinaryStream::getLInt);
        }
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putVarInt(this.dimension);
                this.putVector3f(this.x, this.y, this.z);
                this.putBoolean(this.respawn);
                return;
            }
            this.putByte((byte) (dimension & 0xff));
            this.putFloat(x);
            this.putFloat(y);
            this.putFloat(z);
            this.putByte((byte) 0);
            return;
        }
        this.reset();
        this.putVarInt(this.dimension);
        this.putVector3f(this.x, this.y, this.z);
        this.putBoolean(this.respawn);
        if (protocol >= ProtocolInfo.v1_21_20) {
            this.putOptionalNull(this.loadingScreenId, this::putLInt);
        }
    }

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }
        return NETWORK_ID;
    }
}
