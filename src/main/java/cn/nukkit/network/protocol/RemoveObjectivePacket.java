package cn.nukkit.network.protocol;

import lombok.ToString;
import org.jetbrains.annotations.NotNull;

@ToString
public class RemoveObjectivePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.REMOVE_OBJECTIVE_PACKET;

    public String objectiveId;

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
        this.putString(this.objectiveId);
    }

    public void setObjectiveId(@NotNull String objectiveName) {
        this.objectiveId = objectiveName;
    }

    public String getObjectiveId() {
        return this.objectiveId;
    }
}
