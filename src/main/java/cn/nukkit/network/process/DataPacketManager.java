package cn.nukkit.network.process;

import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.processor.ClientToServerHandshakeProcessor;
import cn.nukkit.network.process.processor.LecternUpdateProcessor;
import cn.nukkit.network.process.processor.NPCRequestProcessor;
import cn.nukkit.network.process.processor.RequestAbilityProcessor;
import cn.nukkit.network.process.processor.v113.ContainerSetSlotProcessorV113;
import cn.nukkit.network.process.processor.v113.DropItemProcessorV113;
import cn.nukkit.network.process.processor.v113.RemoveBlockProcessorV113;
import cn.nukkit.network.process.processor.v137.ContainerOpenProcessorV137;
import cn.nukkit.network.process.processor.v137.InventorySlotProcessorV137;
import cn.nukkit.network.process.processor.v137.UpdateBlockProcessorV137;
import cn.nukkit.network.process.processor.v274.SetLocalPlayerAsInitializedProcessorV274;
import cn.nukkit.network.process.processor.v282.SetLocalPlayerAsInitializedProcessorV282;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.NotNull;

/**
 * DataPacketManager is a static class to manage DataPacketProcessors and process DataPackets.
 */
@SuppressWarnings("rawtypes")
public final class DataPacketManager {
    private static final Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<DataPacketProcessor>> PROTOCOL_PROCESSORS = new Int2ObjectOpenHashMap<>();
    private static final IntOpenHashSet REGISTERED_PACKETS = new IntOpenHashSet();

    public static void registerProcessor(int protocol, @NotNull DataPacketProcessor... processors) {
        Int2ObjectOpenHashMap<DataPacketProcessor> map = PROTOCOL_PROCESSORS.computeIfAbsent(protocol, (v) -> new Int2ObjectOpenHashMap<>());
        for (var processor : processors) {
            REGISTERED_PACKETS.add(processor.getPacketId());
            map.put(processor.getPacketId(), processor);
        }
        map.trim();
    }

    public static boolean canProcess(int protocol, int packetId) {
        return getProcessor(protocol, packetId) != null;
    }

    public static DataPacketProcessor getProcessor(int protocol, int packetId) {
        return getProcessor(protocol, protocol, packetId);
    }

    private static DataPacketProcessor getProcessor(int protocol, int originProtocol, int packetId) {
        if (!REGISTERED_PACKETS.contains(packetId)) {
            return null;
        }
        DataPacketProcessor processor;
        protocol++;
        do {
            protocol--;
            Int2ObjectOpenHashMap<DataPacketProcessor> map = PROTOCOL_PROCESSORS.get(protocol);
            processor = map != null ? map.get(packetId) : null;
        } while (processor == null && protocol >= 0);
        if (protocol != originProtocol && processor != null) {
            registerProcessor(originProtocol, processor);
        }
        return processor;
    }

    public static void processPacket(@NotNull PlayerHandle playerHandle, @NotNull DataPacket packet) {
        DataPacketProcessor processor = getProcessor(playerHandle.getProtocol(), packet.packetId());
        if (processor != null) {
            //noinspection unchecked
            processor.handle(playerHandle, packet);
        } else {
            throw new UnsupportedOperationException("No processor found for packet " + packet.getClass().getName() + " with id " + packet.packetId() + ".");
        }
    }

    public static void registerDefaultProcessors() {
        registerProcessor(
                0, //base
                ClientToServerHandshakeProcessor.INSTANCE,
                LecternUpdateProcessor.INSTANCE,
                RequestAbilityProcessor.INSTANCE,
                NPCRequestProcessor.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_1_0,
                ContainerSetSlotProcessorV113.INSTANCE,
                DropItemProcessorV113.INSTANCE,
                RemoveBlockProcessorV113.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_2_0,
                ContainerOpenProcessorV137.INSTANCE,
                InventorySlotProcessorV137.INSTANCE,
                UpdateBlockProcessorV137.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_5_0,
                SetLocalPlayerAsInitializedProcessorV274.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_6_0_5,
                SetLocalPlayerAsInitializedProcessorV282.INSTANCE
        );
    }
}
