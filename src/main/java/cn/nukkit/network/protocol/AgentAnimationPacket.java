package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @since 594
 */
@ToString
public class AgentAnimationPacket extends DataPacket {
    public static final int NETWORK_ID = ProtocolInfo.AGENT_ANIMATION_PACKET;
    public final byte TYPE_ARM_SWING = 0;
    public final byte TYPE_SHRUG = 1;
    public byte animation;
    public long runtimeEntityId;

    @Override
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.animation = (byte) this.getByte();
        this.runtimeEntityId = this.getEntityRuntimeId();
    }

    @Override
    public void encode() {
        this.putByte(this.animation);
        this.putEntityRuntimeId(this.runtimeEntityId);
    }
}