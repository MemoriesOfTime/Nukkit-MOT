package cn.nukkit.level;

import cn.nukkit.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * 验证并行世界限流门槛与非并行路径（Server.checkTickUpdates 的 tickRate > baseTickRate）一致。
 * <p>
 * Verifies the parallel level's rate-limit gate matches the non-parallel path's
 * {@code tickRate > baseTickRate} threshold.
 */
class LevelTickRateGateTest {
    private final Level level = mock(Level.class);
    private final Server server = mock(Server.class);

    @BeforeEach
    void prepareGate() throws Exception {
        set("server", server);
        doCallRealMethod().when(level).isTickRateLimited();
    }

    @Test
    void defaultConfigMatchesLegacyGate() throws Exception {
        // base=1 时门槛等价旧实现的 tickRate > 1
        configure(1, 1, 0);
        assertFalse(level.isTickRateLimited());

        configure(1, 3, 3);
        assertTrue(level.isTickRateLimited());
        assertTrue(level.isTickRateLimited());
        assertFalse(level.isTickRateLimited());
    }

    @Test
    void baseTickRateAboveOneDisablesLimitingAtTheBaseRate() throws Exception {
        // 回归：base-tick-rate=2 且 tickRate=2 时并行世界不得限流（否则 10 TPS，非并行路径为 20 TPS）
        configure(2, 2, 2);
        assertFalse(level.isTickRateLimited());
        assertFalse(level.isTickRateLimited());
        assertFalse(level.isTickRateLimited());

        // 高于 base 才介入，跳过模式与旧实现一致
        configure(2, 3, 3);
        assertTrue(level.isTickRateLimited());
        assertTrue(level.isTickRateLimited());
        assertFalse(level.isTickRateLimited());
    }

    private void configure(int baseTickRate, int tickRate, int tickRateCounter) throws Exception {
        when(server.getBaseTickRate()).thenReturn(baseTickRate);
        set("tickRate", tickRate);
        set("tickRateCounter", tickRateCounter);
    }

    private void set(String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }
}
