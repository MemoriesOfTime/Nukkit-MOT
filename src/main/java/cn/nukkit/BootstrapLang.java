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
 * 缺键回落英文，英文包也不可读时渲染键名本身（降级不崩溃）。
 * 语言解析顺序：{@code -Dnukkit.bootstrap.lang} &gt; 工作目录 server.properties 的 {@code language}
 * &gt; JVM 默认 Locale；接受服务端短码（eng/chs）与标准 Locale 码（en_US/zh-CN/zh），
 * 某一级取值无法识别时顺延下一级，全部无法识别才回落英文。只允许使用 JDK。
 * <p>
 * Bootstrap-phase i18n: messages come from {@code /bootstrap-lang/<code>.properties} (UTF-8) inside
 * the jar, missing keys fall back to the English bundle, and if even that is unreadable the key
 * itself is rendered (degraded but never fatal). Language resolution: {@code -Dnukkit.bootstrap.lang}
 * &gt; {@code language} in the working directory's server.properties &gt; the JVM default locale;
 * both server short codes (eng/chs) and standard locale codes (en_US/zh-CN/zh) are accepted, an
 * unrecognized value at one level falls through to the next, and only when no level yields a bundle
 * does it fall back to English. JDK-only by design.
 */
public final class BootstrapLang {

    public static final String LANGUAGE_PROPERTY = "nukkit.bootstrap.lang";
    /** 与服务端 lang/ 目录同一套短码词汇 / the same short-code vocabulary as the server's lang/ dirs */
    public static final String FALLBACK_CODE = "eng";
    /** 六种已随包语言 / languages shipped in the jar */
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

    /** 实际生效的语言码（解析失败或无语言包时为 eng）。 */
    public String code() {
        return code;
    }

    /**
     * 取原始消息文本，{@code {0}}、{@code {1}} 依次替换为参数。
     * <p>
     * Raw message text with {@code {0}}, {@code {1}}... replaced by the arguments.
     */
    public static String get(String key, Object... args) {
        return instance().text(key, args);
    }

    /** 控制台行：{@code [Bootstrap] } 前缀 + 消息文本。 */
    public static String getLine(String key, Object... args) {
        return PREFIX + instance().text(key, args);
    }

    /**
     * 单趟替换：参数值不再被后续占位符扫描，值内出现的 {@code {N}} 字样原样保留；
     * 越界序号的占位符也原样保留。
     * <p>
     * Single-pass replacement: argument values are never rescanned for placeholders, so a
     * literal {@code {N}} inside a value survives; out-of-range indices stay verbatim too.
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

    /**
     * 默认实例的解析入口；任何异常都回落英文，引导阶段绝不能因语言包翻车。
     * <p>
     * Resolution entry for the default instance; any failure degrades to English —
     * the bootstrap phase must never die over a message bundle.
     */
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
     * 按语言码构建实例：加载目标语言包并与英文合并（缺键走英文），目标包缺失时整个回落英文。
     * <p>
     * Builds an instance for a language code: the target bundle is merged over English
     * (missing keys serve English); a missing target bundle falls back to English wholesale.
     */
    static BootstrapLang forLanguage(String rawLanguage) {
        String code = normalizeLanguage(rawLanguage);
        Map<String, String> fallback = loadBundle(FALLBACK_CODE);
        if (fallback == null) {
            // 连英文包都读不到（jar 损坏）：渲染键名，保住启动 / even English unreadable
            // (corrupt jar): render keys and keep booting
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
     * 任何一级取值无法识别即顺延下一级而非直接回落英文；全部无法识别才返回 null
     * （由 forLanguage 回落英文）。
     * <p>
     * Full resolution order (pure, test-friendly): explicit property &gt; server.properties &gt;
     * locale; an unrecognized value at one level falls through to the next rather than forcing
     * English immediately. Returns null only when nothing is recognizable (forLanguage then
     * falls to English).
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
     * 归一化语言标识：接受 eng/chs 短码与 en_US/zh-CN/zh 等 Locale 形态（大小写、
     * {@code -}/{@code _} 分隔、区域后缀均容错），无对应语言包的返回 null。
     * zh 的繁体区域（TW/HK/MO/Hant）归 cht（当前无包，最终回落英文）。
     * <p>
     * Normalizes a language tag: accepts server short codes (eng/chs) and locale forms
     * (en_US/zh-CN/zh) tolerating case, {@code -}/{@code _} separators and region suffixes;
     * returns null for languages without a bundle. Traditional-Chinese regions of zh
     * (TW/HK/MO/Hant) map to cht (currently unbundled, so ultimately English).
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
     * 读 server.properties 的 {@code language} 值：行级扫描，容错 BOM、注释与空行，
     * 文件缺失、键不存在或值为空都返回 null。
     * <p>
     * Reads {@code language} from server.properties with a line scanner tolerant of BOM,
     * comments and blank lines; a missing file/key or an empty value yields null.
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

    /**
     * 加载一份语言包（UTF-8 properties）；资源不存在或读失败返回 null。
     * <p>
     * Loads one bundle (UTF-8 properties); returns null when absent or unreadable.
     */
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
