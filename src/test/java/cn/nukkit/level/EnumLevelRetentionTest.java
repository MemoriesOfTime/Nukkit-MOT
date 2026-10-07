package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.utils.MainLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnumLevelRetentionTest {
    @BeforeAll
    static void initializeServer() { MockServer.init(); }

    private static void set(Level level, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }

    private static Level level() throws Exception {
        Level level = new ObjenesisStd().newInstance(Level.class);
        Server server = mock(Server.class);
        Map<Integer, Level> levels = new HashMap<>();
        levels.put(level.getId(), level);
        when(server.getLevels()).thenReturn(levels);
        when(server.getLogger()).thenReturn(mock(MainLogger.class));
        set(level, "server", server);
        set(level, "provider", mock(LevelProvider.class));
        set(level, "providerLock", new ReentrantReadWriteLock());
        // This close-only fixture must not depend on whether generator ownership is
        // implemented by a ThreadLocal or by a level-owned worker map.
        Field cache = Level.class.getDeclaredField("generators");
        set(level, "generators", ThreadLocal.class.isAssignableFrom(cache.getType())
                ? new ThreadLocal<Generator>() : new WeakHashMap<Thread, Generator>());
        return level;
    }

    @Test
    void closeClearsOnlyTheMatchingDefaultDimension() throws Exception {
        Level oldNether = EnumLevel.NETHER.level;
        Level oldEnd = EnumLevel.THE_END.level;
        try {
            Level closing = level();
            Level stillOpen = level();
            EnumLevel.NETHER.level = closing;
            EnumLevel.THE_END.level = stillOpen;
            closing.close();
            assertNull(EnumLevel.NETHER.getLevel(), "a closed level must not remain in the static dimension registry");
            assertSame(stillOpen, EnumLevel.THE_END.getLevel());
        } finally {
            EnumLevel.NETHER.level = oldNether;
            EnumLevel.THE_END.level = oldEnd;
        }
    }

    @Test
    void providerCloseFailureStillReleasesTheRegisteredLevel() throws Exception {
        Level oldNether = EnumLevel.NETHER.level;
        try {
            Level closing = level();
            EnumLevel.NETHER.level = closing;
            doThrow(new IllegalStateException("test provider close failure")).when(closing.getProvider()).close();
            assertDoesNotThrow(closing::close);
            assertNull(EnumLevel.NETHER.getLevel());
        } finally {
            EnumLevel.NETHER.level = oldNether;
        }
    }

    @Test
    void lateCloseOfPreviousInstanceDoesNotClearReplacement() throws Exception {
        Level oldNether = EnumLevel.NETHER.level;
        try {
            Level previous = level();
            Level replacement = level();
            EnumLevel.NETHER.level = replacement;
            previous.close();
            assertSame(replacement, EnumLevel.NETHER.getLevel());
        } finally {
            EnumLevel.NETHER.level = oldNether;
        }
    }
}
