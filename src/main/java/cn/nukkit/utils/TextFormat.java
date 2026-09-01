package cn.nukkit.utils;

import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * All supported formatting values for chat and console.
 */
public enum TextFormat {
    /**
     * Represents black.
     */
    BLACK('0', 0x00),
    /**
     * Represents dark blue.
     */
    DARK_BLUE('1', 0x1),
    /**
     * Represents dark green.
     */
    DARK_GREEN('2', 0x2),
    /**
     * Represents dark blue (aqua).
     */
    DARK_AQUA('3', 0x3),
    /**
     * Represents dark red.
     */
    DARK_RED('4', 0x4),
    /**
     * Represents dark purple.
     */
    DARK_PURPLE('5', 0x5),
    /**
     * Represents gold.
     */
    GOLD('6', 0x6),
    /**
     * Represents gray.
     */
    GRAY('7', 0x7),
    /**
     * Represents dark gray.
     */
    DARK_GRAY('8', 0x8),
    /**
     * Represents blue.
     */
    BLUE('9', 0x9),
    /**
     * Represents green.
     */
    GREEN('a', 0xA),
    /**
     * Represents aqua.
     */
    AQUA('b', 0xB),
    /**
     * Represents red.
     */
    RED('c', 0xC),
    /**
     * Represents light purple.
     */
    LIGHT_PURPLE('d', 0xD),
    /**
     * Represents yellow.
     */
    YELLOW('e', 0xE),
    /**
     * Represents white.
     */
    WHITE('f', 0xF),
    /**
     * Represents minecoins gold.
     */
    MINECOIN_GOLD('g', 0x16),
    /**
     * Represents material quartz.
     */
    MATERIAL_QUARTZ('h', 0x17),
    /**
     * Represents material iron.
     */
    MATERIAL_IRON('i', 0x18),
    /**
     * Represents material netherite.
     */
    MATERIAL_NETHERITE('j', 0x19),
    /**
     * Represents material redstone.
     */
    MATERIAL_REDSTONE('m', 0x20),
    /**
     * Represents material copper.
     */
    MATERIAL_COPPER('n', 0x21),
    /**
     * Represents material gold.
     */
    MATERIAL_GOLD('p', 0x22),
    /**
     * Represents material emerald.
     */
    MATERIAL_EMERALD('q', 0x23),
    /**
     * Represents material diamond.
     */
    MATERIAL_DIAMOND('s', 0x24),
    /**
     * Represents material lapis.
     */
    MATERIAL_LAPIS('t', 0x25),
    /**
     * Represents material amethyst.
     */
    MATERIAL_AMETHYST('u', 0x26),
    /**
     * Represents material resin.
     */
    MATERIAL_RESIN('v', 0x27),
    /**
     * Represents party blue.
     */
    PARTY_BLUE('w', 0x28),
    /**
     * Makes the text obfuscated.
     */
    OBFUSCATED('k', 0x10, true),
    /**
     * Makes the text bold.
     */
    BOLD('l', 0x11, true),
    /**
     * Makes a line appear through the text.
     * 1.19.80+弃用
     */
    @Deprecated
    STRIKETHROUGH('m', 0x12, true),
    /**
     * Makes the text appear underlined.
     * 1.19.80+弃用
     */
    @Deprecated
    UNDERLINE('n', 0x13, true),
    /**
     * Makes the text italic.
     */
    ITALIC('o', 0x14, true),
    /**
     * Resets all previous chat colors or formats.
     */
    RESET('r', 0x15);

    /**
     * The special character which prefixes all format codes. Use this if
     * you need to dynamically convert format codes from your custom format.
     */
    public static final char ESCAPE = '\u00A7';

    private static final Pattern CLEAN_PATTERN = Pattern.compile("(?i)" + ESCAPE + "[0-9A-W]");
    private final static Map<Integer, TextFormat> BY_ID = Maps.newTreeMap();
    private final static Map<Character, TextFormat> BY_CHAR = new HashMap<>();

    static {
        for (TextFormat color : values()) {
            BY_ID.put(color.intCode, color);
            BY_CHAR.put(color.code, color);
        }
    }

    private final int intCode;
    private final char code;
    private final boolean isFormat;
    private final String toString;

    TextFormat(char code, int intCode) {
        this(code, intCode, false);
    }

    TextFormat(char code, int intCode, boolean isFormat) {
        this.code = code;
        this.intCode = intCode;
        this.isFormat = isFormat;
        this.toString = new String(new char[]{ESCAPE, code});
    }

    /**
     * Gets the TextFormat represented by the specified format code.
     *
     * @param code Code to check
     * @return Associative  with the given code,
     * or null if it doesn't exist
     */
    public static TextFormat getByChar(char code) {
        return BY_CHAR.get(code);
    }

    /**
     * Gets the TextFormat represented by the specified format code.
     *
     * @param code Code to check
     * @return Associative  with the given code,
     * or null if it doesn't exist
     */
    public static TextFormat getByChar(String code) {
        if (code == null || code.length() <= 1) {
            return null;
        }

        return BY_CHAR.get(code.charAt(0));
    }

    /**
     * Cleans the given message of all format codes.
     *
     * @param input String to clean.
     * @return A copy of the input string, without any formatting.
     */
    public static String clean(final String input) {
        return clean(input, false);
    }

