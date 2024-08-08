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
    public Integer loadingScreenId;

    @Override
    public void decode() {
        this.dimension = this.getVarInt();
        this.x = this.getVector3f().x;
        this.y = this.getVector3f().y;
        this.z = this.getVector3f().z;
        this.respawn = this.getBoolean();
        if (protocol >= ProtocolInfo.v1_21_20) {
            this.loadingScreenId = this.getOptional(null, BinaryStream::getLInt);
        }
    }

    @Override
    public void encode() {
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
        return NETWORK_ID;
    }
}
