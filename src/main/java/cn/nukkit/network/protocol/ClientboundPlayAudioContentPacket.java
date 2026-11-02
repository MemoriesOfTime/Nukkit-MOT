package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @since v2225
 */
@ToString
public class ClientboundPlayAudioContentPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_PLAY_AUDIO_CONTENT_PACKET;

    public String sharedMetadataJwt = "";
    public String playbackContentJwt = "";
    public String playbackType = "";
    public String soundName = "";
    public int x;
    public int y;
    public int z;
    public float volume;
    public float pitch;
    public int loopCount;
    public boolean bypassListenerRangeCheck;
    public Long serverSoundHandle;
    public Float playbackPositionSeconds;

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.sharedMetadataJwt);
        this.putString(this.playbackContentJwt);
        this.putString(this.playbackType);
        this.putString(this.soundName);
        this.putBlockVector3(this.x << 3, this.y << 3, this.z << 3);
        this.putLFloat(this.volume);
        this.putLFloat(this.pitch);
        this.putVarInt(this.loopCount);
        this.putBoolean(this.bypassListenerRangeCheck);
        this.putOptionalNull(this.serverSoundHandle, this::putLLong);
        this.putOptionalNull(this.playbackPositionSeconds, this::putLFloat);
    }
}
