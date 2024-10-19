package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.event.player.PlayerCommandPreprocessEvent;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.CommandRequestPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommandRequestProcessor extends DataPacketProcessor<CommandRequestPacket> {

    public static final CommandRequestProcessor INSTANCE = new CommandRequestProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull CommandRequestPacket pk) {
        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive()) {
            return;
        }
        player.craftingType = Player.CRAFTING_SMALL;
        PlayerCommandPreprocessEvent playerCommandPreprocessEvent = new PlayerCommandPreprocessEvent(player, pk.command + ' ');
        player.getServer().getPluginManager().callEvent(playerCommandPreprocessEvent);
        if (playerCommandPreprocessEvent.isCancelled()) {
            return;
        }

        player.getServer().dispatchCommand(playerCommandPreprocessEvent.getPlayer(), playerCommandPreprocessEvent.getMessage().substring(1));

    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.COMMAND_REQUEST_PACKET);
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_1_0;
    }
}
