package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.store.ClientStoreEntrypointConfiguration;
import lombok.ToString;

/**
 * Sent by the server to provide ClientStoreEntryPointConfiguration to the client.
 *
 * @since v975
 */
@ToString
public class ServerStoreInfoPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SERVER_STORE_INFO_PACKET;

    public ClientStoreEntrypointConfiguration store;

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
        this.store = this.getOptional(null, (s) -> {
            String storeId = s.getString();
            String storeName = s.getString();
            return new ClientStoreEntrypointConfiguration(storeId, storeName);
        });
    }

    @Override
    public void encode() {
        this.reset();
        this.putOptionalNull(this.store, (s) -> {
            this.putString(s.getStoreId());
            this.putString(s.getStoreName());
        });
    }
}
