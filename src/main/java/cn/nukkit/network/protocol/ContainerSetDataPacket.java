package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class ContainerSetDataPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CONTAINER_SET_DATA_PACKET;

    public static final int PROPERTY_FURNACE_TICK_COUNT = 0;
    public static final int PROPERTY_FURNACE_LIT_TIME = 1;
    public static final int PROPERTY_FURNACE_LIT_DURATION = 2;
    public static final int PROPERTY_UNKNOWN = 3;
    public static final int PROPERTY_FURNACE_FUEL_AUX = 4;

    public static final int PROPERTY_BREWING_STAND_BREW_TIME = 0;
    public static final int PROPERTY_BREWING_STAND_FUEL_AMOUNT = 1;
    public static final int PROPERTY_BREWING_STAND_FUEL_TOTAL = 2;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.CONTAINER_SET_DATA_PACKET;
        }
        return NETWORK_ID;
    }

    public int windowId;
    public int property;
    public int value;

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            this.putByte((byte) (this.windowId & 0xff));
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putVarInt(this.property);
                this.putVarInt(this.value);
                return;
            }
            this.putShort(this.property);
            this.putShort(this.value);
            return;
        }
        this.reset();
        this.putByte((byte) this.windowId);
        this.putVarInt(this.property);
        this.putVarInt(this.value);
    }
}
