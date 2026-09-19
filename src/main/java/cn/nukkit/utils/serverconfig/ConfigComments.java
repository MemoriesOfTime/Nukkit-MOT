package cn.nukkit.utils.serverconfig;

import cn.nukkit.utils.serverconfig.category.WorldEntry;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.schema.FieldDeclaration;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Applies localized comments to the YAML configuration based on server language.
 */
@Log4j2
public class ConfigComments {

    private static final String FALLBACK_LANG = "eng";

    private static final String PROPERTIES_PREFIX = "properties.";

    private static final String UNRECOGNIZED_KEY = "properties.__unrecognized__";

    /**
     * 加载 server.properties 的逐键注释表（键去掉 "properties." 前缀）
     * <p>
     * Load per-key comments for server.properties (with the "properties." prefix stripped).
     * English entries fill in any keys missing from the requested language.
     *
     * @param lang the language code (e.g. "eng", "chs")
     * @return key -> comment map, empty if no "properties.*" entries exist in any language
     */
    public static Map<String, String> loadPropertyComments(String lang) {
        Map<String, String> comments = new HashMap<>();
        collectPropertyEntries(loadComments(FALLBACK_LANG), comments);
        if (!FALLBACK_LANG.equals(lang)) {
            collectPropertyEntries(loadComments(lang), comments);
        }
        return comments;
    }

    /**
     * 加载未识别配置项段的分隔注释（可含换行）
     * <p>
     * Load the separator comment for the trailing unrecognized-keys block (newlines allowed).
     *
     * @param lang the language code (e.g. "eng", "chs")
     * @return comment text, null if not defined in any language
     */
    public static String loadUnrecognizedPropertyComment(String lang) {
        Properties target = FALLBACK_LANG.equals(lang) ? null : loadComments(lang);
        String value = target != null ? target.getProperty(UNRECOGNIZED_KEY) : null;
        if (value == null) {
            Properties eng = loadComments(FALLBACK_LANG);
            value = eng != null ? eng.getProperty(UNRECOGNIZED_KEY) : null;
        }
        return value;
    }

    private static void collectPropertyEntries(Properties props, Map<String, String> out) {
        if (props == null) {
            return;
        }
        for (String name : props.stringPropertyNames()) {
            if (name.startsWith(PROPERTIES_PREFIX) && !name.equals(UNRECOGNIZED_KEY)) {
                out.put(name.substring(PROPERTIES_PREFIX.length()), props.getProperty(name));
            }
        }
    }

    /**
     * Apply localized comments to the server config.
     *
     * @param config the server config instance
     * @param lang   the language code (e.g. "eng", "chs")
     */
    public static void apply(ServerConfig config, String lang) {
        Properties comments = loadComments(lang);
        if (comments == null) {
            comments = loadComments(FALLBACK_LANG);
        }
        if (comments == null) {
            return;
        }

        // Category-level comments on ServerConfig fields
        applyFieldComments(config, comments, null);

        // Field-level comments on each category config
        Map<String, OkaeriConfig> categories = new HashMap<>();
        categories.put("performanceSettings", config.performanceSettings());
        categories.put("networkSettings", config.networkSettings());
        categories.put("chunkSettings", config.chunkSettings());
        categories.put("entitySettings", config.entitySettings());
        categories.put("worldSettings", config.worldSettings());
        categories.put("playerSettings", config.playerSettings());
        categories.put("debugSettings", config.debugSettings());
        categories.put("gameFeatureSettings", config.gameFeatureSettings());
        categories.put("neteaseSettings", config.neteaseSettings());
        categories.put("customBlockSettings", config.customBlockSettings());

        for (Map.Entry<String, OkaeriConfig> entry : categories.entrySet()) {
            applyFieldComments(entry.getValue(), comments, entry.getKey());
        }

        // WorldEntry values inside the worlds map; a fresh instance primes the declaration
        // cache so its (localized) fields survive the save-time ConfigDeclaration.of lookup
        applyFieldComments(new WorldEntry(), comments, "worldEntry");
        for (WorldEntry entry : config.worldSettings().worlds().values()) {
            applyFieldComments(entry, comments, "worldEntry");
        }
    }

    /**
     * Apply comments to fields of an OkaeriConfig.
     * <p>
     * Updates both the current declaration instances and the static FieldDeclaration cache.
     * The cache update is necessary because during save, the configurer resolves
     * sub-config declarations via ConfigDeclaration.of(Class) which creates new
     * FieldDeclaration instances from the static cache, bypassing instance-level changes.
     *
     * @param config   the config object
     * @param comments the loaded properties
     * @param prefix   the property key prefix (null for top-level ServerConfig fields)
     */
    private static void applyFieldComments(OkaeriConfig config, Properties comments, String prefix) {
        for (FieldDeclaration field : config.getDeclaration().getFields()) {
            // Use Java field name for property lookup (not the resolved YAML key name)
            String fieldName = field.getField() != null ? field.getField().getName() : field.getName();
            String key = prefix == null ? fieldName : prefix + "." + fieldName;
            String comment = comments.getProperty(key);
            if (comment != null) {
                String[] lines = comment.split("\n");
                // Add blank line before top-level category sections for readability
                if (prefix == null) {
                    String[] withBlank = new String[lines.length + 1];
                    withBlank[0] = "";
                    System.arraycopy(lines, 0, withBlank, 1, lines.length);
                    lines = withBlank;
                }
                field.setComment(lines);
            }
        }

        // Also update the static FieldDeclaration cache for sub-configs
        if (prefix != null) {
            updateFieldDeclarationCache(config.getClass(), comments, prefix);
        }
    }

    /**
     * Update the static FieldDeclaration.DECLARATION_CACHE so that translated comments
     * persist when new declarations are created during config save.
     * <p>
     * WARNING: This method relies on the internal {@code DECLARATION_CACHE} field of
     * okaeri-configs (6.1.0-beta.1). When upgrading the okaeri-configs library version,
     * verify that this field still exists and has the same structure.
     */
    @SuppressWarnings("unchecked")
    private static void updateFieldDeclarationCache(Class<?> configClass, Properties comments, String prefix) {
        try {
            Field cacheField = FieldDeclaration.class.getDeclaredField("DECLARATION_CACHE");
            cacheField.setAccessible(true);
            Map<?, FieldDeclaration> cache = (Map<?, FieldDeclaration>) cacheField.get(null);

            for (FieldDeclaration cached : cache.values()) {
                if (cached == null || cached.getField() == null) continue;
                if (cached.getField().getDeclaringClass() != configClass) continue;

                String key = prefix + "." + cached.getField().getName();
                String comment = comments.getProperty(key);
                if (comment != null) {
                    cached.setComment(comment.split("\n"));
                }
            }
        } catch (ReflectiveOperationException e) {
            log.warn("Failed to update FieldDeclaration cache for {}", configClass.getSimpleName(), e);
        }
    }

    private static Properties loadComments(String lang) {
        String path = "lang/" + lang + "/config_comments.properties";
        try (InputStream is = ConfigComments.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return null;
            }
            Properties props = new Properties();
            props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            return props;
        } catch (IOException e) {
            log.warn("Failed to load config comments for language: {}", lang, e);
            return null;
        }
    }
}
