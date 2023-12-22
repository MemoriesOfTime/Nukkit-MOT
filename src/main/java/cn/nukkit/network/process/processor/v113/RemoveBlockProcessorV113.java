package cn.nukkit.network.process.processor.v113;

import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.v113.RemoveBlockPacketV113;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
public class RemoveBlockProcessorV113 extends DataPacketProcessor<RemoveBlockPacketV113> {

    public static final RemoveBlockProcessorV113 INSTANCE = new RemoveBlockProcessorV113();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull RemoveBlockPacketV113 pk) {
        //TODO
    }

    @Override
    public int getPacketId() {
        return RemoveBlockPacketV113.NETWORK_ID;
    }
}
