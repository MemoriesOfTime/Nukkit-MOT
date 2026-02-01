package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Sends a set of update properties for the texture shift system from the server to the client.
 * @since v924
 */
@ToString
public class ClientboundTextureShiftPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_TEXTURE_SHIFT_PACKET;

    public static final int ACTION_INVALID = 0;
    public static final int ACTION_INITIALIZE = 1;
    public static final int ACTION_START = 2;
    public static final int ACTION_SET_ENABLED = 3;
    public static final int ACTION_SYNC = 4;

    public int action = ACTION_INVALID;
    public String collectionName = "";
    public String fromStep = "";
    public String toStep = "";
    public String[] allSteps = new String[0];
    public long currentLengthInTicks;
    public long totalLengthInTicks;
    public boolean enabled;

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
        this.action = this.getByte();
        this.collectionName = this.getString();
        this.fromStep = this.getString();
        this.toStep = this.getString();
        int count = (int) this.getUnsignedVarInt();
        this.allSteps = new String[count];
        for (int i = 0; i < count; i++) {
            this.allSteps[i] = this.getString();
        }
        this.currentLengthInTicks = this.getUnsignedVarLong();
        this.totalLengthInTicks = this.getUnsignedVarLong();
        this.enabled = this.getBoolean();
    }

    @Override
    public void encode() {
        this.reset();
        this.putByte((byte) this.action);
        this.putString(this.collectionName);
        this.putString(this.fromStep);
        this.putString(this.toStep);
        this.putUnsignedVarInt(this.allSteps.length);
        for (String step : this.allSteps) {
            this.putString(step);
        }
        this.putUnsignedVarLong(this.currentLengthInTicks);
        this.putUnsignedVarLong(this.totalLengthInTicks);
        this.putBoolean(this.enabled);
    }
}
