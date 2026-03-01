package cn.nukkit.utils.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.schema.FieldDeclaration;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

/**
 * Applies localized comments to the TOML configuration based on server language.
 */
@Log4j2
public class ConfigComments {

    private static final String FALLBACK_LANG = "eng";

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
        Map<String, OkaeriConfig> categories = Map.of(
                "performanceSettings", config.performanceSettings(),
                "networkSettings", config.networkSettings(),
                "chunkSettings", config.chunkSettings(),
                "entitySettings", config.entitySettings(),
                "worldSettings", config.worldSettings(),
                "playerSettings", config.playerSettings(),
                "debugSettings", config.debugSettings(),
                "gameFeatureSettings", config.gameFeatureSettings(),
                "neteaseSettings", config.neteaseSettings()
        );

        for (Map.Entry<String, OkaeriConfig> entry : categories.entrySet()) {
            applyFieldComments(entry.getValue(), comments, entry.getKey());
        }
    }

    /**
     * Apply comments to fields of an OkaeriConfig.
     * <p>
     * Updates both the current declaration instances and the static FieldDeclaration cache.
     * The cache update is necessary because during TOML save, the configurer resolves
     * sub-config declarations via ConfigDeclaration.of(Class) which creates new
     * FieldDeclaration instances from the static cache, bypassing instance-level changes.
     *
     * @param config   the config object
     * @param comments the loaded properties
     * @param prefix   the property key prefix (null for top-level ServerConfig fields)
     */
    private static void applyFieldComments(OkaeriConfig config, Properties comments, String prefix) {
        for (FieldDeclaration field : config.getDeclaration().getFields()) {
            String key = prefix == null ? field.getName() : prefix + "." + field.getName();
            String comment = comments.getProperty(key);
            if (comment != null) {
                String[] commentArray = new String[]{comment};
                field.setComment(commentArray);
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

                String key = prefix + "." + cached.getName();
                String comment = comments.getProperty(key);
                if (comment != null) {
                    cached.setComment(new String[]{comment});
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
