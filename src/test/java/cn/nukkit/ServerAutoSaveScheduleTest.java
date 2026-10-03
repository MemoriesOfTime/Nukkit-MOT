package cn.nukkit;

import cn.nukkit.level.AutoSaveQueue;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.LevelProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The tick loop starts an autosave and continues it over the next ticks instead of saving everything
 * in the tick the autosave interval ends.
 */
class ServerAutoSaveScheduleTest {

    private final Server server = mock(Server.class);
    private final AtomicLong nanos = new AtomicLong();
    private final Map<InetSocketAddress, Player> players = new LinkedHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        set("autoSave", true);
        set("autoSaveTickBudgetNanos", Long.MAX_VALUE / 4);
        set("autoSaveQueue", new AutoSaveQueue());
        set("players", this.players);
        when(this.server.getAutoSave()).thenReturn(true);
        doCallRealMethod().when(this.server).startAutoSave();
        doCallRealMethod().when(this.server).tickAutoSave(any());
    }

    @AfterEach
    void tearDown() {
        Server.nonAutoSaveWorlds.remove("skipped");
    }

    private void set(String name, Object value) throws Exception {
        Field field = Server.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(this.server, value);
    }

    private Player player(int port, boolean online, boolean connected) {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(online);
        when(player.isConnected()).thenReturn(connected);
        this.players.put(new InetSocketAddress(port), player);
        return player;
    }

    private static Level level(String name) {
        Level level = mock(Level.class);
        when(level.getName()).thenReturn(name);
        when(level.getProvider()).thenReturn(mock(LevelProvider.class));
        when(level.getAutoSave()).thenReturn(true);
        when(level.save()).thenReturn(true);
        return level;
    }

    @Test
    void theAutosaveIsSpreadOverTheFollowingTicks() throws Exception {
        set("autoSaveSavesPerTick", 1);
        Player first = this.player(1, true, true);
        Player second = this.player(2, true, true);
        Player gone = this.player(3, false, false);
        Level world = level("world");
        Level skipped = level("skipped");
        Server.nonAutoSaveWorlds.add("skipped");
        set("levelArray", new Level[]{world, skipped});

        this.server.startAutoSave();

        verify(this.server).removePlayer(gone);
        verify(first, never()).save(anyBoolean());
        verify(second, never()).save(anyBoolean());
        verify(world, never()).save();

        this.server.tickAutoSave(this.nanos::get);
        this.server.tickAutoSave(this.nanos::get);
        verify(world, never()).save();
        this.server.tickAutoSave(this.nanos::get);
        this.server.tickAutoSave(this.nanos::get);

        InOrder order = inOrder(first, second, world);
        order.verify(first).save(true);
        order.verify(second).save(true);
        order.verify(world).save();
        verify(skipped, never()).save();
        verify(this.server, never()).doAutoSave();
    }

    @Test
    void noBudgetMeansTheWholeAutosaveInOneTickAsBefore() throws Exception {
        set("autoSaveSavesPerTick", 0);
        set("levelArray", new Level[0]);

        this.server.startAutoSave();

        verify(this.server).doAutoSave();
    }

    @Test
    void switchedOffAutosaveStartsNothing() throws Exception {
        set("autoSave", false);
        set("autoSaveSavesPerTick", 1);
        Player player = this.player(1, true, true);
        set("levelArray", new Level[0]);

        this.server.startAutoSave();
        this.server.tickAutoSave(this.nanos::get);

        verify(player, never()).save(anyBoolean());
        verify(this.server, never()).doAutoSave();
    }
}
