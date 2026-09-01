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
 * @since v1.16.100 (protocol 419+)
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

        // ItemStackRequest is the modern (1.16.100+) authoritative inventory path
        // and must be processed regardless of the server's SAI config. Proxies
        // (e.g. WDPE) may advertise a different SAI flag to the client than the
        // backend's setting; gating on the backend flag would drop requests and
        // leave the client without an ItemStackResponse, causing it to roll back
        // legitimate container edits (e.g. chest item moves).
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
        // Protocols before v419 use the old ItemStackResponse success boolean.
        return protocol >= ProtocolInfo.v1_16_100;
    }
}
