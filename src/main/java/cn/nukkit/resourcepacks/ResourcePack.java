package cn.nukkit.resourcepacks;

import java.util.UUID;

public interface ResourcePack {

    ResourcePack[] EMPTY_ARRAY = new ResourcePack[0];

    String getPackName();

    UUID getPackId();

    Integer getPackProtocol();

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

    default boolean isAddonPack() {
        return false;
    }

    /**
     * @since v748 1.21.40
     */
    default String getCDNUrl() {
        return "";
    }
}
