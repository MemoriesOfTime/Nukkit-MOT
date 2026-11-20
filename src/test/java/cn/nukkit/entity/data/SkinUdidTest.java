package cn.nukkit.entity.data;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网易皮肤归属 UID 的验证、默认皮肤隔离及线格式一致性。
 * <p>
 * NetEase skin owner UID validation, fallback isolation, and wire appearance.
 */
class SkinUdidTest {

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    private Skin validSkin() {
        Skin skin = new Skin();
        skin.setSkinId("udid-test-skin");
        skin.setGeometryData(Skin.STEVE_GEOMETRY);
        skin.setGeometryName("geometry.humanoid.custom");
        skin.setSkinData(new byte[Skin.SINGLE_SKIN_SIZE]);
        return skin;
    }

    @Test
    void udidIsEmptyByDefaultAndNullNormalizesToEmpty() {
        Skin skin = validSkin();
        assertEquals("", skin.getUdid());

        skin.setUdid("123456789");
        assertEquals("123456789", skin.getUdid());

        skin.setUdid(null);
        assertEquals("", skin.getUdid());
    }

    @Test
    void udidLengthBoundMatchesValidation() {
        Skin skin = validSkin();
        skin.setUdid("u".repeat(99));
        assertTrue(skin.isValid(true));

        skin.setUdid("u".repeat(100));
        assertFalse(skin.isValid(true));
    }

    @Test
    void udidDoesNotAffectContentFingerprint() {
        Skin skin = validSkin();
        String before = skin.getContentFingerprint();

        skin.setUdid("123456789");

        assertEquals(before, skin.getContentFingerprint());
    }

    @Test
    void unownedFallbackUsesSharedDefaultSkin() {
        assertSame(Skin.NO_PERSONA_SKIN, validSkin().getDefaultSkin());
    }

    /**
     * 有归属的回退必须复制当前公共默认模板，而不是硬编码 Steve。
     * <p>
     * An owned fallback must copy the current shared default template instead of hard-coded Steve.
     */
    @Test
    void ownedFallbackCopiesCustomizedSharedDefault() {
        Skin savedDefault = Skin.NO_PERSONA_SKIN;
        try {
            Skin template = validSkin();
            template.setSkinId("custom-default");
            template.setGeometryData("{\"custom\":\"default\"}");
            template.setGeometryName("geometry.humanoid.customSlim");
            template.setArmSize("slim");
            template.setSkinIID("custom-iid");
            template.setGrowthLevel(7);
            template.setBloomData(new byte[]{1, 2, 3});
            Skin.NO_PERSONA_SKIN = template;

            Skin owner = validSkin();
            owner.setUdid("123456789");
            Skin fallback = owner.getDefaultSkin();

            assertNotSame(template, fallback);
            assertEquals("custom-default", fallback.getSkinId());
            assertEquals("{\"custom\":\"default\"}", fallback.getGeometryData());
            assertEquals(template.getSkinResourcePatch(), fallback.getSkinResourcePatch());
            assertEquals("slim", fallback.getArmSize());
            assertTrue(fallback.isLegacySlim);
            assertEquals("custom-iid", fallback.getSkinIID());
            assertEquals(7, fallback.getGrowthLevel());
            assertArrayEquals(new byte[]{1, 2, 3}, fallback.getBloomData());
            assertArrayEquals(template.getSkinData().data, fallback.getSkinData().data);
            assertNotSame(template.getSkinData(), fallback.getSkinData());
            assertNotSame(template.getSkinData().data, fallback.getSkinData().data);
            assertTrue(fallback.isValid(false));

            BinaryStream expected = new BinaryStream();
            expected.putSkin(GameVersion.V1_21_124_NETEASE, template);
            BinaryStream actual = new BinaryStream();
            actual.putSkin(GameVersion.V1_21_124_NETEASE, fallback);
            assertArrayEquals(expected.getBuffer(), actual.getBuffer());

            fallback.getSkinData().data[0] ^= (byte) 0xff;
            assertNotEquals(fallback.getSkinData().data[0], template.getSkinData().data[0]);
        } finally {
            Skin.NO_PERSONA_SKIN = savedDefault;
        }
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {
            "V1_21_93_NETEASE", "V1_21_124_NETEASE", "V1_21_130_NETEASE", "V1_21_124"
    })
    void ownedFallbackKeepsDefaultSkinWireAppearance(GameVersion gameVersion) {
        Skin skin = validSkin();
        skin.setUdid("123456789");

        Skin fallback = skin.getDefaultSkin();

        assertEquals("123456789", fallback.getUdid());
        assertTrue(fallback.isValid(false));
        assertNotSame(Skin.NO_PERSONA_SKIN, fallback);
        assertEquals("", Skin.NO_PERSONA_SKIN.getUdid());
        BinaryStream expected = new BinaryStream();
        expected.putSkin(gameVersion, Skin.NO_PERSONA_SKIN);
        BinaryStream actual = new BinaryStream();
        actual.putSkin(gameVersion, fallback);
        assertArrayEquals(expected.getBuffer(), actual.getBuffer());
    }
}
