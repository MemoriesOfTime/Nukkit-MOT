package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Sent to update sound data.
 *
 * @since v1001
 */
@ToString
public class ClientboundUpdateSoundDataPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_UPDATE_SOUND_DATA_PACKET;

    public long serverSoundHandle;
    public String type;

    /**
     * @since v2168 v1_26_40
     */
    public Float volume;
    /**
     * @since v2168 v1_26_40
     */
    public Float pitch;
    /**
     * @since v2168 v1_26_40
     */
    public Float fadeTargetVolume;
    /**
     * @since v2168 v1_26_40
     */
    public float fadeDuration;
    /**
     * @since v2168 v1_26_40
     */
    public Float seekToSeconds;
    /**
     * @since v2168 v1_26_40
     */
    public boolean stop;
    /**
     * @since v2168 v1_26_40
     */
    public boolean pause;
    /**
     * @since v2168 v1_26_40
     */
    public boolean resume;

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
        this.serverSoundHandle = this.getLLong();
        if (this.protocol >= ProtocolInfo.v1_26_40) {
            this.stop = this.getBoolean();
            if (this.stop) {
                this.getUnsignedVarInt();
            }
            if (this.getBoolean()) {
                this.getUnsignedVarInt();
                this.volume = this.getLFloat();
            }
            if (this.getBoolean()) {
                this.getUnsignedVarInt();
                this.pitch = this.getLFloat();
            }
            if (this.getBoolean()) {
                this.getUnsignedVarInt();
                this.fadeTargetVolume = this.getLFloat();
                this.fadeDuration = this.getLFloat();
            }
            if (this.getBoolean()) {
                this.getUnsignedVarInt();
                this.seekToSeconds = this.getLFloat();
            }
            this.pause = this.getBoolean();
            if (this.pause) {
                this.getUnsignedVarInt();
            }
            this.resume = this.getBoolean();
            if (this.resume) {
                this.getUnsignedVarInt();
            }
        } else {
            this.type = this.getString();
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putLLong(this.serverSoundHandle);
        if (this.protocol >= ProtocolInfo.v1_26_40) {
            this.putBoolean(this.stop);
            if (this.stop) {
                this.putUnsignedVarInt(0);
            }
            this.putBoolean(this.volume != null);
            if (this.volume != null) {
                this.putUnsignedVarInt(0);
                this.putLFloat(this.volume);
            }
            this.putBoolean(this.pitch != null);
            if (this.pitch != null) {
                this.putUnsignedVarInt(0);
                this.putLFloat(this.pitch);
            }
            this.putBoolean(this.fadeTargetVolume != null);
            if (this.fadeTargetVolume != null) {
                this.putUnsignedVarInt(0);
                this.putLFloat(this.fadeTargetVolume);
                this.putLFloat(this.fadeDuration);
            }
            this.putBoolean(this.seekToSeconds != null);
            if (this.seekToSeconds != null) {
                this.putUnsignedVarInt(0);
                this.putLFloat(this.seekToSeconds);
            }
            this.putBoolean(this.pause);
            if (this.pause) {
                this.putUnsignedVarInt(0);
            }
            this.putBoolean(this.resume);
            if (this.resume) {
                this.putUnsignedVarInt(0);
            }
        } else {
            this.putString(this.type != null ? this.type : "");
        }
    }
}
