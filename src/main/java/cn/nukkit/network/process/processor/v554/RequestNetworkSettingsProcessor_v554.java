package cn.nukkit.network.process.processor.v554;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.NetworkSettingsPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.RequestNetworkSettingsPacket;
import cn.nukkit.network.protocol.types.PacketCompressionAlgorithm;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestNetworkSettingsProcessor_v554 extends DataPacketProcessor<RequestNetworkSettingsPacket> {

    public static final RequestNetworkSettingsProcessor_v554 INSTANCE = new RequestNetworkSettingsProcessor_v554();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull RequestNetworkSettingsPacket pk) {
        Player player = playerHandle.player;

        if (player.raknetProtocol < 11) {
            return;
        }
        if (playerHandle.isLoginPacketReceived()) {
            log.debug("{}: got a RequestNetworkSettingsPacket but player is already logged in", player.getUsername());
            return;
        }

        player.protocol = pk.protocolVersion;

        NetworkSettingsPacket settingsPacket = new NetworkSettingsPacket();
        PacketCompressionAlgorithm algorithm;
        if (player.getServer().useSnappy && player.protocol >= ProtocolInfo.v1_19_30_23) {
            algorithm = PacketCompressionAlgorithm.SNAPPY;
        } else {
            algorithm = PacketCompressionAlgorithm.ZLIB;
        }
        settingsPacket.compressionAlgorithm = algorithm;
        settingsPacket.compressionThreshold = 1; // compress everything
        player.forceDataPacket(settingsPacket, () -> {
            playerHandle.getNetworkSession().setCompression(CompressionProvider.from(algorithm, player.raknetProtocol));
        });

        if (!ProtocolInfo.SUPPORTED_PROTOCOLS.contains(player.protocol)) {
            player.close("", "You are running unsupported Minecraft version");
            log.debug(player.getAddress() + " disconnected with unsupported protocol (SupportedProtocols) " + player.protocol);
            return;
        }
        if (player.protocol < player.getServer().minimumProtocol) {
            player.close("", "Support for this Minecraft version is not enabled");
            log.debug(player.getAddress() + " disconnected with unsupported protocol (minimumProtocol) " + player.protocol);
        } else if (player.getServer().maximumProtocol >= Math.max(0, player.getServer().minimumProtocol) && player.protocol > player.getServer().maximumProtocol) {
            player.close("", "Support for this Minecraft version is not enabled");
            log.debug(player.getAddress() + " disconnected with unsupported protocol (maximumProtocol) " + player.protocol);
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.REQUEST_NETWORK_SETTINGS_PACKET);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return RequestNetworkSettingsPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_19_30_23;
    }
}
