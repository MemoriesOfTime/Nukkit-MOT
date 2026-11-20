package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @since v2225
 */
@ToString
public class ClientboundMatchmakingStatePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_MATCHMAKING_STATE_PACKET;

    public static final byte STATE_NONE = 0;
    public static final byte STATE_SEARCHING = 1;
    public static final byte JOINING = 2;
    public static final byte SEARCHING_LOCAL = 3;

    public byte state;
    public String destinationName = "";
    public String triggeringPlayerName;
    public Boolean triggeredByLocalPlayer;

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
        this.state = (byte) this.getByte();
        this.destinationName = this.getString(100);
        if (this.getBoolean()) {
            if (this.getBoolean()) {
                this.triggeringPlayerName = this.getString(40);
            }
            if (this.getBoolean()) {
                this.triggeredByLocalPlayer = this.getBoolean();
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putByte(this.state);
        this.putString(this.destinationName);
        boolean hasOptions = this.triggeringPlayerName != null || this.triggeredByLocalPlayer != null;
        this.putBoolean(hasOptions);
        if (hasOptions) {
            this.putOptionalNull(this.triggeringPlayerName, this::putString);
            this.putOptionalNull(this.triggeredByLocalPlayer, this::putBoolean);
        }
    }
}
