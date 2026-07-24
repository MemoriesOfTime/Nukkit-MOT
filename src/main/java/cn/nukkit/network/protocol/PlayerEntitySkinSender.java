package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.entity.data.Skin;

import java.util.Objects;
import java.util.UUID;

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

    private PlayerEntitySkinSender() {
    }

    /**
     * 判断该观察者是否需要保留玩家型 NPC 的列表项。
     * <p>
     * Returns whether player-like NPC list entries must be retained for this viewer.
     */
    public static boolean requiresRetainedEntry(Player viewer) {
        return viewer.getGameVersion() == GameVersion.V1_21_124_NETEASE;
    }

    /**
     * 首次注册皮肤；已注册的 UUID 不会重复发送 ADD。
     * <p>
     * Registers a skin once and suppresses duplicate ADD packets for an existing UUID.
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
