package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.DisplaySlot;
import cn.nukkit.network.protocol.types.SortOrder;
import lombok.ToString;

@ToString
public class SetDisplayObjectivePacket extends DataPacket {

    public DisplaySlot displaySlot;
    public String objectiveId;
    public String displayName;
    public String criteria;
    public SortOrder sortOrder;

    @Override
    public byte pid() {
        return ProtocolInfo.SET_DISPLAY_OBJECTIVE_PACKET;
    }

    @Override
    public void decode() {
        //only server -> client
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.displaySlot.getSlotName());
        this.putString(this.objectiveId);
        this.putString(this.displayName);
        this.putString(this.criteria);
        this.putVarInt(this.sortOrder.ordinal());
    }
}
