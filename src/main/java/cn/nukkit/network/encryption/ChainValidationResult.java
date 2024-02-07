package cn.nukkit.network.encryption;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.jose4j.json.JsonUtil;
import org.jose4j.lang.JoseException;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.nukkit.network.encryption.JsonUtils.childAsType;

public final class ChainValidationResult {
    private final boolean signed;
    private final Map<String, Object> parsedPayload;

    private IdentityClaims identityClaims;

    public ChainValidationResult(boolean signed, String rawPayload) throws JoseException {
        this(signed, JsonUtil.parseJson(rawPayload));
    }

    public ChainValidationResult(boolean signed, Map<String, Object> parsedPayload) {
        this.signed = signed;
        this.parsedPayload = Objects.requireNonNull(parsedPayload);
    }

    public boolean signed() {
        return signed;
    }

    public Map<String, Object> rawIdentityClaims() {
        return new HashMap<>(parsedPayload);
    }

    public IdentityClaims identityClaims() throws IllegalStateException {
        if (identityClaims == null) {
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

            identityClaims = new IdentityClaims(
                    new IdentityData(displayName, identity, xuid, (String) titleId),
                    identityPublicKey
            );
        }
        return identityClaims;
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
        public final UUID identity;
        public final String xuid;
        public final @Nullable String titleId;

        private IdentityData(String displayName, UUID identity, String xuid, @Nullable String titleId) {
            this.displayName = displayName;
            this.identity = identity;
            this.xuid = xuid;
            this.titleId = titleId;
        }
    }
}
