package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Server-bound packet to change the properties of a mob.
 *
 * @since v503
 */
@ToString
public class ChangeMobPropertyPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CHANGE_MOB_PROPERTY_PACKET;
    private long uniqueEntityId;
    private String property;
    private boolean boolValue;
    private String stringValue;
    private int intValue;
    private float floatValue;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.uniqueEntityId = getLong();
        this.property = getString();
        this.boolValue = getBoolean();
        this.stringValue = getString();
        this.intValue = getVarInt();
        this.floatValue = getLFloat();
    }

    @Override
    public void encode() {
        this.putLong(this.uniqueEntityId);
        this.putString(this.property);
        this.putBoolean(this.boolValue);
        this.putString(this.stringValue);
        this.putVarInt(this.intValue);
        this.putLFloat(this.floatValue);
    }
}
