package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.scheduler.FileWriteTask;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Config
 *
 * @author MagicDroidX
 * Nukkit
 */
public class Config {

    public static final int DETECT = -1; //Detect by file extension
    public static final int PROPERTIES = 0; // .properties
    public static final int CNF = Config.PROPERTIES; // .cnf
    public static final int JSON = 1; // .js, .json
    public static final int YAML = 2; // .yml, .yaml
    public static final int EXPORT = 3; // .export, .xport
    public static final int SERIALIZED = 4; // .sl
    public static final int ENUM = 5; // .txt, .list, .enum
    public static final int ENUMERATION = Config.ENUM;
    public static final int TOML = 6; // .toml

    private ConfigSection config = new ConfigSection();
    private File file;
    private boolean correct = false;
    private int type = Config.DETECT;

    /**
     * List of supported config file formats
     */
    public static final Map<String, Integer> format = new TreeMap<>();

    static {
        format.put("properties", Config.PROPERTIES);
        format.put("con", Config.PROPERTIES);
        format.put("conf", Config.PROPERTIES);
        format.put("config", Config.PROPERTIES);
        format.put("js", Config.JSON);
        format.put("json", Config.JSON);
        format.put("yml", Config.YAML);
        format.put("yaml", Config.YAML);
        format.put("sl", Config.SERIALIZED);
        format.put("serialize", Config.SERIALIZED);
        format.put("txt", Config.ENUM);
        format.put("list", Config.ENUM);
        format.put("enum", Config.ENUM);
        format.put("toml", Config.TOML);
    }

    /**
     * Constructor for Config instance with undefined file object
     *
     * @param type - Config type
     */
    public Config(int type) {
        this.type = type;
        this.correct = true;
        this.config = new ConfigSection();
    }

    /**
     * Constructor for Config (YAML) instance with undefined file object
     */
    public Config() {
        this(Config.YAML);
    }

    public Config(String file) {
        this(file, Config.DETECT);
    }

    public Config(File file) {
        this(file.toString(), Config.DETECT);
    }

    public Config(String file, int type) {
        this(file, type, new ConfigSection());
    }

    public Config(File file, int type) {
        this(file.toString(), type, new ConfigSection());
    }

    public Config(String file, int type, LinkedHashMap<String, Object> defaultMap) {
        this.load(file, type, new ConfigSection(defaultMap));
    }

    public Config(String file, int type, ConfigSection defaultMap) {
        this.load(file, type, defaultMap);
    }

    public Config(File file, int type, ConfigSection defaultMap) {
        this.load(file.toString(), type, defaultMap);
    }

    public Config(File file, int type, LinkedHashMap<String, Object> defaultMap) {
        this(file.toString(), type, new ConfigSection(defaultMap));
    }

    /**
     * Reload config from disk
     */
    public void reload() {
        this.config.clear();
        this.correct = false;
        if (this.file == null) throw new IllegalStateException("Failed to reload Config. File object is undefined.");
        this.load(this.file.toString(), this.type);
    }

    /**
     * Try to load a config file and automatically detect its type
     *
     * @param file file path
     * @return loaded
     */
    public boolean load(String file) {
        return this.load(file, Config.DETECT);
    }

    /**
     * Try to load a config file with a given type
     *
     * @param file file path
     * @param type file type
     * @return loaded
     */
    public boolean load(String file, int type) {
        return this.load(file, type, new ConfigSection());
    }

