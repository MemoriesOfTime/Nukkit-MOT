package cn.nukkit.utils;

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
