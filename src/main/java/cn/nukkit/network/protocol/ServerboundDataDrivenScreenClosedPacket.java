package cn.nukkit.network.protocol;

import lombok.ToString;

import java.util.Arrays;
import java.util.List;

/**
 * Sent from the client to the server when a data driven screen is closed.
 *
 * @since v944
 */
@ToString
public class ServerboundDataDrivenScreenClosedPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SERVERBOUND_DATA_DRIVEN_SCREEN_CLOSED_PACKET;

    private static final List<String> CLOSE_REASONS = Arrays.asList(
            "programmaticclose", "programmaticcloseall", "clientcanceled", "userbusy", "invalidform"
    );

    public int formId;
    public CloseReason closeReason = CloseReason.CLIENT_CANCELED;

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
        this.formId = this.getLInt();
        String reason = this.getString();
        int index = CLOSE_REASONS.indexOf(reason);
        if (index >= 0) {
            this.closeReason = CloseReason.values()[index];
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putLInt(this.formId);
        this.putString(CLOSE_REASONS.get(this.closeReason.ordinal()));
    }

    public enum CloseReason {
        PROGRAMMATIC_CLOSE,
        PROGRAMMATIC_CLOSE_ALL,
        CLIENT_CANCELED,
        USER_BUSY,
        INVALID_FORM,
    }
}
