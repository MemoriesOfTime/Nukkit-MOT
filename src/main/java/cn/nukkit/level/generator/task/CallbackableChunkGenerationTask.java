package cn.nukkit.level.generator.task;

import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.generator.PopChunkManager;
import cn.nukkit.scheduler.AsyncTask;

import java.util.function.Consumer;

public class CallbackableChunkGenerationTask<T> extends AsyncTask {
    private final Level level;
    private final T structure;
    private final Consumer<T> callback;
    public boolean state = true;
    private BaseFullChunk chunk;

    public CallbackableChunkGenerationTask(final Level level, final BaseFullChunk chunk, final T structure, final Consumer<T> callback) {
        this.chunk = chunk;
        this.level = level;
        this.structure = structure;
        this.callback = callback;
    }

    @Override
    public void onRun() {
        state = false;

        final Generator generator = level.getGenerator();
        if (generator != null) {
            final PopChunkManager manager = (PopChunkManager) generator.getChunkManager();
            if (manager != null) {
                manager.cleanChunks(level.getSeed());
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
                    manager.cleanChunks(level.getSeed());
                }
            }
        }

        if (state && chunk != null) {
            callback.accept(structure);
        }
    }
}
