package cn.nukkit.level;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 GameLoop 监控环形缓冲：跳过刻将旧样本冲出窗口、零值不计入均值。
 * <p>
 * Verifies the GameLoop metrics ring: skipped ticks age stale samples out of
 * the window and zero slots stay excluded from the averages.
 */
class GameLoopTest {

    @Test
    void freshLoopReportsZeroMetrics() {
        GameLoop loop = GameLoop.builder().build();
        assertEquals(0f, loop.getTPS());
        assertEquals(0f, loop.getMSPT());
        assertEquals(0f, loop.getTickUsage());
    }

    @Test
    void skippedTicksAgeStaleSamplesOutOfTheWindow() throws Exception {
        int loopsPerSec = 20;
        int realTicks = 3;
        int skippedTicks = loopsPerSec + 1; // 跳过整个窗口 + 1，确保旧样本全部被冲掉
        AtomicInteger iterations = new AtomicInteger();
        CountDownLatch realPhaseDone = new CountDownLatch(1);
        float[] tpsAfterRealPhase = new float[1];
        float[] msptAfterRealPhase = new float[1];

        GameLoop loop = GameLoop.builder()
                .loopCountPerSec(loopsPerSec)
                .onTick((gl, startNanos) -> {
                    int i = iterations.incrementAndGet();
                    if (i <= realTicks) {
                        if (i == realTicks) {
                            // 回调与环形写入同线程，读数确定
                            tpsAfterRealPhase[0] = gl.getTPS();
                            msptAfterRealPhase[0] = gl.getMSPT();
                            realPhaseDone.countDown();
                        }
                        return 1_000_000_000L / loopsPerSec; // 恰好满速，不产生补偿睡眠
                    }
                    if (i >= realTicks + skippedTicks) {
                        gl.stop();
                    }
                    return -1; // 模拟限流/转换期间的跳过刻
                })
                .build();

        Thread thread = new Thread(loop::startLoop, "GameLoopTest");
        // daemon + 宽裕 join：回调异常时测试 JVM 不悬挂，CI 负载尖峰不误报
        thread.setDaemon(true);
        thread.start();
        assertTrue(realPhaseDone.await(5, TimeUnit.SECONDS));
        thread.join(30_000);
        assertFalse(thread.isAlive());

        // 真实刻阶段：满速样本，均值恰为 loopCountPerSec / 理想帧时长
        assertEquals(20f, tpsAfterRealPhase[0], 0.001f);
        assertEquals(50f, msptAfterRealPhase[0], 0.001f);

        // 跳过刻覆盖整个窗口后旧样本被零值冲掉：无有效数据 → 0
        assertEquals(0f, loop.getTPS());
        assertEquals(0f, loop.getMSPT());
    }
}
