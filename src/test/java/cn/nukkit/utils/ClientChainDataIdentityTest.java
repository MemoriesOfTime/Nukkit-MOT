package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.network.encryption.EncryptionUtils;
import com.google.gson.Gson;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Identity derivation contract for {@link ClientChainData}.
 * <p>
 * A player who is not authenticated with Xbox Live must keep the same identity UUID no matter
 * which login chain format their client sends, otherwise updating the client swaps the player
 * data file out from under them.
 */
class ClientChainDataIdentityTest {

    private static final Gson GSON = new Gson();

    @BeforeEach
    void setUp() {
        MockServer.reset();
    }

    @Test
    void offlineIdentityIsStableAcrossLoginChainFormats() {
        String username = "Ranel2220";

        ClientChainData legacy = ClientChainData.of(legacyChainLogin(username, UUID.randomUUID()));
        ClientChainData token = ClientChainData.of(selfSignedTokenLogin(username, null, null));

        assertEquals(legacy.getClientUUID(), token.getClientUUID(),
                "offline identity must not depend on the login chain format");
    }

    @Test
    void offlinePlayersWithDifferentNamesGetDistinctIdentities() {
        ClientChainData first = ClientChainData.of(selfSignedTokenLogin("Ranel2220", null, null));
        ClientChainData second = ClientChainData.of(selfSignedTokenLogin("fonce", null, null));

        assertNotEquals(first.getClientUUID(), second.getClientUUID(),
                "distinct offline players must never share an identity");
    }

    @Test
    void offlineIdentityIsStableAcrossSessions() {
        String username = "Trener7162";

        ClientChainData firstSession = ClientChainData.of(legacyChainLogin(username, UUID.randomUUID()));
        ClientChainData secondSession = ClientChainData.of(legacyChainLogin(username, UUID.randomUUID()));

        assertEquals(firstSession.getClientUUID(), secondSession.getClientUUID(),
                "offline identity must survive a client reinstall or device change");
    }

    @Test
    void offlineViaProxyIdentityUsesTheFinalPrefixedUsername() {
        ClientChainData data = ClientChainData.of(selfSignedTokenLogin("Steve", null, null,
                Map.of("ViaProxyAuthToken", "proxy-token")));

        assertFalse(data.isXboxAuthed());
        assertEquals(EncryptionUtils.deriveOfflineIdentity("java_Steve"), data.getClientUUID("java_Steve"));
        assertNotEquals(data.getClientUUID(), data.getClientUUID("java_Steve"),
                "the proxy prefix must separate Java and native offline players");
    }

    @Test
    void proxiedIdentityIsNotOverwritten() {
        Mockito.when(MockServer.get().isWaterdogCapable()).thenReturn(true);
        UUID proxiedIdentity = UUID.randomUUID();

        ClientChainData data = ClientChainData.of(
                waterdogLogin("derakt228", proxiedIdentity, "2535412345678901"));

        assertEquals(proxiedIdentity, data.getClientUUID(),
                "a proxy already validated the player, its identity must be preserved");
    }

    @Test
    void loginWithoutAnyIdentifyingClaimIsRejected() {
        // Unusable chain data is reported as an invalid JWT, same as any other malformed login.
        assertThrows(IllegalArgumentException.class,
                () -> ClientChainData.of(selfSignedTokenLogin(null, null, null)),
                "a client we cannot tell apart must be refused, not folded onto a shared identity");
    }

    @Test
    void legacyChainWithBlankDisplayNameIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> ClientChainData.of(legacyChainLogin("", UUID.randomUUID())));
    }

    ///////////////////////////////////////////////////////////////////////////
    // Login buffer builders
    ///////////////////////////////////////////////////////////////////////////

    /** Pre-v1.21.90 format: a single self-signed certificate carrying a client generated identity. */
    private static byte[] legacyChainLogin(String displayName, UUID clientIdentity) {
        Map<String, Object> extraData = new LinkedHashMap<>();
        extraData.put("displayName", displayName);
        extraData.put("identity", clientIdentity.toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identityPublicKey", publicKeyBase64());
        payload.put("extraData", extraData);

        return loginBuffer(GSON.toJson(Map.of("chain", List.of(sign(payload)))));
    }

    /** v1.21.90+ format: a self-signed token with no XUID and no client generated identity. */
    private static byte[] selfSignedTokenLogin(String displayName, String xuid, String minecraftId) {
        return selfSignedTokenLogin(displayName, xuid, minecraftId, Map.of());
    }

    private static byte[] selfSignedTokenLogin(String displayName, String xuid, String minecraftId,
                                               Map<String, Object> skinClaims) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cpk", publicKeyBase64());
        if (displayName != null) {
            payload.put("xname", displayName);
        }
        payload.put("exp", System.currentTimeMillis() / 1000L + 3600L);
        if (xuid != null) {
            payload.put("xid", xuid);
        }
        if (minecraftId != null) {
            payload.put("mid", minecraftId);
        }

        Map<String, Object> chainData = new LinkedHashMap<>();
        chainData.put("AuthenticationType", 2); // SELF_SIGNED
        chainData.put("Token", sign(payload));

        return loginBuffer(GSON.toJson(chainData), skinClaims);
    }

    private static byte[] loginBuffer(String chainData) {
        return loginBuffer(chainData, Map.of());
    }

    private static byte[] loginBuffer(String chainData, Map<String, Object> skinClaims) {
        BinaryStream stream = new BinaryStream();
        byte[] chainBytes = chainData.getBytes(StandardCharsets.UTF_8);
        stream.putLInt(chainBytes.length);
        stream.put(chainBytes);

        byte[] skinBytes = skinToken(skinClaims).getBytes(StandardCharsets.UTF_8);
        stream.putLInt(skinBytes.length);
        stream.put(skinBytes);
        return stream.getBuffer();
    }

    /** WaterdogPE forwards an unsigned single entry chain but vouches for the player in the skin token. */
    private static byte[] waterdogLogin(String displayName, UUID proxiedIdentity, String xuid) {
        Map<String, Object> extraData = new LinkedHashMap<>();
        extraData.put("displayName", displayName);
        extraData.put("identity", proxiedIdentity.toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identityPublicKey", publicKeyBase64());
        payload.put("extraData", extraData);

        return loginBuffer(GSON.toJson(Map.of("chain", List.of(sign(payload)))),
                Map.of("Waterdog_IP", "203.0.113.7", "Waterdog_XUID", xuid));
    }

    /** Minimal skin token: {@link ClientChainData#decodeToken} only reads the payload segment. */
    private static String skinToken(Map<String, Object> claims) {
        String payload = Base64.getEncoder()
                .encodeToString(GSON.toJson(claims).getBytes(StandardCharsets.UTF_8));
        return "header." + payload + ".signature";
    }

    ///////////////////////////////////////////////////////////////////////////
    // JWT helpers
    ///////////////////////////////////////////////////////////////////////////

    private static final KeyPair KEY_PAIR = EncryptionUtils.createKeyPair();

    private static String sign(Map<String, Object> payload) {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setAlgorithmHeaderValue(EncryptionUtils.ALGORITHM_TYPE);
        jws.setKey(KEY_PAIR.getPrivate());
        jws.setPayload(GSON.toJson(payload));
        try {
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new IllegalStateException("Failed to sign test JWT", e);
        }
    }

    private static String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());
    }
}
