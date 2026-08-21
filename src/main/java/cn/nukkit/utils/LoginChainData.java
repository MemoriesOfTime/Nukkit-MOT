package cn.nukkit.utils;

import cn.nukkit.api.OnlyNetEase;
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

    /**
     * 网易客户端是否为断线重连。
     * <p>Whether the NetEase client is reconnecting after a disconnect.
     */
    @OnlyNetEase
    default boolean isNetEaseReconnect() {
        return false;
    }
}
