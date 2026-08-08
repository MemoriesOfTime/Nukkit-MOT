package cn.nukkit;

import cn.nukkit.entity.data.Skin;
import cn.nukkit.network.protocol.LoginPacket;
import cn.nukkit.utils.Binary;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归测试：从 JWT 字节构造 LoginPacket，模拟 character creator（皮肤创建器）皮肤登录，
 * 覆盖 LoginPacket.decode → Skin 构造 → isValid → putSkin/getSkin(v2168) 完整链路。
 * <p>
 * 关键覆盖点：PersonaPieces[].PieceType 的 serializeName（"body"）与 type（"persona_body"）
 * 两种格式都必须正确映射到 ordinal，否则 character creator 皮肤下发的 persona pieces 全部
 * 塌缩为 UNKNOWN，客户端校验失败导致玩家被踢。
 * <p>
 * Regression: build LoginPacket from JWT bytes simulating a character-creator (persona) skin
 * login, exercising the LoginPacket.decode → Skin → isValid → putSkin/getSkin(v2168) pipeline.
 */
class CharacterCreatorLoginJwtTest {

    @BeforeEach
    void setUp() {
        MockServer.reset();
    }

    @AfterEach
    void tearDown() {
        MockServer.reset();
    }

    private static final Gson GSON = new Gson();

