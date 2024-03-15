package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.event.player.PlayerServerSettingsRequestEvent;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.ServerSettingsRequestPacket;
import cn.nukkit.network.protocol.ServerSettingsResponsePacket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

/**
 * @author glorydark
 * @date {2024/1/10} {12:36}
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServerSettingsRequestProcessor extends DataPacketProcessor<ServerSettingsRequestPacket> {

    public static final ServerSettingsRequestProcessor INSTANCE = new ServerSettingsRequestProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ServerSettingsRequestPacket pk) {
        Player player = playerHandle.player;
        PlayerServerSettingsRequestEvent settingsRequestEvent = new PlayerServerSettingsRequestEvent(player, new HashMap<>(player.getServerSettings()));
        player.getServer().getPluginManager().callEvent(settingsRequestEvent);

        if (!settingsRequestEvent.isCancelled()) {
            settingsRequestEvent.getSettings().forEach((id, window) -> {
                ServerSettingsResponsePacket re = new ServerSettingsResponsePacket();
                re.formId = id;
                re.data = window.getJSONData();
                player.dataPacket(re);
            });
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.SERVER_SETTINGS_REQUEST_PACKET);
    }
}
