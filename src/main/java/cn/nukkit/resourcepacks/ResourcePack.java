package cn.nukkit.resourcepacks;

import cn.nukkit.GameVersion;

import java.util.UUID;

public interface ResourcePack {

    ResourcePack[] EMPTY_ARRAY = new ResourcePack[0];

    String getPackName();

    UUID getPackId();

    default int getPackProtocol() {
        return 0;
    }

    String getPackVersion();

    int getPackSize();

    byte[] getSha256();

    byte[] getPackChunk(int off, int len);

    default String getEncryptionKey() {
        return "";
    }

    default String getSubPackName() {
        return "";
    }

    default boolean usesScripting() {
        return false;
    }

    default boolean isBehaviourPack() {
        return false;
    }

    /**
     * @since v712 1.21.20
     */
    default boolean isAddonPack() {
        return false;
    }

    /**
     * @since v748 1.21.40
     */
    default String getCDNUrl() {
        return "";
    }

    /**
     * 设置资源包类型
     * <p>
     * Set the resource pack type (Microsoft, NetEase, or Universal)
     *
     * @param type the resource pack type
     */
    default void setSupportType(SupportType type) {

    }

    /**
     * 获取资源包类型
     * <p>
     * Get the resource pack type
     *
     * @return the resource pack type (defaults to UNIVERSAL for compatibility)
     */
    default SupportType getSupportType() {
        return SupportType.UNIVERSAL;
    }

    /**
     * @deprecated Use {@link #setSupportType(SupportType)} instead
     */
    @Deprecated
    default void setNetEase(boolean isNetEase) {
        setSupportType(isNetEase ? SupportType.NETEASE : SupportType.MICROSOFT);
    }

    /**
     * @deprecated Use {@link #getSupportType()} instead
     */
    @Deprecated
    default boolean isNetEase() {
        return getSupportType() == SupportType.NETEASE;
    }


    /**
     * Defines which Minecraft editions a resource pack supports
     */
    enum SupportType {
        /**
         * 资源包仅支持微软版
         * <p>
         * Resource pack only supports Microsoft (International) version
         */
        MICROSOFT,

        /**
         * 资源包仅支持网易版
         * <p>
         * Resource pack only supports NetEase (Chinese) version
         */
        NETEASE,

        /**
         * 资源包支持所有版本（包括微软和网易）
         * <p>
         * Resource pack supports all versions (both Microsoft and NetEase)
         */
        UNIVERSAL;

        /**
         * 检查是否兼容指定的游戏版本
         * <p>
         * Check if this support type is compatible with the given game version
         *
         * @param gameVersion the game version to check compatibility with
         * @return true if this support type is compatible with the given game version, false otherwise
         */
        public boolean isCompatibleWith(GameVersion gameVersion) {
            if (this == UNIVERSAL) {
                return true;
            }
            return gameVersion.isNetEase() ? this == NETEASE : this == MICROSOFT;
        }

        /**
         * 检查是否兼容指定的资源包类型
         * <p>
         * Check if this support type is compatible with the given pack type
         *
         * @param packType the pack type to check compatibility with
         * @return true if this support type is compatible with the given pack type, false otherwise
         */
        public boolean isCompatibleWith(SupportType packType) {
            return this == UNIVERSAL || this == packType;
        }
    }
}
