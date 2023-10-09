package cn.nukkit.level.generator;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;

import java.util.HashMap;
import java.util.Map;

public class Void extends Generator {

    private ChunkManager level;

    public Void() {
        this(new HashMap<>());
    }

    public Void(Map<String, Object> options) {
    }

    @Override
    public int getId() {
        return TYPE_VOID;
    }

    @Override
    public ChunkManager getChunkManager() {
        return level;
    }

    @Override
    public void init(ChunkManager level, NukkitRandom random) {
        this.level = level;
    }

    @Override
    public void populateStructure(final int chunkX, final int chunkZ) {
    }

    @Override
    public void generateChunk(int chX, int chZ) {
    }

    @Override
    public void populateChunk(int i, int i1) {
    }

    @Override
    public Map<String, Object> getSettings() {
        return null;
    }

    @Override
    public String getName() {
        return "void";
    }

    @Override
    public Vector3 getSpawn() {
        return new Vector3(0.5, 64, 0.5);
    }
}
