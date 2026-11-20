package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.UpdateAttributesPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.*;

class EntityAbsorptionAttributeTest {

    private static final int[] SUPPORTED_PROTOCOLS = {
            ProtocolInfo.v1_20_0_23, ProtocolInfo.v1_20_0,
            ProtocolInfo.v1_20_10_21, ProtocolInfo.v1_20_10,
            ProtocolInfo.v1_20_30_24, ProtocolInfo.v1_20_30, ProtocolInfo.v1_20_40,
            ProtocolInfo.v1_20_50, ProtocolInfo.v1_20_60, ProtocolInfo.v1_20_70, ProtocolInfo.v1_20_80,
            ProtocolInfo.v1_21_0, ProtocolInfo.v1_21_2, ProtocolInfo.v1_21_20, ProtocolInfo.v1_21_30,
            ProtocolInfo.v1_21_40, ProtocolInfo.v1_21_50_26, ProtocolInfo.v1_21_50,
            ProtocolInfo.v1_21_60, ProtocolInfo.v1_21_70_24, ProtocolInfo.v1_21_70,
            ProtocolInfo.v1_21_80, ProtocolInfo.v1_21_90, ProtocolInfo.v1_21_93, ProtocolInfo.v1_21_100,
            ProtocolInfo.v1_21_110_26, ProtocolInfo.v1_21_111, ProtocolInfo.v1_21_120, ProtocolInfo.v1_21_124,
            ProtocolInfo.v1_21_130_28, ProtocolInfo.v1_21_130, ProtocolInfo.v1_26_0, ProtocolInfo.v1_26_10,
            ProtocolInfo.v1_26_20_26, ProtocolInfo.v1_26_20, ProtocolInfo.v1_26_30, ProtocolInfo.v1_26_40,
            ProtocolInfo.v1_26_45, ProtocolInfo.v1_26_50_27, ProtocolInfo.v1_26_50, ProtocolInfo.v1_26_60
    };

    @BeforeAll
    static void init() {
        MockServer.init();
        Attribute.init();
    }

    @Test
    void keepsCustomHudCapacityWhileAbsorptionIsConsumed() {
        Player player = mockPlayer(ProtocolInfo.v1_26_0);
        player.setAbsorption(24.0f);
        player.setAbsorption(23.0f);

        ArgumentCaptor<UpdateAttributesPacket> packets = ArgumentCaptor.forClass(UpdateAttributesPacket.class);
        verify(player, times(2)).dataPacket(packets.capture());

        Attribute full = packets.getAllValues().get(0).entries[0];
        Attribute damaged = packets.getAllValues().get(1).entries[0];
        assertEquals(24.0f, full.getMaxValue());
        assertEquals(24.0f, full.getValue());
        assertEquals(24.0f, damaged.getMaxValue());
        assertEquals(23.0f, damaged.getValue());
    }

    @Test
    void boundsOversizedAndNonFiniteValuesInVersion126Packets() {
        Player oversizedPlayer = mockPlayer(ProtocolInfo.v1_26_0);
        Attribute oversized = sendAbsorption(oversizedPlayer, Float.MAX_VALUE);
        assertEquals(32.0f, oversized.getMaxValue());
        assertEquals(32.0f, oversized.getValue());
        assertEquals(Float.MAX_VALUE, oversizedPlayer.getAbsorption());

        Player infinitePlayer = mockPlayer(ProtocolInfo.v1_26_0);
        Attribute infinite = sendAbsorption(infinitePlayer, Float.POSITIVE_INFINITY);
        assertEquals(32.0f, infinite.getMaxValue());
        assertEquals(32.0f, infinite.getValue());
        assertTrue(Float.isFinite(infinite.getValue()));

        Player nanPlayer = mockPlayer(ProtocolInfo.v1_26_0);
        Attribute nan = sendAbsorption(nanPlayer, Float.NaN);
        assertEquals(16.0f, nan.getMaxValue());
        assertEquals(0.0f, nan.getValue());
        assertTrue(Float.isFinite(nan.getValue()));
        assertTrue(Float.isNaN(nanPlayer.getAbsorption()));
    }

    @Test
    void preservesProtocolSpecificPacketsAcrossSupportedVersions() {
        for (int protocol : SUPPORTED_PROTOCOLS) {
            Player player = mockPlayer(protocol);
            Attribute attribute = sendAbsorption(player, 24.0f);

            float expectedMax = protocol >= ProtocolInfo.v1_26_0 ? 24.0f : Float.MAX_VALUE;
            assertEquals(expectedMax, attribute.getMaxValue(), "protocol " + protocol);
            assertEquals(24.0f, attribute.getValue(), "protocol " + protocol);
        }
    }

    private static Player mockPlayer(int protocol) {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        player.protocol = protocol;
        doReturn(true).when(player).dataPacket(any(DataPacket.class));
        return player;
    }

    private static Attribute sendAbsorption(Player player, float absorption) {
        player.setAbsorption(absorption);
        ArgumentCaptor<UpdateAttributesPacket> packet = ArgumentCaptor.forClass(UpdateAttributesPacket.class);
        verify(player).dataPacket(packet.capture());
        return packet.getValue().entries[0];
    }
}
