package cn.nukkit.level;

import cn.nukkit.Server;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.config.category.WorldEntry;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Default dimensions and their Levels
 */
public enum EnumLevel {

    OVERWORLD,
    NETHER,
    THE_END;

    Level level;

    /**
     * Get Level
     *
     * @return Level or null if the dimension is not enabled
     */
    public Level getLevel() {
        return level;
    }

    /**
     * Internal: Initialize default overworld, nether and the end Levels
     */
    public static void initLevels() {
        Server server = Server.getInstance();
        OVERWORLD.level = server.getDefaultLevel();
        if (server.netherEnabled) {
            if (server.getLevelByName("nether") == null) {
                server.generateLevel("nether", System.currentTimeMillis(), Generator.getGenerator(Generator.TYPE_NETHER));
                server.loadLevel("nether");
            }
            NETHER.level = server.getLevelByName("nether");
            String list = server.getServerConfig().worldSettings().multiNetherWorlds();
            if (!list.trim().isEmpty()) {
                Map<String, WorldEntry> worlds = server.getServerConfig().worldSettings().worlds();
                StringTokenizer tokenizer = new StringTokenizer(list, ", ");
                while (tokenizer.hasMoreTokens()) {
                    String world = tokenizer.nextToken();
                    Server.multiNetherWorlds.add(world);
                    String nether = world + "-nether";
                    if (server.getLevelByName(nether) == null) {
                        WorldEntry entry = worlds != null ? worlds.get(nether) : null;
                        if (entry != null) {
                            long seed = entry.seed() != 0 ? entry.seed() : System.currentTimeMillis();
                            Class<? extends Generator> gen = Generator.getGenerator(entry.generator());
                            Map<String, Object> options = new HashMap<>();
                            String settings = entry.generatorSettings();
                            if (settings != null && !settings.isEmpty()) {
                                options.put("preset", settings);
                            }
                            server.generateLevel(nether, seed, gen, options);
                        } else {
                            server.generateLevel(nether, System.currentTimeMillis(), Generator.getGenerator(Generator.TYPE_NETHER));
                        }
                        server.loadLevel(nether);
                    }
                }
            }
        }
        if (server.endEnabled) {
            if (server.getLevelByName("the_end") == null) {
                server.generateLevel("the_end", System.currentTimeMillis(), Generator.getGenerator(Generator.TYPE_THE_END));
                server.loadLevel("the_end");
                server.getLevelByName("the_end").setSpawnLocation(new Vector3(100.5, 49, 0.5));
            }
            THE_END.level = server.getLevelByName("the_end");
        }
    }
}
