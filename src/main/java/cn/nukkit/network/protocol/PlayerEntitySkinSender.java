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
            unregister(viewer, uuid);
            return false;
        }

        PlayerSkinPacket update = new PlayerSkinPacket();
        update.uuid = uuid;
        update.skin = skin;
        update.newSkinName = skin.getSkinId();
        update.oldSkinName = "";
        if (!viewer.dataPacket(update)) {
            unregister(viewer, uuid);
            sendRemove(viewer, uuid);
            return false;
        }

        // 递增代次，使此前排队的旧延迟 REMOVE 任务失效。
        currentGeneration(viewer, uuid).incrementAndGet();
        scheduleDelayedRemove(viewer, uuid);
        return true;
    }

    /**
     * Registers a player-like entity for a client that applies the skin straight from the
     * player-list entry: ADD carries the real skin, and the entry is removed after
     * {@link #DELAYED_REMOVE_TICKS}.
     * <p>
     * The V860 handshake cannot be used here: its ADD entry carries a blank skin and relies on a
     * follow-up {@link PlayerSkinPacket}, which international clients ignore for an entity that has
     * not spawned yet, leaving the doll with the default player skin.
     */
    public static boolean sendSkinnedEntryIfAbsent(Player viewer, UUID uuid, long entityId,
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
                new PlayerListPacket.Entry(uuid, entityId, name, skin, xboxUserId)
        };
        if (!viewer.dataPacket(add)) {
            unregister(viewer, uuid);
            return false;
        }

        currentGeneration(viewer, uuid).incrementAndGet();
        scheduleDelayedRemove(viewer, uuid);
        return true;
    }

    /**
     * Sends the real skin of a player-like entity right after its spawn packet.
     * <p>
     * The player-list entry only reserves the identity; the client applies a skin to an entity it
     * already knows about, so this update has to travel after AddPlayerPacket.
     */
    public static void sendSkinAfterSpawn(Player viewer, UUID uuid, Skin skin) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(uuid, "uuid");
        if (skin == null || !skin.isValid()) {
            return;
        }

        PlayerSkinPacket update = new PlayerSkinPacket();
        update.uuid = uuid;
        update.skin = skin;
        update.newSkinName = skin.getSkinId();
        update.oldSkinName = "";
        viewer.dataPacket(update);
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
        unregister(viewer, entry.uuid);

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

        if (!unregister(viewer, uuid)) {
            return;
        }

        sendRemove(viewer, uuid);
    }

    /**
     * 网易 V860 对同一列表项重复确认皮肤会隐藏该实体：指纹相同则抑制，指纹变化则先 REMOVE→ADD 重建条目。
     * 仅做决策与列表项维护，确认包仍由调用方下发。
     * <p>
     * NetEase V860 hides an entity when the same list entry is confirmed twice; an identical
     * fingerprint is suppressed and a changed one rebuilds the entry via REMOVE → ADD first.
     * This only decides and maintains the entry — the caller still sends the confirmation packet.
     *
     * @param skin 即将确认的皮肤，须同时等于确认包内容与 {@code subject} 当前皮肤；指纹变化时它也是
     *             重建 PlayerList ADD 条目的皮肤，三者必须一致，否则过渡期会出现渲染闪烁或实体隐形。
     *             <p>Skin about to be confirmed; must equal both the confirmation-packet payload and
     *             {@code subject}'s current skin. It also seeds the rebuilt PlayerList ADD entry when the
     *             fingerprint changes, so all three must match — otherwise the handshake flickers or hides
     *             the entity during the transition.
     * @return 调用方是否应继续下发确认包
     */
    public static boolean prepareConfirmSkin(Player viewer, UUID subject, Skin skin) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(skin, "skin");

        if (!requiresRetainedEntry(viewer)) {
            return true;
        }
        if (!isConfirmable(viewer, subject)) {
            return false;
        }

        String fingerprint = skin.getContentFingerprint();
        String previous = viewer.confirmedSkins.get(subject);
        if (fingerprint.equals(previous)) {
            return false;
        }
        if (previous != null) {
            Player target = Server.getInstance().getPlayer(subject).orElse(null);
            if (target == null) {
                return false;
            }
            // 重建条目皮肤须用传入的 skin（与确认包同源），不得改用 target.getSkin()：
            // 二者不一致会让 ADD 与随后的 ConfirmSkinPacket 各渲染一次，过渡期闪烁甚至隐形。
            // <p>The rebuilt entry must carry the passed-in skin (same source as the confirmation
            // packet), never target.getSkin(): a mismatch renders once per packet and flickers or
            // hides the entity during the handshake.
            PlayerListPacket.Entry entry = new PlayerListPacket.Entry(subject, target.getId(),
                    target.getDisplayName(), skin, target.getLoginChainData().getXUID(),
                    target.getLocatorBarColor());
            if (!replacePlayerListEntry(viewer, entry)) {
                return false;
            }
        }
        viewer.confirmedSkins.put(subject, fingerprint);
        return true;
    }

    /**
     * 观察者是否仍缺该玩家的皮肤确认：条目与实体都已就绪，但从未确认过。
     * 不计算皮肤指纹，可供调用方按 tick 轮询补发。
     * <p>
     * Whether the viewer is still missing this player's skin confirmation: both the entry and the
     * entity are in place but nothing has been confirmed yet. It never hashes the skin, so callers
     * can poll it every few ticks to re-drive pending confirmations.
     */
    public static boolean needsSkinConfirmation(Player viewer, UUID subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");

        return requiresRetainedEntry(viewer)
                && isConfirmable(viewer, subject)
                && !viewer.confirmedSkins.containsKey(subject);
    }

    /**
     * 客户端此刻能否应用确认包：必须已有 PlayerList 条目，且该玩家实体已生成到观察者。
     * 缺任一项客户端都会静默丢弃该条目，而我们已记下指纹便再不重发，因此必须提前拦下。
     * 条目不存在时顺带清掉残留指纹，避免条目重建后首次确认被误抑制。
     * <p>
     * Whether the client can apply a confirmation right now: it needs both the PlayerList entry and
     * the player entity spawned to this viewer. Missing either makes the client drop the entry
     * silently while we would have recorded the fingerprint and never resent it. A missing entry
     * also clears any stale fingerprint so the first confirmation after a rebuild isn't suppressed.
     */
    private static boolean isConfirmable(Player viewer, UUID subject) {
        if (!viewer.sentSkins.contains(subject)) {
            viewer.confirmedSkins.remove(subject);
            return false;
        }
        Player target = Server.getInstance().getPlayer(subject).orElse(null);
        return target != null && target.hasSpawned.containsKey(viewer.getLoaderId());
    }

    /**
     * 撤销观察者对该 UUID 的注册，皮肤确认指纹与列表项登记必须同进同退。
     * <p>
     * Drops the viewer's registration; the confirmed-skin fingerprint and the list entry
     * registration must always be cleared together.
     *
     * @return 撤销前是否确实存在登记
     */
    private static boolean unregister(Player viewer, UUID uuid) {
        viewer.confirmedSkins.remove(uuid);
        return viewer.sentSkins.remove(uuid);
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
            if (!unregister(viewer, uuid)) {
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
