package cn.nukkit.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * TextFormat.clamp 回归测试：code point 数上限 + 格式化码原子性（对应 protocol-docs / JSON Schema maxLength 字符语义）。
 * <p>
 * Regression tests for clamp: code-point ceiling and §-format-code atomicity (matching protocol-docs /
 * JSON-Schema maxLength character semantics).
 */
public class TextFormatClampTest {

    @Test
    public void nullAndEmptyAndShort() {
        Assertions.assertNull(TextFormat.clamp(null, 16));
        Assertions.assertEquals("", TextFormat.clamp("", 0));
        Assertions.assertEquals("abc", TextFormat.clamp("abc", 16));
        // Exactly at the limit stays intact.
        Assertions.assertEquals("abc", TextFormat.clamp("abc", 3));
    }

    @Test
    public void zeroOrNegativeMaxReturnsEmpty() {
        Assertions.assertEquals("", TextFormat.clamp("abc", 0));
        Assertions.assertEquals("", TextFormat.clamp("abc", -1));
    }

    @Test
    public void asciiTruncatesToCodePointLimit() {
        Assertions.assertEquals("ab", TextFormat.clamp("abcdef", 2));
        Assertions.assertEquals("hello", TextFormat.clamp("hello world!", 5));
    }

    @Test
    public void cjkCountsByCodePointNotBytes() {
        // 每个中文字符是 1 个 code point（但 3 个 UTF-8 字节）。maxChars 按字符算。
        // Each CJK char is 1 code point (but 3 UTF-8 bytes). maxChars counts code points.
        String cjk = "你好世界你好世界"; // 8 code points
        Assertions.assertEquals("你好世", TextFormat.clamp(cjk, 3));
        // 8 CJK chars fit within maxChars=8 even though their byte length is 24.
        Assertions.assertEquals(cjk, TextFormat.clamp(cjk, 8));
    }

    @Test
    public void formatCodePairStaysAtomic() {
        // "§a" is atomic (2 code points: § + 'a') — never split, kept or dropped together.
        String s = "ab" + TextFormat.ESCAPE + "ac"; // "ab§ac" = 4 code points
        // maxChars=2 keeps only "ab"; the §a pair (cps 3-4) would overflow and is dropped wholesale.
        Assertions.assertEquals("ab", TextFormat.clamp(s, 2));
        Assertions.assertFalse(TextFormat.clamp(s, 2).endsWith(String.valueOf(TextFormat.ESCAPE)));
    }

    @Test
    public void formatCodePairKeptAsSingleUnit() {
        // "§ax" = 3 code points: §a pair (2 cps) + 'x' (1 cp). The pair is atomic but counts as 2 cps.
        String s = TextFormat.ESCAPE + "a" + "x";
        // maxChars=1 cannot fit the 2-cp pair → dropped; maxChars=2 fits the whole pair.
        Assertions.assertEquals("", TextFormat.clamp(s, 1));
        Assertions.assertEquals(TextFormat.ESCAPE + "a", TextFormat.clamp(s, 2));
    }

    @Test
    public void loneTrailingEscapeDropped() {
        // Truncation that lands right after a lone § must drop the § to avoid a dangling format-code prefix.
        String s = "a" + TextFormat.ESCAPE + "bcde"; // "a§bcde" — truncating at cps=2 keeps "a§", then § is dropped.
        Assertions.assertEquals("a", TextFormat.clamp(s, 2));
        Assertions.assertFalse(TextFormat.clamp(s, 2).endsWith(String.valueOf(TextFormat.ESCAPE)));
    }

    @Test
    public void loneEscapeInputDroppedByFastPath() {
        // Fast path (len ≤ maxChars) must not bypass the "no dangling §" rule.
        String lone = String.valueOf(TextFormat.ESCAPE); // "§", len=1
        Assertions.assertEquals("", TextFormat.clamp(lone, 1));
        Assertions.assertEquals("", TextFormat.clamp(lone, 16));
    }

    @Test
    public void supplementaryCodePointIsOneUnit() {
        // 😀 (U+1F600) is a surrogate pair in Java = 1 code point = 1 clamp unit.
        String s = "ab😀cd";
        Assertions.assertEquals("ab", TextFormat.clamp(s, 2)); // emoji is the 3rd unit
        Assertions.assertEquals("ab😀", TextFormat.clamp(s, 3)); // emoji fits as 1 unit
    }

    @Test
    public void reproducesBossBarCrashCase() {
        // The exact title from the original client-crash packet: 298 code points / 438 UTF-8 bytes,
        // over the protocol-docs maxLength 256 (character/code-point) ceiling.
        String title = "§eLV.§r46   §7(960/5272)   §bGS.§f1672\n\n§a端午主题商店限时开放兑换二周年/端午限定武器§b（——>打开终端兑换）\n§a[稳重§7lv.§31§a] §7§e(19) \n\n§r   \n\n\n\n\n\n\n" + bars(30, "a|", 30, "c|");
        Assertions.assertTrue(codePoints(title) > 256);

        String clamped = TextFormat.clamp(title, 256);
        Assertions.assertTrue(codePoints(clamped) <= 256,
                () -> "clamped code points=" + codePoints(clamped));
        Assertions.assertFalse(clamped.endsWith(String.valueOf(TextFormat.ESCAPE)));
        // Idempotent.
        Assertions.assertEquals(clamped, TextFormat.clamp(clamped, 256));
    }

    private static String bars(int green, String greenUnit, int red, String redUnit) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < green; i++) b.append(TextFormat.ESCAPE).append(greenUnit);
        for (int i = 0; i < red; i++) b.append(TextFormat.ESCAPE).append(redUnit);
        return b.toString();
    }

    private static int codePoints(String s) {
        return Character.codePointCount(s, 0, s.length());
    }
}
