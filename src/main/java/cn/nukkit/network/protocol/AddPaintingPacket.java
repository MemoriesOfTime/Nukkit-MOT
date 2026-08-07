package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

/**
 * @author Nukkit Project Team
 */
@ToString
public class AddPaintingPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ADD_PAINTING_PACKET;

    public long entityUniqueId;
    public long entityRuntimeId;
    public float x;
    public float y;
    public float z;
    public int direction;
    public String title;

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putEntityUniqueId(this.entityUniqueId);
                this.putEntityUniqueId(this.entityRuntimeId);
                this.putBlockVector3((int) this.x, (int) this.y, (int) this.z);
                this.putVarInt(this.direction);
                this.putString(this.title);
                return;
            }
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.putLong(entityRuntimeId);
            }else{
                this.putInt((int) entityRuntimeId);
            }
            this.putInt((int) x);
            this.putInt((int) y);
            this.putInt((int) z);
            this.putInt(direction);
            this.putString_old(title);
            return;
        }
        this.reset();
        this.putEntityUniqueId(this.entityUniqueId);
        if (protocol < ProtocolInfo.v1_2_0) {
            this.putEntityUniqueId(this.entityRuntimeId);
        } else {
            this.putEntityRuntimeId(this.entityRuntimeId);
        }
        if (protocol < 361) {
            this.putBlockVector3((int) this.x, (int) this.y, (int) this.z);
        } else {
            this.putVector3f(this.x, this.y, this.z);
        }
        this.putVarInt(this.direction);
        this.putString(this.title);
    }

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.ADD_PAINTING_PACKET;
        }
        return NETWORK_ID;
    }
}
