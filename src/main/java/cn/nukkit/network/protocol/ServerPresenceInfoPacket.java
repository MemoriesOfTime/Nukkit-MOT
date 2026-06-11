package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Sent by the server to provide PresenceConfiguration to the client.
 *
 * @since v975
 */
@ToString
public class ServerPresenceInfoPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SERVER_PRESENCE_INFO_PACKET;

    public PresenceConfiguration presenceConfiguration;

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
        this.presenceConfiguration = this.getOptional(null, (s) -> {
            String experienceName = s.getString();
            String worldName = s.getString();
            return new PresenceConfiguration(experienceName, worldName);
        });
    }

    @Override
    public void encode() {
        this.reset();
        this.putOptionalNull(this.presenceConfiguration, (s) -> {
            this.putString(s.getExperienceName());
            this.putString(s.getWorldName());
        });
    }

    public static class PresenceConfiguration {
        private final String experienceName;
        private final String worldName;

        public PresenceConfiguration(String experienceName, String worldName) {
            this.experienceName = experienceName;
            this.worldName = worldName;
        }

        public String getExperienceName() {
            return experienceName;
        }

        public String getWorldName() {
            return worldName;
        }
    }
}
