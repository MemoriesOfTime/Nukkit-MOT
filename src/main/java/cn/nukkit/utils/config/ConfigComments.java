package cn.nukkit.utils.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.schema.FieldDeclaration;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * Post-process the TOML file to convert dotted worlds entries
     * (e.g. {@code worlds.lobby.generator = 'flat'}) into proper TOML table sections
     * (e.g. {@code [worldSettings.worlds.lobby]}).
     *
     * @param tomlFile the nukkit-mot.toml file
     */
    public static void formatWorldEntries(File tomlFile) {
        try {
            List<String> lines = Files.readAllLines(tomlFile.toPath(), StandardCharsets.UTF_8);
            List<String> result = new ArrayList<>();

            Pattern dottedKey = Pattern.compile("^worlds\\.(\\S+)\\.(\\S+)\\s*=\\s*(.*)$");

            boolean inWorldSettings = false;
            boolean firstWorldSeen = false;
            String currentWorld = null;
            List<String> pendingComments = new ArrayList<>();

            for (String line : lines) {
                String trimmed = line.trim();

                // Track section headers
                if (trimmed.startsWith("[") && !trimmed.startsWith("[[")) {
                    // Flush any remaining pending comments before a new section
                    result.addAll(pendingComments);
                    pendingComments.clear();
                    inWorldSettings = trimmed.equals("[worldSettings]");
                    currentWorld = null;
                    firstWorldSeen = false;
                    result.add(line);
                    continue;
                }

                if (inWorldSettings) {
                    Matcher m = dottedKey.matcher(trimmed);
                    if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                        pendingComments.add(line);
                        continue;
                    } else if (m.matches()) {
                        String worldName = m.group(1);
                        String field = m.group(2);
                        String value = m.group(3);

                        if (!worldName.equals(currentWorld)) {
                            // New world section
                            if (!firstWorldSeen) {
                                firstWorldSeen = true;
                                // First pending comment is the worlds field-level comment
                                if (!pendingComments.isEmpty()) {
                                    result.add(pendingComments.remove(0));
                                }
                            }
                            currentWorld = worldName;
                            result.add("[worldSettings.worlds." + worldName + "]");
                        }
                        result.addAll(pendingComments);
                        pendingComments.clear();
                        result.add(field + " = " + value);
                        continue;
                    }
                }

                // Non-worlds line or outside worldSettings
                result.addAll(pendingComments);
                pendingComments.clear();
                result.add(line);
            }

            result.addAll(pendingComments);
            Files.write(tomlFile.toPath(), result, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to format worlds table sections in TOML", e);
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
