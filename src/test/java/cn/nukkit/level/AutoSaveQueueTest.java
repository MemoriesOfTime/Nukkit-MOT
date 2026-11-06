package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.level.LevelSaveEvent;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * An autosave used to save every player and snapshot every changed chunk of every level in one tick.
 * A pass now does the same saves in portions over several ticks, through the same per-chunk save.
 */
class AutoSaveQueueTest {

    private static final long NO_TIME_LIMIT = Long.MAX_VALUE / 4;

    @TempDir
    Path tempDir;

    private final List<String> order = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong();
    private Server server;
    private PluginManager plugins;
    private RecordingProvider provider;
    private Level level;

    /** Records the order of chunk saves; everything else is the real provider. */
    private final class RecordingProvider extends LevelDBProvider {
        RecordingProvider(Level level, String path) {
            super(level, path);
        }

        @Override
        public void saveChunk(int chunkX, int chunkZ, FullChunk chunk) {
            AutoSaveQueueTest.this.order.add("chunk " + chunkX + "," + chunkZ);
            super.saveChunk(chunkX, chunkZ, chunk);
        }
    }

    @BeforeAll
    static void setUpClass() {
        MockServer.init();
        Server.getInstance().levelDbCache = 8;
        Server.getInstance().useNativeLevelDB = false;
    }

    @BeforeEach
    void setUp() throws Exception {
        Server.getInstance().maxPendingChunkWrites = 128;
        this.server = Mockito.mock(Server.class);
        this.plugins = Mockito.mock(PluginManager.class);
        Mockito.lenient().when(this.server.getAutoSave()).thenReturn(true);
        Mockito.lenient().when(this.server.getPluginManager()).thenReturn(this.plugins);

        LevelDBProvider.generate(this.tempDir.toString(), "autosave-queue-test", 404L, Flat.class);
        this.level = Mockito.mock(Level.class);
        Mockito.lenient().when(this.level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
        Mockito.lenient().when(this.level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
        Mockito.lenient().when(this.level.isAutoCompaction()).thenReturn(false);
        Mockito.lenient().when(this.level.getCurrentTick()).thenReturn(0L);
        Mockito.lenient().when(this.level.getName()).thenReturn("autosave-queue-test");
        Mockito.lenient().when(this.level.getAutoSave()).thenReturn(true);
        Field lock = Level.class.getDeclaredField("providerLock");
        lock.setAccessible(true);
        lock.set(this.level, new ReentrantReadWriteLock());
        this.provider = new RecordingProvider(this.level, this.tempDir.toString());
        Mockito.lenient().when(this.level.getProvider()).thenReturn(this.provider);
        doAnswer(invocation -> this.order.add("level.dat")).when(this.level).saveMetadata();
    }

    @AfterEach
    void tearDown() {
        Server.getInstance().maxPendingChunkWrites = 128;
        if (this.provider != null) {
            this.provider.close();
            this.provider = null;
        }
    }

    private void dirtyChunks(int count) {
        for (int i = 0; i < count; i++) {
            LevelDBChunk chunk = this.provider.getEmptyChunk(i, 3);
            chunk.setGenerated(true);
            this.provider.setChunk(i, 3, chunk);
            chunk.setBlock(0, 64, 0, 1);
            assertTrue(chunk.hasChanged());
        }
    }

    private int chunkSaves() {
        return (int) this.order.stream().filter(entry -> entry.startsWith("chunk")).count();
    }

    private int step(AutoSaveQueue queue, int maxSaves) {
        return queue.step(this.server, maxSaves, NO_TIME_LIMIT, this.clock::get);
    }

    @Test
    void chunksAreSavedInPortionsAndLevelDataAfterTheLastOfThem() {
        this.dirtyChunks(10);
        AutoSaveQueue queue = new AutoSaveQueue();
        queue.begin(Collections.emptyList(), List.of(this.level));

        assertEquals(3, this.step(queue, 3));
        assertEquals(3, this.chunkSaves(), "one portion per tick, not the whole level");
        verify(this.plugins).callEvent(isA(LevelSaveEvent.class));
        verify(this.level, never()).saveMetadata();

        assertEquals(3, this.step(queue, 3));
        assertEquals(3, this.step(queue, 3));
        assertEquals(9, this.chunkSaves());
        assertTrue(queue.isActive());

        assertEquals(1, this.step(queue, 3));
        assertFalse(queue.isActive());
        assertEquals(10, this.chunkSaves(), "every changed chunk once");
        assertEquals("level.dat", this.order.get(this.order.size() - 1), "level.dat after the last chunk of the level");
        verify(this.level, times(1)).saveMetadata();
        verify(this.plugins, times(1)).callEvent(any());
    }

    @Test
    void aChunkThatWasSavedMeanwhileIsNotSavedAgain() {
        this.dirtyChunks(4);
        AutoSaveQueue queue = new AutoSaveQueue();
        queue.begin(Collections.emptyList(), List.of(this.level));
        assertEquals(1, this.step(queue, 1));

        // Unloading, an explicit save or a container save wrote the rest before the pass reached them.
        String first = this.order.get(0);
        for (int x = 0; x < 4; x++) {
            if (!first.equals("chunk " + x + ",3")) {
                this.provider.getLoadedChunk(x, 3).setChanged(false);
            }
        }
        while (queue.isActive()) {
            this.step(queue, 1);
        }

        assertEquals(1, this.chunkSaves());
        verify(this.level).saveMetadata();
    }

    @Test
    void aHeldWorldSaveSkipsTheLevelsButStillSavesPlayers() {
        this.dirtyChunks(5);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isOnline()).thenReturn(true);
        this.server.holdWorldSave = true;
        AutoSaveQueue queue = new AutoSaveQueue();
        queue.begin(List.of(player), List.of(this.level));

        this.step(queue, 32);

        verify(player).save(true);
        assertFalse(queue.isActive());
        assertEquals(0, this.chunkSaves());
        verify(this.level, never()).saveMetadata();
        verify(this.plugins, never()).callEvent(any());
    }

    @Test
    void aBackloggedWriterPausesThePassWithoutLosingItsPlace() {
        this.dirtyChunks(6);
        AutoSaveQueue queue = new AutoSaveQueue();
        queue.begin(Collections.emptyList(), List.of(this.level));
        assertEquals(2, this.step(queue, 2));

        Server.getInstance().maxPendingChunkWrites = 0;
        assertEquals(0, this.step(queue, 2), "no new writes while the writer is behind");
        assertEquals(0, this.step(queue, 2));
        assertTrue(queue.isActive());

        Server.getInstance().maxPendingChunkWrites = 128;
        while (queue.isActive()) {
            this.step(queue, 2);
        }
        assertEquals(6, this.chunkSaves(), "resumed where it stopped, each chunk once");
        verify(this.level).saveMetadata();
    }

    @Test
    void aLevelClosedDuringThePassIsLeftToItsOwnSave() {
        this.dirtyChunks(6);
        AutoSaveQueue queue = new AutoSaveQueue();
        queue.begin(Collections.emptyList(), List.of(this.level));
        assertEquals(2, this.step(queue, 2));

        Mockito.when(this.level.getProvider()).thenReturn(null);
        this.step(queue, 2);

        assertFalse(queue.isActive());
        assertEquals(2, this.chunkSaves());
        verify(this.level, never()).saveMetadata();
    }

    @Test
    void aProviderWithoutPortionedSavesIsSavedWholeInOneStep() {
        Level other = Mockito.mock(Level.class);
        Mockito.when(other.getProvider()).thenReturn(Mockito.mock(LevelProvider.class));
        Mockito.when(other.getAutoSave()).thenReturn(true);
        Mockito.when(other.save()).thenReturn(true);
        AutoSaveQueue queue = new AutoSaveQueue();
        queue.begin(Collections.emptyList(), List.of(other));

        assertEquals(1, this.step(queue, 32));

        verify(other).save();
        assertFalse(queue.isActive());
    }

    @Test
    void everyTickMakesProgressEvenWithoutTimeLeft() {
        this.dirtyChunks(3);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isOnline()).thenReturn(true);
        AutoSaveQueue queue = new AutoSaveQueue();
        queue.begin(List.of(player), List.of(this.level));

        int ticks = 0;
        while (queue.isActive()) {
            queue.step(this.server, 32, 0, this.clock::get);
            ticks++;
            assertTrue(ticks < 100, "a pass always ends");
        }

        // One step per tick: the player, the level's start, three chunks, level.dat.
        assertEquals(6, ticks);
        verify(player).save(true);
        assertEquals(3, this.chunkSaves());
    }

