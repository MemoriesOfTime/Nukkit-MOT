package cn.nukkit.network.protocol;

import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.item.Item;
import cn.nukkit.utils.Binary;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class AddItemEntityPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ADD_ITEM_ENTITY_PACKET;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }
        return NETWORK_ID;
    }

    public long entityUniqueId;
    public long entityRuntimeId;
    public Item item;
    public float x;
    public float y;
    public float z;
    public float speedX;
    public float speedY;
    public float speedZ;
    public EntityMetadata metadata = new EntityMetadata();
    public boolean isFromFishing = false;

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
                this.putSlot(this.protocol, this.item);
                this.putVector3f(this.x, this.y, this.z);
                this.putVector3f(this.speedX, this.speedY, this.speedZ);
            }else {
                if(this.protocol <= ProtocolInfo.v_0_10_0){
                    this.putInt((int) this.entityRuntimeId);
                }else{
                    this.putLong(this.entityRuntimeId);
                }
                this.putSlot_old(this.protocol, this.item);
                this.putFloat(this.x);
                this.putFloat(this.y);
                this.putFloat(this.z);
                if(this.protocol > ProtocolInfo.v_0_10_0){
                    this.putFloat(this.speedX);
                    this.putFloat(this.speedY);
                    this.putFloat(this.speedZ);
                }else{
                    this.putByte((byte) this.speedX);
                    this.putByte((byte) this.speedY);
                    this.putByte((byte) this.speedZ);
                }
            }

            return;
        }
        this.reset();
        this.putEntityUniqueId(this.entityUniqueId);
        if (protocol < ProtocolInfo.v1_2_0) {
            this.putEntityUniqueId(this.entityRuntimeId);
        } else {
            this.putEntityRuntimeId(this.entityRuntimeId);
        }
        this.putSlot(gameVersion, this.item);
        this.putVector3f(this.x, this.y, this.z);
        this.putVector3f(this.speedX, this.speedY, this.speedZ);
        if (protocol >= ProtocolInfo.v1_2_0) {
            this.put(Binary.writeMetadata(gameVersion, metadata));
        }
        if (protocol >= 223) {
            this.putBoolean(this.isFromFishing);
        }
    }
}
