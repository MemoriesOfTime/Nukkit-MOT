package cn.nukkit.network.protocol.regression;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.DataPacket;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.junit.jupiter.params.provider.Arguments;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Base class for cross-decode regression tests.
 * Nukkit-MOT encodes packets, CB Protocol decodes them to verify wire format correctness.
 */
public abstract class AbstractPacketRegressionTest {

    // --- Version Sources ---

    protected static Stream<Arguments> allVersions() {
        return ProtocolCodecMapping.getSupportedVersions().stream().map(Arguments::of);
    }

    protected static Stream<Arguments> filteredVersions(int minVersion) {
        return ProtocolCodecMapping.getSupportedVersions().stream()
                .filter(v -> v >= minVersion)
                .map(Arguments::of);
    }

    protected static Stream<Arguments> filteredVersionsRange(int minVersion, int maxVersionExclusive) {
        return ProtocolCodecMapping.getSupportedVersions().stream()
                .filter(v -> v >= minVersion && v < maxVersionExclusive)
                .map(Arguments::of);
    }

    // --- Helper ---

    @SuppressWarnings("unchecked")
    protected <T extends BedrockPacket> T crossDecode(DataPacket nukkitPacket, Class<T> cbPacketClass) {
        return crossDecode(nukkitPacket, cbPacketClass, null);
    }

    @SuppressWarnings("unchecked")
    protected <T extends BedrockPacket> T crossDecode(DataPacket nukkitPacket, Class<T> cbPacketClass,
                                                       Consumer<BedrockCodecHelper> helperConfigurer) {
        ByteBuf buf = PacketBridgeUtil.nukkitPacketToByteBuf(nukkitPacket);
        try {
            BedrockCodec codec = ProtocolCodecMapping.getCodec(nukkitPacket.protocol);
            BedrockCodecHelper helper = codec.createHelper();
            if (helperConfigurer != null) {
                helperConfigurer.accept(helper);
            }
            BedrockPacketDefinition<T> definition = codec.getPacketDefinition(cbPacketClass);
            assertNotNull(definition, cbPacketClass.getSimpleName() + " not registered in codec v" + nukkitPacket.protocol);
            T cbPacket;
            try {
                cbPacket = cbPacketClass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to instantiate " + cbPacketClass.getSimpleName(), e);
            }
            definition.getSerializer().deserialize(buf, helper, cbPacket);
            assertEquals(0, buf.readableBytes(),
                    "Buffer not fully consumed for " + cbPacketClass.getSimpleName() + " v" + nukkitPacket.protocol);
            return cbPacket;
        } finally {
            buf.release();
        }
    }

    // --- Cross-Encode: CB encode → NK decode ---

    /**
     * Cross-encode test helper: CB Protocol encodes a packet, Nukkit-MOT decodes it.
     * This verifies that Nukkit-MOT can correctly decode packets encoded by a reference implementation.
     *
     * @param cbPacket         the CB packet with fields populated
     * @param nukkitPacketFactory supplier that creates a fresh Nukkit-MOT DataPacket instance
     * @param protocolVersion  the target protocol version
     * @param <T>              CB packet type
     * @param <N>              Nukkit packet type
     * @return the decoded Nukkit-MOT DataPacket with fields populated by decode()
     */
    protected <T extends BedrockPacket, N extends DataPacket> N crossEncode(
            T cbPacket, Supplier<N> nukkitPacketFactory, int protocolVersion) {
        return crossEncode(cbPacket, nukkitPacketFactory, protocolVersion, null);
    }

    /**
     * Cross-encode test helper with codec helper configuration support.
     *
     * @param cbPacket           the CB packet with fields populated
     * @param nukkitPacketFactory supplier that creates a fresh Nukkit-MOT DataPacket instance
     * @param protocolVersion    the target protocol version
     * @param helperConfigurer   optional consumer to configure the BedrockCodecHelper (e.g., block definitions)
     * @param <T>                CB packet type
     * @param <N>                Nukkit packet type
     * @return the decoded Nukkit-MOT DataPacket with fields populated by decode()
     */
    protected <T extends BedrockPacket, N extends DataPacket> N crossEncode(
            T cbPacket, Supplier<N> nukkitPacketFactory, int protocolVersion,
            Consumer<BedrockCodecHelper> helperConfigurer) {
        BedrockCodec codec = ProtocolCodecMapping.getCodec(protocolVersion);
        BedrockCodecHelper helper = codec.createHelper();
        if (helperConfigurer != null) {
            helperConfigurer.accept(helper);
        }

        N nukkitPacket = nukkitPacketFactory.get();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = GameVersion.byProtocol(protocolVersion, false);

        byte[] buffer = PacketBridgeUtil.cbPacketToNukkitBuffer(
                cbPacket, codec, helper, nukkitPacket.packetId(), protocolVersion);

        nukkitPacket.setBuffer(buffer);
        // Skip the packet ID header (same as what the server does when receiving)
        if (protocolVersion <= 274) {
            if (protocolVersion >= 113) { // v1_2_0
                nukkitPacket.getByte();
                nukkitPacket.getShort();
            } else {
                nukkitPacket.getByte();
            }
        } else {
            nukkitPacket.getUnsignedVarInt();
        }
        nukkitPacket.decode();
        return nukkitPacket;
    }
}
