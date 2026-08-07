package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class ScriptMessagePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SCRIPT_MESSAGE_PACKET;

    public String channel;
    public String message;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.channel = this.getString();
        this.message = this.getString();
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.channel);
        this.putString(this.message);
    }
}
