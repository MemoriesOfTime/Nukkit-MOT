package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.inventory.ContainerType;
import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class ContainerClosePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CONTAINER_CLOSE_PACKET;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.CONTAINER_CLOSE_PACKET;
        }
        return NETWORK_ID;
    }

    public int windowId;
    public boolean wasServerInitiated;
    /**
     * @since v685
     */
    public ContainerType type = ContainerType.NONE;

    @Override
    public void decode() {
        this.windowId = (byte) this.getByte();
        if (protocol >= ProtocolInfo.v1_16_100) {
            if (protocol >= ProtocolInfo.v1_21_0) {
                this.type = ContainerType.from((byte) this.getByte());
            }
            this.wasServerInitiated = this.getBoolean();
        }
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            this.putByte((byte) this.windowId);
            return;
        }
        this.reset();
        this.putByte((byte) this.windowId);
        if (protocol >= ProtocolInfo.v1_16_100) {
            if (protocol >= ProtocolInfo.v1_21_0) {
                this.putByte((byte) this.type.getId());
            }
            this.putBoolean(this.wasServerInitiated);
        }
    }
}
