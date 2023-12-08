package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector3f;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(doNotUseGetters = true)
@ToString(doNotUseGetters = true)
public class ServerPostMovePositionPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SERVER_POST_MOVE_POSITION;

    public Vector3f position;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.position = this.getVector3f();
    }

    @Override
    public void encode() {
        this.reset();
        this.putVector3f(this.position);
    }
}
