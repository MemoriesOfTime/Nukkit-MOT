package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryTransactionProcessor extends DataPacketProcessor<InventoryTransactionPacket> {

    public static final InventoryTransactionProcessor INSTANCE = new InventoryTransactionProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull InventoryTransactionPacket pk) {
        Player player = playerHandle.player;

        if (!player.isAlive() || !player.spawned) {
            return;
        }

        player.handleInventoryTransactionPacket(pk);
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.INVENTORY_TRANSACTION_PACKET);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return InventoryTransactionPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return true;
    }
}
