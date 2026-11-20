package cn.nukkit.level.generator.task;

import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.generator.SimpleChunkManager;
import cn.nukkit.scheduler.AsyncTask;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class PopulationTask extends AsyncTask {

    private final long seed;
    private final Level level;
    private final int centerX;
    private final int centerZ;
    private boolean state;
    private BaseFullChunk centerChunk;
    private boolean isPopulated;

    public final BaseFullChunk[] chunks = new BaseFullChunk[9];

    public PopulationTask(Level level, BaseFullChunk chunk) {
        this.state = true;
        this.level = level;
        this.centerChunk = chunk;
        this.centerX = chunk.getX();
        this.centerZ = chunk.getZ();
        this.seed = level.getSeed();

        chunks[4] = chunk;

        int i = 0;
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++, i++) {
                if (i == 4) continue;
                BaseFullChunk ck = level.getChunk(chunk.getX() + x, chunk.getZ() + z, true);
                this.chunks[i] = ck;
            }
        }
    }


    @Override
    public void onRun() {
        try {
            syncGen(0);
        } catch (Throwable failure) {
            this.state = false;
            Server server = Server.getInstance();
            if (server != null) {
                server.getLogger().error("Population task failed for chunk " + this.centerX + ", " + this.centerZ, failure);
            }
        }
    }

    private void syncGen(int i) {
        if (i == chunks.length) {
            generationTask();
        } else {
            BaseFullChunk chunk = chunks[i];
            if (chunk != null) {
                synchronized (chunk) {
                    syncGen(i + 1);
                }
            }
        }
    }

    private void generationTask() {
        this.state = false;
        Generator generator = level.getGenerator();
        if (generator == null) {
            return;
        }

        SimpleChunkManager manager = (SimpleChunkManager) generator.getChunkManager();

        if (manager == null) {
            this.state = false;
            return;
        }

        synchronized (manager) {
            try {
                manager.cleanChunks(this.seed);
                BaseFullChunk centerChunk = this.centerChunk;

                if (centerChunk == null) {
                    return;
                }

                int index = 0;
                for (int x = -1; x < 2; x++) {
                    for (int z = -1; z < 2; z++, index++) {
                        BaseFullChunk ck = this.chunks[index];
                        if (ck == centerChunk) continue;
                        if (ck == null) {
                            this.chunks[index] = level.getProvider().getEmptyChunk(centerChunk.getX() + x, centerChunk.getZ() + z);
                        } else {
                            this.chunks[index] = ck;
                        }
                    }
                }

                for (BaseFullChunk chunk : this.chunks) {
                    manager.setChunk(chunk.getX(), chunk.getZ(), chunk);
                    if (!chunk.isGenerated()) {
                        generator.generateChunk(chunk.getX(), chunk.getZ());
                        BaseFullChunk newChunk = manager.getChunk(chunk.getX(), chunk.getZ());
                        newChunk.setGenerated();
                        if (newChunk != chunk) manager.setChunk(chunk.getX(), chunk.getZ(), newChunk);
                    }
                }

                isPopulated = centerChunk.isPopulated();
                if (!isPopulated) {
                    generator.populateChunk(centerChunk.getX(), centerChunk.getZ());
                    centerChunk = manager.getChunk(centerChunk.getX(), centerChunk.getZ());
                    // Published before the light phase for onCompletion's retry
                    this.centerChunk = centerChunk;
                    centerChunk.setPopulated();
                    centerChunk.recalculateHeightMap();
                    centerChunk.populateSkyLight();
                    centerChunk.populateBlockLight();
                    centerChunk.setLightPopulated();
                }

                manager.setChunk(centerChunk.getX(), centerChunk.getZ());

                index = 0;
                for (int x = -1; x < 2; x++) {
                    for (int z = -1; z < 2; z++, index++) {
                        chunks[index] = null;
                        BaseFullChunk newChunk = manager.getChunk(centerChunk.getX() + x, centerChunk.getZ() + z);
                        if (newChunk != null) {
                            if (newChunk.hasChanged()) {
                                chunks[index] = newChunk;
                            }
                        }
                    }
                }
                this.state = true;
            } finally {
                manager.cleanChunks(this.seed);
            }
        }
    }

    @Override
    public void onCompletion(Server server) {
        if (level != null) {
            if (!this.state) {
                BaseFullChunk centerChunk = this.centerChunk;
                // Decorated but unlit: retry light only
                if (!this.isPopulated && centerChunk != null && centerChunk.isPopulated()
                        && !centerChunk.isLightPopulated()) {
                    level.scheduleChunkLightRetry(this.centerX, this.centerZ, centerChunk);
                    return;
                }
                level.abandonChunkPopulation(this.centerX, this.centerZ);
                return;
            }

            BaseFullChunk centerChunk = this.centerChunk;

            if (centerChunk == null) {
                return;
            }

            for (BaseFullChunk chunk : this.chunks) {
                if (chunk != null) {
                    level.generateChunkCallback(chunk.getX(), chunk.getZ(), chunk);
                }
            }

            level.generateChunkCallback(centerChunk.getX(), centerChunk.getZ(), centerChunk, isPopulated);
        }
    }
}
