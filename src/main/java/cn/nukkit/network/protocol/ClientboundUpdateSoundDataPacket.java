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

    /**
     * v2168+ 的 SoundDataEvent 类型标记 / SoundDataEvent tags since v2168
     */
    private static final int SOUND_STOP = 0;
    private static final int SOUND_SET_VOLUME = 1;
    private static final int SOUND_SET_PITCH = 2;
    private static final int SOUND_FADE = 3;
    private static final int SOUND_SEEK_TO = 4;
    private static final int SOUND_PAUSE = 5;
    private static final int SOUND_RESUME = 6;

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
            this.stop = this.readSoundSlot().type() == SOUND_STOP;
            SoundSlot slot = this.readSoundSlot();
            this.volume = slot.type() == SOUND_SET_VOLUME ? slot.first() : null;
            slot = this.readSoundSlot();
            this.pitch = slot.type() == SOUND_SET_PITCH ? slot.first() : null;
            slot = this.readSoundSlot();
            this.fadeDuration = slot.type() == SOUND_FADE ? slot.first() : 0;
            this.fadeTargetVolume = slot.type() == SOUND_FADE ? slot.second() : null;
            slot = this.readSoundSlot();
            this.seekToSeconds = slot.type() == SOUND_SEEK_TO ? slot.first() : null;
            this.pause = this.readSoundSlot().type() == SOUND_PAUSE;
            this.resume = this.readSoundSlot().type() == SOUND_RESUME;
        } else {
            this.type = this.getString();
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putLLong(this.serverSoundHandle);
        if (this.protocol >= ProtocolInfo.v1_26_40) {
            int tag = SOUND_STOP;
            Float first = null;
            Float second = null;
            if (!this.stop) {
                if (this.volume != null) {
                    tag = SOUND_SET_VOLUME;
                    first = this.volume;
                } else if (this.pitch != null) {
                    tag = SOUND_SET_PITCH;
                    first = this.pitch;
                } else if (this.fadeTargetVolume != null) {
                    tag = SOUND_FADE;
                    first = this.fadeDuration;
                    second = this.fadeTargetVolume;
                } else if (this.seekToSeconds != null) {
                    tag = SOUND_SEEK_TO;
                    first = this.seekToSeconds;
                } else if (this.pause) {
                    tag = SOUND_PAUSE;
                } else if (this.resume) {
                    tag = SOUND_RESUME;
                }
            }
            for (int i = 0; i < 7; i++) {
                this.putUnsignedVarInt(tag);
                if (first != null) {
                    this.putLFloat(first);
                }
                if (second != null) {
                    this.putLFloat(second);
                }
            }
        } else {
            this.putString(this.type != null ? this.type : "stop");
        }
    }

    /**
     * 读取一个槽位：类型 uvarint + 按类型的负载；fade 为 duration 在前。
     * <p>
     * Reads one slot: type uvarint + type-specific payload; fade carries duration first.
     */
    private SoundSlot readSoundSlot() {
        int type = (int) this.getUnsignedVarInt();
        return switch (type) {
            case SOUND_SET_VOLUME, SOUND_SET_PITCH, SOUND_SEEK_TO -> new SoundSlot(type, this.getLFloat(), 0);
            case SOUND_FADE -> new SoundSlot(type, this.getLFloat(), this.getLFloat());
            default -> new SoundSlot(type, 0, 0);
        };
    }

    private record SoundSlot(int type, float first, float second) {
    }
}
