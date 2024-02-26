package cn.nukkit.network.protocol;

import org.jetbrains.annotations.NotNull;

public class RemoveObjectivePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.REMOVE_OBJECTIVE_PACKET;

    public String objectiveName;

    @Override
    public byte pid() {
        return ProtocolInfo.REMOVE_OBJECTIVE_PACKET;
    }

    @Override
    public void decode() {
        //only server -> client
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.objectiveName);
    }

    public void setObjectiveName(@NotNull String objectiveName) {
        this.objectiveName = objectiveName;
    }

    public String getObjectiveName() {
        return this.objectiveName;
    }
}
