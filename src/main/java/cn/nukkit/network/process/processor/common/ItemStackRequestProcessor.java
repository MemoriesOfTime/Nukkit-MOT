package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ItemStackRequestPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Processor for ItemStackRequestPacket
 * Handles server-authoritative inventory requests from clients
 *
 * @author Nukkit-MOT Team
 * @since v1.16.100 (protocol 407+)
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemStackRequestProcessor extends DataPacketProcessor<ItemStackRequestPacket> {

    public static final ItemStackRequestProcessor INSTANCE = new ItemStackRequestProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ItemStackRequestPacket pk) {
        Player player = playerHandle.player;

        if (!player.isAlive() || !player.spawned) {
            return;
        }

        // Only process if server authoritative inventory is enabled
        if (!player.isInventoryServerAuthoritative()) {
            return;
        }

        // Handle the requests
        if (!pk.getRequests().isEmpty()) {
            player.handleItemStackRequests(pk.getRequests());
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.ITEM_STACK_REQUEST_PACKET);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return ItemStackRequestPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        // ItemStackRequest was introduced in v1.16.100
        return protocol >= ProtocolInfo.v1_16_100;
    }
}
