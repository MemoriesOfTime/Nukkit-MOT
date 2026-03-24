package cn.nukkit.network.protocol.regression;

import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

/**
 * Converts Nukkit-MOT encoded packets to CB Protocol compatible ByteBuf format
 * by stripping the packet ID header written by {@link DataPacket#reset()}.
 */
public final class PacketBridgeUtil {

    private PacketBridgeUtil() {
    }

    /**
     * Extracts the packet body (payload without header) from an encoded Nukkit-MOT DataPacket
     * and wraps it in a Netty ByteBuf for CB Protocol deserialization.
     *
     * <p>For protocol versions >= 275, {@link DataPacket#reset()} writes the packet ID
     * as an unsigned VarInt header. This method skips that header.</p>
     *
     * @param packet an already-encoded Nukkit-MOT DataPacket
     * @return ByteBuf containing only the packet body (no header)
     */
    public static ByteBuf nukkitPacketToByteBuf(DataPacket packet) {
        byte[] raw = packet.getBuffer();
        BinaryStream temp = new BinaryStream(raw);
        temp.getUnsignedVarInt(); // skip packet ID header
        int bodyOffset = temp.getOffset();
        return Unpooled.wrappedBuffer(raw, bodyOffset, raw.length - bodyOffset);
    }

    /**
     * Encodes a CloudburstMC BedrockPacket using the specified codec and wraps the result
     * as a Nukkit-MOT compatible byte array (with unsigned VarInt packet ID header prepended).
     *
     * <p>This is the reverse of {@link #nukkitPacketToByteBuf(DataPacket)}: CB encodes the packet,
     * then we prepend the Nukkit-MOT packet ID header so that {@link DataPacket#decode()} can
     * consume the buffer correctly after skipping the header via {@code setBuffer + offset}.</p>
     *
     * @param cbPacket         the CB packet to encode
     * @param codec            the BedrockCodec for the target protocol version
     * @param helper           the codec helper (may be pre-configured)
     * @param nukkitPacketId   the Nukkit-MOT packet ID (from {@link DataPacket#packetId()})
     * @param protocolVersion  the target protocol version
     * @return byte array in Nukkit-MOT wire format (packet ID header + body)
     */
    @SuppressWarnings("unchecked")
    public static <T extends BedrockPacket> byte[] cbPacketToNukkitBuffer(
            T cbPacket, BedrockCodec codec, BedrockCodecHelper helper,
            int nukkitPacketId, int protocolVersion) {
        BedrockPacketDefinition<T> definition =
                (BedrockPacketDefinition<T>) codec.getPacketDefinition(cbPacket.getClass());
        if (definition == null) {
            throw new IllegalArgumentException(cbPacket.getClass().getSimpleName()
                    + " not registered in codec v" + protocolVersion);
        }

        // CB encode to ByteBuf
        ByteBuf body = Unpooled.buffer();
        try {
            definition.getSerializer().serialize(body, helper, cbPacket);
            byte[] bodyBytes = new byte[body.readableBytes()];
            body.readBytes(bodyBytes);

            // Prepend Nukkit-MOT packet ID header
            BinaryStream stream = new BinaryStream();
            if (protocolVersion <= 274) {
                if (protocolVersion >= ProtocolInfo.v1_2_0) {
                    stream.putByte((byte) (nukkitPacketId & 0xff));
                    stream.putShort(0);
                } else {
                    stream.putByte((byte) (nukkitPacketId & 0xff));
                }
            } else {
                stream.putUnsignedVarInt(nukkitPacketId);
            }
            stream.put(bodyBytes);
            return stream.getBuffer();
        } finally {
            body.release();
        }
    }
}
