package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Allows the server to tell the client to close all the Data Driven UI screens.
 * @since v924
 */
@ToString
public class ClientboundDataDrivenUICloseAllScreensPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_DATA_DRIVEN_UI_CLOSE_ALL_SCREENS_PACKET;

    /**
     * @since v944
     */
    public Integer formId;

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if (this.protocol >= ProtocolInfo.v1_26_10) {
            boolean hasFormId = this.getBoolean();
            if (hasFormId) {
                this.formId = this.getLInt();
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_26_10) {
            if (this.formId != null) {
                this.putBoolean(true);
                this.putLInt(this.formId);
            } else {
                this.putBoolean(false);
            }
        }
    }
}
