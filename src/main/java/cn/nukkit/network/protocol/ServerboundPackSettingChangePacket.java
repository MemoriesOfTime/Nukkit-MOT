package cn.nukkit.network.protocol;

import lombok.ToString;

import java.util.UUID;

/**
 * Sent by the client when a resource pack setting is changed in the pack settings UI.
 *
 * @since v844
 */
@ToString
public class ServerboundPackSettingChangePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SERVERBOUND_PACK_SETTING_CHANGE_PACKET;

    public static final int TYPE_FLOAT = 0;
    public static final int TYPE_BOOL = 1;
    public static final int TYPE_STRING = 2;

    public UUID packId;
    public String packSettingName;
    public int valueType;
    public float floatValue;
    public boolean boolValue;
    public String stringValue;

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
        this.packId = this.getUUID();
        this.packSettingName = this.getString();
        this.valueType = (int) this.getUnsignedVarInt();

        switch (this.valueType) {
            case TYPE_FLOAT -> this.floatValue = this.getLFloat();
            case TYPE_BOOL -> this.boolValue = this.getBoolean();
            case TYPE_STRING -> this.stringValue = this.getString();
            default -> throw new IllegalStateException("Invalid pack setting type: " + this.valueType);
        }
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }
}
