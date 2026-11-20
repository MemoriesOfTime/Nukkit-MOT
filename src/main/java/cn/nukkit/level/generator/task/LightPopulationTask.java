package cn.nukkit.level.generator.task;

import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.scheduler.AsyncTask;

/**
 * 在区块副本上重算高度图与光照并挂载回 level。
 * <p>
 * Recomputes height map and light on a chunk copy and mounts it back.
 *
 * @author MagicDroidX
 * Nukkit Project
 */
public class LightPopulationTask extends AsyncTask {

    private static final int MAX_ATTEMPTS = 3;
    private static final int RETRY_DELAY_TICKS = 20;
    private static final int LONG_RETRY_DELAY_TICKS = 200;

    public final int levelId;
    public BaseFullChunk chunk;
    private final BaseFullChunk sourceChunk;
    private long sourceVersion;
    private final int attempt;
    // null for ordinary relighting
    private final Object populationReceipt;
    private boolean state = true;

    public LightPopulationTask(Level level, BaseFullChunk chunk) {
        this(level, chunk, 1, null);
    }

    public LightPopulationTask(Level level, BaseFullChunk chunk, Object populationReceipt) {
        this(level, chunk, 1, populationReceipt);
    }

    private LightPopulationTask(Level level, BaseFullChunk chunk, int attempt, Object populationReceipt) {
        this.levelId = level.getId();
        this.chunk = chunk;
        this.sourceChunk = chunk;
        this.attempt = attempt;
        this.populationReceipt = populationReceipt;
    }

    @Override
    public void onRun() {
        BaseFullChunk source = this.sourceChunk;
        if (source == null) {
            this.state = false;
            return;
        }
        try {
            BaseFullChunk chunk;
            synchronized (source) {
                this.sourceVersion = source.getModificationVersion();
                chunk = source.clone();
            }
            if (chunk == null) {
                this.state = false;
                return;
            }

            chunk.recalculateHeightMap();
            chunk.populateSkyLight();
            chunk.populateBlockLight();
            chunk.setLightPopulated();

            this.chunk = chunk.clone();
        } catch (Throwable failure) {
            this.state = false;
            Server server = Server.getInstance();
            if (server != null) {
                server.getLogger().error("Light population failed for chunk " + source.getX() + ", " + source.getZ(), failure);
            }
        }
    }

    @Override
    public void onCompletion(Server server) {
        Level level = server.getLevel(this.levelId);
        if (level == null) {
            return;
        }

        BaseFullChunk source = this.sourceChunk;
        if (this.populationReceipt != null && source != null
                && !level.isChunkLightRetryCurrent(source.getX(), source.getZ(), this.populationReceipt)) {
            return;
        }
        if (!this.state) {
            this.retry(level, server, source);
            return;
        }
        if (source != null && (level.getChunkIfLoaded(source.getX(), source.getZ()) != source
                || source.getModificationVersion() != this.sourceVersion)) {
            // Discard a stale copy
            this.retry(level, server, source);
            return;
        }

        BaseFullChunk chunk = this.chunk == null ? null : this.chunk.clone();
        if (chunk == null) {
            if (source != null) {
                level.pauseChunkLightRetry(source.getX(), source.getZ(), this.populationReceipt);
            }
            return;
        }

        if (this.populationReceipt == null) {
            level.generateChunkCallback(chunk.getX(), chunk.getZ(), chunk, true);
        } else if (!level.completeChunkLightRetry(chunk.getX(), chunk.getZ(), chunk, this.populationReceipt)) {
            this.retry(level, server, source);
        }
    }

    private void retry(Level level, Server server, BaseFullChunk source) {
        if (source == null) {
            return;
        }
        int x = source.getX();
        int z = source.getZ();
        if (this.attempt >= MAX_ATTEMPTS) {
            if (this.populationReceipt == null) {
                server.getLogger().error("Light population gave up for chunk " + x + ", " + z);
                return;
            }
            level.releaseChunkPopulationLocks(x, z);
            server.getLogger().error("Light population is still failing for chunk " + x + ", " + z + "; retrying later");
            this.scheduleRetry(level, server, x, z, LONG_RETRY_DELAY_TICKS, true);
            return;
        }
        this.scheduleRetry(level, server, x, z, RETRY_DELAY_TICKS, false);
    }

    private void scheduleRetry(Level level, Server server, int x, int z, int delay, boolean restart) {
        try {
            server.getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, () -> {
                try {
                    this.retryLoadedChunk(level, server, x, z, delay, restart);
                } catch (RuntimeException | Error failure) {
                    this.pauseRejectedRetry(level, server, x, z, failure);
                }
            }, delay);
        } catch (RuntimeException | Error failure) {
            this.pauseRejectedRetry(level, server, x, z, failure);
        }
    }

    private void retryLoadedChunk(Level level, Server server, int x, int z, int delay, boolean restart) {
        if (this.populationReceipt != null && !level.isChunkLightRetryCurrent(x, z, this.populationReceipt)) {
            return;
        }
        BaseFullChunk live = level.getChunkIfLoaded(x, z);
        if (live == null) {
            level.pauseChunkLightRetry(x, z, this.populationReceipt);
            return;
        }
        if (live.isLightPopulated() || !live.isPopulated()) {
            if (live.isLightPopulated()) {
                boolean completed = level.completeChunkLightRetry(x, z, live, this.populationReceipt);
                if (!completed && this.populationReceipt != null) {
                    this.scheduleRetry(level, server, x, z, delay, restart);
                }
            } else {
                level.pauseChunkLightRetry(x, z, this.populationReceipt);
            }
            return;
        }
        if (restart) {
            level.pauseChunkLightRetry(x, z, this.populationReceipt);
            level.scheduleChunkLightRetry(x, z, live);
        } else {
            server.getScheduler().scheduleAsyncTask(InternalPlugin.INSTANCE,
                    new LightPopulationTask(level, live, this.attempt + 1, this.populationReceipt));
        }
    }

    private void pauseRejectedRetry(Level level, Server server, int x, int z, Throwable failure) {
        level.pauseChunkLightRetry(x, z, this.populationReceipt);
        server.getLogger().error("Failed to schedule light retry for chunk " + x + ", " + z, failure);
    }
}