    /**
     * Cleans the given message of all format codes.
     *
     * @param input String to clean.
     * @param recursive Do recursively.
     * @return A copy of the input string, without any formatting.
     */
    public static String clean(final String input, final boolean recursive) {
        if (input == null) {
            return null;
        }

        String result = CLEAN_PATTERN.matcher(input).replaceAll("");

        if (recursive && CLEAN_PATTERN.matcher(result).find()) {
            return clean(result, true);
        }
        return result;
    }

    /**
     * 按 code point 数截断字符串（对应 protocol-docs / JSON Schema 的 maxLength 字符语义），
     * 格式化码对（{@code §X}）作为原子单元，不会在边界留下孤立的 {@code §}。
     * <p>
     * Truncates the string to at most {@code maxChars} code points (matching protocol-docs /
     * JSON-Schema maxLength character semantics), treating a {@code §X} format pair as atomic so the
     * result never ends on a dangling {@code §}.
     */
    public static String clamp(String input, int maxChars) {
        if (input == null) {
            return null;
        }
        if (maxChars <= 0) {
            return "";
        }
        int len = input.length();
        // Fast path: char count ≤ maxChars ⇒ code point count ≤ maxChars (a surrogate pair counts 2 chars, 1 cp).
        if (len <= maxChars) {
            return stripTrailingLoneEscape(input);
        }

        StringBuilder out = new StringBuilder(maxChars + 2);
        int cps = 0;
        for (int i = 0; i < len; ) {
            char c = input.charAt(i);
            int step;
            int unitCps;
            if (c == ESCAPE && i < len - 1) {
                // § + next char is atomic: kept or dropped together, never split.
                // § + 下一字符为原子单元：整体保留或丢弃，绝不拆分。
                // It still counts as 2 code points toward the limit (§ and X are both real cps).
                step = 2;
                unitCps = 2;
            } else if (Character.isHighSurrogate(c) && i < len - 1 && Character.isLowSurrogate(input.charAt(i + 1))) {
                step = 2; // One supplementary code point.
                unitCps = 1;
            } else {
                step = 1;
                unitCps = 1;
            }
            if (cps + unitCps > maxChars) {
                break; // Next unit would exceed the code-point budget.
            }
            out.append(input, i, i + step);
            cps += unitCps;
            i += step;
        }
        // Drop a lone trailing § so the result never ends on a dangling format-code prefix.
        return stripTrailingLoneEscape(out.toString());
    }

    private static String stripTrailingLoneEscape(String s) {
        if (s.isEmpty() || s.charAt(s.length() - 1) != ESCAPE) {
            return s;
        }
        return s.substring(0, s.length() - 1);
    }

    /**
     * Translates a string using an alternate format code character into a
     * string that uses the internal TextFormat.ESCAPE format code
     * character. The alternate format code character will only be replaced if
     * it is immediately followed by 0-9, A-G, a-g, K-O, k-o, R or r.
     *
     * @param altFormatChar   The alternate format code character to replace. Ex: &amp;amp;
     * @param textToTranslate Text containing the alternate format code character.
     * @return Text containing the TextFormat.ESCAPE format code character.
     */
    public static String colorize(char altFormatChar, String textToTranslate) {
        char[] b = textToTranslate.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            int x = i + 1;
            if (b[i] == altFormatChar && "0123456789AaBbCcDdEeFfGgHhIiJjKkLlMmNnPpQqOoRrSsTtUuVv".indexOf(b[x]) > -1) {
                b[i] = TextFormat.ESCAPE;
                b[x] = Character.toLowerCase(b[x]);
            }
        }
        return new String(b);
    }

    /**
     * Translates a string, using an ampersand (&amp;) as an alternate format code
     * character, into a string that uses the internal TextFormat.ESCAPE format
     * code character. The alternate format code character will only be replaced if
     * it is immediately followed by 0-9, A-G, a-g, K-O, k-o, R or r.
     *
     * @param textToTranslate Text containing the alternate format code character.
     * @return Text containing the TextFormat.ESCAPE format code character.
     */
    public static String colorize(String textToTranslate) {
        return colorize('&', textToTranslate);
    }

    /**
     * Gets the chat color used at the end of the given input string.
     *
     * @param input Input string to retrieve the colors from.
     * @return Any remaining chat color to pass onto the next line.
     */
    public static String getLastColors(String input) {
        StringBuilder result = new StringBuilder();
        int length = input.length();

        // Search backwards from the end as it is faster
        for (int index = length - 1; index > -1; index--) {
            if (input.charAt(index) == ESCAPE && index < length - 1) {
                TextFormat color = getByChar(input.charAt(index + 1));

                if (color != null) {
                    result.insert(0, color.toString());

                    // Once we find a color or reset we can stop searching
                    if (color.isColor() || color.equals(RESET)) {
                        break;
                    }
                }
            }
        }

        return result.toString();
    }

    /**
     * Gets the char value associated with this color
     *
     * @return A char value of this color code
     */
    public char getChar() {
        return code;
    }

    @Override
    public String toString() {
        return toString;
    }

    /**
     * Checks if this code is a format code as opposed to a color code.
     */
    public boolean isFormat() {
        return isFormat;
    }

    /**
     * Checks if this code is a color code as opposed to a format code.
     */
    public boolean isColor() {
        return !isFormat && this != RESET;
    }
}