    /**
     * Try to load a config file with a given type and default content
     *
     * @param file file path
     * @param type file type
     * @param defaultMap default content
     * @return loaded
     */
    public boolean load(String file, int type, ConfigSection defaultMap) {
        this.correct = true;
        this.type = type;
        this.file = new File(file);
        if (!this.file.exists()) {
            try {
                this.file.getParentFile().mkdirs();
                this.file.createNewFile();
            } catch (IOException e) {
                MainLogger.getLogger().error("Could not create Config " + this.file.toString(), e);
            }
            this.config = defaultMap;
            this.save();
        } else {
            if (this.type == Config.DETECT) {
                String extension = "";
                if (this.file.getName().lastIndexOf('.') != -1 && this.file.getName().lastIndexOf('.') != 0) {
                    extension = this.file.getName().substring(this.file.getName().lastIndexOf('.') + 1);
                }
                if (format.containsKey(extension)) {
                    this.type = format.get(extension);
                } else {
                    this.correct = false;
                }
            }
            if (this.correct) {
                String content = "";
                try {
                    content = Utils.readFile(this.file);
                } catch (IOException e) {
                    Server.getInstance().getLogger().logException(e);
                }
                this.parseContent(content);
                if (!this.correct) return false;
                if (this.setDefault(defaultMap) > 0) {
                    this.save();
                }
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Load Config from InputStream
     *
     * @param inputStream InputStream
     * @return loaded
     */
    public boolean load(InputStream inputStream) {
        if (inputStream == null) return false;
        if (this.correct) {
            String content;
            try {
                content = Utils.readFile(inputStream);
            } catch (IOException e) {
                Server.getInstance().getLogger().logException(e);
                return false;
            }
            this.parseContent(content);
        }
        return correct;
    }

    /**
     * Load and return a Config from InputStream
     *
     * @param inputStream InputStream
     * @return Config
     */
    public Config loadFromStream(InputStream inputStream) {
        if (inputStream == null) return null;
        if (this.correct) {
            String content;
            try {
                content = Utils.readFile(inputStream);
            } catch (IOException e) {
                Server.getInstance().getLogger().logException(e);
                return null;
            }
            this.parseContent(content);
        }
        return this;
    }

    /**
     * Check if the config is valid
     *
     * @return valid
     */
    public boolean check() {
        return this.correct;
    }

    /**
     * Check if the config is valid
     *
     * @return valid
     */
    public boolean isCorrect() {
        return this.correct;
    }

    /**
     * Save configuration into provided file. Internal file object will be set to new file.
     *
     * @param file file
     * @param async async
     * @return save success
     */
    public boolean save(File file, boolean async) {
        this.file = file;
        return save(async);
    }

    /**
     * Save configuration into provided file. Internal file object will be set to new file.
     *
     * @param file file
     * @return save success
     */
    public boolean save(File file) {
        this.file = file;
        return save();
    }

    /**
     * Save the config to disk
     *
     * @return saved
     */
    public boolean save() {
        return this.save(false);
    }

    /**
     * Save the config to disk
     *
     * @param async async
     * @return saved
     */
    public boolean save(Boolean async) { // Note: do not change to 'boolean' or plugins will break
        if (this.file == null) throw new IllegalStateException("Failed to save Config. File object is undefined.");
        if (this.correct) {
            StringBuilder content = new StringBuilder();
            switch (this.type) {
                case Config.PROPERTIES:
                    content = new StringBuilder(this.writeProperties());
                    break;
                case Config.JSON:
                    content = new StringBuilder(new GsonBuilder().setPrettyPrinting().create().toJson(this.config));
                    break;
                case Config.YAML:
                    DumpSettings dumperOptions = DumpSettings.builder()
                            .setDefaultFlowStyle(FlowStyle.BLOCK)
                            .setSplitLines(false)
                            .setDumpComments(false)
                            .build();
                    Dump yaml = new Dump(dumperOptions);
                    content = new StringBuilder(yaml.dumpToString(this.config));
                    break;
                case Config.TOML:
                    content = new StringBuilder(this.writeToml());
                    break;
                case Config.ENUM:
                    for (Object o : this.config.entrySet()) {
                        Map.Entry entry = (Map.Entry) o;
                        content.append(entry.getKey()).append("\r\n");
                    }
                    break;
            }
            if (async) {
                Server.getInstance().getScheduler().scheduleAsyncTask(InternalPlugin.INSTANCE, new FileWriteTask(this.file, content.toString()));
            } else {
                try {
                    Utils.writeFile(this.file, content.toString());
                } catch (IOException e) {
                    Server.getInstance().getLogger().logException(e);
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Set a value in the config
     *
     * @param key key
     * @param value value
     */
    public void set(final String key, Object value) {
        this.config.set(key, value);
    }

    /**
     * Get a value in the config
     *
     * @param key key
     * @return value
     */
    public Object get(String key) {
        return this.get(key, null);
    }

    public <T> T get(String key, T defaultValue) {
        return this.correct ? this.config.get(key, defaultValue) : defaultValue;
    }

    public ConfigSection getSection(String key) {
        return this.correct ? this.config.getSection(key) : new ConfigSection();
    }

    public boolean isSection(String key) {
        return config.isSection(key);
    }

    public ConfigSection getSections(String key) {
        return this.correct ? this.config.getSections(key) : new ConfigSection();
    }

    public ConfigSection getSections() {
        return this.correct ? this.config.getSections() : new ConfigSection();
    }

    public int getInt(String key) {
        return this.getInt(key, 0);
    }

    public int getInt(String key, int defaultValue) {
        return this.correct ? this.config.getInt(key, defaultValue) : defaultValue;
    }

    public boolean isInt(String key) {
        return config.isInt(key);
    }

    public long getLong(String key) {
        return this.getLong(key, 0);
    }

    public long getLong(String key, long defaultValue) {
        return this.correct ? this.config.getLong(key, defaultValue) : defaultValue;
    }

    public boolean isLong(String key) {
        return config.isLong(key);
    }

    public double getDouble(String key) {
        return this.getDouble(key, 0);
    }

    public double getDouble(String key, double defaultValue) {
        return this.correct ? this.config.getDouble(key, defaultValue) : defaultValue;
    }

    public boolean isDouble(String key) {
        return config.isDouble(key);
    }

    public String getString(String key) {
        return this.getString(key, "");
    }

    public String getString(String key, String defaultValue) {
        return this.correct ? this.config.getString(key, defaultValue) : defaultValue;
    }

    public boolean isString(String key) {
        return config.isString(key);
    }

    public boolean getBoolean(String key) {
        return this.getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return this.correct ? this.config.getBoolean(key, defaultValue) : defaultValue;
    }

    public boolean isBoolean(String key) {
        return config.isBoolean(key);
    }

    public List getList(String key) {
        return this.getList(key, null);
    }

    public List getList(String key, List defaultList) {
        return this.correct ? this.config.getList(key, defaultList) : defaultList;
    }

    public boolean isList(String key) {
        return config.isList(key);
    }

    public List<String> getStringList(String key) {
        return config.getStringList(key);
    }

    public List<Integer> getIntegerList(String key) {
        return config.getIntegerList(key);
    }

    public List<Boolean> getBooleanList(String key) {
        return config.getBooleanList(key);
    }

    public List<Double> getDoubleList(String key) {
        return config.getDoubleList(key);
    }

    public List<Float> getFloatList(String key) {
        return config.getFloatList(key);
    }

    public List<Long> getLongList(String key) {
        return config.getLongList(key);
    }

    public List<Byte> getByteList(String key) {
        return config.getByteList(key);
    }

    public List<Character> getCharacterList(String key) {
        return config.getCharacterList(key);
    }

    public List<Short> getShortList(String key) {
        return config.getShortList(key);
    }

    public List<Map> getMapList(String key) {
        return config.getMapList(key);
    }

    public void setAll(LinkedHashMap<String, Object> map) {
        this.config = new ConfigSection(map);
    }

    public void setAll(ConfigSection section) {
        this.config = section;
    }

    public boolean exists(String key) {
        return config.exists(key);
    }

    public boolean exists(String key, boolean ignoreCase) {
        return config.exists(key, ignoreCase);
    }

    public void remove(String key) {
        config.remove(key);
    }

    public Map<String, Object> getAll() {
        return this.config.getAllMap();
    }

    /**
     * Get root (main) config section of the Config
     *
     * @return root config section of the Config
     */
    public ConfigSection getRootSection() {
        return config;
    }

    public int setDefault(LinkedHashMap<String, Object> map) {
        return setDefault(new ConfigSection(map));
    }

    public int setDefault(ConfigSection map) {
        int size = this.config.size();
        this.config = this.fillDefaults(map, this.config);
        return this.config.size() - size;
    }


    private ConfigSection fillDefaults(ConfigSection defaultMap, ConfigSection data) {
        for (String key : defaultMap.keySet()) {
            if (!data.containsKey(key)) {
                data.put(key, defaultMap.get(key));
            }
        }
        return data;
    }

    private void parseList(String content) {
        content = content.replace("\r\n", "\n");
        for (String v : content.split("\n")) {
            if (v.trim().isEmpty()) {
                continue;
            }
            config.put(v, true);
        }
    }

    private String writeProperties() {
        StringBuilder content = new StringBuilder("#Properties Config File\r\n");
        for (Object o : this.config.entrySet()) {
            Map.Entry entry = (Map.Entry) o;
            Object v = entry.getValue();
            Object k = entry.getKey();
            if (v instanceof Boolean) {
                v = (Boolean) v ? "on" : "off";
            }
            content.append(k).append('=').append(v).append("\r\n");
        }
        return content.toString();
    }

    private void parseProperties(String content) {
        for (final String line : content.split("\n")) {
            if (Pattern.compile("[a-zA-Z0-9\\-_.]*+=+[^\\r\\n]*").matcher(line).matches()) {
                final int splitIndex = line.indexOf('=');
                if (splitIndex == -1) {
                    continue;
                }
                final String key = line.substring(0, splitIndex);
                final String value = line.substring(splitIndex + 1);
                if (this.config.containsKey(key)) {
                    MainLogger.getLogger().debug("[Config] Repeated property " + key + " in file " + this.file.toString());
                }
                switch (value.toLowerCase(Locale.ROOT)) {
                    case "on":
                    case "true":
                    case "yes":
                        this.config.put(key, true);
                        break;
                    case "off":
                    case "false":
                    case "no":
                        this.config.put(key, false);
                        break;
                    default:
                        this.config.put(key, value);
                        break;
                }
            }
        }
    }

    private void parseToml(String content) {
        try {
            TomlParseResult result = Toml.parse(content);
            if (result.hasErrors()) {
                for (var error : result.errors()) {
                    MainLogger.getLogger().error("[Config] TOML parse error: " + error.toString());
                }
                this.correct = false;
                return;
            }
            this.config = new ConfigSection(tomlTableToMap(result));
        } catch (Exception e) {
            MainLogger.getLogger().error("[Config] Failed to parse TOML", e);
            this.correct = false;
        }
    }

    /**
     * Unified conversion method for TOML values
     */
    private Object convertTomlValue(Object value) {
        if (value == null || value instanceof String ||
            value instanceof Number || value instanceof Boolean) {
            return value;
        }

        if (value instanceof TomlTable) {
            return tomlTableToMap((TomlTable) value);
        }
        if (value instanceof TomlArray) {
            return convertTomlArray((TomlArray) value);
        }
        if (value instanceof List) {
            return convertTomlList((List<?>) value);
        }

        if (value instanceof LocalDate || value instanceof LocalDateTime ||
            value instanceof LocalTime || value instanceof OffsetDateTime) {
            return value.toString();
        }

        return value;
    }

    private LinkedHashMap<String, Object> tomlTableToMap(TomlTable table) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>(table.size());
        for (String key : table.keySet()) {
            map.put(key, convertTomlValue(table.get(key)));
        }
        return map;
    }

    private List<Object> convertTomlArray(TomlArray array) {
        List<Object> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            result.add(convertTomlValue(array.get(i)));
        }
        return result;
    }

    private List<Object> convertTomlList(List<?> list) {
        List<Object> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(convertTomlValue(item));
        }
        return result;
    }

    private String writeToml() {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("#TOML Config File\r\n");
        writeTomlSection(sb, "", this.config.getAllMap());
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void writeTomlSection(StringBuilder sb, String prefix, Map<String, Object> map) {
        // First pass: write simple key-value pairs
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof Map)) {
                writeTomlValue(sb, entry.getKey(), value);
            }
        }

        // Second pass: write tables/sections
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof ConfigSection) {
                value = ((ConfigSection) value).getAllMap();
            }

            if (value instanceof Map) {
                String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
                sb.append("\r\n[").append(fullKey).append("]\r\n");
                writeTomlSection(sb, fullKey, (Map<String, Object>) value);
            }
        }
    }

