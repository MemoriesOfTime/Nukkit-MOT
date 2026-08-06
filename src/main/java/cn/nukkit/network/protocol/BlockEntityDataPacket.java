package cn.nukkit.network.protocol;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString(exclude = "namedTag")
public class BlockEntityDataPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.BLOCK_ENTITY_DATA_PACKET;

    public int x;
    public int y;
    public int z;
    public byte[] namedTag;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.BLOCK_ENTITY_DATA_PACKET;
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            this.x = this.getInt();
            if(this.protocol <= ProtocolInfo.v_0_11_0){
                this.y = this.getByte();
            }else{
                this.y = this.getInt();
            }
            this.z = this.getInt();
            this.namedTag = this.get();
            return;
        }

        BlockVector3 v = this.getBlockVector3();
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
        this.namedTag = this.get();
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putBlockVector3(this.x, this.y, this.z);
                this.put(this.namedTag);
                return;
            }
            this.putInt(this.x);
            if(this.protocol <= ProtocolInfo.v_0_11_0){
                this.putByte((byte)(this.y & 0xff));
            }else{
                this.putInt(this.y);
            }
            this.putInt(this.z);
            this.put(this.namedTag);
            return;
        }
        this.reset();
        this.putBlockVector3(this.x, this.y, this.z);
        this.put(this.namedTag);
    }
}