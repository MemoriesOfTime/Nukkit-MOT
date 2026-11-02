package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.audio.AudioContentRegistrationEntry;
import lombok.ToString;

/**
 * @since v2225
 */
@ToString
public class ServerboundRegisterAudioContentPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SERVERBOUND_REGISTER_AUDIO_CONTENT_PACKET;

    public static final int MAX_REGISTRATIONS = 64;

    public AudioContentRegistrationEntry[] registrations = new AudioContentRegistrationEntry[0];

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
        this.registrations = this.getArray(AudioContentRegistrationEntry.class, AudioContentRegistrationEntry::read,
                MAX_REGISTRATIONS, "audio content registration count");
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(this.registrations.length);
        for (AudioContentRegistrationEntry entry : this.registrations) {
            AudioContentRegistrationEntry.write(this, entry);
        }
    }
}
