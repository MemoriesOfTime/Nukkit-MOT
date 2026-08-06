package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

/**
 * @author Nukkit Project Team
 */
@ToString
public class SetSpawnPositionPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SET_SPAWN_POSITION_PACKET;

    public static final int TYPE_PLAYER_SPAWN = 0;
    public static final int TYPE_WORLD_SPAWN = 1;

    public int spawnType;
    public int y;
    public int z;
    public int x;
    public int dimension = 0;

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putUnsignedVarInt(this.spawnType);
                this.putBlockVector3(this.x, this.y, this.z);
                this.putBoolean(false);
                return;
            }
            this.putInt(x);
            if(this.protocol <= ProtocolInfo.v_0_11_0){
                this.putInt(z);
                this.putByte((byte)(y & 0xff));
                return;
            }
            this.putInt(y);
            this.putInt(z);
            return;
        }

        this.reset();
        this.putVarInt(this.spawnType);
        this.putBlockVector3(this.x, this.y, this.z);
        if (protocol >= 407) {
            this.putVarInt(this.dimension);
            this.putBlockVector3(this.x, this.y, this.z);
        } else {
            this.putBoolean(false);
        }
    }

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.SET_SPAWN_POSITION_PACKET;
        }
        return NETWORK_ID;
    }
}
