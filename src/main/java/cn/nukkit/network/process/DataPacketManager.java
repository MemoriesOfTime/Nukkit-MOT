package cn.nukkit.network.process;

import cn.nukkit.GameVersion;
import cn.nukkit.PlayerHandle;
import cn.nukkit.Server;
import cn.nukkit.network.process.processor.ServerboundDataDrivenScreenClosedProcessor;
import cn.nukkit.network.process.processor.ServerboundDataStoreProcessor;
import cn.nukkit.network.process.processor.common.*;
import cn.nukkit.network.process.processor.netease.PyRpcProcessor;
import cn.nukkit.network.process.processor.netease.SyncSkinProcessor;
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

    // 注册表：registerProcessor 按协议阈值注册的分桶，与具体客户端无关
    // Registration tables bucketed by protocol threshold, independent of any client
    private static final Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<DataPacketProcessor>> PROTOCOL_PROCESSORS = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<Object2ObjectOpenHashMap<Class<? extends DataPacket>, DataPacketProcessor>> PROTOCOL_PROCESSORS_BY_CLASS = new Int2ObjectOpenHashMap<>();
    private static final LinkedList<Integer> PROTOCOL_PROCESSORS_KEYS = new LinkedList<>();
    private static final IntOpenHashSet REGISTERED_PACKETS = new IntOpenHashSet();
    private static final ObjectOpenHashSet REGISTERED_PACKETS_BY_CLASS = new ObjectOpenHashSet<Class>();

    // 解析缓存：按客户端 GameVersion 分桶，标准/网易同协议号互不污染
    // Resolution caches keyed by client GameVersion so standard/NetEase never share entries
    private static final Object2ObjectOpenHashMap<GameVersion, Int2ObjectOpenHashMap<DataPacketProcessor>> RESOLVED_PROCESSORS = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<GameVersion, Object2ObjectOpenHashMap<Class<? extends DataPacket>, DataPacketProcessor>> RESOLVED_PROCESSORS_BY_CLASS = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<GameVersion, IntOpenHashSet> UNRESOLVED_PACKETS = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<GameVersion, ObjectOpenHashSet<Class<?>>> UNRESOLVED_PACKETS_BY_CLASS = new Object2ObjectOpenHashMap<>();

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

        RESOLVED_PROCESSORS.clear();
        RESOLVED_PROCESSORS_BY_CLASS.clear();
        UNRESOLVED_PACKETS.clear();
        UNRESOLVED_PACKETS_BY_CLASS.clear();
    }

    public static boolean canProcess(GameVersion gameVersion, int packetId) {
        return getProcessor(gameVersion, packetId) != null;
    }

    public static boolean canProcess(GameVersion gameVersion, Class<? extends DataPacket> packet) {
        return getProcessor(gameVersion, packet) != null;
    }

    public static DataPacketProcessor getProcessor(GameVersion gameVersion, Class<? extends DataPacket> packet) {
        if (!REGISTERED_PACKETS_BY_CLASS.contains(packet) || getUnresolvedPacketsByClass(gameVersion).contains(packet)) {
            return null;
        }

        DataPacketProcessor processor = getResolved0(gameVersion, packet);
        if (processor != null) {
            return processor;
        }

        int protocol = gameVersion.getProtocol();
        for (int p : PROTOCOL_PROCESSORS_KEYS) {
            if (p > protocol) {
                continue;
            }

            processor = getProcessor0(p, packet);
            if (processor != null && processor.isSupported(gameVersion)) {
                RESOLVED_PROCESSORS_BY_CLASS.computeIfAbsent(gameVersion, (v) -> new Object2ObjectOpenHashMap<>()).put(packet, processor);
                return processor;
            }
        }

        getUnresolvedPacketsByClass(gameVersion).add(packet);
        return null;
    }

    private static DataPacketProcessor getProcessor0(int protocol, Class<? extends DataPacket> packet) {
        Object2ObjectOpenHashMap<Class<? extends DataPacket>, DataPacketProcessor> map = PROTOCOL_PROCESSORS_BY_CLASS.get(protocol);
        if (map == null) {
            return null;
        }

        return map.get(packet);
    }

    public static DataPacketProcessor getProcessor(GameVersion gameVersion, int packetId) {
        if (!REGISTERED_PACKETS.contains(packetId) || getUnresolvedPackets(gameVersion).contains(packetId)) {
            return null;
        }

        DataPacketProcessor processor = getResolved0(gameVersion, packetId);
        if (processor != null) {
            return processor;
        }

        int protocol = gameVersion.getProtocol();
        for (int p : PROTOCOL_PROCESSORS_KEYS) {
            if (p > protocol) {
                continue;
            }

            processor = getProcessor0(p, packetId);
            if (processor != null && processor.isSupported(gameVersion)) {
                RESOLVED_PROCESSORS.computeIfAbsent(gameVersion, (v) -> new Int2ObjectOpenHashMap<>()).put(packetId, processor);
                return processor;
            }
        }

        getUnresolvedPackets(gameVersion).add(packetId);
        return null;
    }

    private static DataPacketProcessor getProcessor0(int protocol, int packetId) {
        Int2ObjectOpenHashMap<DataPacketProcessor> map = PROTOCOL_PROCESSORS.get(protocol);
        if (map == null) {
            return null;
        }

        return map.get(packetId);
    }

    private static DataPacketProcessor getResolved0(GameVersion gameVersion, Class<? extends DataPacket> packet) {
        Object2ObjectOpenHashMap<Class<? extends DataPacket>, DataPacketProcessor> map = RESOLVED_PROCESSORS_BY_CLASS.get(gameVersion);
        return map == null ? null : map.get(packet);
    }

    private static DataPacketProcessor getResolved0(GameVersion gameVersion, int packetId) {
        Int2ObjectOpenHashMap<DataPacketProcessor> map = RESOLVED_PROCESSORS.get(gameVersion);
        return map == null ? null : map.get(packetId);
    }

    private static IntOpenHashSet getUnresolvedPackets(GameVersion gameVersion) {
        return UNRESOLVED_PACKETS.computeIfAbsent(gameVersion, (v) -> new IntOpenHashSet());
    }

    @SuppressWarnings("unchecked")
    private static ObjectOpenHashSet<Class<? extends DataPacket>> getUnresolvedPacketsByClass(GameVersion gameVersion) {
        return (ObjectOpenHashSet) UNRESOLVED_PACKETS_BY_CLASS.computeIfAbsent(gameVersion, (v) -> new ObjectOpenHashSet());
    }

    /**
     * 以协议号判断能否处理。
     * <p>
     * 已弃用，待删除：int 无法区分网易与标准客户端（同协议号共享同一 GameVersion 协议空间），
     * 混合模式下按标准客户端解析。请使用 {@link #canProcess(GameVersion, int)}。
     * <p>
     * Deprecated, pending removal: an int cannot distinguish NetEase from standard clients
     * (same number, parallel GameVersion spaces); resolves as standard in mixed mode.
     * Use {@link #canProcess(GameVersion, int)} instead.
     */
    @Deprecated
    public static boolean canProcess(int protocol, int packetId) {
        return canProcess(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode), packetId);
    }

    /**
     * 以协议号判断能否处理。
     * <p>
     * 已弃用，待删除：int 无法区分网易与标准客户端，混合模式下按标准客户端解析。
     * 请使用 {@link #canProcess(GameVersion, Class)}。
     * <p>
     * Deprecated, pending removal: an int cannot distinguish NetEase from standard clients.
     * Use {@link #canProcess(GameVersion, Class)} instead.
     */
    @Deprecated
    public static boolean canProcess(int protocol, Class<? extends DataPacket> packet) {
        return canProcess(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode), packet);
    }

    /**
     * 以协议号获取处理器。
     * <p>
     * 已弃用，待删除：int 无法区分网易与标准客户端，混合模式下按标准客户端解析。
     * 请使用 {@link #getProcessor(GameVersion, Class)}。
     * <p>
     * Deprecated, pending removal: an int cannot distinguish NetEase from standard clients.
     * Use {@link #getProcessor(GameVersion, Class)} instead.
     */
    @Deprecated
    public static DataPacketProcessor getProcessor(int protocol, Class<? extends DataPacket> packet) {
        return getProcessor(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode), packet);
    }

    /**
     * 以协议号获取处理器。
     * <p>
     * 已弃用，待删除：int 无法区分网易与标准客户端，混合模式下按标准客户端解析。
     * 请使用 {@link #getProcessor(GameVersion, int)}。
     * <p>
     * Deprecated, pending removal: an int cannot distinguish NetEase from standard clients.
     * Use {@link #getProcessor(GameVersion, int)} instead.
     */
    @Deprecated
    public static DataPacketProcessor getProcessor(int protocol, int packetId) {
        return getProcessor(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode), packetId);
    }

    public static void processPacket(@NotNull PlayerHandle playerHandle, @NotNull DataPacket packet) {
        DataPacketProcessor processor = getProcessor(packet.gameVersion, packet.getClass());
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
                InventoryTransactionProcessor.INSTANCE,
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
                PyRpcProcessor.INSTANCE,
                SyncSkinProcessor.INSTANCE,
                RequestChunkRadiusProcessor.INSTANCE,
                ResourcePackChunkRequestProcessor.INSTANCE,
                RespawnProcessor.INSTANCE,
                ServerSettingsRequestProcessor.INSTANCE,
                SetDifficultyProcessor.INSTANCE,
                SetPlayerGameTypeProcessor.INSTANCE,
                TextProcessor.INSTANCE,
                CommandBlockUpdateProcessor.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_1_0,
                CommandStepProcessor_v113.INSTANCE,
                ContainerSetSlotProcessor_v113.INSTANCE,
                CraftingEventProcessor_v113.INSTANCE,
                DropItemProcessor_v113.INSTANCE,
                InteractProcessor_v113.INSTANCE,
                PlayerActionProcessor_v113.INSTANCE,
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
                ProtocolInfo.v1_16_100,
                ItemStackRequestProcessor.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_19_0,
                RequestAbilityProcessor_v527.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_19_30,
                RequestNetworkSettingsProcessor_v554.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_20_50,
                ToggleCrafterSlotRequestProcessor.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_21_130,
                ServerboundDataStoreProcessor.INSTANCE
        );

        registerProcessor(
                ProtocolInfo.v1_26_10,
                ServerboundDataDrivenScreenClosedProcessor.INSTANCE
        );
    }
}
