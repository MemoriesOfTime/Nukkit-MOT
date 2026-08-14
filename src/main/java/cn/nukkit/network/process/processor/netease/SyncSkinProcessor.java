package cn.nukkit.network.process.processor.netease;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.Server;
import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.event.player.PlayerChangeSkinEvent;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.netease.SyncSkinPacket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

@OnlyNetEase
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncSkinProcessor extends DataPacketProcessor<SyncSkinPacket> {

    public static final SyncSkinProcessor INSTANCE = new SyncSkinProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull SyncSkinPacket pk) {
        Player player = playerHandle.player;
        Skin skin = pk.skin;

        if (!skin.isValid()) {
            player.getServer().getLogger().warning(playerHandle.getUsername() + ": SyncSkinPacket with invalid skin");
            player.close("", "disconnectionScreen.invalidSkin");
            return;
        }

        PlayerChangeSkinEvent playerChangeSkinEvent = new PlayerChangeSkinEvent(player, skin);
        if (TimeUnit.SECONDS.toMillis(player.getServer().getPlayerSkinChangeCooldown()) > System.currentTimeMillis() - player.lastSkinChange) {
            playerChangeSkinEvent.setCancelled(true);
            Server.getInstance().getLogger().warning("Player " + playerHandle.getUsername() + " change skin too quick! (SyncSkinPacket)");
        }
        player.getServer().getPluginManager().callEvent(playerChangeSkinEvent);

        Skin appliedSkin = skin;
        if (playerChangeSkinEvent.isCancelled()) {
            appliedSkin = player.getSkin();
        } else {
            player.lastSkinChange = System.currentTimeMillis();
            player.setSkin(skin.isPersona() && !player.getServer().personaSkins ? Skin.NO_PERSONA_SKIN : skin);
        }

        this.sendSelfSyncSkin(player, appliedSkin);
    }

    private void sendSelfSyncSkin(Player sender, Skin skin) {
        SyncSkinPacket echo = new SyncSkinPacket();
        echo.setSkin(skin);
        SyncSkinPacket.SyncSkinEntry entry = new SyncSkinPacket.SyncSkinEntry();
        entry.flag = true;
        entry.uuid = sender.getUniqueId();
        echo.addEntry(entry);
        sender.dataPacket(echo);
        sender.getServer().getLogger().debug("[SyncSkin] echoed SyncSkinPacket to self sender=" + sender.getName());
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.PACKET_SYNC_SKIN;
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return SyncSkinPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol == GameVersion.V1_21_124_NETEASE.getProtocol();
    }
}
