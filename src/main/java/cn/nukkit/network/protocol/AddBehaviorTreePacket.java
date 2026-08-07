package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class AddBehaviorTreePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ADD_BEHAVIOR_TREE_PACKET;

    public String behaviorTreeJson;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.behaviorTreeJson = this.getString();
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(behaviorTreeJson);
    }
}
