package cn.nukkit.lang;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BaseLangLiteralTranslationTest {
    @Test
    void plainTextPreservesEveryUtf16CodeUnitWithoutDictionaryReads() {
        Fixture lang = new Fixture();
        String[] values = {null, "", "help", "Открывает список команд", "中文 😀 café",
                "\u0000\uD800x\uDC00\uFFFF", "полноширинный ％", "line\n\tline", "abc".repeat(4096)};
        for (String value : values) {
            for (String prefix : new String[]{null, "", "k", "%"}) {
                assertEquals(String.valueOf(value), lang.parse(value, prefix));
                assertTrue(lang.lookups.isEmpty(), "Plain text must not consult either dictionary");
            }
        }
    }

    @Test
    void percentTokensKeepTheirOriginalBoundariesAndLookupOrder() {
        Fixture lang = new Fixture();
        Object[][] cases = {
                {"%", "E", List.of("")},
                {"%%", "EE", List.of("", "")},
                {"A%", "AE", List.of("")},
                {"%missing", "%missing", List.of("missing")},
                {"%k_x", "K_x", List.of("k")},
                {"%ключ", "Eключ", List.of("")},
                {"%k%%b?", "KEB?", List.of("k", "", "b")},
                {"%a-1.x!", "A!", List.of("a-1.x")},
                {"{%0}", "{%0}", List.of("0")},
                {"%k\uD800", "K\uD800", List.of("k")}
        };
        for (Object[] row : cases) {
            lang.lookups.clear();
            assertEquals(row[1], lang.parse((String) row[0], null));
            assertEquals(row[2], lang.lookups);
        }
    }

    @Test
    void prefixFiltersReplacementAfterEveryVirtualLookup() {
        Fixture lang = new Fixture();
        String[] prefixes = {"k", "", "%k", "missing"};
        String[] expected = {"K %b", "%k %b", "%k %b", "%k %b"};
        for (int i = 0; i < prefixes.length; i++) {
            lang.lookups.clear();
            assertEquals(expected[i], lang.parse("%k %b", prefixes[i]));
            assertEquals(List.of("k", "b"), lang.lookups);
        }
    }

    @Test
    void prefixMismatchDoesNotSuppressLookupFailures() {
        Fixture lang = new Fixture() {
            @Override public String internalGet(String key) { throw new IllegalStateException(key); }
        };
        assertEquals("plain", lang.parse("plain", "missing"));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> lang.parse("%k", "missing"));
        assertEquals("k", failure.getMessage());
    }

    @Test
    void replacementTextIsNotParsedAgainAndDictionariesRemainLive() {
        Fixture lang = new Fixture();
        lang.lang.put("k", "%next");
        lang.lang.put("next", "unexpected recursion");
        assertEquals("%next", lang.parse("%k", null));
        assertEquals(List.of("k"), lang.lookups);
        lang.lang.put("k", "changed");
        assertEquals("changed", lang.parse("%k", null));
        assertEquals("fallback", lang.parse("%fallback", null));
    }

    @Test
    void publicTranslationStillLooksUpPlainKeysAndSubstitutesLiteralParameters() {
        Fixture lang = new Fixture();
        assertEquals("plain translation", lang.translateString("plain-key"));
        assertEquals(List.of("plain-key"), lang.gets);
        assertEquals("Hi K", lang.translateString("template", new String[]{"%k"}, null));
        assertEquals("Hi null", lang.translateString("template", new String[]{null}, null));
        assertEquals("Hi $\\", lang.translateString("template", new String[]{"$\\"}, null));
        assertEquals("fallback", lang.translateString("fallback"));
    }

    @Test
    void parametersAreStillParsedWhenTemplateHasNoPlaceholder() {
        Fixture lang = new Fixture();
        assertEquals("plain", lang.translateString("plain", new String[]{"%k"}, null));
        assertEquals(List.of("k"), lang.lookups);
    }

    @Test
    void oneArgumentParserOverrideKeepsPublicDispatch() {
        Fixture lang = new Fixture() {
            @Override protected String parseTranslation(String text) {
                return "one[" + super.parseTranslation(text) + "]";
            }
        };
        assertEquals("plain", lang.translateString("plain"));
        assertEquals("Hi one[K]", lang.translateString("template", new String[]{"%k"}, null));
        assertEquals("one[plain]", lang.translate(new TextContainer("plain")));
    }

    @Test
    void twoArgumentParserOverrideKeepsPublicDispatch() {
        List<String> calls = new ArrayList<>();
        Fixture lang = new Fixture() {
            @Override protected String parseTranslation(String text, String prefix) {
                calls.add(String.valueOf(text));
                return "two[" + super.parseTranslation(text, prefix) + "]";
            }
        };
        assertEquals("two[Hi two[K]]", lang.translateString("template", new String[]{"%k"}, null));
        assertEquals(List.of("Hi {%0}", "%k"), calls);
    }

    private static class Fixture extends BaseLang {
        final List<String> lookups = new ArrayList<>();
        final List<String> gets = new ArrayList<>();

        Fixture() {
            super("eng");
            lang = new HashMap<>(Map.of("", "E", "k", "K", "b", "B", "a-1.x", "A",
                    "plain-key", "plain translation", "template", "Hi {%0}"));
            fallbackLang = new HashMap<>(Map.of("fallback", "fallback"));
        }

        String parse(String text, String prefix) { return parseTranslation(text, prefix); }

        @Override public String internalGet(String key) {
            lookups.add(key);
            return super.internalGet(key);
        }

        @Override public String get(String key) {
            gets.add(key);
            return super.get(key);
        }
    }
}
