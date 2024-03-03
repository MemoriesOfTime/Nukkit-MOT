package cn.nukkit.network.process.processor.v527;

import cn.nukkit.AdventureSettings;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.event.player.PlayerKickEvent;
import cn.nukkit.event.player.PlayerToggleFlightEvent;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.RequestAbilityPacket;
import cn.nukkit.network.protocol.types.PlayerAbility;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class RequestAbilityProcessor_v527 extends DataPacketProcessor<RequestAbilityPacket> {

    public static final RequestAbilityProcessor_v527 INSTANCE = new RequestAbilityProcessor_v527();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull RequestAbilityPacket pk) {
        Player player = playerHandle.player;
        if (player.protocol < ProtocolInfo.v1_19_30_23
                || player.protocol >= ProtocolInfo.v1_20_30_24) { //1.20.30开始飞行切换使用PlayerAuthInputPacket/PlayerActionPacket
            return;
        }
        PlayerAbility ability = pk.getAbility();
        if (ability != PlayerAbility.FLYING) {
            log.info("[" + player.getName() + "] has tried to trigger " + ability + " ability " + (pk.isBoolValue() ? "on" : "off"));
            return;
        }

        if (!player.getServer().getAllowFlight() && pk.boolValue && !player.getAdventureSettings().get(AdventureSettings.Type.ALLOW_FLIGHT)) {
            player.kick(PlayerKickEvent.Reason.FLYING_DISABLED, "Flying is not enabled on this server");
            return;
        }

        PlayerToggleFlightEvent playerToggleFlightEvent = new PlayerToggleFlightEvent(player, pk.isBoolValue());
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
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.REQUEST_ABILITY_PACKET);
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_19_0_29;
    }
}
