package cn.nukkit.level.generator.task;

import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.level.ChunkLoader;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.scheduler.AsyncTask;

/**
 * Rebuild lighting on a snapshot, then merge only into the unchanged live chunk.
 *
 * @author MagicDroidX
 * Nukkit Project
 */
public class LightPopulationTask extends AsyncTask {

    public final int levelId;
    public BaseFullChunk chunk;

    private final BaseFullChunk original;
    private final long revision;
    private boolean ownsPendingSlot;
    private boolean computed;

    public LightPopulationTask(Level level, BaseFullChunk chunk) {
        if (!level.getServer().isPrimaryThread()) {
            throw new IllegalStateException("Light snapshots must be captured on the main thread");
        }
        this.levelId = level.getId();
        this.original = chunk;
        this.revision = chunk.getMutationRevision();
        this.ownsPendingSlot = !level.isChunkGenerationPending(chunk.getX(), chunk.getZ())
                && chunk.beginLightPopulation();
        if (ownsPendingSlot) {
            try {
                // Capture before dispatch; cloning a live chunk on a worker races block writes.
                this.chunk = chunk.clone();
            } catch (RuntimeException | Error failure) {
                chunk.endLightPopulation();
                ownsPendingSlot = false;
                throw failure;
            }
            if (this.chunk == null) {
                chunk.endLightPopulation();
                ownsPendingSlot = false;
            }
        }
    }

    public static void schedule(Level level, BaseFullChunk chunk) {
        if (!level.getServer().isPrimaryThread()) {
            level.getServer().getScheduler().scheduleTask(InternalPlugin.INSTANCE, () -> {
                if (level.getProvider() != null
                        && level.getChunkIfLoaded(chunk.getX(), chunk.getZ()) == chunk) {
                    schedule(level, chunk);
                }
            });
            return;
        }
        if (chunk.isLightPopulated()) {
            return;
        }
        if (level.isChunkGenerationPending(chunk.getX(), chunk.getZ())) {
            retryLater(level, chunk);
            return;
        }
        LightPopulationTask task = new LightPopulationTask(level, chunk);
        if (!task.ownsPendingSlot) {
            return;
        }
        try {
            level.getServer().getScheduler().scheduleAsyncTask(InternalPlugin.INSTANCE, task);
        } catch (RuntimeException | Error failure) {
            chunk.endLightPopulation();
            task.ownsPendingSlot = false;
            throw failure;
        }
    }

    private static void retryLater(Level level, BaseFullChunk chunk) {
        if (!chunk.beginLightPopulation()) {
            return;
        }
        try {
            level.getServer().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, () -> {
                chunk.endLightPopulation();
                Level current = level.getServer().getLevel(level.getId());
                if (current == level && current.getProvider() != null
                        && current.getChunkIfLoaded(chunk.getX(), chunk.getZ()) == chunk) {
                    schedule(current, chunk);
                }
            }, 1);
        } catch (RuntimeException | Error failure) {
            chunk.endLightPopulation();
            throw failure;
        }
    }

    @Override
    public void onRun() {
        if (!ownsPendingSlot) {
            return;
        }
        try {
            chunk.recalculateHeightMap();
            chunk.populateSkyLight();
            chunk.populateBlockLight();
            computed = true;
        } catch (RuntimeException failure) {
            // Completion releases the pending slot; failed work must never replace world data.
            Server.getInstance().getLogger().error("Could not populate chunk lighting", failure);
        } catch (Error failure) {
            // AsyncTask.run does not enqueue completion after an Error. Release here;
            // The atomic pending flag publishes worker-to-main visibility of the slot.
            ownsPendingSlot = false;
            original.endLightPopulation();
            throw failure;
        }
    }

    @Override
    public void onCompletion(Server server) {
        if (!ownsPendingSlot) {
            return;
        }
        original.endLightPopulation();
        ownsPendingSlot = false;
        Level level = server.getLevel(levelId);
        if (level == null || level.getProvider() == null
                || level.getChunkIfLoaded(original.getX(), original.getZ()) != original || !computed) {
            return;
        }
        if (level.isChunkGenerationPending(original.getX(), original.getZ())
                || original.getMutationRevision() != revision) {
            // Main-thread generation queues remain held until the worker completion callback.
            // A new population cannot start between this check and the main-thread merge.
            retryLater(level, original);
            return;
        }
        original.applyLightingFrom(chunk);
        for (ChunkLoader loader : level.getChunkLoaders(original.getX(), original.getZ())) {
            loader.onChunkChanged(original);
        }
    }
}
