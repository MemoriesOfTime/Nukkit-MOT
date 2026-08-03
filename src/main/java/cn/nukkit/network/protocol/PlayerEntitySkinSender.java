package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.plugin.InternalPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 统一网易 V860 玩家型实体的皮肤注册与清理顺序。
 * <p>
 * Coordinates skin registration and cleanup for player-like entities on NetEase V860.
 * <p>
 * Adapted from Nukkit-EC (<a href="https://github.com/EaseCation/Nukkit">Nukkit-EC</a>)
 */
@OnlyNetEase
public final class PlayerEntitySkinSender {

    private static final String EMPTY_SKIN_ID = "nukkit.empty-player-entity-skin";

    /** 握手后移除 PlayerList 条目的延迟（tick），对齐 Geyser SkullPlayerEntity 的 250ms。
     *  <p>Delay (in ticks) before removing the entry after the skin handshake, mirroring Geyser's 250ms. */
    private static final int DELAYED_REMOVE_TICKS = 5;

    /** 每观察者按 UUID 记录的注册代次，使旧延迟 REMOVE 任务在 re-spawn 后失效。
     *  <p>Per-viewer generation per UUID; stale delayed REMOVE tasks are ignored after a re-spawn. */
    private static final Map<Player, Map<UUID, AtomicLong>> REMOVE_GENERATIONS = new WeakHashMap<>();

    private PlayerEntitySkinSender() {
    }

    /**
     * 该观察者是否走网易 V860 的玩家型实体皮肤握手。
     * <p>
     * Whether the viewer uses the NetEase V860 skin handshake for player-like entities.
     */
    public static boolean requiresRetainedEntry(Player viewer) {
        return viewer.getGameVersion() == GameVersion.V1_21_124_NETEASE;
    }

    /**
     * 首次注册皮肤：ADD → 真实皮肤包，并在 {@link #DELAYED_REMOVE_TICKS} 后移除条目。已注册则去重。
     * <p>
     * Registers a skin once (ADD → skin packet → delayed REMOVE); duplicate ADDs are suppressed.
     */
    public static boolean sendInitialSkinIfAbsent(Player viewer, UUID uuid, long entityId,
                                                  String name, Skin skin, String xboxUserId) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(skin, "skin");

        if (!viewer.sentSkins.add(uuid)) {
            return true;
        }

        PlayerListPacket add = new PlayerListPacket();
        add.type = PlayerListPacket.TYPE_ADD;
        add.entries = new PlayerListPacket.Entry[]{
                new PlayerListPacket.Entry(uuid, entityId, name, createEmptyPlayerListSkin(), xboxUserId)
        };
        if (!viewer.dataPacket(add)) {
            viewer.sentSkins.remove(uuid);
            return false;
        }

        PlayerSkinPacket update = new PlayerSkinPacket();
        update.uuid = uuid;
        update.skin = skin;
        update.newSkinName = skin.getSkinId();
        update.oldSkinName = "";
        if (!viewer.dataPacket(update)) {
            viewer.sentSkins.remove(uuid);
            sendRemove(viewer, uuid);
            return false;
        }

        // 递增代次，使此前排队的旧延迟 REMOVE 任务失效。
        currentGeneration(viewer, uuid).incrementAndGet();
        scheduleDelayedRemove(viewer, uuid);
        return true;
    }

    /**
     * 以 REMOVE → ADD 原子顺序替换已注册的 V860 玩家列表项。
     * <p>
     * Replaces a registered V860 player-list entry using the safe REMOVE → ADD order.
     */
    public static boolean replacePlayerListEntry(Player viewer, PlayerListPacket.Entry entry) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(entry.uuid, "entry.uuid");

        if (!requiresRetainedEntry(viewer) || !viewer.sentSkins.contains(entry.uuid)) {
            return false;
        }

        PlayerListPacket remove = new PlayerListPacket();
        remove.type = PlayerListPacket.TYPE_REMOVE;
        remove.entries = new PlayerListPacket.Entry[]{new PlayerListPacket.Entry(entry.uuid)};
        if (!viewer.dataPacket(remove)) {
            return false;
        }
        viewer.sentSkins.remove(entry.uuid);

        PlayerListPacket add = new PlayerListPacket();
        add.type = PlayerListPacket.TYPE_ADD;
        add.entries = new PlayerListPacket.Entry[]{entry};
        if (!viewer.dataPacket(add)) {
            return false;
        }
        viewer.sentSkins.add(entry.uuid);
        return true;
    }

    /**
     * 移除玩家型实体的列表项并清理观察者状态。
     * <p>
     * Removes a player-like entity list entry and clears the viewer-side registration state.
     */
    public static void sendRemoveAndClear(Player viewer, UUID uuid) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(uuid, "uuid");

        if (!viewer.sentSkins.remove(uuid)) {
            return;
        }

        sendRemove(viewer, uuid);
    }

    private static void sendRemove(Player viewer, UUID uuid) {
        PlayerListPacket remove = new PlayerListPacket();
        remove.type = PlayerListPacket.TYPE_REMOVE;
        remove.entries = new PlayerListPacket.Entry[]{new PlayerListPacket.Entry(uuid)};
        viewer.dataPacket(remove);
    }

    /**
     * 握手后延迟移除 PlayerList 条目。与 {@link #sendRemoveAndClear} 互斥（先清 sentSkins 者胜），
     * 并用注册代次防止 despawn → 快速 re-spawn 后旧任务误删新条目。
     * <p>
     * Removes the entry after the handshake. Mutually exclusive with {@link #sendRemoveAndClear}
     * (whichever clears sentSkins wins); a generation check skips stale tasks after a re-spawn.
     */
    private static void scheduleDelayedRemove(Player viewer, UUID uuid) {
        AtomicLong generation = currentGeneration(viewer, uuid);
        long registeredAt = generation.get();
        Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, () -> {
            if (viewer.closed) {
                return;
            }
            // 互斥：despawn/close 已先清则跳过。
            if (!viewer.sentSkins.remove(uuid)) {
                return;
            }
            // 代次已变说明 re-spawn 注册了新条目，恢复 sentSkins 不发 REMOVE。
            if (generation.get() != registeredAt) {
                viewer.sentSkins.add(uuid);
                return;
            }
            sendRemove(viewer, uuid);
        }, DELAYED_REMOVE_TICKS);
    }

    private static AtomicLong currentGeneration(Player viewer, UUID uuid) {
        synchronized (REMOVE_GENERATIONS) {
            return REMOVE_GENERATIONS
                    .computeIfAbsent(viewer, v -> new ConcurrentHashMap<>())
                    .computeIfAbsent(uuid, u -> new AtomicLong());
        }
    }

    private static Skin createEmptyPlayerListSkin() {
        Skin skin = new Skin();
        skin.setSkinId(EMPTY_SKIN_ID);
        skin.setSkinData(new byte[Skin.SINGLE_SKIN_SIZE]);
        skin.setGeometryName("geometry.humanoid.custom");
        if (!skin.isValid()) {
            throw new IllegalStateException("Empty player list skin must be valid");
        }
        return skin;
    }
}
