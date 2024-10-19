package cn.nukkit.network.process.processor.common;

import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ClientToServerHandshakePacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientToServerHandshakeProcessor extends DataPacketProcessor<ClientToServerHandshakePacket> {

    public static final ClientToServerHandshakeProcessor INSTANCE = new ClientToServerHandshakeProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ClientToServerHandshakePacket pk) {
        if (playerHandle.player.isEnableNetworkEncryption()) {
            if (!playerHandle.isAwaitingEncryptionHandshake()) {
                playerHandle.player.close("Invalid encryption handshake");
                return;
            }

            playerHandle.setAwaitingEncryptionHandshake(false);
            playerHandle.processPreLogin();
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.CLIENT_TO_SERVER_HANDSHAKE_PACKET);
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_1_0;
    }
}
