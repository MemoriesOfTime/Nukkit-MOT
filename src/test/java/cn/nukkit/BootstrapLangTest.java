package cn.nukkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 引导多语言：六语言包键集合与英文基准一致、占位符不漂移、语言解析顺序
 * （属性 &gt; server.properties &gt; Locale）与未知语言回落英文。
 * <p>
 * Bootstrap i18n: key sets and placeholders stay locked across the six bundles, and the
 * language resolution order (property &gt; server.properties &gt; locale) with English fallback.
 */
class BootstrapLangTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

    @Test
    void allBundlesShipEveryEnglishKey() {
        Map<String, String> eng = BootstrapLang.loadBundle("eng");
        assertTrue(eng != null && !eng.isEmpty(), "英文基准包必须内置 / English base bundle must ship");
        for (String code : BootstrapLang.BUNDLED_CODES) {
            if (code.equals(BootstrapLang.FALLBACK_CODE)) {
                continue;
            }
            Map<String, String> bundle = BootstrapLang.loadBundle(code);
            assertTrue(bundle != null && !bundle.isEmpty(), "语言包应存在且非空 / expected non-empty bundle: " + code);
            assertEquals(new TreeMap<>(eng).keySet(), new TreeMap<>(bundle).keySet(),
                    "键集合须与英文一致 / key set must match English: " + code);
            for (Map.Entry<String, String> entry : bundle.entrySet()) {
                assertFalse(entry.getValue().isBlank(), "翻译不得为空 / blank translation: " + code + "!" + entry.getKey());
            }
        }
    }

    @Test
    void placeholdersMatchEnglishAcrossLanguages() {
        Map<String, String> eng = BootstrapLang.loadBundle("eng");
        for (String code : BootstrapLang.BUNDLED_CODES) {
            if (code.equals(BootstrapLang.FALLBACK_CODE)) {
                continue;
            }
            for (Map.Entry<String, String> entry : BootstrapLang.loadBundle(code).entrySet()) {
                assertEquals(placeholderTokens(eng.get(entry.getKey())), placeholderTokens(entry.getValue()),
                        "占位符漂移 / placeholder drift: " + code + "!" + entry.getKey());
            }
        }
    }

    @Test
    void formatsPositionalArguments() {
        String message = BootstrapLang.forLanguage("chs").text("download.ok", 1, 3, "netty.jar", "25.6");
        assertTrue(message.contains("(1/3)"), message);
        assertTrue(message.contains("netty.jar"), message);
        assertTrue(message.contains("25.6"), message);
        assertFalse(message.contains("{"), message);
    }

    @Test
    void argumentValuesAreNotRescannedForPlaceholders() {
        // {0} 的值含 {1} 字样时不得被后续参数再替换；越界序号 {2}/{3} 原样保留
        // a literal {1} inside an argument value must survive; out-of-range {2}/{3} stay verbatim
        assertEquals("Download ({1}/x) {2} ... OK ({3})",
                BootstrapLang.forLanguage("eng").text("download.ok", "{1}", "x"));
    }

    @Test
    void missingKeyRendersKeyItself() {
        assertEquals("no.such.key", BootstrapLang.forLanguage("eng").text("no.such.key"));
    }

    @Test
    void unknownLanguageFallsBackToEnglish() {
        String english = BootstrapLang.forLanguage("eng").text("lib.verified");
        assertEquals(english, BootstrapLang.forLanguage(null).text("lib.verified"));
        assertEquals(english, BootstrapLang.forLanguage("fr_FR").text("lib.verified"));
        assertEquals(english, BootstrapLang.forLanguage("cht").text("lib.verified"));
        assertEquals(BootstrapLang.FALLBACK_CODE, BootstrapLang.forLanguage("pt_BR").code());
        assertEquals("chs", BootstrapLang.forLanguage("zh_CN").code());
    }

    @Test
    void staticAccessorsPrefixAndResolutionSurviveDefaultPath() {
        assertTrue(BootstrapLang.getLine("lib.verified").startsWith("[Bootstrap] "));
        assertFalse(BootstrapLang.get("lib.verified").startsWith("[Bootstrap] "));
        assertFalse(BootstrapLang.get("lib.verified").isEmpty());
    }

    @Test
    void normalizeAcceptsServerCodesAndLocaleForms() {
        assertEquals("eng", BootstrapLang.normalizeLanguage("eng"));
        assertEquals("eng", BootstrapLang.normalizeLanguage("ENG"));
        assertEquals("eng", BootstrapLang.normalizeLanguage("en"));
        assertEquals("eng", BootstrapLang.normalizeLanguage("en-US"));
        assertEquals("eng", BootstrapLang.normalizeLanguage("en_GB"));
        assertEquals("chs", BootstrapLang.normalizeLanguage("chs"));
        assertEquals("chs", BootstrapLang.normalizeLanguage("zh"));
        assertEquals("chs", BootstrapLang.normalizeLanguage("zh_CN"));
        assertEquals("chs", BootstrapLang.normalizeLanguage("zh-SG"));
        assertEquals("chs", BootstrapLang.normalizeLanguage("zh-Hans"));
        assertEquals("cht", BootstrapLang.normalizeLanguage("cht"));
        assertEquals("cht", BootstrapLang.normalizeLanguage("zh_TW"));
        assertEquals("cht", BootstrapLang.normalizeLanguage("zh-Hant"));
        assertEquals("jpn", BootstrapLang.normalizeLanguage("ja"));
        assertEquals("jpn", BootstrapLang.normalizeLanguage("ja_JP"));
        assertEquals("rus", BootstrapLang.normalizeLanguage("ru_RU"));
        assertEquals("deu", BootstrapLang.normalizeLanguage("de"));
        assertEquals("vie", BootstrapLang.normalizeLanguage("vi-VN"));
        assertNull(BootstrapLang.normalizeLanguage(null));
        assertNull(BootstrapLang.normalizeLanguage("  "));
        assertNull(BootstrapLang.normalizeLanguage("fr"));
    }

    @Test
    void resolutionPrefersPropertyThenServerPropertiesThenLocale(@TempDir Path dir) throws IOException {
        Path properties = dir.resolve("server.properties");
        Files.writeString(properties, "language=jpn\n", StandardCharsets.UTF_8);
        Path missing = dir.resolve("absent.properties");

        assertEquals("chs", BootstrapLang.resolveLanguage("zh_CN", properties, Locale.ENGLISH));
        assertEquals("chs", BootstrapLang.resolveLanguage(" zh ", missing, Locale.ENGLISH));
        // 显式属性无法识别时顺延下一级而非直接回落英文 / an unrecognized explicit value
        // falls through to the next source instead of forcing English immediately
        assertEquals("jpn", BootstrapLang.resolveLanguage("fr", properties, Locale.ENGLISH));
        assertEquals("jpn", BootstrapLang.resolveLanguage(null, properties, Locale.ENGLISH));
        assertEquals("jpn", BootstrapLang.resolveLanguage(null, missing, Locale.JAPAN));
        assertEquals("chs", BootstrapLang.resolveLanguage(null, missing, Locale.SIMPLIFIED_CHINESE));
        assertEquals("cht", BootstrapLang.resolveLanguage(null, missing, Locale.TRADITIONAL_CHINESE));
        assertEquals("cht", BootstrapLang.resolveLanguage(null, missing, Locale.forLanguageTag("zh-Hant-TW")));
        assertEquals("rus", BootstrapLang.resolveLanguage(null, missing, Locale.forLanguageTag("ru-RU")));
        assertEquals("eng", BootstrapLang.resolveLanguage(null, missing, Locale.ENGLISH));
        assertNull(BootstrapLang.resolveLanguage(null, missing, Locale.FRANCE));
        assertNull(BootstrapLang.resolveLanguage(null, missing, null));
    }

    @Test
    void serverPropertiesLanguageParsing(@TempDir Path dir) throws IOException {
        Path properties = dir.resolve("server.properties");
        Files.writeString(properties, "\uFEFF# header comment\r\n! another comment\r\n"
                + "motd=Hello\r\nlanguage = deu\r\nlanguage-again=x\r\nnot-a-property\r\n", StandardCharsets.UTF_8);
        assertEquals("deu", BootstrapLang.serverPropertiesLanguage(properties));

        Files.writeString(properties, "language=\n", StandardCharsets.UTF_8);
        assertNull(BootstrapLang.serverPropertiesLanguage(properties));

        Files.writeString(properties, "motd=no language key here\n", StandardCharsets.UTF_8);
        assertNull(BootstrapLang.serverPropertiesLanguage(properties));

        assertNull(BootstrapLang.serverPropertiesLanguage(dir.resolve("absent.properties")));
        assertNull(BootstrapLang.serverPropertiesLanguage(null));
    }

    private static List<String> placeholderTokens(String template) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        tokens.sort(null);
        return tokens;
    }
}
