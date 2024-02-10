package cn.nukkit.network.process;

import cn.nukkit.PlayerHandle;
import cn.nukkit.Server;
import cn.nukkit.network.process.processor.common.*;
import cn.nukkit.network.process.processor.v113.ContainerSetSlotProcessor_v113;
import cn.nukkit.network.process.processor.v113.DropItemProcessor_v113;
import cn.nukkit.network.process.processor.v113.RemoveBlockProcessor_v113;
import cn.nukkit.network.process.processor.v282.SetLocalPlayerAsInitializedProcessor_v282;
import cn.nukkit.network.process.processor.v340.LecternUpdateProcessor_v340;
import cn.nukkit.network.process.processor.v527.RequestAbilityProcessor_v527;
import cn.nukkit.network.process.processor.v554.RequestNetworkSettingsProcessor_v554;
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
    private static final IntOpenHashSet UNREGISTERED_PACKETS = new IntOpenHashSet();

    public static void registerProcessor(int protocol, @NotNull DataPacketProcessor... processors) {
        Int2ObjectOpenHashMap<DataPacketProcessor> map = PROTOCOL_PROCESSORS.computeIfAbsent(protocol, (v) -> new Int2ObjectOpenHashMap<>());
        for (var processor : processors) {
            REGISTERED_PACKETS.add(processor.getPacketId());
            map.put(processor.getPacketId(), processor);
        }
        map.trim();
        UNREGISTERED_PACKETS.clear();
    }

    public static boolean canProcess(int protocol, int packetId) {
        return getProcessor(protocol, packetId) != null;
    }

    public static DataPacketProcessor getProcessor(int protocol, int packetId) {
        return getProcessor(protocol, protocol, packetId);
    }

    private static DataPacketProcessor getProcessor(int protocol, int originProtocol, int packetId) {
        int index = getIndex(protocol, packetId);
        if (!REGISTERED_PACKETS.contains(packetId) || UNREGISTERED_PACKETS.contains(index)) {
            return null;
        }

        DataPacketProcessor processor;
        protocol++;
        do {
            protocol--;
            Int2ObjectOpenHashMap<DataPacketProcessor> map = PROTOCOL_PROCESSORS.get(protocol);
            processor = map != null ? map.get(packetId) : null;
        } while ((processor == null || !processor.isSupported(originProtocol)) && protocol >= Server.getInstance().minimumProtocol);

        if (processor != null && processor.isSupported(originProtocol)) {
            if (protocol != originProtocol) {
                registerProcessor(originProtocol, processor);
            }
            return processor;
        }

        UNREGISTERED_PACKETS.add(index);
        return null;
    }

    private static int getIndex(int protocol, int packetId) {
        return protocol * 10000 + packetId;
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
                BookEditProcessor.INSTANCE,
                ClientToServerHandshakeProcessor.INSTANCE,
                CommandRequestProcessor.INSTANCE,
                NPCRequestProcessor.INSTANCE,
                EmotePacketProcessor.INSTANCE,
                FilterTextProcessor.INSTANCE,
                ItemFrameDropItemProcessor.INSTANCE,
                LevelSoundEventProcessor.INSTANCE,
                LevelSoundEventProcessorV1.INSTANCE,
                LevelSoundEventProcessorV2.INSTANCE,
                MapInfoRequestProcessor.INSTANCE,
                PacketViolationWarningProcessor.INSTANCE,
                PlayerHotbarProcessor.INSTANCE,
                PlayerSkinProcessor.INSTANCE,
                RequestChunkRadiusProcessor.INSTANCE,
                ResourcePackChunkRequestProcessor.INSTANCE,
                RespawnProcessor.INSTANCE,
                ServerSettingsRequestProcessor.INSTANCE,
                SetDifficultyProcessor.INSTANCE,
                SetPlayerGameTypeProcessor.INSTANCE,
                TextProcessor.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_1_0,
                ContainerSetSlotProcessor_v113.INSTANCE,
                DropItemProcessor_v113.INSTANCE,
                RemoveBlockProcessor_v113.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_6_0_5,
                SetLocalPlayerAsInitializedProcessor_v282.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_10_0,
                LecternUpdateProcessor_v340.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_19_0,
                RequestAbilityProcessor_v527.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_19_30,
                RequestNetworkSettingsProcessor_v554.INSTANCE
        );
    }
}
