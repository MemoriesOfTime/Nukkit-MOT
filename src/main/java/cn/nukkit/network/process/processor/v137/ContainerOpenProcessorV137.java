package cn.nukkit.network.process.processor.v137;

import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ContainerOpenPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
public class ContainerOpenProcessorV137 extends DataPacketProcessor<ContainerOpenPacket> {

    public static final ContainerOpenProcessorV137 INSTANCE = new ContainerOpenProcessorV137();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ContainerOpenPacket pk) {
        //覆盖数据包id一样的DropItemProcessorV113
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ContainerOpenPacket.NETWORK_ID);
    }

}
