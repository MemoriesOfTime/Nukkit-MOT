package cn.nukkit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 引导阶段的多语言消息：从 jar 内 {@code /bootstrap-lang/<code>.properties}（UTF-8）读取，
 * 缺键回落英文，英文包也不可读时渲染键名本身（降级不崩溃）。只允许使用 JDK。
 * <p>
 * Bootstrap-phase i18n from {@code /bootstrap-lang/<code>.properties} (UTF-8) inside the jar;
 * missing keys fall back to English, and if even that bundle is unreadable the key itself is
 * rendered (degraded but never fatal). JDK-only by design.
 */
public final class BootstrapLang {

    public static final String LANGUAGE_PROPERTY = "nukkit.bootstrap.lang";
    /** 与服务端 lang/ 目录同一套短码词汇 / the same short-code vocabulary as the server's lang/ dirs */
    public static final String FALLBACK_CODE = "eng";
    public static final List<String> BUNDLED_CODES = List.of("eng", "chs", "jpn", "rus", "deu", "vie");

    private static final String BUNDLE_DIR = "/bootstrap-lang/";
    private static final String SERVER_PROPERTIES_FILE = "server.properties";
    private static final String SERVER_LANGUAGE_KEY = "language";
    private static final String PREFIX = "[Bootstrap] ";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    private static volatile BootstrapLang instance;

    private final String code;
    private final Map<String, String> messages;

    private BootstrapLang(String code, Map<String, String> messages) {
        this.code = code;
        this.messages = messages;
    }

    public String code() {
        return code;
    }

    public static String get(String key, Object... args) {
        return instance().text(key, args);
    }

    public static String getLine(String key, Object... args) {
        return PREFIX + instance().text(key, args);
    }

