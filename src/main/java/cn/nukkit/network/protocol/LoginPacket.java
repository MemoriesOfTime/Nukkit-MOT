package cn.nukkit.network.protocol;

import cn.nukkit.Server;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.network.encryption.EncryptionUtils;
import cn.nukkit.network.protocol.types.auth.AuthPayload;
import cn.nukkit.network.protocol.types.auth.AuthType;
import cn.nukkit.network.protocol.types.auth.CertificateChainPayload;
import cn.nukkit.network.protocol.types.auth.TokenPayload;
import cn.nukkit.utils.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.ToString;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtContext;

import java.nio.charset.StandardCharsets;
import java.util.*;

@ToString
public class LoginPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.LOGIN_PACKET;

    /**
     * The JWT payload signed by Minecraft's authentication server.
     * Assuming this is a valid signature, it can be trusted to contain the player's identity and other information.
     */
    private AuthPayload authPayload;

    public String username;
    private int protocol_;
    public UUID clientUUID;
    public String minecraftId;
    public long clientId;
    public Skin skin;

    private static final Gson GSON = new Gson();

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.protocol_ = this.getInt();
        if (this.protocol_ > ProtocolInfo.CURRENT_PROTOCOL + 1000) {
            int ofs = this.getOffset();
            this.setOffset(1);
            try {
                this.protocol_ = this.getInt();
                if (this.protocol_ >= ProtocolInfo.v1_2_0) {
                    throw new RuntimeException();
                }
                this.getByte(); //gameEdition
            } catch (Throwable th) {
                setOffset(ofs);
            }
        }
        if (this.protocol_ == 0) {
            setOffset(getOffset() + 2);
            this.protocol_ = getInt();
        }
        if (ProtocolInfo.SUPPORTED_PROTOCOLS.contains(this.protocol_)) { // Avoid errors with unsupported versions
            this.setBuffer(this.getByteArray(), 0);
            decodeChainData();
            decodeSkinData();
        }
    }

    @Override
    public void encode() {
    }

    public int getProtocol() {
        return protocol_;
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
        int size = this.getLInt();
        if (size > 3145728) {
            throw new IllegalArgumentException("The chain data is too big: " + size);
        }

        this.authPayload = this.readAuthJwt(new String(this.get(size), StandardCharsets.UTF_8));

        try {
            // 不要在这里验证数据，只读取必要的字段
            if (this.authPayload instanceof CertificateChainPayload chainPayload) {
                List<String> chain = chainPayload.getChain();
                if (chain == null || chain.isEmpty()) {
                    throw new IllegalStateException("Certificate chain is empty");
                }

                for (String c : chain) {
                    JsonObject chainMap = ClientChainData.decodeToken(c);
                    if (chainMap == null) continue;
                    if (chainMap.has("extraData")) {
                        JsonObject extra = chainMap.get("extraData").getAsJsonObject();
                        if (extra.has("displayName")) this.username = extra.get("displayName").getAsString();
                        if (extra.has("identity")) this.clientUUID = UUID.fromString(extra.get("identity").getAsString());
                    }
                }
            } else if (this.authPayload instanceof TokenPayload tokenPayload) {
                String token = tokenPayload.getToken();
                if (token == null || token.isEmpty()) {
                    throw new IllegalStateException("Token is empty");
                }

                JwtContext context = EncryptionUtils.OFFLINE_CONSUMER.process(token);
                JwtClaims claims = context.getJwtClaims();
                String xuid = claims.getClaimValueAsString("xid");
                this.username = claims.getClaimValueAsString("xname");
                this.clientUUID = UUID.nameUUIDFromBytes(("pocket-auth-1-xuid:" + xuid).getBytes(StandardCharsets.UTF_8));
                this.minecraftId = claims.getClaimValueAsString("mid");
            } else {
                throw new IllegalArgumentException("Unsupported AuthPayload type: " + this.authPayload.getClass().getName());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT: " + e.getMessage(), e);
        }
    }

    private void decodeSkinData() {
        int size = this.getLInt();
        if (size > 52428800) {
            Server.getInstance().getLogger().warning(username + ": The skin data is too big: " + size);
            return; // Get disconnected due to "invalid skin"
        }

        JsonObject skinToken = ClientChainData.decodeToken(new String(this.get(size), StandardCharsets.UTF_8));
        if (skinToken == null) throw new RuntimeException("Invalid null skin token");

        // 将1.19.62按1.19.63版本处理 修复1.19.62皮肤修改问题
        if (this.protocol_ == ProtocolInfo.v1_19_60 &&
                skinToken.has("GameVersion") && !skinToken.get("GameVersion").getAsString().startsWith("1.19.60")) {
            this.protocol_ = ProtocolInfo.v1_19_63;
        }

        if (skinToken.has("ClientRandomId")) {
            this.clientId = skinToken.get("ClientRandomId").getAsLong();
        }

        skin = new Skin();

        if (protocol_ < ProtocolInfo.v1_19_60) {
            if (skinToken.has("SkinId")) {
                skin.setSkinId(skinToken.get("SkinId").getAsString());
            }
        }

        if (protocol_ < 388) {
            if (skinToken.has("SkinData")) {
                skin.setSkinData(Base64.getDecoder().decode(skinToken.get("SkinData").getAsString()));
            }

            if (skinToken.has("CapeData")) {
                skin.setCapeData(Base64.getDecoder().decode(skinToken.get("CapeData").getAsString()));
            }

            if (skinToken.has("SkinGeometryName")) {
                skin.setGeometryName(skinToken.get("SkinGeometryName").getAsString());
            }

            if (skinToken.has("SkinGeometry")) {
                skin.setGeometryData(new String(Base64.getDecoder().decode(skinToken.get("SkinGeometry").getAsString()), StandardCharsets.UTF_8));
            }
        } else {
            if (skinToken.has("PlayFabId")) {
                skin.setPlayFabId(skinToken.get("PlayFabId").getAsString());
            }

            if (skinToken.has("CapeId")) {
                skin.setCapeId(skinToken.get("CapeId").getAsString());
            }

            if (protocol_ >= ProtocolInfo.v1_19_60) {
                if (skinToken.has("SkinId")) {
                    //这边获取到的"SkinId"是FullId
                    //FullId = SkinId + CapeId
                    //而Skin对象中的skinId不是FullId,我们需要减掉CapeId
                    String fullSkinId = skinToken.get("SkinId").getAsString();
                    skin.setFullSkinId(fullSkinId);
                    if (skin.getCapeId() != null) {
                        skin.setSkinId(fullSkinId.substring(0, fullSkinId.length() - skin.getCapeId().length()));
                    }else {
                        skin.setSkinId(fullSkinId);
                    }
                }
            }

            skin.setSkinData(getImage(skinToken, "Skin"));
            skin.setCapeData(getImage(skinToken, "Cape"));

            if (skinToken.has("PremiumSkin")) {
                skin.setPremium(skinToken.get("PremiumSkin").getAsBoolean());
            }

            if (skinToken.has("PersonaSkin")) {
                skin.setPersona(skinToken.get("PersonaSkin").getAsBoolean());
            }

            if (skinToken.has("CapeOnClassicSkin")) {
                skin.setCapeOnClassic(skinToken.get("CapeOnClassicSkin").getAsBoolean());
            }

            if (skinToken.has("SkinResourcePatch")) {
                skin.setSkinResourcePatch(new String(Base64.getDecoder().decode(skinToken.get("SkinResourcePatch").getAsString()), StandardCharsets.UTF_8));
            }

            if (skinToken.has("SkinGeometryData")) {
                skin.setGeometryData(new String(Base64.getDecoder().decode(skinToken.get("SkinGeometryData").getAsString()), StandardCharsets.UTF_8));
            }

            if (skinToken.has("SkinAnimationData")) {
                skin.setAnimationData(new String(Base64.getDecoder().decode(skinToken.get("SkinAnimationData").getAsString()), StandardCharsets.UTF_8));
            }

            if (skinToken.has("AnimatedImageData")) {
                for (JsonElement element : skinToken.get("AnimatedImageData").getAsJsonArray()) {
                    skin.getAnimations().add(getAnimation(protocol_, element.getAsJsonObject()));
                }
            }

            if (skinToken.has("SkinColor")) {
                skin.setSkinColor(skinToken.get("SkinColor").getAsString());
            }

            if (skinToken.has("ArmSize")) {
                skin.setArmSize(skinToken.get("ArmSize").getAsString());
            }

            if (skinToken.has("PersonaPieces")) {
                for (JsonElement object : skinToken.get("PersonaPieces").getAsJsonArray()) {
                    skin.getPersonaPieces().add(getPersonaPiece(object.getAsJsonObject()));
                }
            }

            if (skinToken.has("PieceTintColors")) {
                for (JsonElement object : skinToken.get("PieceTintColors").getAsJsonArray()) {
                    skin.getTintColors().add(getTint(object.getAsJsonObject()));
                }
            }
        }
    }

    private static SkinAnimation getAnimation(int protocol, JsonObject element) {
        float frames = element.get("Frames").getAsFloat();
        int type = element.get("Type").getAsInt();
        byte[] data = Base64.getDecoder().decode(element.get("Image").getAsString());
        int width = element.get("ImageWidth").getAsInt();
        int height = element.get("ImageHeight").getAsInt();
        int expression = protocol >= ProtocolInfo.v1_16_100 ? element.get("AnimationExpression").getAsInt() : 0;
        return new SkinAnimation(new SerializedImage(width, height, data), type, frames, expression);
    }

    private static SerializedImage getImage(JsonObject token, String name) {
        if (token.has(name + "Data")) {
            byte[] skinImage = Base64.getDecoder().decode(token.get(name + "Data").getAsString());
            if (token.has(name + "ImageHeight") && token.has(name + "ImageWidth")) {
                int width = token.get(name + "ImageWidth").getAsInt();
                int height = token.get(name + "ImageHeight").getAsInt();
                return new SerializedImage(width, height, skinImage);
            } else {
                return SerializedImage.fromLegacy(skinImage);
            }
        }
        return SerializedImage.EMPTY;
    }

    private static PersonaPiece getPersonaPiece(JsonObject object) {
        String pieceId = object.get("PieceId").getAsString();
        String pieceType = object.get("PieceType").getAsString();
        String packId = object.get("PackId").getAsString();
        boolean isDefault = object.get("IsDefault").getAsBoolean();
        String productId = object.get("ProductId").getAsString();
        return new PersonaPiece(pieceId, pieceType, packId, isDefault, productId);
    }

    public static PersonaPieceTint getTint(JsonObject object) {
        String pieceType = object.get("PieceType").getAsString();
        List<String> colors = new ArrayList<>();
        for (JsonElement element : object.get("Colors").getAsJsonArray()) {
            colors.add(element.getAsString()); // remove #
        }
        return new PersonaPieceTint(pieceType, colors);
    }

    private static class MapTypeToken extends TypeToken<Map<String, Object>> {
    }
}