package cn.nukkit.network.protocol.netease.pyrpc;

import cn.nukkit.network.protocol.netease.pyrpc.codec.ModEventPyRpcSubPacketCodec;
import cn.nukkit.network.protocol.netease.pyrpc.codec.StoreBuySuccessPyRpcSubPacketCodec;
import cn.nukkit.network.protocol.netease.pyrpc.io.PyRpcReader;
import cn.nukkit.network.protocol.netease.pyrpc.subpacket.ModEventPyRpcSubPacket;
import cn.nukkit.network.protocol.netease.pyrpc.subpacket.RawPyRpcSubPacket;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * NetEase PyRpc message envelope codec.
 */
public final class PyRpcProtocol {

    public static final PyRpcProtocol DEFAULT = createDefault();

    private static final int MAX_PAYLOAD_SIZE = 64 * 1024;
    private static final Object MISSING_VALUE = new Object();

    private final PyRpcCodecRegistry codecRegistry;

    public PyRpcProtocol(PyRpcCodecRegistry codecRegistry) {
        this.codecRegistry = codecRegistry;
    }

    private static PyRpcProtocol createDefault() {
        PyRpcCodecRegistry registry = new PyRpcCodecRegistry();
        registry.register(new ModEventPyRpcSubPacketCodec());
        registry.register(new StoreBuySuccessPyRpcSubPacketCodec());
        return new PyRpcProtocol(registry);
    }

    public <T extends PyRpcSubPacket> void register(PyRpcSubPacketCodec<T> codec) {
        this.codecRegistry.register(codec);
    }

    public PyRpcMessage decode(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_PAYLOAD_SIZE) {
            return null;
        }

        try {
            Object root = new PyRpcReader(data).readValue();
            List<?> values = root instanceof Map<?, ?> map ? asList(getMapValue(map, "value")) : asList(root);
            if (values == null || values.isEmpty()) {
                return null;
            }

            String method = asString(values.get(0));
            if (method == null) {
                return null;
            }
            List<?> arguments = values.size() > 1 ? asList(values.get(1)) : Collections.emptyList();
            if (arguments == null) {
                return null;
            }
            PyRpcMessage envelope = new PyRpcMessage(method, arguments, root, data, null);
            PyRpcSubPacket subPacket = this.codecRegistry.decode(envelope);
            return subPacket != null ? envelope.withSubPacket(subPacket) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public byte[] encode(PyRpcSubPacket subPacket) {
        return this.codecRegistry.encode(subPacket);
    }

    public List<?> argumentsOf(PyRpcSubPacket subPacket) {
        if (subPacket instanceof RawPyRpcSubPacket raw) {
            return raw.getArguments();
        }
        if (subPacket instanceof ModEventPyRpcSubPacket modEvent) {
            return List.of(
                    modEvent.getModName(),
                    modEvent.getSystemName(),
                    modEvent.getEventName(),
                    modEvent.getEventData());
        }
        return Collections.emptyList();
    }

    static List<?> asList(Object value) {
        Object unwrapped = unwrapValue(value);
        return unwrapped instanceof List<?> list ? list : null;
    }

    private static Object unwrapValue(Object value) {
        if (value instanceof Map<?, ?> map && map.containsKey("value")) {
            return map.get("value");
        } else if (value instanceof Map<?, ?> map) {
            Object wrapped = getMapValue(map, "value");
            if (wrapped != MISSING_VALUE) {
                return wrapped;
            }
        }
        return value;
    }

    private static Object getMapValue(Map<?, ?> map, String key) {
        if (map.containsKey(key)) {
            return map.get(key);
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (key.equals(asString(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return MISSING_VALUE;
    }

    public static String asString(Object value) {
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return null;
    }

    public static Map<String, Object> asStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = asString(entry.getKey());
            if (key != null) {
                result.put(key, normalizeValue(entry.getValue()));
            }
        }
        return result;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object element : list) {
                result.add(normalizeValue(element));
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = asString(entry.getKey());
                if (key != null) {
                    result.put(key, normalizeValue(entry.getValue()));
                }
            }
            return result;
        }
        return value;
    }
}
