package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.nukkit.network.encryption.ChainValidationResult;
import cn.nukkit.network.encryption.EncryptionUtils;
import cn.nukkit.network.protocol.LoginPacket;
import cn.nukkit.network.protocol.types.auth.AuthPayload;
import cn.nukkit.network.protocol.types.auth.AuthType;
import cn.nukkit.network.protocol.types.auth.CertificateChainPayload;
import cn.nukkit.network.protocol.types.auth.TokenPayload;
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
 *
 * Device information such as client UUID, xuid and serverAddress, can be
 * read from instances of this object.
 *
 * To get chain data, you can use player.getLoginChainData() or read(loginPacket)
 *
 * ===============
 * @author boybook
 * Nukkit Project
 * ===============
 */
public final class ClientChainData implements LoginChainData {

    private static final Gson GSON = new Gson();

    public static ClientChainData of(byte[] buffer) {
        return new ClientChainData(buffer);
    }

    public static ClientChainData read(LoginPacket pk) {
        return of(pk.getBuffer());
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
    public String getMinecraftId() {
        return minecraftId;
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

    private boolean xboxAuthed;

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

    public final static int UI_PROFILE_CLASSIC = 0;
    public final static int UI_PROFILE_POCKET = 1;

    @Override
    public int getUIProfile() {
        return UIProfile;
    }

    @Override
    public String getTitleId() {
        return titleId;
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

    ///////////////////////////////////////////////////////////////////////////
    // Override
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ClientChainData && Objects.equals(bs, ((ClientChainData) obj).bs);
    }

    @Override
    public int hashCode() {
        return bs.hashCode();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Internal
    ///////////////////////////////////////////////////////////////////////////

    private AuthPayload authPayload;

    private String username;
    private UUID clientUUID;
    private String xuid;
    public String minecraftId;

    private static ECPublicKey generateKey(String base64) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

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
    private String titleId;

    private JsonObject rawData;

    private final BinaryStream bs = new BinaryStream();

    private ClientChainData(byte[] buffer) {
        bs.setBuffer(buffer, 0);
        decodeChainData();
        decodeSkinData();
    }

    @Override
    public boolean isXboxAuthed() {
        return xboxAuthed;
    }

    private void decodeSkinData() {
        int size = bs.getLInt();
        if (size > 52428800) {
            throw new TooBigSkinException("The skin data is too big: " + size);
        }
        JsonObject skinToken = decodeToken(new String(bs.get(size), StandardCharsets.UTF_8));
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

    public static JsonObject decodeToken(String token) {
        String[] base = token.split("\\.", 5);
        if (base.length < 2) return null;
        return GSON.fromJson(new String((Server.getInstance().netEaseMode ? Base64.getUrlDecoder() : Base64.getDecoder())
                .decode(base[1]), StandardCharsets.UTF_8), JsonObject.class);
    }

    protected AuthPayload readAuthJwt(String authJwt) {
        Map<String, Object> map = GSON.fromJson(authJwt, new MapTypeToken());

        AuthType authType = AuthType.UNKNOWN;
        if (map.containsKey("AuthenticationType")) { // >= v1.21.90
            int authTypeOrdinal = ((Number) map.get("AuthenticationType")).intValue();
            if (authTypeOrdinal < 0 || authTypeOrdinal >= AuthType.values().length - 1) {
                throw new IllegalArgumentException("Invalid AuthenticationType ordinal: " + authTypeOrdinal);
            }
            authType = AuthType.values()[authTypeOrdinal + 1];
        }

        if (map.containsKey("Token") && map.get("Token") instanceof String token && !((String) map.get("Token")).isBlank()) {
            return new TokenPayload(token, authType);
        } else {
            String certificate = (String) map.get("Certificate");
            if (certificate != null && !certificate.isBlank()) {
                map = GSON.fromJson(certificate, new MapTypeToken());
            }

            List<String> chains = (List<String>) map.get("chain");
            if (chains == null || chains.isEmpty()) {
                throw new IllegalArgumentException("Invalid Certificate chain in JWT");
            }

            return new CertificateChainPayload(chains, authType);
        }
    }

    private void decodeChainData() {
        int size = bs.getLInt();
        if (size > 52428800) {
            throw new IllegalArgumentException("The chain data is too big: " + size);
        }

        this.authPayload = this.readAuthJwt(new String(bs.get(size), StandardCharsets.UTF_8));

        try {
            ChainValidationResult result = EncryptionUtils.validatePayload(this.authPayload);

            this.xboxAuthed = result.signed();

            ChainValidationResult.IdentityData extraData = result.identityClaims().extraData;
            this.username = extraData.displayName;
            this.clientUUID = extraData.identity;
            this.xuid = extraData.xuid;
            this.minecraftId = extraData.minecraftId;

            this.titleId = extraData.titleId;

            this.identityPublicKey = result.identityClaims().identityPublicKey;

            if (!xboxAuthed) {
                xuid = null;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT: " + e.getMessage(), e);
        }
    }

    private static class MapTypeToken extends TypeToken<Map<String, Object>> {
    }

    public static class TooBigSkinException extends RuntimeException {

        public TooBigSkinException(String s) {
            super(s);
        }
    }
}
