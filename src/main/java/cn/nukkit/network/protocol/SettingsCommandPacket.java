package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @since v748
 */
@ToString
public class SettingsCommandPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SETTINGS_COMMAND_PACKET;

    public String command;
    public boolean suppressOutput;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.command = this.getString();
        this.suppressOutput = this.getBoolean();
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }
}