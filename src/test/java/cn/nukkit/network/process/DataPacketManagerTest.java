package cn.nukkit.network.process;

import cn.nukkit.network.protocol.ItemStackRequestPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataPacketManagerTest {

    @Test
    void itemStackRequestProcessorStartsAtV116100() {
        DataPacketManager.registerDefaultProcessors();

        assertFalse(DataPacketManager.canProcess(ProtocolInfo.v1_16_0, ItemStackRequestPacket.class));
        assertFalse(DataPacketManager.canProcess(ProtocolInfo.v1_16_100_52, ItemStackRequestPacket.class));
        assertTrue(DataPacketManager.canProcess(ProtocolInfo.v1_16_100, ItemStackRequestPacket.class));
    }
}
