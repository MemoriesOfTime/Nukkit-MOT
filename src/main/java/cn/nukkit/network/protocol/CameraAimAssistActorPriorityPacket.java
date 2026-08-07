package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.camera.AimAssistActorPriorityData;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Camera aim-assist actor priority data sent from the server to clients.
 * @since v924
 */
@ToString
public class CameraAimAssistActorPriorityPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CAMERA_AIM_ASSIST_ACTOR_PRIORITY_PACKET;

    public List<AimAssistActorPriorityData> priorities = new ArrayList<>();

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
        int count = (int) this.getUnsignedVarInt();
        this.priorities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int presetIndex = this.getLInt();
            int categoryIndex = this.getLInt();
            int actorIndex = this.getLInt();
            int priorityValue = this.getLInt();
            this.priorities.add(new AimAssistActorPriorityData(presetIndex, categoryIndex, actorIndex, priorityValue));
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(this.priorities.size());
        for (AimAssistActorPriorityData priority : this.priorities) {
            this.putLInt(priority.getPresetIndex());
            this.putLInt(priority.getCategoryIndex());
            this.putLInt(priority.getActorIndex());
            this.putLInt(priority.getPriorityValue());
        }
    }
}
