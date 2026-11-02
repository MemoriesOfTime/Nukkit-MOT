package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 染色玻璃板（含硬化变体）meta 高 4 位的连接位不得影响颜色解析：取色只看低 4 位颜色位。
 * 曾因裸 meta 撞上 DyeColor.convertNewToOld 的新版染料值 16-19（=北连接位+颜色 0-3），
 * 橙/品红/淡蓝板北连接时名称与地图颜色解析成淡蓝/黄/黑。
 * <p>
 * Connection bits in a stained pane's upper meta nibble must not affect color resolution: only the
 * low color nibble may be read. Raw meta once collided with DyeColor.convertNewToOld's new dye
 * values 16-19 (= north connection bit + colors 0-3), resolving orange/magenta/light-blue panes
 * with a north connection to light-blue/yellow/black.
 */
class StainedGlassPaneConnectionMetaColorTest {

    @Test
    void connectionBitsDoNotChangePaneColor() {
        int[] flagCombos = {0x10, 0x20, 0x40, 0x80, BlockThin.CONNECTION_FLAGS};
        for (int color = 0; color < 16; color++) {
            DyeColor expected = DyeColor.getByWoolData(color);
            for (int flags : flagCombos) {
                Assertions.assertEquals(expected, new BlockGlassPaneStained(color | flags).getDyeColor(),
                        "stained pane color=" + color + " flags=0x" + Integer.toHexString(flags));
                Assertions.assertEquals(expected, new BlockHardGlassPaneStained(color | flags).getDyeColor(),
                        "hard stained pane color=" + color + " flags=0x" + Integer.toHexString(flags));
            }
        }
    }

    @Test
    void previouslyCollidingMetasResolveTheirOwnColor() {
        // 16-19 撞 BROWN_NEW/BLUE_NEW/WHITE_NEW 前缀的三个坏值 / the three bad values that used
        // to collide with BROWN_NEW/BLUE_NEW/WHITE_NEW in convertNewToOld
        Assertions.assertEquals(DyeColor.ORANGE, new BlockGlassPaneStained(1 | BlockThin.FLAG_CONNECTION_NORTH).getDyeColor());
        Assertions.assertEquals(DyeColor.MAGENTA, new BlockGlassPaneStained(2 | BlockThin.FLAG_CONNECTION_NORTH).getDyeColor());
        Assertions.assertEquals(DyeColor.LIGHT_BLUE, new BlockGlassPaneStained(3 | BlockThin.FLAG_CONNECTION_NORTH).getDyeColor());
        Assertions.assertEquals(DyeColor.LIGHT_BLUE, new BlockHardGlassPaneStained(3 | BlockThin.FLAG_CONNECTION_NORTH).getDyeColor());

        // 名称与地图颜色走同一取色路径 / name and map color share the same color resolution
        Assertions.assertEquals("Light Blue Stained Glass Pane",
                new BlockGlassPaneStained(3 | BlockThin.FLAG_CONNECTION_NORTH).getName());
    }
}
