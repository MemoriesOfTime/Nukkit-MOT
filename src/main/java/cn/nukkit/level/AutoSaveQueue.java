package cn.nukkit.level;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.level.LevelSaveEvent;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.function.LongSupplier;

/**
 * Autosave in portions spread over ticks.
 * <p>
 * {@link Server#doAutoSave()} saves every online player and snapshots every changed chunk of every level
 * on the main thread in one tick. Production JFR of 27.09.2026 put the minute tick that carried it at
 * 62.9 ms on average against 33.2 ms for the other minute ticks, most of it in Level.save.
 * <p>
 * A pass does the same saves in portions. Every tick it saves the queued players first, then the changed
 * chunks of one level after another, until the tick's budget is spent: a number of saves and a time
 * limit. At least one step is taken per tick, so a pass always ends. Every chunk still goes through the
 * provider's own saveChunk - the same write slot, sequence and acknowledgement as any other save - and a
 * level's level.dat is written after its last chunk, so the provider's writer still receives the chunks of
 * a level before its metadata. While the provider's write backlog is full the pass waits instead of
 * adding to it. A provider that cannot save chunk by chunk saves the whole level in one step, as before.
 * <p>
 * Explicit saves - /save-all, Level#save(true), closing a level, stopping the server - stay complete and
 * synchronous; a pass never stands in for them.
 */
@Log4j2
public final class AutoSaveQueue {

    private final ArrayDeque<Player> players = new ArrayDeque<>();
    private final ArrayDeque<Level> levels = new ArrayDeque<>();
    /** Level whose chunks are being saved, with the changed chunks it had when its turn came. */
    private Level current;
    private long[] chunks;
    private int cursor;

    public boolean isActive() {
        return !this.players.isEmpty() || this.current != null || !this.levels.isEmpty();
    }

    /**
     * Starts a pass over these players and levels. A pass that has not finished is replaced: its chunks
     * are still changed, and the new pass finds them again.
     *
     * @return true when an unfinished pass was replaced
     */
    public boolean begin(Collection<Player> players, Collection<Level> levels) {
        boolean unfinished = this.isActive();
        this.clear();
        this.players.addAll(players);
        this.levels.addAll(levels);
        return unfinished;
    }

    public void clear() {
        this.players.clear();
        this.levels.clear();
        this.finishLevel();
    }

    /**
     * Continues the pass within one tick's budget.
     *
     * @param maxSaves    saves allowed in this tick
     * @param budgetNanos main-thread time allowed in this tick
     * @return saves done in this tick
     */
    public int step(Server server, int maxSaves, long budgetNanos, LongSupplier nanoTime) {
        if (!server.getAutoSave()) {
            this.clear();
            return 0;
        }
        long deadline = nanoTime.getAsLong() + budgetNanos;
        int saves = 0;
        int steps = 0;
        while (this.isActive()) {
            if (steps > 0 && (saves >= maxSaves || nanoTime.getAsLong() - deadline >= 0)) {
                break;
            }
            steps++;

            Player player = this.players.poll();
            if (player != null) {
                if (savePlayer(player)) {
                    saves++;
                }
                continue;
            }

            if (server.holdWorldSave) {
                // Held for a backup: the levels are skipped, as a whole autosave is skipped during a hold.
                this.levels.clear();
                this.finishLevel();
                break;
            }

            if (this.current == null) {
                Level level = this.levels.poll();
                if (level != null && this.startLevel(server, level)) {
                    saves++;
                }
                continue;
            }

            Level level = this.current;
            if (this.cursor >= this.chunks.length) {
                this.saveMetadata(level);
                this.finishLevel();
                continue;
            }
            int result = this.saveChunk(level, this.chunks[this.cursor]);
            if (result == BACKLOGGED) {
                // The writer is behind: wait for it instead of growing its queue.
                break;
            }
            if (result == CLOSED) {
                // Closed or switched off meanwhile; a closing level saves itself.
                this.finishLevel();
                continue;
            }
            this.cursor++;
            if (result == SAVED) {
                saves++;
            }
        }
        return saves;
    }

    private static final int SKIPPED = 0;
    private static final int SAVED = 1;
    private static final int BACKLOGGED = 2;
    private static final int CLOSED = 3;

    private static boolean savePlayer(Player player) {
        if (!player.isOnline()) {
            return false;
        }
        try {
            player.save(true);
            return true;
        } catch (IllegalStateException closed) {
            // Closed between two checks; closing saved it already.
            return false;
        } catch (Exception e) {
            log.error("Failed to auto save player {}", player.getName(), e);
            return false;
        }
    }

    /**
     * @return true when the level was saved whole in this step
     */
    private boolean startLevel(Server server, Level level) {
        try {
            LevelProvider provider = level.getProvider();
            if (provider == null || !level.getAutoSave()) {
                return false;
            }
            if (!(provider instanceof LevelDBProvider)) {
                return level.save();
            }
            // Outside the provider lock, as in Level#save: a listener must not run under it.
            server.getPluginManager().callEvent(new LevelSaveEvent(level));
            level.providerLock.readLock().lock();
            try {
                if (!(level.getProvider() instanceof LevelDBProvider leveldb) || !level.getAutoSave()) {
                    return false;
                }
                this.chunks = leveldb.getChangedChunkHashes();
                this.cursor = 0;
                this.current = level;
                return false;
            } finally {
                level.providerLock.readLock().unlock();
            }
        } catch (Exception e) {
            log.error("Failed to start the auto save of level {}", level.getName(), e);
            this.finishLevel();
            return false;
        }
    }

    private int saveChunk(Level level, long hash) {
        level.providerLock.readLock().lock();
        try {
            if (!(level.getProvider() instanceof LevelDBProvider leveldb) || !level.getAutoSave()) {
                return CLOSED;
            }
            if (leveldb.isChunkSaveBacklogged()) {
                return BACKLOGGED;
            }
            return leveldb.saveChunkIfChanged(hash) ? SAVED : SKIPPED;
        } catch (Exception e) {
            log.error("Failed to auto save chunk {}, {} of level {}", Level.getHashX(hash), Level.getHashZ(hash), level.getName(), e);
            return SKIPPED;
        } finally {
            level.providerLock.readLock().unlock();
        }
    }

    private void saveMetadata(Level level) {
        level.providerLock.readLock().lock();
        try {
            if (level.getProvider() != null && level.getAutoSave()) {
                level.saveMetadata();
            }
        } catch (Exception e) {
            log.error("Failed to auto save the data of level {}", level.getName(), e);
        } finally {
            level.providerLock.readLock().unlock();
        }
    }

    private void finishLevel() {
        this.current = null;
        this.chunks = null;
        this.cursor = 0;
    }
}
