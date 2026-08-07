package cn.nukkit.network.protocol.mapping;

import cn.nukkit.network.protocol.LevelSoundEventPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps {@link LevelSoundEventPacket} numeric sound IDs to the string sound names
 * used by modern Bedrock protocols.
 */
public final class LevelSoundEventMap {

    public static final String UNDEFINED_NAME = "undefined";

    private static final Map<String, Integer> NAME_TO_ID = buildNameToId();

    private LevelSoundEventMap() {
        throw new IllegalStateException();
    }

    public static String getName(int id) {
        return LevelSoundEventPacket.getSoundName(id);
    }

    public static int getId(String name) {
        if (name == null) {
            return LevelSoundEventPacket.SOUND_UNDEFINED;
        }
        return NAME_TO_ID.getOrDefault(name, LevelSoundEventPacket.SOUND_UNDEFINED);
    }

    public static boolean isKnownName(String name) {
        return name != null && NAME_TO_ID.containsKey(name);
    }

    public static Set<String> getNames() {
        return NAME_TO_ID.keySet();
    }

    public static Map<String, Integer> getNameToIdMap() {
        return NAME_TO_ID;
    }

    private static Map<String, Integer> buildNameToId() {
        Map<String, Integer> nameToId = new HashMap<>();
        for (int id = LevelSoundEventPacket.SOUND_ITEM_USE_ON; id < LevelSoundEventPacket.SOUND_UNDEFINED; id++) {
            String name = getName(id);
            if (!UNDEFINED_NAME.equals(name)) {
                Integer previous = nameToId.put(name, id);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate level sound name: " + name + " for " + previous + " and " + id);
                }
            }
        }
        return Map.copyOf(nameToId);
    }
}
