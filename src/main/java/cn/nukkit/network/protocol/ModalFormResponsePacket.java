package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class ModalFormResponsePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.MODAL_FORM_RESPONSE_PACKET;
    public static final int CANCEL_REASON_USER_CLOSED = 0;
    public static final int CANCEL_REASON_USER_BUSY = 1;

    public int formId;
    public String data = "null";
    public int cancelReason;
    public boolean hasCancelReason;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.formId = (int) this.getUnsignedVarInt();
        this.hasCancelReason = false;
        if (this.protocol >= ProtocolInfo.v1_19_20) {
            if (this.getBoolean()) {
                this.data = this.getString();
            }
            if (this.getBoolean()) {
                this.hasCancelReason = true;
                this.cancelReason = this.getByte();
            }
        } else {
            this.data = this.getString(); // Data will be null if player close form without submit (by cross button or ESC)
        }
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }
}
