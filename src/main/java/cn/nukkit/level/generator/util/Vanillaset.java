package cn.nukkit.level.generator.util;

import cn.nukkit.level.generator.Generator;
import cn.nukkit.utils.Config;

import java.util.Collections;
import java.util.Map;

public abstract class Vanillaset extends Generator {

    public static final int TYPE_LARGE_BIOMES = 5;
    public static final int TYPE_AMPLIFIED = 6;

    public static int SEA_LEVEL;

    private static Config config;





    @Override
    public Map<String, Object> getSettings() {
        return Collections.emptyMap();
    }
}
