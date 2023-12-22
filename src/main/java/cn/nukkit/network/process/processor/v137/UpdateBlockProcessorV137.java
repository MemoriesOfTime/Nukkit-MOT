package cn.nukkit.network.process.processor.v137;

import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
public class UpdateBlockProcessorV137 extends DataPacketProcessor<UpdateBlockPacket> {

    public static final UpdateBlockProcessorV137 INSTANCE = new UpdateBlockProcessorV137();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull UpdateBlockPacket pk) {
        //Do nothing
        //覆盖数据包id一样的RemoveBlockProcessorV113
    }

    @Override
    public int getPacketId() {
        return UpdateBlockPacket.NETWORK_ID;
    }
}
