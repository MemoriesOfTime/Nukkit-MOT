package cn.nukkit.network.process.processor.v282;

import cn.nukkit.GameVersion;
import cn.nukkit.PlayerHandle;
import cn.nukkit.event.player.PlayerLocallyInitializedEvent;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.SetLocalPlayerAsInitializedPacket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SetLocalPlayerAsInitializedProcessor_v282 extends DataPacketProcessor<SetLocalPlayerAsInitializedPacket> {

    public static final SetLocalPlayerAsInitializedProcessor_v282 INSTANCE = new SetLocalPlayerAsInitializedProcessor_v282();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull SetLocalPlayerAsInitializedPacket pk) {
        if (playerHandle.player.locallyInitialized) {
            return;
        }

        playerHandle.doFirstSpawn();

        playerHandle.player.getServer().getPluginManager().callEvent(new PlayerLocallyInitializedEvent(playerHandle.player));

        // 收尾（本处理器的事件）完成后才发布接管，世界线程不与主线程并发操作该玩家
        // Publish the handover only after this finalization event so the level thread
        // cannot race the primary thread's remaining work on the player
        playerHandle.player.publishSpawnInitCompleted();
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.SET_LOCAL_PLAYER_AS_INITIALIZED_PACKET);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return SetLocalPlayerAsInitializedPacket.class;
    }

    @Override
    public boolean isSupported(GameVersion gameVersion) {
        return gameVersion.getProtocol() >= ProtocolInfo.v1_6_0_5;
    }
}
