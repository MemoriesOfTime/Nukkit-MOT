package cn.nukkit.level.generator.task;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.generator.SimpleChunkManager;
import cn.nukkit.level.generator.populator.overworld.PopulatorScatteredStructure;
import cn.nukkit.level.generator.structure.ScatteredStructurePiece;
import cn.nukkit.scheduler.AsyncTask;

public class CallbackableScatteredGenerationTask extends AsyncTask {
    private final Level world;
    private final PopulatorScatteredStructure structure;
    private final ScatteredStructurePiece piece;
    private final ChunkManager level;
    private final int startChunkX;
    private final int startChunkZ;
    public boolean state = true;
    private BaseFullChunk chunk;

    public CallbackableScatteredGenerationTask(final Level world, final BaseFullChunk chunk, final PopulatorScatteredStructure structure, final ScatteredStructurePiece piece, final ChunkManager level, final int startChunkX, final int startChunkZ) {
        this.chunk = chunk;
        this.world = world;
        this.structure = structure;
        this.piece = piece;
        this.level = level;
        this.startChunkX = startChunkX;
        this.startChunkZ = startChunkZ;
    }

    @Override
    public void onRun() {
        state = false;
        final Generator generator = world.getGenerator();
        if (generator != null) {
            final SimpleChunkManager manager = (SimpleChunkManager) generator.getChunkManager();
            if (manager != null) {
                manager.cleanChunks(world.getSeed());
                try {
                    BaseFullChunk chunk = this.chunk;
                    if (chunk != null) {
                        if (!chunk.isGenerated()) {
                            manager.setChunk(chunk.getX(), chunk.getZ(), chunk);
                            generator.generateChunk(chunk.getX(), chunk.getZ());
                            chunk = manager.getChunk(chunk.getX(), chunk.getZ());
                            chunk.setGenerated();
                        }
                        this.chunk = chunk;
                        state = true;
                    }
                } finally {
                    manager.cleanChunks(world.getSeed());
                }
            }
        }

        if (state && chunk != null) {
            structure.generateChunkCallback(level, startChunkX, startChunkZ, piece, chunk.getX(), chunk.getZ());
        }
    }
}
