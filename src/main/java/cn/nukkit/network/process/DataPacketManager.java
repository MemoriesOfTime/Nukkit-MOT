package cn.nukkit.network.process;

import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.processor.ClientToServerHandshakeProcessor;
import cn.nukkit.network.protocol.DataPacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * DataPacketManager is a static class to manage DataPacketProcessors and process DataPackets.
 */
@SuppressWarnings("rawtypes")
public final class DataPacketManager {
    private static final Int2ObjectOpenHashMap<DataPacketProcessor> CURRENT_PROTOCOL_PROCESSORS = new Int2ObjectOpenHashMap<>(300);

    public static void registerProcessor(@NotNull DataPacketProcessor... processors) {
        for (DataPacketProcessor processor : processors) {
            CURRENT_PROTOCOL_PROCESSORS.put(processor.getPacketId(), processor);
        }
        CURRENT_PROTOCOL_PROCESSORS.trim();
    }

    public static boolean canProcess(int protocol, int packetId) {
        return CURRENT_PROTOCOL_PROCESSORS.containsKey(packetId);
    }

    public static void processPacket(@NotNull PlayerHandle playerHandle, @NotNull DataPacket packet) {
        DataPacketProcessor processor = CURRENT_PROTOCOL_PROCESSORS.get(packet.packetId());
        if (processor != null) {
            //noinspection unchecked
            processor.handle(playerHandle, packet);
        } else {
            throw new UnsupportedOperationException("No processor found for packet " + packet.getClass().getName() + " with id " + packet.packetId() + ".");
        }
    }

    public static void registerDefaultProcessors() {
        registerProcessor(
                new ClientToServerHandshakeProcessor()
        );
    }
}
