package cn.nukkit.network.process.processor;

import cn.nukkit.PlayerHandle;
import cn.nukkit.ddui.DataDrivenScreen;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ClientboundDataDrivenUICloseScreenPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.ServerboundDataDrivenScreenClosedPacket;
import org.jetbrains.annotations.NotNull;

public class ServerboundDataDrivenScreenClosedProcessor extends DataPacketProcessor<ServerboundDataDrivenScreenClosedPacket> {

    public static final ServerboundDataDrivenScreenClosedProcessor INSTANCE = new ServerboundDataDrivenScreenClosedProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ServerboundDataDrivenScreenClosedPacket pk) {
        DataDrivenScreen screen = DataDrivenScreen.getActiveScreen(playerHandle.player);
        if (screen != null) {
            screen.removeViewer(playerHandle.player);

            ClientboundDataDrivenUICloseScreenPacket closePacket = new ClientboundDataDrivenUICloseScreenPacket();
            closePacket.formId = pk.formId;
            playerHandle.player.dataPacket(closePacket);
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.SERVERBOUND_DATA_DRIVEN_SCREEN_CLOSED_PACKET;
    }

    @Override
    public Class<? extends cn.nukkit.network.protocol.DataPacket> getPacketClass() {
        return ServerboundDataDrivenScreenClosedPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol >= ProtocolInfo.v1_26_10;
    }
}
