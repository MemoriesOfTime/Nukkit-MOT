package cn.nukkit.lang;

import cn.nukkit.Server;
import cn.nukkit.utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class BaseLang {

    public static final String FALLBACK_LANGUAGE = "eng";

    protected final String langName;

    protected Map<String, String> lang;
    protected Map<String, String> fallbackLang = new HashMap<>();

    public BaseLang(String lang) {
        this(lang, null);
    }

    public BaseLang(String lang, String path) {
        this(lang, path, FALLBACK_LANGUAGE);
    }

    public BaseLang(String lang, String path, String fallback) {
        this.langName = lang.toLowerCase();
        boolean useFallback = !lang.equals(fallback);

        if (path == null) {
            path = "lang/";
            this.lang = loadLang(this.getClass().getClassLoader().getResourceAsStream(path + this.langName + "/lang.ini"));
            if (useFallback) this.fallbackLang = loadLang(this.getClass().getClassLoader().getResourceAsStream(path + fallback + "/lang.ini"));
        } else {
            this.lang = loadLang(path + this.langName + "/lang.ini");
            if (useFallback) this.fallbackLang = loadLang(path + fallback + "/lang.ini");
        }

        if (this.fallbackLang == null) {
            this.fallbackLang = this.lang;
        }
    }

    public Map<String, String> getLangMap() {
        return this.lang;
    }

    public Map<String, String> getFallbackLangMap() {
        return this.fallbackLang;
    }

    public String getName() {
        return this.get("language.name");
    }

    public String getLang() {
        return this.langName;
    }

    protected static Map<String, String> loadLang(String path) {
        try {
            return getKeys(Utils.readFile(path));

        } catch (IOException e) {
            Server.getInstance().getLogger().logException(e);
            return null;
        }
    }

    protected static Map<String, String> loadLang(InputStream stream) {
        try {
            return getKeys(Utils.readFile(stream));
        } catch (IOException e) {
            Server.getInstance().getLogger().logException(e);
            return null;
        }
    }

    public String translateString(String str) {
        return this.translateString(str, new String[]{}, null);
    }

    public String translateString(String str, String... params) {
        return this.translateString(str, params == null ? new String[0] : params, null);
    }

    public String translateString(String str, Object... params) {
        if (params != null) {
            String[] paramsToString = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                paramsToString[i] = Objects.toString(params[i]);
            }
            return this.translateString(str, paramsToString, null);
        }
        return this.translateString(str, new String[0], null);
    }

    public String translateString(String str, String param, String onlyPrefix) {
        return this.translateString(str, new String[]{param}, onlyPrefix);
    }

    public String translateString(String str, String[] params, String onlyPrefix) {
        String baseText = this.get(str);
        baseText = this.parseTranslation((baseText != null && (onlyPrefix == null || str.indexOf(onlyPrefix) == 0)) ? baseText : str, onlyPrefix);
        for (int i = 0; i < params.length; i++) {
            baseText = baseText.replace("{%" + i + '}', this.parseTranslation(String.valueOf(params[i])));
        }

        return baseText;
    }

    public String translate(TextContainer c) {
        String baseText = this.parseTranslation(c.getText());
        if (c instanceof TranslationContainer) {
            baseText = this.internalGet(c.getText());
            baseText = this.parseTranslation(baseText != null ? baseText : c.getText());
            for (int i = 0; i < ((TranslationContainer) c).getParameters().length; i++) {
                baseText = baseText.replace("{%" + i + '}', this.parseTranslation(((TranslationContainer) c).getParameters()[i]));
            }
        }
        return baseText;
    }

    public String internalGet(String id) {
        if (this.lang.containsKey(id)) {
            return this.lang.get(id);
        } else if (this.fallbackLang.containsKey(id)) {
            return this.fallbackLang.get(id);
        }
        return null;
    }

    public String get(String id) {
        if (this.lang.containsKey(id)) {
            return this.lang.get(id);
        } else if (this.fallbackLang.containsKey(id)) {
            return this.fallbackLang.get(id);
        }
        return id;
    }

    protected String parseTranslation(String text) {
        return this.parseTranslation(text, null);
    }

    protected String parseTranslation(String text, String onlyPrefix) {
        StringBuilder newString = new StringBuilder();
        text = String.valueOf(text);

        StringBuilder replaceString = null;

        int len = text.length();

        for (int i = 0; i < len; ++i) {
            char c = text.charAt(i);
            if (replaceString != null) {
                if (((int) c >= 0x30 && (int) c <= 0x39) // 0-9
                        || ((int) c >= 0x41 && (int) c <= 0x5a) // A-Z
                        || ((int) c >= 0x61 && (int) c <= 0x7a) || // a-z
                        c == '.' || c == '-') {
                    replaceString.append(c);
                } else {
                    String t = this.internalGet(replaceString.substring(1));
                    if (t != null && (onlyPrefix == null || replaceString.indexOf(onlyPrefix) == 1)) {
                        newString.append(t);
                    } else {
                        newString.append(replaceString);
                    }
                    replaceString = null;
                    if (c == '%') {
                        replaceString = new StringBuilder(String.valueOf(c));
                    } else {
                        newString.append(c);
                    }
                }
            } else if (c == '%') {
                replaceString = new StringBuilder(String.valueOf(c));
            } else {
                newString.append(c);
            }
        }

        if (replaceString != null) {
            String t = this.internalGet(replaceString.substring(1));
            if (t != null && (onlyPrefix == null || replaceString.indexOf(onlyPrefix) == 1)) {
                newString.append(t);
            } else {
                newString.append(replaceString);
            }
        }
        return newString.toString();
    }

    private static Map<String, String> getKeys(String content) {
        Map<String, String> d = new HashMap<>();
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            String[] t = line.split("=", 2);
            if (t.length != 2) {
                continue;
            }
            d.put(t[0], t[1]);
        }
        return d;
    }
}