package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.nukkit.network.encryption.EncryptionUtils;
import cn.nukkit.network.protocol.LoginPacket;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/**
 * ClientChainData is a container of chain data sent from clients.
 * <p>
 * Device information such as client UUID, xuid and serverAddress, can be
 * read from instances of this object.
 * <p>
 * To get chain data, you can use player.getLoginChainData() or read(loginPacket)
 * <p>
 * ===============
 *
 * @author boybook
 * Nukkit Project
 * ===============
 */
public final class ClientChainData implements LoginChainData {

    public final static int UI_PROFILE_CLASSIC = 0;
    public final static int UI_PROFILE_POCKET = 1;
    private static final Gson GSON = new Gson();
    private final BinaryStream bs = new BinaryStream();
    private boolean xboxAuthed;
    private String username;
    private UUID clientUUID;
    private String xuid;
    private String identityPublicKey;
    private long clientId;
    private String serverAddress;
    private String deviceModel;
    private int deviceOS;
    private String deviceId;
    private String gameVersion;
    private int guiScale;
    private String languageCode;
    private int currentInputMode;
    private int defaultInputMode;
    private String waterdogIP;
    private String waterdogXUID;
    private int UIProfile;
    private String capeData;
    private JsonObject rawData;

    private ClientChainData(byte[] buffer) {
        bs.setBuffer(buffer, 0);
        decodeChainData();
        decodeSkinData();
    }

    public static ClientChainData of(byte[] buffer) {
        return new ClientChainData(buffer);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Override
    ///////////////////////////////////////////////////////////////////////////

    public static ClientChainData read(LoginPacket pk) {
        return of(pk.getBuffer());
    }

    private static ECPublicKey generateKey(String base64) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    ///////////////////////////////////////////////////////////////////////////
    // Internal
    ///////////////////////////////////////////////////////////////////////////

    private static JsonObject decodeToken(String token) {
        String[] base = token.split("\\.");
        if (base.length < 2) return null;
        return GSON.fromJson(new String(Base64.getDecoder().decode(base[1]), StandardCharsets.UTF_8), JsonObject.class);
    }

    private static boolean verifyChain(List<String> chains) throws Exception {
        try {
            return EncryptionUtils.validateChain(chains).signed();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public UUID getClientUUID() {
        return clientUUID;
    }

    @Override
    public String getIdentityPublicKey() {
        return identityPublicKey;
    }

    @Override
    public long getClientId() {
        return clientId;
    }

    @Override
    public String getServerAddress() {
        return serverAddress;
    }

    @Override
    public String getDeviceModel() {
        return deviceModel;
    }

    @Override
    public int getDeviceOS() {
        return deviceOS;
    }

    @Override
    public String getDeviceId() {
        return deviceId;
    }

    @Override
    public String getGameVersion() {
        return gameVersion;
    }

    @Override
    public int getGuiScale() {
        return guiScale;
    }

    @Override
    public String getLanguageCode() {
        return languageCode;
    }

    @Override
    public String getXUID() {
        if (this.isWaterdog()) {
            return waterdogXUID;
        } else {
            return xuid;
        }
    }

    @Override
    public int getCurrentInputMode() {
        return currentInputMode;
    }

    @Override
    public int getDefaultInputMode() {
        return defaultInputMode;
    }

    @Override
    public String getCapeData() {
        return capeData;
    }

    @Override
    public int getUIProfile() {
        return UIProfile;
    }

    @Override
    @Nullable
    public String getWaterdogXUID() {
        return waterdogXUID;
    }

    @Override
    @Nullable
    public String getWaterdogIP() {
        return waterdogIP;
    }

    @Override
    public JsonObject getRawData() {
        return rawData;
    }

    private boolean isWaterdog() {
        if (waterdogXUID == null || Server.getInstance() == null) {
            return false;
        }

        return Server.getInstance().isWaterdogCapable();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ClientChainData && Objects.equals(bs, ((ClientChainData) obj).bs);
    }

    @Override
    public int hashCode() {
        return bs.hashCode();
    }

    @Override
    public boolean isXboxAuthed() {
        return xboxAuthed;
    }

    private void decodeSkinData() {
        int size = bs.getLInt();
        if (size > 3000000) {
            throw new IllegalArgumentException("The skin data is too big: " + size);
        }
        JsonObject skinToken = decodeToken(new String(bs.get(size)));
        if (skinToken == null) return;
        if (skinToken.has("ClientRandomId")) this.clientId = skinToken.get("ClientRandomId").getAsLong();
        if (skinToken.has("ServerAddress")) this.serverAddress = skinToken.get("ServerAddress").getAsString();
        if (skinToken.has("DeviceModel")) this.deviceModel = skinToken.get("DeviceModel").getAsString();
        if (skinToken.has("DeviceOS")) this.deviceOS = skinToken.get("DeviceOS").getAsInt();
        if (skinToken.has("DeviceId")) this.deviceId = skinToken.get("DeviceId").getAsString();
        if (skinToken.has("GameVersion")) this.gameVersion = skinToken.get("GameVersion").getAsString();
        if (skinToken.has("GuiScale")) this.guiScale = skinToken.get("GuiScale").getAsInt();
        if (skinToken.has("LanguageCode")) this.languageCode = skinToken.get("LanguageCode").getAsString();
        if (skinToken.has("CurrentInputMode")) this.currentInputMode = skinToken.get("CurrentInputMode").getAsInt();
        if (skinToken.has("DefaultInputMode")) this.defaultInputMode = skinToken.get("DefaultInputMode").getAsInt();
        if (skinToken.has("UIProfile")) this.UIProfile = skinToken.get("UIProfile").getAsInt();
        if (skinToken.has("CapeData")) this.capeData = skinToken.get("CapeData").getAsString();
        if (skinToken.has("Waterdog_IP")) this.waterdogIP = skinToken.get("Waterdog_IP").getAsString();
        if (skinToken.has("Waterdog_XUID")) this.waterdogXUID = skinToken.get("Waterdog_XUID").getAsString();

        if (this.isWaterdog()) {
            xboxAuthed = true;
        }
        this.rawData = skinToken;
    }

    private void decodeChainData() {
        int size = bs.getLInt();
        if (size > 3000000) {
            throw new IllegalArgumentException("The chain data is too big: " + size);
        }
        Map<String, List<String>> map = GSON.fromJson(new String(bs.get(size), StandardCharsets.UTF_8), new MapTypeToken().getType());
        if (map.isEmpty() || !map.containsKey("chain") || map.get("chain").isEmpty()) return;
        List<String> chains = map.get("chain");

        // Validate keys
        try {
            xboxAuthed = verifyChain(chains);
        } catch (Exception e) {
            xboxAuthed = false;
        }

        for (String c : chains) {
            JsonObject chainMap = decodeToken(c);
            if (chainMap == null) continue;
            if (chainMap.has("extraData")) {
                JsonObject extra = chainMap.get("extraData").getAsJsonObject();
                if (extra.has("displayName")) this.username = extra.get("displayName").getAsString();
                if (extra.has("identity")) this.clientUUID = UUID.fromString(extra.get("identity").getAsString());
                if (extra.has("XUID")) this.xuid = extra.get("XUID").getAsString();
            }
            if (chainMap.has("identityPublicKey")) {
                this.identityPublicKey = chainMap.get("identityPublicKey").getAsString();
            }
        }

        if (!xboxAuthed) {
            xuid = null;
        }
    }

    private static class MapTypeToken extends TypeToken<Map<String, List<String>>> {
    }
}
