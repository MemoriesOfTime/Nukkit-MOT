package cn.nukkit.network.process.processor.v282;

import cn.nukkit.PlayerHandle;
import cn.nukkit.event.player.PlayerLocallyInitializedEvent;
import cn.nukkit.network.process.DataPacketProcessor;
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
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.SET_LOCAL_PLAYER_AS_INITIALIZED_PACKET);
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_6_0_5;
    }
}
