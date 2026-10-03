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

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

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

    // 无锁解析缓存：packetClass → [gameVersion, processor|NO_PROCESSOR, ...] 平铺数组（已解析含负结果）；注册时整体失效。
    // 键用 GameVersion 而非协议号：标准/网易同协议号互不污染
    // Lock-free resolution cache: per-class flat [gameVersion, result] arrays (nulls cached too);
    // keyed by GameVersion (not protocol number) so standard/NetEase never share entries; cleared on registration
    private static final ConcurrentHashMap<Class<? extends DataPacket>, Object[]> RESOLUTION_CACHE = new ConcurrentHashMap<>();
    // packetId 版缓存（deprecated int 桥仍为插件可达 API，高频调用不应退化为持类锁全量遍历）
    // packetId-keyed cache (the deprecated int bridges remain plugin-reachable and must not
    // degrade into a full scan under the class monitor on hot paths)
    private static final ConcurrentHashMap<Integer, Object[]> RESOLUTION_CACHE_BY_ID = new ConcurrentHashMap<>();
    private static final Object NO_PROCESSOR = new Object();

    public static synchronized void registerProcessor(int protocol, @NotNull DataPacketProcessor... processors) {
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

        RESOLUTION_CACHE.clear();
        RESOLUTION_CACHE_BY_ID.clear();
    }

    public static boolean canProcess(GameVersion gameVersion, int packetId) {
        return getProcessor(gameVersion, packetId) != null;
    }

    public static boolean canProcess(GameVersion gameVersion, Class<? extends DataPacket> packet) {
        return getProcessor(gameVersion, packet) != null;
    }

    public static DataPacketProcessor getProcessor(GameVersion gameVersion, Class<? extends DataPacket> packet) {
        Object cached = lookupResolutionCache(gameVersion, packet);
        if (cached != null) {
            return cached == NO_PROCESSOR ? null : (DataPacketProcessor) cached;
        }
        return resolveProcessor(gameVersion, packet);
    }

    private static synchronized DataPacketProcessor resolveProcessor(GameVersion gameVersion, Class<? extends DataPacket> packet) {
        // 双检：等锁期间其他线程可能已完成解析并写回缓存
        Object cached = lookupResolutionCache(gameVersion, packet);
        if (cached != null) {
            return cached == NO_PROCESSOR ? null : (DataPacketProcessor) cached;
        }
        DataPacketProcessor processor = getProcessorUncached(gameVersion, packet);
        cacheResolution(gameVersion, packet, processor);
        return processor;
    }

    private static DataPacketProcessor getProcessorUncached(GameVersion gameVersion, Class<? extends DataPacket> packet) {
        if (!REGISTERED_PACKETS_BY_CLASS.contains(packet)) {
            return null;
        }

        int protocol = gameVersion.getProtocol();
        for (int p : PROTOCOL_PROCESSORS_KEYS) {
            if (p > protocol) {
                continue;
            }

            DataPacketProcessor processor = getProcessor0(p, packet);
            if (processor != null && processor.isSupported(gameVersion)) {
                return processor;
            }
        }
        return null;
    }

    private static Object lookupResolutionCache(GameVersion gameVersion, Class<? extends DataPacket> packet) {
        Object[] table = RESOLUTION_CACHE.get(packet);
        if (table == null) {
            return null;
        }
        for (int i = 0; i < table.length; i += 2) {
            if (table[i] == gameVersion) {
                return table[i + 1];
            }
        }
        return null;
    }

    // 仅在 resolveProcessor 的类监视器内调用：平铺数组的读-改-写非原子，靠该锁串行
    private static void cacheResolution(GameVersion gameVersion, Class<? extends DataPacket> packet, DataPacketProcessor processor) {
        Object value = processor == null ? NO_PROCESSOR : processor;
        Object[] old = RESOLUTION_CACHE.get(packet);
        if (old == null) {
            RESOLUTION_CACHE.put(packet, new Object[]{gameVersion, value});
            return;
        }
        for (int i = 0; i < old.length; i += 2) {
            if (old[i] == gameVersion) {
                Object[] copy = old.clone();
                copy[i + 1] = value;
                RESOLUTION_CACHE.put(packet, copy);
                return;
            }
        }
        Object[] updated = Arrays.copyOf(old, old.length + 2);
        updated[old.length] = gameVersion;
        updated[old.length + 1] = value;
        RESOLUTION_CACHE.put(packet, updated);
    }

    // packetId 版与 Class 版同款：无锁缓存命中 + 类监视器内双检解析，热点不再持锁全量遍历
    // Same pattern as the Class version: lock-free cache hit + double-checked resolve under
    // the class monitor, so hot paths no longer scan everything while holding the lock
    public static DataPacketProcessor getProcessor(GameVersion gameVersion, int packetId) {
        Object cached = lookupResolutionCacheById(gameVersion, packetId);
        if (cached != null) {
            return cached == NO_PROCESSOR ? null : (DataPacketProcessor) cached;
        }
        return resolveProcessorById(gameVersion, packetId);
    }

    private static synchronized DataPacketProcessor resolveProcessorById(GameVersion gameVersion, int packetId) {
        // 双检：等锁期间其他线程可能已完成解析并写回缓存
        Object cached = lookupResolutionCacheById(gameVersion, packetId);
        if (cached != null) {
            return cached == NO_PROCESSOR ? null : (DataPacketProcessor) cached;
        }
        DataPacketProcessor processor = getProcessorUncached(gameVersion, packetId);
        cacheResolutionById(gameVersion, packetId, processor);
        return processor;
    }

    private static DataPacketProcessor getProcessorUncached(GameVersion gameVersion, int packetId) {
        if (!REGISTERED_PACKETS.contains(packetId)) {
            return null;
        }

        int protocol = gameVersion.getProtocol();
        for (int p : PROTOCOL_PROCESSORS_KEYS) {
            if (p > protocol) {
                continue;
            }

            DataPacketProcessor processor = getProcessor0(p, packetId);
            if (processor != null && processor.isSupported(gameVersion)) {
                return processor;
            }
        }
        return null;
    }

    private static Object lookupResolutionCacheById(GameVersion gameVersion, int packetId) {
        Object[] table = RESOLUTION_CACHE_BY_ID.get(packetId);
        if (table == null) {
            return null;
        }
        for (int i = 0; i < table.length; i += 2) {
            if (table[i] == gameVersion) {
                return table[i + 1];
            }
        }
        return null;
    }

    // 仅在 resolveProcessorById 的类监视器内调用（与 cacheResolution 同锁串行）
    private static void cacheResolutionById(GameVersion gameVersion, int packetId, DataPacketProcessor processor) {
        Object value = processor == null ? NO_PROCESSOR : processor;
        Object[] old = RESOLUTION_CACHE_BY_ID.get(packetId);
        if (old == null) {
            RESOLUTION_CACHE_BY_ID.put(packetId, new Object[]{gameVersion, value});
            return;
        }
        for (int i = 0; i < old.length; i += 2) {
            if (old[i] == gameVersion) {
                Object[] copy = old.clone();
                copy[i + 1] = value;
                RESOLUTION_CACHE_BY_ID.put(packetId, copy);
                return;
            }
        }
        Object[] updated = Arrays.copyOf(old, old.length + 2);
        updated[old.length] = gameVersion;
        updated[old.length + 1] = value;
        RESOLUTION_CACHE_BY_ID.put(packetId, updated);
    }

    private static DataPacketProcessor getProcessor0(int protocol, Class<? extends DataPacket> packet) {
        Object2ObjectOpenHashMap<Class<? extends DataPacket>, DataPacketProcessor> map = PROTOCOL_PROCESSORS_BY_CLASS.get(protocol);
        if (map == null) {
            return null;
        }

        return map.get(packet);
    }

    private static DataPacketProcessor getProcessor0(int protocol, int packetId) {
        Int2ObjectOpenHashMap<DataPacketProcessor> map = PROTOCOL_PROCESSORS.get(protocol);
        if (map == null) {
            return null;
        }

        return map.get(packetId);
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

    /**
     * 单次取锁的快速路径，找到 processor 则锁外执行 handle；替代 canProcess+processPacket 的两次取锁。
     * Single-lock fast path replacing the canProcess+processPacket pair.
     */
    public static boolean tryProcessPacket(@NotNull PlayerHandle playerHandle, @NotNull DataPacket packet) {
        DataPacketProcessor processor = getProcessor(packet.gameVersion, packet.getClass());
        if (processor != null) {
            //noinspection unchecked
            processor.handle(playerHandle, packet);
            return true;
        }
        return false;
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
