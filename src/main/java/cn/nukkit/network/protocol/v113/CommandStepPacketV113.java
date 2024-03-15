package cn.nukkit.network.protocol.v113;

import cn.nukkit.command.data.CommandArgs;
import cn.nukkit.network.protocol.DataPacket;
import com.google.gson.Gson;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class CommandStepPacketV113 extends DataPacket {

    public static final byte NETWORK_ID = 0x4f;

    /**
     * unknown (string)
     * unknown (string)
     * unknown (uvarint)
     * unknown (uvarint)
     * unknown (bool)
     * unknown (uvarint64)
     * unknown (string)
     * unknown (string)
     * https://gist.github.com/dktapps/8285b93af4ca38e0104bfeb9a6c87afd
     */


    public String command;
    public String overload;
    public long uvarint1;
    public long currentStep;
    public boolean done;
    public long clientId;
    public CommandArgs args = new CommandArgs(); //JSON formatted command arguments
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
        this.clientId = this.getVarLong();
        String argsString = this.getString();
        this.args = new Gson().fromJson(argsString, CommandArgs.class);
        this.outputJson = this.getString();
        while (!this.feof()) {
            this.getByte(); //prevent assertion errors. TODO: find out why there are always 3 extra bytes at the end of this packet.
        }

    }

    @Override
    public void encode() {
    }

}