    /**
     * 单趟替换：参数值不再被扫描，值内 {@code {N}} 字样原样保留，越界序号亦然。
     * <p>
     * Single-pass replacement: argument values are never rescanned, so a literal
     * {@code {N}} inside a value and out-of-range indices stay verbatim.
     */
    String text(String key, Object... args) {
        String template = messages.get(key);
        if (template == null) {
            return key;
        }
        if (args == null || args.length == 0) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder(template.length());
        while (matcher.find()) {
            String replacement;
            try {
                int index = Integer.parseInt(matcher.group(1));
                replacement = index < args.length ? String.valueOf(args[index]) : matcher.group();
            } catch (NumberFormatException e) {
                replacement = matcher.group();
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static BootstrapLang instance() {
        BootstrapLang local = instance;
        if (local == null) {
            synchronized (BootstrapLang.class) {
                if (instance == null) {
                    instance = createDefault();
                }
                local = instance;
            }
        }
        return local;
    }

    /** 任何失败都回落英文：引导阶段绝不能因语言包翻车 / any failure degrades to English */
    private static BootstrapLang createDefault() {
        try {
            return forLanguage(resolveLanguage(
                    System.getProperty(LANGUAGE_PROPERTY),
                    Path.of(System.getProperty("user.dir"), SERVER_PROPERTIES_FILE),
                    Locale.getDefault()));
        } catch (Throwable t) {
            return forLanguage(FALLBACK_CODE);
        }
    }

    /**
     * 按语言码构建实例：目标包与英文合并（缺键走英文），目标包缺失时整个回落英文。
     * <p>
     * Builds an instance for a code: the target bundle merges over English; a missing
     * bundle falls back to English wholesale.
     */
    static BootstrapLang forLanguage(String rawLanguage) {
        String code = normalizeLanguage(rawLanguage);
        Map<String, String> fallback = loadBundle(FALLBACK_CODE);
        if (fallback == null) {
            // 连英文包都读不到（jar 损坏）：渲染键名，保住启动 / render keys and keep booting
            return new BootstrapLang(FALLBACK_CODE, Map.of());
        }
        if (code == null || code.equals(FALLBACK_CODE)) {
            return new BootstrapLang(FALLBACK_CODE, fallback);
        }
        Map<String, String> target = loadBundle(code);
        if (target == null) {
            return new BootstrapLang(FALLBACK_CODE, fallback);
        }
        Map<String, String> merged = new HashMap<>(fallback);
        merged.putAll(target);
        return new BootstrapLang(code, merged);
    }

    /**
     * 语言解析全序（纯函数，供测试）：显式属性 &gt; server.properties &gt; Locale，
     * 某一级无法识别时顺延下一级，全部无法识别返回 null（由 forLanguage 回落英文）。
     * <p>
     * Full resolution order (pure, test-friendly): explicit property &gt; server.properties
     * &gt; locale; an unrecognized value falls through to the next level, null only when
     * nothing is recognizable.
     */
    static String resolveLanguage(String explicit, Path serverProperties, Locale locale) {
        String fromProperty = normalizeLanguage(explicit);
        if (fromProperty != null) {
            return fromProperty;
        }
        String fromServer = normalizeLanguage(serverPropertiesLanguage(serverProperties));
        if (fromServer != null) {
            return fromServer;
        }
        if (locale != null) {
            String language = locale.getLanguage();
            if ("zh".equals(language)) {
                return isTraditionalChinese(locale.getCountry(), locale.getScript()) ? "cht" : "chs";
            }
            return normalizeLanguage(language);
        }
        return null;
    }

    /**
     * 归一化语言标识：接受 eng/chs 短码与 en_US/zh-CN 等 Locale 形态（大小写、分隔符、
     * 区域后缀容错），无对应语言包返回 null；zh 繁体区域（TW/HK/MO/Hant）归 cht
     * （当前无包，最终回落英文）。
     * <p>
     * Normalizes server short codes and locale forms (case, separator and region tolerant);
     * returns null when no bundle matches. Traditional-Chinese regions map to cht (currently
     * unbundled, so ultimately English).
     */
    static String normalizeLanguage(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (s.isEmpty()) {
            return null;
        }
        int sep = s.indexOf('_');
        String language = sep < 0 ? s : s.substring(0, sep);
        String region = sep < 0 ? "" : s.substring(sep + 1);
        return switch (language) {
            case "eng", "en" -> "eng";
            case "chs" -> "chs";
            case "cht" -> "cht";
            case "zh" -> isTraditionalChinese(region, "") ? "cht" : "chs";
            case "jpn", "ja" -> "jpn";
            case "rus", "ru" -> "rus";
            case "deu", "de" -> "deu";
            case "vie", "vi" -> "vie";
            default -> null;
        };
    }

    private static boolean isTraditionalChinese(String region, String script) {
        if ("hant".equalsIgnoreCase(script)) {
            return true;
        }
        return switch (region.toLowerCase(Locale.ROOT)) {
            case "tw", "hk", "mo", "hant" -> true;
            default -> false;
        };
    }

    /**
     * 读 server.properties 的 {@code language}：容错 BOM、注释与空行；文件缺失、
     * 键不存在或值为空返回 null。
     * <p>
     * Reads {@code language} from server.properties, tolerant of BOM and comments; a
     * missing file/key or empty value yields null.
     */
    static String serverPropertiesLanguage(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            boolean first = true;
            while (line != null) {
                if (first) {
                    line = line.replaceFirst("^\uFEFF", "");
                    first = false;
                }
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("!")) {
                    int eq = line.indexOf('=');
                    if (eq > 0 && line.substring(0, eq).trim().equals(SERVER_LANGUAGE_KEY)) {
                        String value = line.substring(eq + 1).trim();
                        return value.isEmpty() ? null : value;
                    }
                }
                line = reader.readLine();
            }
        } catch (IOException ignored) {
            // 读不了就当没配置 / unreadable counts as unset
        }
        return null;
    }

    static Map<String, String> loadBundle(String code) {
        try (InputStream in = BootstrapLang.class.getResourceAsStream(BUNDLE_DIR + code + ".properties")) {
            if (in == null) {
                return null;
            }
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            Map<String, String> map = new HashMap<>();
            for (String name : props.stringPropertyNames()) {
                map.put(name, props.getProperty(name));
            }
            return map;
        } catch (IOException e) {
            return null;
        }
    }
}
