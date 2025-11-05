package cn.nukkit.network.encryption;

import cn.nukkit.Server;
import lombok.extern.log4j.Log4j2;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.lang.JoseException;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.nukkit.network.encryption.JsonUtils.childAsType;

@Log4j2
public final class ChainValidationResult {
    private final boolean signed;
    private final Map<String, Object> parsedPayload;
    private final JwtContext jwtContext;

    private IdentityClaims identityClaims;

    public ChainValidationResult(boolean signed, String rawPayload) throws JoseException {
        this(signed, JsonUtil.parseJson(rawPayload));
    }

    public ChainValidationResult(boolean signed, Map<String, Object> parsedPayload) {
        this.signed = signed;
        this.parsedPayload = Objects.requireNonNull(parsedPayload);
        this.jwtContext = null;
    }

    public ChainValidationResult(boolean signed, JwtContext context) {
        this.signed = signed;
        this.jwtContext = Objects.requireNonNull(context);
        this.parsedPayload = null;
    }

    public boolean signed() {
        return signed;
    }

    public Map<String, Object> rawIdentityClaims() {
        if (parsedPayload == null) {
            return jwtContext.getJwtClaims().getClaimsMap();
        } else {
            return new HashMap<>(parsedPayload);
        }
    }

    public IdentityClaims identityClaims() throws IllegalStateException {
        if (identityClaims == null) {
            if (parsedPayload == null) {
                identityClaims = createClaims();
            } else {
                identityClaims = createLegacyClaims();
            }
        }
        return identityClaims;
    }

    private IdentityClaims createLegacyClaims() {
        String identityPublicKey = childAsType(parsedPayload, "identityPublicKey", String.class);
        Map<?, ?> extraData = childAsType(parsedPayload, "extraData", Map.class);

        String displayName = childAsType(extraData, "displayName", String.class);
        String identityString = childAsType(extraData, "identity", String.class);
        String xuid = childAsType(extraData, "XUID", String.class);
        Object titleId = extraData.get("titleId");

        UUID identity;
        try {
            identity = UUID.fromString(identityString);
        } catch (Exception exception) {
            throw new IllegalStateException("identity node is an invalid UUID");
        }

        Long neteaseUid = null;
        String neteaseSid = null;
        String neteasePlatform = null;
        String neteaseClientOsName = null;
        String neteaseClientOsVersion = null;
        String neteaseEnv = null;
        String neteaseClientEngineVersion = null;
        String neteaseClientPatchVersion = null;
        String neteaseClientBit = null;
        if (Server.getInstance().netEaseMode) {
            try {
                neteaseUid = childAsType(extraData, "uid", Long.class);
                neteaseSid = childAsType(extraData, "netease_sid", String.class);
                neteasePlatform = childAsType(extraData, "platform", String.class);
                neteaseClientOsName = childAsType(extraData, "os_name", String.class);
                neteaseEnv = childAsType(extraData, "env", String.class);
                neteaseClientEngineVersion = childAsType(extraData, "engineVersion", String.class);
                neteaseClientPatchVersion = childAsType(extraData, "patchVersion", String.class);
                neteaseClientBit = childAsType(extraData, "bit", String.class);
            } catch (Exception exception) {
                log.debug("Failed to parse netease data from extraData", exception);
            }
        }

        return new IdentityClaims(
                new IdentityData(displayName, identity, xuid, (String) titleId, null,
                        neteaseUid, neteaseSid, neteasePlatform, neteaseClientOsName, neteaseEnv,
                        neteaseClientEngineVersion, neteaseClientPatchVersion, neteaseClientBit),
                identityPublicKey
        );
    }

    private IdentityClaims createClaims() {
        JwtClaims claims = jwtContext.getJwtClaims();

        String identityPublicKey = claims.getClaimValueAsString("cpk");
        String displayName = claims.getClaimValueAsString("xname");
        String xuid = claims.getClaimValueAsString("xid");
        String minecraftId = claims.getClaimValueAsString("mid");
        UUID identity = UUID.nameUUIDFromBytes(("pocket-auth-1-xuid:" + xuid).getBytes(StandardCharsets.UTF_8));

        return new IdentityClaims(
                new IdentityData(displayName, identity, xuid, null, minecraftId,
                        null, null, null, null,
                        null,null, null, null),
                identityPublicKey
        );
    }

    public static final class IdentityClaims {
        public final IdentityData extraData;
        public final String identityPublicKey;
        private PublicKey parsedIdentityPublicKey;

        private IdentityClaims(IdentityData extraData, String identityPublicKey) {
            this.extraData = extraData;
            this.identityPublicKey = identityPublicKey;
        }

        public PublicKey parsedIdentityPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
            if (parsedIdentityPublicKey == null) {
                parsedIdentityPublicKey = EncryptionUtils.parseKey(identityPublicKey);
            }
            return parsedIdentityPublicKey;
        }
    }

    public static final class IdentityData {
        public final String displayName;
        /**
         * Identity UUID, derived from the XUID when online, or from the username when offline.
         * @deprecated v818: Use {@link #minecraftId} instead.
         */
        @Nullable
        public final UUID identity;
        public final String xuid;
        public final @Nullable String titleId;
        /**
         * The player's Minecraft PlayFab ID
         * @since v818
         */
        @Nullable
        public final String minecraftId;

        @Nullable
        public Long neteaseUid;
        @Nullable
        public String neteaseSid;
        @Nullable
        public String neteasePlatform;
        @Nullable
        public String neteaseClientOsName;
        @Nullable
        public String neteaseEnv;
        @Nullable
        public String neteaseClientEngineVersion;
        @Nullable
        public String neteaseClientPatchVersion;
        @Nullable
        public String neteaseClientBit;

        private IdentityData(String displayName, @Nullable UUID identity, String xuid, @Nullable String titleId, @Nullable String minecraftId,
                             @Nullable Long neteaseUid, @Nullable String neteaseSid, @Nullable String neteasePlatform, @Nullable String neteaseClientOsName,
                             @Nullable String neteaseEnv, @Nullable String neteaseClientEngineVersion, @Nullable String neteaseClientPatchVersion, @Nullable String neteaseClientBit) {
            this.displayName = displayName;
            this.identity = identity;
            this.xuid = xuid;
            this.titleId = titleId;
            this.minecraftId = minecraftId;

            this.neteaseUid = neteaseUid;
            this.neteaseSid = neteaseSid;
            this.neteasePlatform = neteasePlatform;
            this.neteaseClientOsName = neteaseClientOsName;
            this.neteaseEnv = neteaseEnv;
            this.neteaseClientEngineVersion = neteaseClientEngineVersion;
            this.neteaseClientPatchVersion = neteaseClientPatchVersion;
            this.neteaseClientBit = neteaseClientBit;
        }
    }
}
