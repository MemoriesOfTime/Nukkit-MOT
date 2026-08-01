package cn.nukkit.utils;

import cn.nukkit.network.encryption.EncryptionUtils;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Login chain data
 *
 * @author CreeperFace
 */
public interface LoginChainData {

    String getUsername();

    UUID getClientUUID();

    /**
     * Resolve the identity used by the server after applying username normalization such as a
     * ViaProxy prefix. Authenticated identities remain account-derived; unauthenticated identities
     * must follow the final server-visible name to avoid sharing data with another player.
     *
     * @param normalizedUsername final username accepted by the server
     * @return identity UUID used for duplicate-login checks and player data
     */
    default UUID getClientUUID(String normalizedUsername) {
        return isXboxAuthed() ? getClientUUID() : EncryptionUtils.deriveOfflineIdentity(normalizedUsername);
    }

    /**
     * @return the player's Minecraft PlayFab ID
     */
    String getMinecraftId();

    String getIdentityPublicKey();

    long getClientId();

    String getServerAddress();

    String getDeviceModel();

    int getDeviceOS();

    String getDeviceId();

    String getGameVersion();

    int getGuiScale();

    String getLanguageCode();

    String getXUID();
   
    boolean isXboxAuthed();

    int getCurrentInputMode();

    int getDefaultInputMode();

    String getCapeData();

    int getUIProfile();

    /**
     * @return the XUID of the player, or null if the server is not using Waterdog
     */
    @Nullable
    String getWaterdogXUID();

    /**
     * @return the IP of the player, or null if the server is not using Waterdog
     */
    @Nullable
    String getWaterdogIP();

    /**
     * @return the ViaProxy auth token, or null if the client is not connecting through ViaProxy
     */
    @Nullable
    String getViaProxyAuthToken();

    JsonObject getRawData();

    String getTitleId();

    default Long getNetEaseUID() {
        return -1L;
    }

    default String getNetEaseSid() {
        return "";
    }

    default String getNetEasePlatform() {
        return "";
    }

    default String getNetEaseClientOsName() {
        return "";
    }

    default String getNetEaseClientBit() {
        return "";
    }

    default String getNetEaseClientEngineVersion() {
        return "";
    }

    default String getNetEaseClientPatchVersion() {
        return "";
    }

    default String getNetEaseEnv() {
        return "";
    }
}
