package cn.nukkit.network.protocol;

import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.network.protocol.types.PropertySyncData;
import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import cn.nukkit.utils.Binary;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class SetEntityDataPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SET_ENTITY_DATA_PACKET;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.SET_ENTITY_DATA_PACKET;
        }
        return NETWORK_ID;
    }

    public long eid;
    public EntityMetadata metadata;
    public long frame;
    /**
     * @since v557
     */
    public PropertySyncData syncedProperties = new PropertySyncData(new int[]{}, new float[]{});

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putVarLong(this.eid);
                this.put(Binary.writeMetadata_016(this.metadata));
                return;
            }
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.putLong(this.eid);
            }else{
                this.putInt((int) this.eid);
            }

            // 测试, 只发送默认metadata
            // this.metadata = EntityMetadataController.getDefaultEntityMetadata(this.protocol);

            this.put(Binary.writeMetadata_old(this.metadata));
            return;
        }
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_2_0){
            this.putEntityRuntimeId(this.eid);
        }else{
            this.putEntityUniqueId(this.eid);
        }
        this.put(Binary.writeMetadata(protocol, this.metadata));
        if (protocol >= ProtocolInfo.v1_16_100) {
            if (protocol >= ProtocolInfo.v1_19_40) {
                this.putUnsignedVarInt(this.syncedProperties.intProperties().length);
                for (int i = 0, len = this.syncedProperties.intProperties().length; i < len; ++i) {
                    this.putUnsignedVarInt(i);
                    this.putVarInt(this.syncedProperties.intProperties()[i]);
                }
                this.putUnsignedVarInt(this.syncedProperties.floatProperties().length);
                for (int i = 0, len = this.syncedProperties.floatProperties().length; i < len; ++i) {
                    this.putUnsignedVarInt(i);
                    this.putLFloat(this.syncedProperties.floatProperties()[i]);
                }
            }
            this.putUnsignedVarLong(this.frame);
        }
    }
}
