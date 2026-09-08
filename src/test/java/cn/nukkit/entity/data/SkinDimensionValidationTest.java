package cn.nukkit.entity.data;

import cn.nukkit.MockServer;
import cn.nukkit.utils.SerializedImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 皮肤尺寸校验回归：像素长度与宽高不符的皮肤会使客户端渲染隐身
 * （移植自 Nukkit-EC 2467b155e「修复错误的皮肤导致隐身的bug」）。
 * <p>
 * Regression for skin dimension validation: a skin whose pixel length doesn't
 * match width×height renders invisible on the client.
 */
class SkinDimensionValidationTest {

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    private Skin validBase() {
        Skin skin = new Skin();
        skin.setSkinId("test-skin");
        skin.setGeometryData(Skin.STEVE_GEOMETRY);
        skin.setGeometryName("geometry.humanoid.custom");
        return skin;
    }

    @Test
    void consistentDimensionsPass() {
        Skin skin = validBase();
        skin.setSkinData(new SerializedImage(64, 64, new byte[Skin.DOUBLE_SKIN_SIZE]));
        assertTrue(skin.isValid(true));

        Skin legacy = validBase();
        legacy.setSkinData(new byte[Skin.SINGLE_SKIN_SIZE]); // fromLegacy → 64×32
        assertTrue(legacy.isValid(true));
    }

    @Test
    void mismatchedDimensionsRejected() {
        // 声称 128×128 却只有 64×64 的数据量 —— 修复前通过校验，客户端渲染隐身。
        Skin skin = validBase();
        skin.setSkinData(new SerializedImage(128, 128, new byte[Skin.DOUBLE_SKIN_SIZE]));
        assertFalse(skin.isValid(true));
    }

    @Test
    void bundledDefaultsStayValid() {
        assertTrue(Skin.NO_PERSONA_SKIN.isValid(true));
    }
}
