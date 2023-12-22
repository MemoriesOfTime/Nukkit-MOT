package cn.nukkit.network.process.processor.v274;

import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.SetLocalPlayerAsInitializedPacket;
import org.jetbrains.annotations.NotNull;

public class SetLocalPlayerAsInitializedProcessorV274 extends DataPacketProcessor<SetLocalPlayerAsInitializedPacket> {

    public static final SetLocalPlayerAsInitializedProcessorV274 INSTANCE = new SetLocalPlayerAsInitializedProcessorV274();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull SetLocalPlayerAsInitializedPacket pk) {
        //Do nothing
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.SET_LOCAL_PLAYER_AS_INITIALIZED_PACKET);
    }
}
