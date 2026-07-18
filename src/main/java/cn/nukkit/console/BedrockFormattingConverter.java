package cn.nukkit.console;

import net.minecrell.terminalconsole.TerminalConsoleAppender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.*;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Converts Bedrock {@code §} formatting codes to terminal ANSI sequences.
 */
@Plugin(name = "bedrockFormatting", category = PatternConverter.CATEGORY)
@ConverterKeys("bedrockFormatting")
@PerformanceSensitive("allocation")
public final class BedrockFormattingConverter extends LogEventPatternConverter {

    public static final String KEEP_FORMATTING_PROPERTY =
            "terminal.keepMinecraftFormatting";

    static final String ANSI_RESET = "\u001B[m";

    private static final char COLOR_CHAR = '§';

    private static final String MINECOIN_GOLD = trueColor(221, 214, 5);
    private static final String MATERIAL_QUARTZ = trueColor(227, 212, 209);
    private static final String MATERIAL_IRON = trueColor(206, 202, 202);
    private static final String MATERIAL_NETHERITE = trueColor(68, 58, 59);
    private static final String MATERIAL_REDSTONE = trueColor(151, 22, 7);
    private static final String MATERIAL_COPPER = trueColor(180, 104, 77);
    private static final String MATERIAL_GOLD = trueColor(222, 177, 45);
    private static final String MATERIAL_EMERALD = trueColor(17, 160, 54);
    private static final String MATERIAL_DIAMOND = trueColor(44, 186, 168);
    private static final String MATERIAL_LAPIS = trueColor(33, 73, 123);
    private static final String MATERIAL_AMETHYST = trueColor(154, 92, 198);
    private static final String MATERIAL_RESIN = trueColor(235, 113, 20);
    private static final String PARTY_BLUE = trueColor(140, 179, 255);

    private static final boolean KEEP_FORMATTING = PropertiesUtil.getProperties()
            .getBooleanProperty(KEEP_FORMATTING_PROPERTY);

    private final boolean ansi;
    private final List<PatternFormatter> formatters;

    private BedrockFormattingConverter(List<PatternFormatter> formatters, boolean strip) {
        super("bedrockFormatting", null);
        this.formatters = formatters;
        this.ansi = !strip;
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        int start = toAppendTo.length();
        for (int i = 0, size = formatters.size(); i < size; i++) {
            formatters.get(i).format(event, toAppendTo);
        }

        if (KEEP_FORMATTING || toAppendTo.length() == start) {
            return;
        }

        String content = toAppendTo.substring(start);
        format(content, toAppendTo, start, ansi && TerminalConsoleAppender.isAnsiSupported());
    }

    static void format(String input, StringBuilder result, int start, boolean ansi) {
        int first = input.indexOf(COLOR_CHAR);
        int last = input.length() - 1;
        if (first == -1 || first == last) {
            return;
        }

        StringBuilder formatted = new StringBuilder(input.length() + 32);
        int pos = 0;
        int next = first;
        boolean replaced = false;

        while (next != -1 && next < last) {
            String ansiCode = ansiCode(input.charAt(next + 1));
            if (ansiCode != null) {
                formatted.append(input, pos, next);
                if (ansi) {
                    formatted.append(ansiCode);
                }
                pos = next + 2;
                next = input.indexOf(COLOR_CHAR, pos);
                replaced = true;
            } else {
                next = input.indexOf(COLOR_CHAR, next + 1);
            }
        }

        if (!replaced) {
            return;
        }

        formatted.append(input, pos, input.length());
        result.setLength(start);
        result.append(formatted);
        if (ansi) {
            result.append(ANSI_RESET);
        }
    }

    private static @Nullable String ansiCode(char rawCode) {
        return switch (Character.toLowerCase(rawCode)) {
            case '0' -> "\u001B[0;30m";
            case '1' -> "\u001B[0;34m";
            case '2' -> "\u001B[0;32m";
            case '3' -> "\u001B[0;36m";
            case '4' -> "\u001B[0;31m";
            case '5' -> "\u001B[0;35m";
            case '6' -> "\u001B[0;33m";
            case '7' -> "\u001B[0;37m";
            case '8' -> "\u001B[0;30;1m";
            case '9' -> "\u001B[0;34;1m";
            case 'a' -> "\u001B[0;32;1m";
            case 'b' -> "\u001B[0;36;1m";
            case 'c' -> "\u001B[0;31;1m";
            case 'd' -> "\u001B[0;35;1m";
            case 'e' -> "\u001B[0;33;1m";
            case 'f' -> "\u001B[0;37;1m";
            case 'g' -> MINECOIN_GOLD;
            case 'h' -> MATERIAL_QUARTZ;
            case 'i' -> MATERIAL_IRON;
            case 'j' -> MATERIAL_NETHERITE;
            case 'k' -> "\u001B[5m";
            case 'l' -> "\u001B[1m";
            case 'm' -> MATERIAL_REDSTONE;
            case 'n' -> MATERIAL_COPPER;
            case 'o' -> "\u001B[3m";
            case 'p' -> MATERIAL_GOLD;
            case 'q' -> MATERIAL_EMERALD;
            case 'r' -> ANSI_RESET;
            case 's' -> MATERIAL_DIAMOND;
            case 't' -> MATERIAL_LAPIS;
            case 'u' -> MATERIAL_AMETHYST;
            case 'v' -> MATERIAL_RESIN;
            case 'w' -> PARTY_BLUE;
            default -> null;
        };
    }

    private static String trueColor(int red, int green, int blue) {
        return "\u001B[38;2;" + red + ';' + green + ';' + blue + 'm';
    }

    public static @Nullable BedrockFormattingConverter newInstance(Configuration config, String[] options) {
        if (options.length < 1 || options.length > 2) {
            LOGGER.error("Incorrect number of options on bedrockFormatting. Expected at least 1, max 2 received {}",
                    options.length);
            return null;
        }
        if (options[0] == null) {
            LOGGER.error("No pattern supplied on bedrockFormatting");
            return null;
        }

        PatternParser parser = PatternLayout.createPatternParser(config);
        List<PatternFormatter> formatters = parser.parse(options[0]);
        boolean strip = options.length > 1 && "strip".equals(options[1]);
        return new BedrockFormattingConverter(formatters, strip);
    }
}
