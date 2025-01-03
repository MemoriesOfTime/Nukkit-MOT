package cn.nukkit.level.generator;

import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.MainLogger;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class Generator implements BlockID {
    public static final int TYPE_OLD = 0;
    public static final int TYPE_INFINITE = 1;
    public static final int TYPE_FLAT = 2;
    public static final int TYPE_NETHER = 3;
    public static final int TYPE_THE_END = 4;
    public static final int TYPE_VOID = 5;

    public abstract int getId();

    public DimensionData getDimensionData() {
        DimensionData dimensionData = DimensionEnum.getDataFromId(this.getDimension());
        if (dimensionData == null) {
            dimensionData = DimensionEnum.OVERWORLD.getDimensionData();
        }
        return dimensionData;
    }

    @Deprecated
    public int getDimension() {
        return Level.DIMENSION_OVERWORLD;
    }

    private static final Map<String, Class<? extends Generator>> nameList = new HashMap<>();

    private static final Map<Integer, Class<? extends Generator>> typeList = new HashMap<>();

    public static boolean addGenerator(Class<? extends Generator> clazz, String name, int type) {
        name = name.toLowerCase();
        if (clazz != null && !Generator.nameList.containsKey(name)) {
            Generator.nameList.put(name, clazz);
            if (!Generator.typeList.containsKey(type)) {
                Generator.typeList.put(type, clazz);
            }
            return true;
        }
        return false;
    }

    public static String[] getGeneratorList() {
        String[] keys = new String[Generator.nameList.size()];
        return Generator.nameList.keySet().toArray(keys);
    }

    public static Class<? extends Generator> getGenerator(String name) {
        name = name.toLowerCase();
        if (Generator.nameList.containsKey(name)) {
            return Generator.nameList.get(name);
        }
        return OldNormal.class;
    }

    public static Class<? extends Generator> getGenerator(int type) {
        if (Generator.typeList.containsKey(type)) {
            return Generator.typeList.get(type);
        }
        return OldNormal.class;
    }

    public static String getGeneratorName(Class<? extends Generator> c) {
        for (Map.Entry<String, Class<? extends Generator>> entry : Generator.nameList.entrySet()) {
            if (entry.getValue() == c) {
                return entry.getKey();
            }
        }
        return "unknown";
    }

    public static int getGeneratorType(Class<? extends Generator> c) {
        for (Map.Entry<Integer, Class<? extends Generator>> entry : Generator.typeList.entrySet()) {
            if (entry.getValue() == c) {
                return entry.getKey();
            }
        }
        return Generator.TYPE_INFINITE;
    }

    public static CompoundTag loadNBT(final String path) {
        try (final InputStream inputStream = Server.class.getClassLoader().getResourceAsStream(path)) {
            return NBTIO.readCompressed(inputStream);
        } catch (final IOException e) {
            MainLogger.getLogger().error("Error while loading: " + path);
            throw new RuntimeException(e);
        }
    }

    public abstract void init(ChunkManager level, NukkitRandom random);

    public abstract void populateStructure(final int chunkX, final int chunkZ);

    public abstract void generateChunk(int chunkX, int chunkZ);

    public abstract void populateChunk(int chunkX, int chunkZ);

    public abstract Map<String, Object> getSettings();

    public abstract String getName();

    public abstract Vector3 getSpawn();

    public abstract ChunkManager getChunkManager();
}
