package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.PlayerHotbarPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.ContainerIds;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author glorydark
 * @date {2024/1/10} {12:40}
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerHotbarProcessor extends DataPacketProcessor<PlayerHotbarPacket> {

    public static final PlayerHotbarProcessor INSTANCE = new PlayerHotbarProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull PlayerHotbarPacket pk) {
        Player player = playerHandle.player;
        if (pk.windowId != ContainerIds.INVENTORY) {
            return;
        }

        player.getInventory().equipItem(pk.selectedHotbarSlot);
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.PLAYER_HOTBAR_PACKET);
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_1_0;
    }
}
