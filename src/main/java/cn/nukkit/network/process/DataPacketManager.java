package cn.nukkit.network.process;

import cn.nukkit.PlayerHandle;
import cn.nukkit.network.process.processor.common.*;
import cn.nukkit.network.process.processor.v113.*;
import cn.nukkit.network.process.processor.v137.CommandRequestProcessor_v137;
import cn.nukkit.network.process.processor.v282.SetLocalPlayerAsInitializedProcessor_v282;
import cn.nukkit.network.process.processor.v340.LecternUpdateProcessor_v340;
import cn.nukkit.network.process.processor.v422.FilterTextProcessor_v422;
import cn.nukkit.network.process.processor.v527.RequestAbilityProcessor_v527;
import cn.nukkit.network.process.processor.v554.RequestNetworkSettingsProcessor_v554;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.LinkedList;

/**
 * DataPacketManager is a static class to manage DataPacketProcessors and process DataPackets.
 */
@SuppressWarnings("rawtypes")
public final class DataPacketManager {

    private static final LinkedList<Integer> PROTOCOL_PROCESSORS_KEYS = new LinkedList<>();

    private static final Int2ObjectOpenHashMap<Object2ObjectOpenHashMap<Class<? extends DataPacket>, DataPacketProcessor>> PROTOCOL_PROCESSORS_BY_CLASS = new Int2ObjectOpenHashMap<>();
    private static final ObjectOpenHashSet REGISTERED_PACKETS_BY_CLASS = new ObjectOpenHashSet<Class>();
    private static final IntOpenHashSet UNREGISTERED_PACKETS_BY_CLASS = new IntOpenHashSet();

    private static final Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<DataPacketProcessor>> PROTOCOL_PROCESSORS = new Int2ObjectOpenHashMap<>();

    private static final IntOpenHashSet REGISTERED_PACKETS = new IntOpenHashSet();
    private static final IntOpenHashSet UNREGISTERED_PACKETS = new IntOpenHashSet();

    public static void registerProcessor(int protocol, @NotNull DataPacketProcessor... processors) {
        Int2ObjectOpenHashMap<DataPacketProcessor> map = PROTOCOL_PROCESSORS.computeIfAbsent(protocol, (v) -> new Int2ObjectOpenHashMap<>());
        Object2ObjectOpenHashMap<Class<? extends DataPacket>, DataPacketProcessor> mapByClass = PROTOCOL_PROCESSORS_BY_CLASS.computeIfAbsent(protocol, (v) -> new Object2ObjectOpenHashMap<>());
        for (var processor : processors) {
            //noinspection unchecked
            REGISTERED_PACKETS_BY_CLASS.add(processor.getPacketClass());
            //noinspection unchecked
            mapByClass.put(processor.getPacketClass(), processor);

            REGISTERED_PACKETS.add(processor.getPacketId());
            map.put(processor.getPacketId(), processor);
        }
        mapByClass.trim();
        map.trim();

        if (PROTOCOL_PROCESSORS_KEYS.size() != PROTOCOL_PROCESSORS.size()) {
            PROTOCOL_PROCESSORS_KEYS.clear();
            PROTOCOL_PROCESSORS_KEYS.addAll(PROTOCOL_PROCESSORS.keySet());
            PROTOCOL_PROCESSORS_KEYS.sort(Comparator.reverseOrder());
        }

        UNREGISTERED_PACKETS_BY_CLASS.clear();
        UNREGISTERED_PACKETS.clear();
    }

    public static boolean canProcess(int protocol, int packetId) {
        return getProcessor(protocol, packetId) != null;
    }

    public static boolean canProcess(int protocol, Class<? extends DataPacket> packet) {
        return getProcessor(protocol, packet) != null;
    }

