package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.SoundAttachOptions;
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
     * @since v2192 v1_26_50_27
     */
    public boolean bypassListenerRangeCheck;
    /**
     * @since v2192 v1_26_50_27
     */
    public Float playbackPositionSeconds;
    /**
     * 可选播放起点延迟（秒）。
     * <p>
     * Optional playback timeline start delay in seconds.
     *
     * @since v2259 1.26.70
     */
    public Float startDelaySeconds;
    /**
     * 可选挂载目标，取代裸 Entity To Attach To 字段。
     * <p>
     * Optional attach target, replacing the bare Entity To Attach To field.
     *
     * @since v2259 1.26.70
     */
    public SoundAttachOptions attachOptions;

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
        if (protocol >= ProtocolInfo.v1_26_50_27) {
            this.putVarInt((int) this.loopCount);
            this.putBoolean(this.bypassListenerRangeCheck);
            this.putOptionalNull(this.serverSoundHandle, this::putLLong);
            this.putOptionalNull(this.playbackPositionSeconds, this::putLFloat);
            if (protocol >= ProtocolInfo.v1_26_70) {
                this.putOptionalNull(this.startDelaySeconds, this::putLFloat);
                this.putOptionalNull(this.attachOptions, SoundAttachOptions::put);
            }
        } else if (protocol >= ProtocolInfo.v1_26_40) {
            this.putVarInt((int) this.loopCount);
            this.putOptionalNull(this.serverSoundHandle, this::putLLong);
        } else if (protocol >= ProtocolInfo.v1_26_20_26) {
            this.putOptionalNull(this.serverSoundHandle, this::putLLong);
        }
    }
}
