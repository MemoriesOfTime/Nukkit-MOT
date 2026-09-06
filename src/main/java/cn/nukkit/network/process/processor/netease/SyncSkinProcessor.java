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

/**
 * 处理网易客户端 C→S SyncSkinPacket（皮肤变更通知）。反汇编证实该包无确认语义
 * （300s 超时仅日志+丢弃条目），故不回显——应用皮肤并经 setSkin 广播即可。
 * <p>
 * Handles the NetEase C→S SyncSkinPacket. Disassembly shows no ack semantics (the
 * 300s timeout only logs and drops the entry), so no echo — apply and let setSkin broadcast.
 */
@OnlyNetEase
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncSkinProcessor extends DataPacketProcessor<SyncSkinPacket> {

    public static final SyncSkinProcessor INSTANCE = new SyncSkinProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull SyncSkinPacket pk) {
        Player player = playerHandle.player;
        Skin skin = pk.skin;

        if (!skin.isValid()) {
            // 软丢弃而非断开：线格式异常不应踢掉换肤玩家
            player.getServer().getLogger().warning(playerHandle.getUsername() + ": SyncSkinPacket with invalid skin, ignored");
            return;
        }

        PlayerChangeSkinEvent playerChangeSkinEvent = new PlayerChangeSkinEvent(player, skin);
        if (TimeUnit.SECONDS.toMillis(player.getServer().getPlayerSkinChangeCooldown()) > System.currentTimeMillis() - player.lastSkinChange) {
            playerChangeSkinEvent.setCancelled(true);
            Server.getInstance().getLogger().warning("Player " + playerHandle.getUsername() + " change skin too quick! (SyncSkinPacket)");
        }
        player.getServer().getPluginManager().callEvent(playerChangeSkinEvent);

        if (!playerChangeSkinEvent.isCancelled()) {
            player.lastSkinChange = System.currentTimeMillis();
            player.setSkin(skin.isPersona() && !player.getServer().personaSkins ? Skin.NO_PERSONA_SKIN : skin);
        }
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
        return protocol == GameVersion.V1_21_124_NETEASE.getProtocol()
                || protocol == GameVersion.V1_21_93_NETEASE.getProtocol();
    }
}