    @Test
    void theTimeBudgetEndsAPortion() {
        this.dirtyChunks(8);
        AutoSaveQueue queue = new AutoSaveQueue();
        queue.begin(Collections.emptyList(), List.of(this.level));
        // Every reading of the clock advances it by one microsecond; the budget allows about four readings.
        AtomicLong ticking = new AtomicLong();
        int saves = queue.step(this.server, 32, 4_000, () -> ticking.addAndGet(1_000));

        assertTrue(saves >= 1 && saves < 8, "saves " + saves);
        assertTrue(queue.isActive());
    }

    @Test
    void aNewPassTakesOverAnUnfinishedOne() {
        this.dirtyChunks(6);
        AutoSaveQueue queue = new AutoSaveQueue();
        assertFalse(queue.begin(Collections.emptyList(), List.of(this.level)));
        assertEquals(2, this.step(queue, 2));

        assertTrue(queue.begin(Collections.emptyList(), List.of(this.level)), "the unfinished pass is reported");
        while (queue.isActive()) {
            this.step(queue, 2);
        }

        // The new pass looked again: two chunks may still wait for their write, the other four are new.
        assertTrue(this.chunkSaves() >= 6 && this.chunkSaves() <= 8, "saves " + this.chunkSaves());
        verify(this.level, times(1)).saveMetadata();
    }

    @Test
    void autosaveSwitchedOffDropsThePass() {
        this.dirtyChunks(4);
        Player player = Mockito.mock(Player.class);
        AutoSaveQueue queue = new AutoSaveQueue();
        queue.begin(List.of(player), List.of(this.level));
        Mockito.when(this.server.getAutoSave()).thenReturn(false);

        assertEquals(0, this.step(queue, 32));

        assertFalse(queue.isActive());
        verify(player, never()).save(Mockito.anyBoolean());
        assertEquals(0, this.chunkSaves());
    }

    @Test
    void anOfflinePlayerIsNotSaved() {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isOnline()).thenReturn(false);
        AutoSaveQueue queue = new AutoSaveQueue();
        queue.begin(List.of(player), Collections.emptyList());

        assertEquals(0, this.step(queue, 32));

        verify(player, never()).save(Mockito.anyBoolean());
        assertFalse(queue.isActive());
    }
}
