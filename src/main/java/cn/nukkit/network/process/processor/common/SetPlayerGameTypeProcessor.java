package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.event.player.PlayerKickEvent;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.SetPlayerGameTypePacket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author glorydark
 * @date {2024/1/10} {12:49}
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SetPlayerGameTypeProcessor extends DataPacketProcessor<SetPlayerGameTypePacket> {

    public static final SetPlayerGameTypeProcessor INSTANCE = new SetPlayerGameTypeProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull SetPlayerGameTypePacket pk) {
        Player player = playerHandle.player;
        if (pk.gamemode != player.gamemode) {
            if (!player.hasPermission("nukkit.command.gamemode")) {
                player.kick(PlayerKickEvent.Reason.INVALID_PACKET, "Invalid SetPlayerGameTypePacket", true, "type=SetPlayerGameTypePacket");
                /*SetPlayerGameTypePacket setPlayerGameTypePacket1 = new SetPlayerGameTypePacket();
                setPlayerGameTypePacket1.gamemode = this.gamemode & 0x01;
                this.dataPacket(setPlayerGameTypePacket1);
                this.adventureSettings.update();*/
                return;
            }
            player.setGamemode(pk.gamemode, true);
            Command.broadcastCommandMessage(player, new TranslationContainer("commands.gamemode.success.self", Server.getGamemodeString(player.gamemode)));
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.SET_PLAYER_GAME_TYPE_PACKET);
    }
}
