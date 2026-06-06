package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.event.HandlerList;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NetEase client-to-server mod event carried by PyRpcPacket.
 */
@OnlyNetEase
public class PlayerNetEaseModEventC2SEvent extends PlayerEvent {

    private static final HandlerList handlers = new HandlerList();

    private final String modName;
    private final String systemName;
    private final String customEventName;
    private final Map<String, Object> eventData;

    public PlayerNetEaseModEventC2SEvent(Player player, String modName, String systemName,
                                         String customEventName, Map<String, Object> eventData) {
        this.player = player;
        this.modName = modName;
        this.systemName = systemName;
        this.customEventName = customEventName;
        this.eventData = eventData != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(eventData))
                : Collections.emptyMap();
    }

    public String getModName() {
        return modName;
    }

    public String getSystemName() {
        return systemName;
    }

    public String getCustomEventName() {
        return customEventName;
    }

    public Map<String, Object> getEventData() {
        return eventData;
    }

    public Map<String, Object> getArgs() {
        return eventData;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
