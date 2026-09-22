package cn.nukkit.network.encryption;

import com.google.gson.Gson;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Identity derivation contract for the v1.21.90+ token login payload.
 * <p>
 * Xbox authenticated logins (including split screen guests) keep the identity produced here,
 * so a token that carries no XUID must still yield a value unique to the player.
 */
class ChainValidationResultIdentityTest {

    private static final Gson GSON = new Gson();
    private static final KeyPair KEY_PAIR = EncryptionUtils.createKeyPair();

    @Test
    void xboxAuthedIdentityStaysXuidDerived() throws Exception {
        String xuid = "2535412345678901";

        UUID identity = identityOf(claims("Ranel2220", xuid, "playfab-a"));

        assertEquals(UUID.nameUUIDFromBytes(("pocket-auth-1-xuid:" + xuid).getBytes(StandardCharsets.UTF_8)),
                identity, "authenticated identities must not change");
    }

    @Test
    void guestsWithoutXuidFallBackToMinecraftId() throws Exception {
        UUID first = identityOf(claims("Guest", null, "playfab-a"));
        UUID second = identityOf(claims("Guest", null, "playfab-b"));

        assertNotEquals(first, second,
                "guests share no XUID, their PlayFab id must keep them apart");
    }

    @Test
    void blankXuidIsTreatedAsMissing() throws Exception {
        UUID blank = identityOf(claims("Guest", "", "playfab-a"));
        UUID absent = identityOf(claims("Guest", null, "playfab-a"));

        assertEquals(absent, blank, "an empty XUID identifies nobody");
    }

    ///////////////////////////////////////////////////////////////////////////
    // Helpers
    ///////////////////////////////////////////////////////////////////////////

    /** Authenticated token payloads reach {@code createClaims} with {@code signed = true}. */
    private static UUID identityOf(Map<String, Object> claims) throws InvalidJwtException {
        return new ChainValidationResult(true, EncryptionUtils.OFFLINE_CONSUMER.process(sign(claims)))
                .identityClaims().extraData.identity;
    }

    private static Map<String, Object> claims(String displayName, String xuid, String minecraftId) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("cpk", Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded()));
        claims.put("xname", displayName);
        claims.put("exp", System.currentTimeMillis() / 1000L + 3600L);
        if (xuid != null) {
            claims.put("xid", xuid);
        }
        if (minecraftId != null) {
            claims.put("mid", minecraftId);
        }
        return claims;
    }

    private static String sign(Map<String, Object> claims) {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setAlgorithmHeaderValue(EncryptionUtils.ALGORITHM_TYPE);
        jws.setKey(KEY_PAIR.getPrivate());
        jws.setPayload(GSON.toJson(claims));
        try {
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new IllegalStateException("Failed to sign test JWT", e);
        }
    }
}
