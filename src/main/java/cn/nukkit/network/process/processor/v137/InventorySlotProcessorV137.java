package cn.nukkit.network.process.processor.v137;

import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.InventorySlotPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
public class InventorySlotProcessorV137 extends DataPacketProcessor<InventorySlotPacket> {

    public static final InventorySlotProcessorV137 INSTANCE = new InventorySlotProcessorV137();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull InventorySlotPacket pk) {
        //覆盖数据包id一样的ContainerSetSlotProcessorV113
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.INVENTORY_SLOT_PACKET);
    }
}
