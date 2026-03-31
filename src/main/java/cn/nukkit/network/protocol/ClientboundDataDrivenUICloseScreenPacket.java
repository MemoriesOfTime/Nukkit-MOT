package cn.nukkit.network.protocol;

import cn.nukkit.utils.BinaryStream;
import lombok.ToString;

/**
 * Allows the server to tell the client to close Data Driven UI screens.
 * <p>
 * When {@code formId} is provided, closes the specific screen identified by that ID.
 * When {@code formId} is {@code null}, closes all open DDUI screens.
 *
 * @since v924
 */
@ToString
public class ClientboundDataDrivenUICloseScreenPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_DATA_DRIVEN_UI_CLOSE_SCREEN_PACKET;

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
            this.formId = this.getOptional(null, BinaryStream::getLInt);
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_26_10) {
            this.putOptionalNull(this.formId, this::putLInt);
        }
    }
}
