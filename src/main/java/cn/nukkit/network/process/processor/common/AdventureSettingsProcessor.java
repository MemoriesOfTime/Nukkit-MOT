package cn.nukkit.network.process.processor.common;

import cn.nukkit.AdventureSettings;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.event.player.PlayerKickEvent;
import cn.nukkit.event.player.PlayerToggleFlightEvent;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.AdventureSettingsPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdventureSettingsProcessor extends DataPacketProcessor<AdventureSettingsPacket> {

    public static final AdventureSettingsProcessor INSTANCE = new AdventureSettingsProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull AdventureSettingsPacket pk) {
        Player player = playerHandle.player;
        if (pk.entityUniqueId != player.getId()) {
            return;
        }
        if (!player.getServer().getAllowFlight() && pk.getFlag(AdventureSettingsPacket.FLYING) && !player.getAdventureSettings().get(AdventureSettings.Type.ALLOW_FLIGHT)
                || pk.getFlag(AdventureSettingsPacket.NO_CLIP) && !player.getAdventureSettings().get(AdventureSettings.Type.NO_CLIP)) {
            player.kick(PlayerKickEvent.Reason.FLYING_DISABLED, "Flying is not enabled on player server", true, "type=AdventureSettingsPacket, flags=ALLOW_FLIGHT: " + pk.getFlag(AdventureSettingsPacket.ALLOW_FLIGHT) + ", FLYING: " + pk.getFlag(AdventureSettingsPacket.ALLOW_FLIGHT));
            return;
        }
        PlayerToggleFlightEvent playerToggleFlightEvent = new PlayerToggleFlightEvent(player, pk.getFlag(AdventureSettingsPacket.FLYING));
        if (player.isSpectator()) {
            playerToggleFlightEvent.setCancelled();
        }
        player.getServer().getPluginManager().callEvent(playerToggleFlightEvent);
        if (playerToggleFlightEvent.isCancelled()) {
            player.getAdventureSettings().update();
        } else {
            player.getAdventureSettings().set(AdventureSettings.Type.FLYING, playerToggleFlightEvent.isFlying());
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.ADVENTURE_SETTINGS_PACKET);
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_2_0 && protocol < ProtocolInfo.v1_19_30_23;
    }
}
