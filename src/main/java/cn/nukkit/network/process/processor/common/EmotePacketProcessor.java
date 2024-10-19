package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.EmotePacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author glorydark
 * @date {2024/1/10} {12:21}
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmotePacketProcessor extends DataPacketProcessor<EmotePacket> {

    public static final EmotePacketProcessor INSTANCE = new EmotePacketProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull EmotePacket pk) {
        Player player = playerHandle.player;
        if (!player.spawned || player.getServer().getTick() - player.getLastEmote() < 20) {
            return;
        }
        player.setLastEmote(player.getServer().getTick());
        if (pk.runtimeId != player.getId()) {
            player.getServer().getLogger().warning(player.getUsername() + " tried to send EmotePacket with invalid entity id: " + pk.runtimeId + "!=" + player.getId());
            return;
        }
        player.emote(pk);
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.EMOTE_PACKET);
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_1_0;
    }
}
