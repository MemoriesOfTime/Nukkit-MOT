package cn.nukkit.network.protocol;

import cn.nukkit.utils.BinaryStream;
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
            // v1001 引入 optional 名称 + richPresenceId；CB 的 v2168 helper 继承 v975，2168 回退为普通字符串
            // v1001 added optional names + richPresenceId; CB's v2168 helper extends v975, so 2168 reverts to plain strings
            if (this.protocol >= ProtocolInfo.v1_26_30 && this.protocol < ProtocolInfo.v1_26_40) {
                String experienceName = s.getOptional(null, BinaryStream::getString);
                String worldName = s.getOptional(null, BinaryStream::getString);
                String richPresenceId = s.getString();
                return new PresenceConfiguration(experienceName, worldName, richPresenceId);
            }
            return new PresenceConfiguration(s.getString(), s.getString(), null);
        });
    }

    @Override
    public void encode() {
        this.reset();
        this.putOptionalNull(this.presenceConfiguration, (s) -> {
            if (this.protocol >= ProtocolInfo.v1_26_30 && this.protocol < ProtocolInfo.v1_26_40) {
                this.putOptionalNull(s.getExperienceName(), this::putString);
                this.putOptionalNull(s.getWorldName(), this::putString);
                this.putString(s.getRichPresenceId() != null ? s.getRichPresenceId() : "");
            } else {
                this.putString(s.getExperienceName());
                this.putString(s.getWorldName());
            }
        });
    }

    public static class PresenceConfiguration {
        private final String experienceName;
        private final String worldName;
        /**
         * @since v1_26_30
         */
        private final String richPresenceId;

        public PresenceConfiguration(String experienceName, String worldName) {
            this(experienceName, worldName, null);
        }

        public PresenceConfiguration(String experienceName, String worldName, String richPresenceId) {
            this.experienceName = experienceName;
            this.worldName = worldName;
            this.richPresenceId = richPresenceId;
        }

        public String getExperienceName() {
            return experienceName;
        }

        public String getWorldName() {
            return worldName;
        }

        public String getRichPresenceId() {
            return richPresenceId;
        }
    }
}
