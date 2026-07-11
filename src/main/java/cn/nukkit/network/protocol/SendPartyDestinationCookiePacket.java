package cn.nukkit.network.protocol;

import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

/**
 * Sent by the server to a client with a party destination cookie.
 *
 * @since v1001
 */
@ToString
public class SendPartyDestinationCookiePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SEND_PARTY_DESTINATION_COOKIE_PACKET;

    public String cookie;
    public Intent intent;
    public String destinationName;

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
        this.cookie = this.getString();
        this.intent = Intent.fromName(this.getString());
        this.destinationName = this.getString();
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.cookie != null ? this.cookie : "");
        this.putString(this.intent != null ? this.intent.getSerializeName() : "");
        this.putString(this.destinationName != null ? this.destinationName : "");
    }

    public enum Intent {
        NOTIFY("notify"),
        OPT_IN("optin"),
        OPT_OUT("optout");

        private static final Map<String, Intent> SERIALIZE_NAMES = new HashMap<>(values().length);

        static {
            for (Intent value : values()) {
                SERIALIZE_NAMES.put(value.getSerializeName(), value);
            }
        }

        private final String serializeName;

        Intent(String serializeName) {
            this.serializeName = serializeName;
        }

        public String getSerializeName() {
            return this.serializeName;
        }

        public static Intent fromName(String serializeName) {
            return SERIALIZE_NAMES.get(serializeName);
        }
    }
}
