package cn.nukkit.network.encryption;

import java.util.Map;

public class JsonUtils {
    @SuppressWarnings("unchecked")
    public static  <T> T childAsType(Map<?, ?> data, String key, Class<T> asType) {
        Object value = data.get(key);
        if (!(asType.isInstance(value))) {
            throw new IllegalStateException(key + " node is missing");
        }
        return (T) value;
    }

    /**
     * Optional variant of {@link #childAsType}: returns {@code defaultValue} when the node is
     * missing or has an unexpected type, instead of throwing.
     * <p>
     * Used for legacy clients (e.g. v1.1.0) and offline clients whose JWT chain omits fields
     * that are only present for Xbox-authenticated sessions (such as {@code XUID}).
     */
    @SuppressWarnings("unchecked")
    public static <T> T childAsTypeOrDefault(Map<?, ?> data, String key, Class<T> asType, T defaultValue) {
        Object value = data.get(key);
        if (asType.isInstance(value)) {
            return (T) value;
        }
        return defaultValue;
    }
}
