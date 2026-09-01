package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class PlaySoundPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.PLAY_SOUND_PACKET;

    public String name;
    public int x;
    public int y;
    public int z;
    public float volume;
    public float pitch;
    /**
     * @since v975
     */
    public Long serverSoundHandle;
    /**
     * @since v2168 v1_26_40
     */
    public long loopCount;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.name);
        this.putBlockVector3(this.x << 3, this.y << 3, this.z << 3);
        this.putLFloat(this.volume);
        this.putLFloat(this.pitch);
        if (protocol >= ProtocolInfo.v1_26_40) {
            this.putUnsignedVarInt(this.loopCount);
            this.putOptionalNull(this.serverSoundHandle, this::putLLong);
        } else if (protocol >= ProtocolInfo.v1_26_20_26) {
            this.putOptionalNull(this.serverSoundHandle, this::putLLong);
        }
    }
}
