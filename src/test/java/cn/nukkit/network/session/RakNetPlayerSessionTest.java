package cn.nukkit.network.session;

import cn.nukkit.MockServer;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.Network;
import cn.nukkit.network.protocol.ClientToServerHandshakePacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RakNetPlayerSessionTest {

    private static final int RAKNET_PROTOCOL = 11;
    private static Network network;

    @BeforeAll
    static void setUp() {
        MockServer.init();
        network = new Network(MockServer.get());
    }

    @Test
    void decodeInboundPrefixedBatchFallsBackToLegacyWhenPrefixCollides() {
        byte[] legacyPacketBuffer = createLegacyHandshakeBatch();

        RakNetPlayerSession.InboundBatchDecodeResult result = RakNetPlayerSession.decodeInboundPrefixedBatch(
                network,
                legacyPacketBuffer,
                CompressionProvider.NONE,
                true,
                RAKNET_PROTOCOL,
                null
        );

        assertTrue(result.success());
        assertFalse(result.prefixed());
        assertSame(CompressionProvider.NONE, result.compression());
        assertEquals(1, result.packets().size());
        assertInstanceOf(ClientToServerHandshakePacket.class, result.packets().get(0));
    }

    @Test
    void decodeInboundPrefixedBatchKeepsPrefixedSnappyPackets() throws Exception {
        byte[] legacyPacketBuffer = createLegacyHandshakeBatch();
        byte[] compressed = CompressionProvider.SNAPPY.compress(new cn.nukkit.utils.BinaryStream(legacyPacketBuffer), 7);
        byte[] prefixedPacketBuffer = new byte[compressed.length + 1];
        prefixedPacketBuffer[0] = CompressionProvider.SNAPPY.getPrefix();
        System.arraycopy(compressed, 0, prefixedPacketBuffer, 1, compressed.length);

        RakNetPlayerSession.InboundBatchDecodeResult result = RakNetPlayerSession.decodeInboundPrefixedBatch(
                network,
                prefixedPacketBuffer,
                CompressionProvider.NONE,
                true,
                RAKNET_PROTOCOL,
                null
        );

        assertTrue(result.success());
        assertTrue(result.prefixed());
        assertSame(CompressionProvider.SNAPPY, result.compression());
        assertEquals(1, result.packets().size());
        assertInstanceOf(ClientToServerHandshakePacket.class, result.packets().get(0));
    }

    private static byte[] createLegacyHandshakeBatch() {
        return new byte[]{
                0x01,
                ClientToServerHandshakePacket.NETWORK_ID
        };
    }
}
