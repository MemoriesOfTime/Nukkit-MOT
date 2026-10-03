package cn.nukkit.network.protocol.netease.pyrpc.subpacket;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcSubPacket;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NetEase PyRpc mod event payload.
 */
@OnlyNetEase
public final class ModEventPyRpcSubPacket implements PyRpcSubPacket {

    public static final String CLIENT_TO_SERVER_METHOD = "ModEventC2S";
    public static final String SERVER_TO_CLIENT_METHOD = "ModEventS2C";

    private final String method;
    private final String modName;
    private final String systemName;
    private final String eventName;
    private final Map<String, Object> eventData;

    public ModEventPyRpcSubPacket(String method, String modName, String systemName,
                                  String eventName, Map<String, ?> eventData) {
        this.method = method;
        this.modName = modName;
        this.systemName = systemName;
        this.eventName = eventName;
        if (eventData != null) {
            Map<String, Object> copiedEventData = new LinkedHashMap<>();
            for (Map.Entry<String, ?> entry : eventData.entrySet()) {
                copiedEventData.put(entry.getKey(), entry.getValue());
            }
            this.eventData = Collections.unmodifiableMap(copiedEventData);
        } else {
            this.eventData = Collections.emptyMap();
        }
    }

    @Override
    public String getMethod() {
        return method;
    }

    public String getModName() {
        return modName;
    }

    public String getSystemName() {
        return systemName;
    }

    public String getEventName() {
        return eventName;
    }

    public Map<String, Object> getEventData() {
        return eventData;
    }
}
