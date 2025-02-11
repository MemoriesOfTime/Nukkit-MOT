package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.MovementEffectType;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MovementEffectPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.MOVEMENT_EFFECT_PACKET;

    public long targetRuntimeID;
    public MovementEffectType effectType;
    public int effectDuration;
    public long tick;

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
        //packet is client bounded
    }

    @Override
    public void encode() {
        this.putUnsignedVarLong(this.targetRuntimeID);
        this.putUnsignedVarInt(this.effectType.getId());
        this.putUnsignedVarInt(this.effectDuration);
        this.putUnsignedVarLong(this.tick);
    }

}