    public static DataPacketProcessor getProcessor(int protocol, Class<? extends DataPacket> packet) {
        int index = getIndex(protocol, packet);
        if (!REGISTERED_PACKETS_BY_CLASS.contains(packet) || UNREGISTERED_PACKETS_BY_CLASS.contains(index)) {
            return null;
        }

        DataPacketProcessor processor = getProcessor0(protocol, packet);
        if (processor != null) {
            return processor;
        }

        for (int p : PROTOCOL_PROCESSORS_KEYS) {
            if (p > protocol) {
                continue;
            }

            processor = getProcessor0(p, packet);
            if (processor != null && processor.isSupported(protocol)) {
                registerProcessor(protocol, processor);
                return processor;
            }
        }

        UNREGISTERED_PACKETS_BY_CLASS.add(index);
        return null;
    }

    private static DataPacketProcessor getProcessor0(int protocol, Class<? extends DataPacket> packet) {
        Object2ObjectOpenHashMap<Class<? extends DataPacket>, DataPacketProcessor> map = PROTOCOL_PROCESSORS_BY_CLASS.get(protocol);
        if (map == null) {
            return null;
        }

        return map.get(packet);
    }

    public static DataPacketProcessor getProcessor(int protocol, int packetId) {
        int index = getIndex(protocol, packetId);
        if (!REGISTERED_PACKETS.contains(packetId) || UNREGISTERED_PACKETS.contains(index)) {
            return null;
        }

        DataPacketProcessor processor = getProcessor0(protocol, packetId);
        if (processor != null) {
            return processor;
        }

        for (int p : PROTOCOL_PROCESSORS_KEYS) {
            if (p > protocol) {
                continue;
            }

            processor = getProcessor0(p, packetId);
            if (processor != null && processor.isSupported(protocol)) {
                registerProcessor(protocol, processor);
                return processor;
            }
        }

        UNREGISTERED_PACKETS.add(index);
        return null;
    }

    private static DataPacketProcessor getProcessor0(int protocol, int packetId) {
        Int2ObjectOpenHashMap<DataPacketProcessor> map = PROTOCOL_PROCESSORS.get(protocol);
        if (map == null) {
            return null;
        }

        return map.get(packetId);
    }

    private static int getIndex(int protocol, int packetId) {
        return protocol * 10000 + packetId;
    }

    private static int getIndex(int protocol, Class<? extends DataPacket> packet) {
        return protocol + packet.getName().hashCode();
    }

    public static void processPacket(@NotNull PlayerHandle playerHandle, @NotNull DataPacket packet) {
        DataPacketProcessor processor = getProcessor(playerHandle.getProtocol(), packet.getClass());
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
                AdventureSettingsProcessor.INSTANCE,
                BookEditProcessor.INSTANCE,
                ClientToServerHandshakeProcessor.INSTANCE,
                EmotePacketProcessor.INSTANCE,
                ItemFrameDropItemProcessor.INSTANCE,
                LevelSoundEventProcessor.INSTANCE,
                LevelSoundEventProcessorV1.INSTANCE,
                LevelSoundEventProcessorV2.INSTANCE,
                MapInfoRequestProcessor.INSTANCE,
                MobEquipmentProcessor.INSTANCE,
                ModalFormResponseProcessor.INSTANCE,
                MoveEntityAbsoluteProcessor.INSTANCE,
                NPCRequestProcessor.INSTANCE,
                PacketViolationWarningProcessor.INSTANCE,
                PlayerHotbarProcessor.INSTANCE,
                PlayerInputProcessor.INSTANCE,
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
                CommandStepProcessor_v113.INSTANCE,
                ContainerSetSlotProcessor_v113.INSTANCE,
                DropItemProcessor_v113.INSTANCE,
                InteractProcessor_v113.INSTANCE,
                RemoveBlockProcessor_v113.INSTANCE,
                UseItemProcessor_v113.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_2_0,
                CommandRequestProcessor_v137.INSTANCE
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
                ProtocolInfo.v1_16_200,
                FilterTextProcessor_v422.INSTANCE
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
