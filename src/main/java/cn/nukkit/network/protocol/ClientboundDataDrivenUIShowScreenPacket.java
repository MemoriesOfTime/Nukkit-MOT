package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Allows the server to tell the client to show a Data Driven UI screen.
 * @since v924
 */
@ToString
public class ClientboundDataDrivenUIShowScreenPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_DATA_DRIVEN_UI_SHOW_SCREEN_PACKET;

    /**
     * The ID of the screen to show.
     */
    public String screenId;
    /**
     * @since v944
     */
    public int formId;
    /**
     * @since v944
     */
    public Integer dataInstanceId;

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
        this.screenId = this.getString();
        if (this.protocol >= ProtocolInfo.v1_26_10) {
            this.formId = this.getLInt();
            boolean hasDataInstanceId = this.getBoolean();
            if (hasDataInstanceId) {
                this.dataInstanceId = this.getLInt();
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.screenId);
        if (this.protocol >= ProtocolInfo.v1_26_10) {
            this.putLInt(this.formId);
            if (this.dataInstanceId != null) {
                this.putBoolean(true);
                this.putLInt(this.dataInstanceId);
            } else {
                this.putBoolean(false);
            }
        }
    }
}
