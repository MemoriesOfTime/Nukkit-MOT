package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.Server;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.event.player.PlayerChangeSkinEvent;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.PlayerSkinPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerSkinProcessor extends DataPacketProcessor<PlayerSkinPacket> {

    public static final PlayerSkinProcessor INSTANCE = new PlayerSkinProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull PlayerSkinPacket pk) {
        Skin skin = pk.skin;
        Player player = playerHandle.player;

        // 上传包不携带归属字段，只继承服务端当前账号，客户端对象自带的 udid 不可采信
        // Skin uploads carry no ownership; inherit only the server-side current owner,
        // never a udid supplied on the client object
        Skin previousSkin = player.getSkin();
        skin.setUdid(previousSkin != null ? previousSkin.getUdid() : "");

        if (!skin.isValid()) {
            player.getServer().getLogger().warning(playerHandle.getUsername() + ": PlayerSkinPacket with invalid skin");
            player.close("", "disconnectionScreen.invalidSkin");
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
            player.setSkin(skin.isPersona() && !player.getServer().personaSkins ? skin.getDefaultSkin() : skin);
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.PLAYER_SKIN_PACKET);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return PlayerSkinPacket.class;
    }

    @Override
    public boolean isSupported(GameVersion gameVersion) {
        return gameVersion.getProtocol() >= ProtocolInfo.v1_1_0;
    }
}