    /**
     * 构造一个无签名的 JWT（header.payload.signature，签名占位）。
     * payload 内容以标准 Base64 编码（非 URL-safe），匹配 ClientChainData.decodeToken。
     */
    private static String fakeJwt(JsonObject payload) {
        JsonObject header = new JsonObject();
        header.addProperty("alg", "none");
        header.addProperty("typ", "JWT");
        String headerB64 = Base64.getEncoder().encodeToString(GSON.toJson(header).getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getEncoder().encodeToString(GSON.toJson(payload).getBytes(StandardCharsets.UTF_8));
        return headerB64 + "." + payloadB64 + ".";
    }

    /**
     * 构造一个真实结构的 character creator (persona) 皮肤 JWT payload。
     * 字段名匹配 LoginPacket.decodeSkinData 的读取逻辑。
     */
    private static JsonObject buildPersonaSkinPayload() {
        JsonObject skin = new JsonObject();
        skin.addProperty("ClientRandomId", 1234567890L);
        skin.addProperty("ServerAddress", "127.0.0.1:19132");
        skin.addProperty("DeviceModel", "PC");
        skin.addProperty("DeviceOS", 1);
        skin.addProperty("DeviceId", "test-device-id");
        skin.addProperty("GameVersion", "1.26.40");
        skin.addProperty("GuiScale", 0);
        skin.addProperty("LanguageCode", "en_US");
        skin.addProperty("CurrentInputMode", 1);
        skin.addProperty("DefaultInputMode", 1);
        skin.addProperty("UIProfile", 0);

        // character creator 核心字段
        skin.addProperty("SkinId", "00000000-0000-0000-0000-000000000000.persona");
        skin.addProperty("PlayFabId", "abcdef0123456789");
        skin.addProperty("CapeId", "");
        skin.addProperty("FullSkinId", "00000000-0000-0000-0000-000000000000.persona-full");
        skin.addProperty("PremiumSkin", false);
        skin.addProperty("PersonaSkin", true);
        skin.addProperty("CapeOnClassicSkin", false);
        skin.addProperty("PrimaryUser", true);
        skin.addProperty("OverridingPlayerAppearance", true);
        skin.addProperty("TrustedSkin", true);

        // 皮肤贴图 256x256 persona
        skin.addProperty("SkinData", Base64.getEncoder().encodeToString(new byte[256 * 256 * 4]));
        skin.addProperty("SkinImageWidth", 256);
        skin.addProperty("SkinImageHeight", 256);

        // 披风空数据 64x32
        skin.addProperty("CapeData", Base64.getEncoder().encodeToString(new byte[64 * 32 * 4]));
        skin.addProperty("CapeImageWidth", 64);
        skin.addProperty("CapeImageHeight", 32);

        // persona geometry resource patch（Base64 编码的 JSON，匹配 decodeSkinData 逻辑）
        skin.addProperty("SkinResourcePatch", Base64.getEncoder().encodeToString(
                "{\"geometry\" : {\"default\" : \"geometry.humanoid.executable_custom_player\"}}"
                        .getBytes(StandardCharsets.UTF_8)));

        // geometry data（persona 通常带 geometry，Base64 编码）
        skin.addProperty("SkinGeometryData", Base64.getEncoder().encodeToString(
                "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[]}".getBytes(StandardCharsets.UTF_8)));
        skin.addProperty("SkinGeometryDataEngineVersion", Base64.getEncoder().encodeToString(
                "1.21.90".getBytes(StandardCharsets.UTF_8)));

        skin.addProperty("SkinColor", "#292929");
        skin.addProperty("ArmSize", "wide");

        // 动画图像数据（character creator 常带 128x128 动画贴图）
        JsonObject anim = new JsonObject();
        anim.addProperty("Image", Base64.getEncoder().encodeToString(new byte[128 * 128 * 4]));
        anim.addProperty("ImageWidth", 128);
        anim.addProperty("ImageHeight", 128);
        anim.addProperty("Frames", 1.0f);
        anim.addProperty("Type", 0);
        anim.addProperty("AnimationExpression", 0);
        skin.add("AnimatedImageData", GSON.toJsonTree(new JsonObject[]{anim}));

        // persona 部件
        JsonObject piece1 = new JsonObject();
        piece1.addProperty("PieceId", "piece-body-id");
        piece1.addProperty("PieceType", "persona_body");
        piece1.addProperty("PackId", UUID.randomUUID().toString());
        piece1.addProperty("IsDefault", true);
        piece1.addProperty("ProductId", "product-body");
        JsonObject piece2 = new JsonObject();
        piece2.addProperty("PieceId", "piece-eyes-id");
        piece2.addProperty("PieceType", "persona_eyes");
        piece2.addProperty("PackId", UUID.randomUUID().toString());
        piece2.addProperty("IsDefault", false);
        piece2.addProperty("ProductId", "product-eyes");
        skin.add("PersonaPieces", GSON.toJsonTree(new JsonObject[]{piece1, piece2}));

        // 部件染色
        JsonObject tint = new JsonObject();
        tint.addProperty("PieceType", "persona_eyes");
        tint.add("Colors", GSON.toJsonTree(new String[]{"#ff0000", "#00ff00", "#0000ff", "#ffffff"}));
        skin.add("PieceTintColors", GSON.toJsonTree(new JsonObject[]{tint}));

        return skin;
    }

    /**
     * 构造完整的 LoginPacket 二进制 buffer：protocol + chain + skin。
     * 格式：protocol(int BE) + payload(varInt-len + [chainLInt + chain + skinLInt + skin])
     */
    private static byte[] buildLoginPacketBuffer(int protocol, JsonObject skinPayload) {
        // chain payload: {"chain":["<jwt>"]}
        JsonObject chainExtra = new JsonObject();
        chainExtra.addProperty("displayName", "TestPlayer");
        chainExtra.addProperty("identity", UUID.randomUUID().toString());
        chainExtra.addProperty("XUID", "0000000000000000");
        JsonObject chainExtraWrap = new JsonObject();
        chainExtraWrap.add("extraData", chainExtra);
        JsonObject chainObj = new JsonObject();
        chainObj.add("chain", GSON.toJsonTree(new String[]{fakeJwt(chainExtraWrap)}));
        byte[] chainBytes = GSON.toJson(chainObj).getBytes(StandardCharsets.UTF_8);

        String skinJwt = fakeJwt(skinPayload);
        byte[] skinBytes = skinJwt.getBytes(StandardCharsets.UTF_8);

        // inner payload = LInt(chainLen) + chain + LInt(skinLen) + skin
        byte[] inner = new byte[4 + chainBytes.length + 4 + skinBytes.length];
        int p = 0;
        byte[] chainLenLe = Binary.writeLInt(chainBytes.length);
        System.arraycopy(chainLenLe, 0, inner, p, 4); p += 4;
        System.arraycopy(chainBytes, 0, inner, p, chainBytes.length); p += chainBytes.length;
        byte[] skinLenLe = Binary.writeLInt(skinBytes.length);
        System.arraycopy(skinLenLe, 0, inner, p, 4); p += 4;
        System.arraycopy(skinBytes, 0, inner, p, skinBytes.length); p += skinBytes.length;

        // outer = Int(protocol) + varInt(inner.length) + inner
        byte[] varIntLen = writeUnsignedVarInt(inner.length);
        byte[] protocolBe = Binary.writeInt(protocol);
        byte[] outer = new byte[4 + varIntLen.length + inner.length];
        int q = 0;
        System.arraycopy(protocolBe, 0, outer, q, 4); q += 4;
        System.arraycopy(varIntLen, 0, outer, q, varIntLen.length); q += varIntLen.length;
        System.arraycopy(inner, 0, outer, q, inner.length);
        return outer;
    }

    private static byte[] writeUnsignedVarInt(long value) {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(5);
        while ((value & ~0x7FL) != 0) {
            bos.write((int) (value & 0x7F) | 0x80);
            value >>>= 7;
        }
        bos.write((int) value);
        return bos.toByteArray();
    }

    @Test
    void personaSkinJwtDecodesWithoutException() {
        JsonObject skinPayload = buildPersonaSkinPayload();
        byte[] buffer = buildLoginPacketBuffer(2168, skinPayload);

        LoginPacket pkt = new LoginPacket();
        pkt.setBuffer(buffer, 0);
        try {
            pkt.decode();
        } catch (Exception e) {
            fail("LoginPacket.decode() threw for character creator skin: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage(), e);
        }

        Skin skin = pkt.skin;
        assertNotNull(skin, "skin should be parsed (not null) — null means silent disconnect");
        assertTrue(skin.isPersona(), "character creator skin should be persona");
        assertEquals(2, skin.getPersonaPieces().size());
        assertEquals(1, skin.getTintColors().size());
        assertEquals(1, skin.getAnimations().size());
        assertTrue(skin.isValid(), "character creator skin should pass isValid()");

        assertEquals("TestPlayer", pkt.username, "username should be parsed from chain");
    }

    @Test
    void personaSkinWithEmptyCapeDataDecodesWithoutException() {
        // 边缘场景：persona 皮肤无 CapeData 字段（character creator 可能不带披风）
        JsonObject skinPayload = buildPersonaSkinPayload();
        skinPayload.remove("CapeData");
        skinPayload.remove("CapeImageWidth");
        skinPayload.remove("CapeImageHeight");

        byte[] buffer = buildLoginPacketBuffer(2168, skinPayload);
        LoginPacket pkt = new LoginPacket();
        pkt.setBuffer(buffer, 0);
        try {
            pkt.decode();
        } catch (Exception e) {
            fail("LoginPacket.decode() threw for persona skin without cape: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
        assertNotNull(pkt.skin);
    }

    @Test
    void personaSkinWithoutImageDimensionsDecodesWithoutException() {
        // 关键边缘场景：SkinData 存在但无 SkinImageWidth/Height → fromLegacy 路径
        // 如果数据长度非标准，fromLegacy 会抛 IllegalArgumentException
        JsonObject skinPayload = buildPersonaSkinPayload();
        skinPayload.remove("SkinImageWidth");
        skinPayload.remove("SkinImageHeight");
        // 用标准 persona 大小，fromLegacy 应能识别
        byte[] buffer = buildLoginPacketBuffer(2168, skinPayload);
        LoginPacket pkt = new LoginPacket();
        pkt.setBuffer(buffer, 0);
        try {
            pkt.decode();
            assertNotNull(pkt.skin);
        } catch (Exception e) {
            fail("LoginPacket.decode() threw when SkinData lacks image dimensions: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * 回归测试：character creator 客户端可能以 serializeName（"body"）
     * 或 type（"persona_body"）两种格式发送 PersonaPieces[].PieceType。
     * 解析与 putSkin 的 ordinal 映射必须兼容两种格式，否则所有 piece 会被映射为 UNKNOWN(0)，
     * 下发坏数据导致客户端校验失败、玩家被踢。
     * <p>
     * Regression: the client may send PersonaPieces[].PieceType in either serializeName
     * ("body") or type ("persona_body") form. Parsing and putSkin's ordinal mapping must accept
     * both, otherwise pieces collapse to UNKNOWN(0) and clients reject the skin.
     */
    @Test
    void personaPieceTypeDualFormatRoundTrip() {
        cn.nukkit.utils.PersonaPieceType[] expected = {
                cn.nukkit.utils.PersonaPieceType.BODY,
                cn.nukkit.utils.PersonaPieceType.EYES};

        // serializeName 格式（短名）
        JsonObject skinPayloadSer = buildPersonaSkinPayload();
        skinPayloadSer.getAsJsonArray("PersonaPieces").get(0).getAsJsonObject()
                .addProperty("PieceType", "body");
        skinPayloadSer.getAsJsonArray("PersonaPieces").get(1).getAsJsonObject()
                .addProperty("PieceType", "eyes");
        Skin roundTripSer = roundTripSkin(decode(buildLoginPacketBuffer(2168, skinPayloadSer)).skin);
        assertPersonaPieceTypes(expected, roundTripSer, "serializeName format");

        // type 格式（persona_ 前缀）
        JsonObject skinPayloadType = buildPersonaSkinPayload();
        skinPayloadType.getAsJsonArray("PersonaPieces").get(0).getAsJsonObject()
                .addProperty("PieceType", "persona_body");
        skinPayloadType.getAsJsonArray("PersonaPieces").get(1).getAsJsonObject()
                .addProperty("PieceType", "persona_eyes");
        Skin roundTripType = roundTripSkin(decode(buildLoginPacketBuffer(2168, skinPayloadType)).skin);
        assertPersonaPieceTypes(expected, roundTripType, "type format");
    }

    private static Skin roundTripSkin(Skin skin) {
        cn.nukkit.utils.BinaryStream enc = new cn.nukkit.utils.BinaryStream();
        enc.putSkin(GameVersion.V1_26_40, skin);
        cn.nukkit.utils.BinaryStream dec = new cn.nukkit.utils.BinaryStream();
        dec.setBuffer(enc.getBuffer(), 0);
        return dec.getSkin(2168);
    }

    private static void assertPersonaPieceTypes(cn.nukkit.utils.PersonaPieceType[] expected,
                                                 Skin skin, String label) {
        assertEquals(expected.length, skin.getPersonaPieces().size(), label + ": piece count");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], skin.getPersonaPieces().get(i).type,
                    label + ": piece[" + i + "] type");
            assertNotEquals(cn.nukkit.utils.PersonaPieceType.UNKNOWN,
                    skin.getPersonaPieces().get(i).type,
                    label + ": piece[" + i + "] must not collapse to UNKNOWN");
        }
    }

    private static LoginPacket decode(byte[] buffer) {
        LoginPacket pkt = new LoginPacket();
        pkt.setBuffer(buffer, 0);
        pkt.decode();
        return pkt;
    }

    /**
     * 网易皮肤扩展字段（SkinIID / GrowthLevel / BloomData）必须从皮肤 JWT 正确解析到 Skin 对象。
     * 参考 SynapseAPI LoginPacket14 的字段名与类型。
     * <p>
     * NetEase skin extension fields (SkinIID / GrowthLevel / BloomData) must be parsed from the
     * skin JWT into the Skin object. Field names and types follow SynapseAPI LoginPacket14.
     */
    @Test
    void netEaseSkinExtensionFieldsAreParsed() {
        JsonObject skinPayload = buildPersonaSkinPayload();
        skinPayload.addProperty("SkinIID", "netease-skin-iid-123");
        skinPayload.addProperty("GrowthLevel", 7);
        skinPayload.addProperty("BloomData", "bloom-payload-base64");
        skinPayload.addProperty("IsReconnect", true);

        LoginPacket pkt = decode(buildLoginPacketBuffer(2168, skinPayload));
        assertNotNull(pkt.skin, "skin should be parsed");
        assertEquals("netease-skin-iid-123", pkt.skin.getSkinIID(), "SkinIID");
        assertEquals(7, pkt.skin.getGrowthLevel(), "GrowthLevel");
        assertEquals("bloom-payload-base64", pkt.skin.getBloomData(), "BloomData");
    }

    /**
     * 缺失网易扩展字段时不报错，Skin 对象返回默认值（空串 / 0）。
     * <p>
     * Missing NetEase extension fields must not throw; the Skin object returns defaults.
     */
    @Test
    void netEaseSkinExtensionFieldsDefaultWhenAbsent() {
        JsonObject skinPayload = buildPersonaSkinPayload();
        // 不添加 SkinIID / GrowthLevel / BloomData / IsReconnect

        LoginPacket pkt = decode(buildLoginPacketBuffer(2168, skinPayload));
        assertNotNull(pkt.skin);
        assertEquals("", pkt.skin.getSkinIID(), "SkinIID default");
        assertEquals(0, pkt.skin.getGrowthLevel(), "GrowthLevel default");
        assertEquals("", pkt.skin.getBloomData(), "BloomData default");
    }
}
