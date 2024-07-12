package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector3f;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(doNotUseGetters = true, callSuper = false)
@ToString(doNotUseGetters = true)
public class UpdateClientInputLocksPacket extends DataPacket {

    public static final int FLAG_CAMERA = 1 << 1;
    public static final int FLAG_MOVEMENT = 1 << 2;

    public int lockComponentData;
    public Vector3f serverPosition;


    @Override
    public byte pid() {
        return ProtocolInfo.UPDATE_CLIENT_INPUT_LOCKS;
    }

    @Override
    public void decode() {
        this.lockComponentData = (int) this.getUnsignedVarInt();
        this.serverPosition = this.getVector3f();
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(lockComponentData);
        this.putVector3f(serverPosition);
    }
}