    /**
     * Escape string for TOML format
     */
    private String escapeTomlString(String str) {
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void writeTomlValue(StringBuilder sb, String key, Object value) {
        sb.append(key).append(" = ");

        if (value == null) {
            sb.append("\"\"");
        } else if (value instanceof String) {
            sb.append('"').append(escapeTomlString((String) value)).append('"');
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else if (value instanceof List) {
            writeTomlArray(sb, (List<?>) value);
        } else {
            sb.append('"').append(escapeTomlString(value.toString())).append('"');
        }

        sb.append("\r\n");
    }

    /**
     * Write TOML array format
     */
    private void writeTomlArray(StringBuilder sb, List<?> list) {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Object item = list.get(i);
            if (item instanceof String) {
                sb.append('"').append(escapeTomlString((String) item)).append('"');
            } else if (item instanceof Map) {
                sb.append('{');
                Map<?, ?> map = (Map<?, ?>) item;
                int j = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (j > 0) sb.append(", ");
                    sb.append(entry.getKey()).append(" = ");
                    Object val = entry.getValue();
                    if (val instanceof String) {
                        sb.append('"').append(escapeTomlString((String) val)).append('"');
                    } else {
                        sb.append(val);
                    }
                    j++;
                }
                sb.append('}');
            } else {
                sb.append(item);
            }
        }
        sb.append(']');
    }

