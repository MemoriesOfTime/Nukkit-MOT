package cn.nukkit.network.protocol;

/**
 * @since 594
 */
public class AgentAnimationPacket extends DataPacket {

    public byte animation;
    public long runtimeEntityId;

    @Override
    public byte pid() {
        return ProtocolInfo.__INTERNAL__AGENT_ANIMATION;
    }

    @Override
    public int packetId() {
        return ProtocolInfo.AGENT_ANIMATION;
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