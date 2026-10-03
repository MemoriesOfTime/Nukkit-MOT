package cn.nukkit.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PersonaPieceType 枚举契约测试：序数与 v2168 协议对齐、fromName 双格式、fromOrdinal 越界安全。
 * <p>
 * Contract tests for PersonaPieceType: ordinal alignment with v2168, dual-format fromName,
 * and safe fromOrdinal.
 */
class PersonaPieceTypeTest {

    @Test
    void ordinalMatchesV2168Protocol() {
        // 序数必须与 CloudburstMC/pm1e 的枚举顺序一致，否则 v2168 二进制编解码错位
        assertEquals(0, PersonaPieceType.UNKNOWN.ordinal());
        assertEquals(1, PersonaPieceType.SKELETON.ordinal());
        assertEquals(2, PersonaPieceType.BODY.ordinal());
        assertEquals(9, PersonaPieceType.HANDS.ordinal());
        assertEquals(13, PersonaPieceType.EYES.ordinal());
        assertEquals(25, PersonaPieceType.CAPES.ordinal());
        assertEquals(26, PersonaPieceType.CLASSIC_SKIN.ordinal());
        assertEquals(27, PersonaPieceType.EMOTE.ordinal());
        assertEquals(28, PersonaPieceType.UNSUPPORTED.ordinal());
        assertEquals(29, PersonaPieceType.values().length, "total constant count");
    }

    @Test
    void fromNameAcceptsSerializeNameAndType() {
        // serializeName（短名）格式
        assertEquals(PersonaPieceType.BODY, PersonaPieceType.fromName("body"));
        assertEquals(PersonaPieceType.EYES, PersonaPieceType.fromName("eyes"));
        assertEquals(PersonaPieceType.HANDS, PersonaPieceType.fromName("hands"));
        assertEquals(PersonaPieceType.CLASSIC_SKIN, PersonaPieceType.fromName("classicskin"));
        // type（persona_ 前缀）格式
        assertEquals(PersonaPieceType.BODY, PersonaPieceType.fromName("persona_body"));
        assertEquals(PersonaPieceType.EYES, PersonaPieceType.fromName("persona_eyes"));
        assertEquals(PersonaPieceType.HANDS, PersonaPieceType.fromName("persona_hand"));
        assertEquals(PersonaPieceType.CLASSIC_SKIN, PersonaPieceType.fromName("persona_classic_skin"));
    }

    @Test
    void fromNameFallsBackToUnknownForBadInput() {
        // fromName 对未知/空值不应抛异常，回退 UNKNOWN，避免恶意 JWT 导致登录崩溃
        assertEquals(PersonaPieceType.UNKNOWN, PersonaPieceType.fromName(null));
        assertEquals(PersonaPieceType.UNKNOWN, PersonaPieceType.fromName(""));
        assertEquals(PersonaPieceType.UNKNOWN, PersonaPieceType.fromName("not_a_real_piece"));
    }

    @Test
    void fromOrdinalIsSafeForOutOfRange() {
        // 越界序数（恶意/损坏数据）必须回退 UNKNOWN，而非抛 ArrayIndexOutOfBoundsException
        assertEquals(PersonaPieceType.UNKNOWN, PersonaPieceType.fromOrdinal(-1));
        assertEquals(PersonaPieceType.UNKNOWN, PersonaPieceType.fromOrdinal(29));
        assertEquals(PersonaPieceType.UNKNOWN, PersonaPieceType.fromOrdinal(Integer.MAX_VALUE));
        // 正常序数往返
        for (PersonaPieceType t : PersonaPieceType.values()) {
            assertEquals(t, PersonaPieceType.fromOrdinal(t.ordinal()));
        }
    }

    @Test
    void serializeNameAndTypeAreDistinct() {
        // serializeName 与 type 应能区分，且 getSerializeName/getType 可取回
        assertEquals("body", PersonaPieceType.BODY.getSerializeName());
        assertEquals("persona_body", PersonaPieceType.BODY.getType());
        assertEquals("hands", PersonaPieceType.HANDS.getSerializeName());
        assertEquals("persona_hand", PersonaPieceType.HANDS.getType(), "HANDS type uses singular persona_hand");
    }
}
