package cn.nukkit.network.encryption;

import lombok.extern.log4j.Log4j2;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.json.internal.json_simple.parser.JSONParser;
import org.jose4j.json.internal.json_simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages persistent disk cache for Minecraft authentication service data
 * to avoid rate limiting during frequent server restarts.
 */
@Log4j2
public class AuthCacheManager {
    private static final String CACHE_DIR = "cache/auth";
    private static final String DISCOVERY_CACHE_FILE = "discovery.json";
    private static final String OPENID_CACHE_FILE = "openid-config.json";
    private static final long DEFAULT_CACHE_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours
    private static final JSONParser JSON_PARSER = new JSONParser();

    private final Path cacheDirectory;
    private final long cacheTtlMs;

    public AuthCacheManager(String dataPath) {
        this(dataPath, DEFAULT_CACHE_TTL_MS);
    }

    public AuthCacheManager(String dataPath, long cacheTtlMs) {
        this.cacheDirectory = Paths.get(dataPath, CACHE_DIR);
        this.cacheTtlMs = cacheTtlMs;
        ensureCacheDirectoryExists();
    }

    private void ensureCacheDirectoryExists() {
        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException e) {
            log.warn("Failed to create auth cache directory: {}", e.getMessage());
        }
    }

    /**
     * Load discovery data from disk cache
     * @return cached discovery data, or null if cache is missing or expired
     */
    public Map<String, Object> loadDiscoveryData() {
        return loadCache(DISCOVERY_CACHE_FILE);
    }

    /**
     * Save discovery data to disk cache
     * @param data discovery data to cache
     */
    public void saveDiscoveryData(Map<String, Object> data) {
        saveCache(DISCOVERY_CACHE_FILE, data);
    }

    /**
     * Load OpenID configuration from disk cache
     * @return cached OpenID configuration, or null if cache is missing or expired
     */
    public Map<String, Object> loadOpenIdConfiguration() {
        return loadCache(OPENID_CACHE_FILE);
    }

    /**
     * Save OpenID configuration to disk cache
     * @param data OpenID configuration to cache
     */
    public void saveOpenIdConfiguration(Map<String, Object> data) {
        saveCache(OPENID_CACHE_FILE, data);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadCache(String filename) {
        Path cacheFile = cacheDirectory.resolve(filename);

        if (!Files.exists(cacheFile)) {
            log.debug("Cache file does not exist: {}", filename);
            return null;
        }

        try {
            long lastModified = Files.getLastModifiedTime(cacheFile).toMillis();
            long age = System.currentTimeMillis() - lastModified;

            if (age > cacheTtlMs) {
                log.info("Cache file expired (age: {}ms, ttl: {}ms): {}", age, cacheTtlMs, filename);
                return null;
            }

            try (BufferedReader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
                Map<String, Object> cacheWrapper = (Map<String, Object>) JSON_PARSER.parse(reader);
                Map<String, Object> data = (Map<String, Object>) cacheWrapper.get("data");

                if (data == null) {
                    log.warn("Cache file has invalid format: {}", filename);
                    return null;
                }

                log.info("Loaded cached data from: {} (age: {}ms)", filename, age);
                return data;
            }
        } catch (IOException | ParseException e) {
            log.warn("Failed to load cache file {}: {}", filename, e.getMessage());
            return null;
        }
    }

    private void saveCache(String filename, Map<String, Object> data) {
        if (data == null) {
            return;
        }

        Path cacheFile = cacheDirectory.resolve(filename);

        try {
            Map<String, Object> cacheWrapper = new HashMap<>();
            cacheWrapper.put("timestamp", System.currentTimeMillis());
            cacheWrapper.put("data", data);

            String json = JSONObject.toJSONString(cacheWrapper);

            try (BufferedWriter writer = Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8)) {
                writer.write(json);
            }

            log.info("Saved cache to: {}", filename);
        } catch (IOException e) {
            log.warn("Failed to save cache file {}: {}", filename, e.getMessage());
        }
    }

    /**
     * Clear all cached authentication data
     */
    public void clearCache() {
        try {
            Files.deleteIfExists(cacheDirectory.resolve(DISCOVERY_CACHE_FILE));
            Files.deleteIfExists(cacheDirectory.resolve(OPENID_CACHE_FILE));
            log.info("Cleared authentication cache");
        } catch (IOException e) {
            log.warn("Failed to clear cache: {}", e.getMessage());
        }
    }
}
