package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.Server;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.event.player.PlayerChangeSkinEvent;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.PlayerSkinPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * @author LT_Name
 */
public class PlayerSkinProcessor extends DataPacketProcessor<PlayerSkinPacket> {

    public static final PlayerSkinProcessor INSTANCE = new PlayerSkinProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull PlayerSkinPacket pk) {
        Skin skin = pk.skin;
        Player player = playerHandle.player;

        if (!skin.isValid()) {
            player.getServer().getLogger().warning(playerHandle.getUsername() + ": PlayerSkinPacket with invalid skin");
            return;
        }

        PlayerChangeSkinEvent playerChangeSkinEvent = new PlayerChangeSkinEvent(player, skin);
        if (TimeUnit.SECONDS.toMillis(player.getServer().getPlayerSkinChangeCooldown()) > System.currentTimeMillis() - player.lastSkinChange) {
            playerChangeSkinEvent.setCancelled(true);
            Server.getInstance().getLogger().warning("Player " + playerHandle.getUsername() + " change skin too quick!");
        }
        player.getServer().getPluginManager().callEvent(playerChangeSkinEvent);
        if (!playerChangeSkinEvent.isCancelled()) {
            player.lastSkinChange = System.currentTimeMillis();
            player.setSkin(skin.isPersona() && !player.getServer().personaSkins ? Skin.NO_PERSONA_SKIN : skin);
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.PLAYER_SKIN_PACKET);
    }
}
