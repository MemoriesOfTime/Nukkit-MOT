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
}