    public Object getNested(String key) {
        return get(key);
    }

    public <T> T getNested(String key, T defaultValue) {
        return get(key, defaultValue);
    }

    @SuppressWarnings("unchecked")
    public <T> T getNestedAs(String key, Class<T> type) {
        return (T) get(key);
    }

    public void removeNested(String key) {
        remove(key);
    }

    @SuppressWarnings("unchecked")
    private void parseContent(String content) {
        switch (this.type) {
            case Config.PROPERTIES:
                this.parseProperties(content);
                break;
            case Config.JSON:
                GsonBuilder builder = new GsonBuilder();
                Gson gson = builder.create();
                this.config = new ConfigSection(gson.fromJson(content, new LinkedHashMapTypeToken()));
                break;
            case Config.YAML:
                LoadSettings settings = LoadSettings.builder()
                        .setParseComments(false)
                        .build();
                Load yaml = new Load(settings);
                this.config = new ConfigSection((LinkedHashMap<String, Object>) yaml.loadFromString(content));
                break;
            case Config.TOML:
                this.parseToml(content);
                break;
            case Config.ENUM:
                this.parseList(content);
                break;
            default:
                this.correct = false;
        }
    }

    public Set<String> getKeys() {
        if (this.correct) return config.getKeys();
        return new HashSet<>();
    }

    public Set<String> getKeys(boolean child) {
        if (this.correct) return config.getKeys(child);
        return new HashSet<>();
    }

    private static class LinkedHashMapTypeToken extends TypeToken<LinkedHashMap<String, Object>> {
    }
}
