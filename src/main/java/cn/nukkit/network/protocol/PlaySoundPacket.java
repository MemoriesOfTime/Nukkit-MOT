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
    /**
     * v2192 起在 loopCount 与 serverSoundHandle 之间插入 / inserted between loopCount and serverSoundHandle since v2192
     */
    public boolean bypassListenerRangeCheck;
    /**
     * @since v2192 v1_26_50
     */
    public Float playbackPositionSeconds;

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
        if (protocol >= ProtocolInfo.v1_26_50) {
            this.putUnsignedVarInt(this.loopCount);
            this.putBoolean(this.bypassListenerRangeCheck);
            this.putOptionalNull(this.serverSoundHandle, this::putLLong);
            this.putOptionalNull(this.playbackPositionSeconds, this::putLFloat);
        } else if (protocol >= ProtocolInfo.v1_26_40) {
            this.putUnsignedVarInt(this.loopCount);
            this.putOptionalNull(this.serverSoundHandle, this::putLLong);
        } else if (protocol >= ProtocolInfo.v1_26_20_26) {
            this.putOptionalNull(this.serverSoundHandle, this::putLLong);
        }
    }
}
