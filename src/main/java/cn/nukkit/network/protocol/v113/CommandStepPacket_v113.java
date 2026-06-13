package cn.nukkit.network.protocol.v113;

import cn.nukkit.command.data.v113.CommandArgs_v113;
import com.google.gson.Gson;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class CommandStepPacket_v113 extends DataPacket_v113 {

    public static final byte NETWORK_ID = ProtocolInfo_v113.COMMAND_STEP_PACKET;

    public String command;
    public String overload;
    public long uvarint1;
    public long currentStep;
    public boolean done;
    public long clientId;
    public CommandArgs_v113 args = new CommandArgs_v113(); //JSON formatted command arguments
    public String outputJson;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.command = this.getString();
        this.overload = this.getString();
        this.uvarint1 = this.getUnsignedVarInt();
        this.currentStep = this.getUnsignedVarInt();
        this.done = this.getBoolean();
        this.clientId = this.getUnsignedVarInt();
        String argsString = this.getString();
        this.args = new Gson().fromJson(argsString, CommandArgs_v113.class);
        this.outputJson = this.getString();
        while (!this.feof()) {
            this.getByte(); //prevent assertion errors. TODO: find out why there are always 3 extra bytes at the end of this packet.
        }

    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }

}
