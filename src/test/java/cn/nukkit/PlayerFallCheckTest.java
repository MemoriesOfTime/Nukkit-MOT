package cn.nukkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 空中速度校验核心公式的回归测试。
 * <p>
 * 验证 {@code Player#handleMovement} 中下坠/飞行检测的 diff 计算与触发条件：
 * <ul>
 *     <li>正常下落不触发校正</li>
 *     <li>悬停/缓降触发校正</li>
 *     <li>非法上升触发校正</li>
 * </ul>
 * <p>
 * 公式说明：
 * <pre>
 * speed.y = from.y - to.y          // 正=下落，负=上升
 * expectedVelocity = (-g/d) * (1 - exp(-d * t))  // 物理约定：负=下落
 * diff = |speed.y + expectedVelocity|             // 符号相反时相消≈0
 * 触发条件：diff > 1 && expectedVelocity < 0 && speed.y != 0
 * </pre>
 */
class PlayerFallCheckTest {

    private static final float GRAVITY = 0.08f;
    private static final float DRAG = 0.02f;
    private static final double DIFF_THRESHOLD = 1.0;

    /**
     * 计算期望下落速度（物理约定：负=下落）
     */
    private static double expectedVelocity(int inAirTicks, int startAirTicks) {
        double t = inAirTicks - startAirTicks;
        return (-GRAVITY / (double) DRAG) - (-GRAVITY / (double) DRAG) * Math.exp(-(double) DRAG * t);
    }

    /**
     * 计算 diff（speed.y 与 expectedVelocity 的符号统一误差）
     */
    private static double calcDiff(double speedY, double expectedVelocity) {
        return Math.abs(speedY + expectedVelocity);
    }

    /**
     * 判断是否触发校正
     */
    private static boolean shouldTrigger(double speedY, int inAirTicks, int startAirTicks) {
        if (speedY == 0) return false;
        double ev = expectedVelocity(inAirTicks, startAirTicks);
        double diff = calcDiff(speedY, ev);
        return diff > DIFF_THRESHOLD && ev < 0;
    }

    // ========== 正常下落：不触发 ==========

    @ParameterizedTest(name = "正常下落 inAirTicks={0}")
    @CsvSource({"21, 5", "30, 5", "50, 5", "100, 5", "149, 5"})
    void normalFalling_doesNotTrigger(int inAirTicks, int startAirTicks) {
        double ev = expectedVelocity(inAirTicks, startAirTicks);
        // 正常下落：speed.y = -expectedVelocity（符号相反，大小相等）
        double speedY = -ev;

        assertFalse(shouldTrigger(speedY, inAirTicks, startAirTicks),
                "正常下落不应触发校正: inAirTicks=" + inAirTicks + ", speed.y=" + speedY + ", expected=" + ev);
    }

    @Test
    void normalFallingWithSmallVariance_doesNotTrigger() {
        int inAirTicks = 50;
        int startAirTicks = 5;
        double ev = expectedVelocity(inAirTicks, startAirTicks);
        // 模拟客户端与服务端的微小物理误差（±0.5 blocks/tick）
        double speedY = -ev + 0.5;

        assertFalse(shouldTrigger(speedY, inAirTicks, startAirTicks),
                "小幅偏差下落不应触发校正");
    }

    // ========== 悬停：触发 ==========

    @ParameterizedTest(name = "悬停 inAirTicks={0}")
    @CsvSource({"25, 5", "30, 5", "50, 5"})
    void hovering_triggers(int inAirTicks, int startAirTicks) {
        double speedY = 0.001; // 几乎静止（微量下落避免 speed.y==0 守卫）

        assertTrue(shouldTrigger(speedY, inAirTicks, startAirTicks),
                "悬停应触发校正: inAirTicks=" + inAirTicks);
    }

    // ========== 非法上升：触发 ==========

    @Test
    void flyingUp_triggers() {
        int inAirTicks = 30;
        int startAirTicks = 5;
        double ev = expectedVelocity(inAirTicks, startAirTicks);
        // 作弊：以期望下落速度的相同幅度上升
        double speedY = ev; // 负值=上升，与 expectedVelocity 同号，sum 翻倍

        double diff = calcDiff(speedY, ev);
        assertTrue(diff > DIFF_THRESHOLD,
                "非法上升 diff 应远大于阈值: diff=" + diff);
        assertTrue(shouldTrigger(speedY, inAirTicks, startAirTicks),
                "非法上升（speed.y=" + speedY + "）应触发校正");
    }

    @Test
    void slowFlyingUp_triggers() {
        int inAirTicks = 30;
        int startAirTicks = 5;
        // 缓慢上升 0.5 blocks/tick
        double speedY = -0.5;

        assertTrue(shouldTrigger(speedY, inAirTicks, startAirTicks),
                "缓慢上升应触发校正");
    }

    // ========== 方向区分：abs版本会漏判的场景 ==========

    @Test
    void absVersionWouldMiss_butSumVersionCatches() {
        int inAirTicks = 30;
        int startAirTicks = 5;
        double ev = expectedVelocity(inAirTicks, startAirTicks);
        // 作弊：上升速度恰好等于 abs(expectedVelocity)
        double speedY = ev; // 负值，与 expected 同号

        // abs 版本: |abs(ev) - abs(speedY)| = |abs(ev) - abs(ev)| = 0 → 漏判
        double absDiff = Math.abs(Math.abs(ev) - Math.abs(speedY));
        assertEquals(0, absDiff, 0.0001, "abs 版本 diff 为 0，会漏判");

        // sum 版本: |speedY + ev| = |2 * ev| → 正确触发
        double sumDiff = calcDiff(speedY, ev);
        assertTrue(sumDiff > DIFF_THRESHOLD, "sum 版本正确检测到非法上升: diff=" + sumDiff);
    }

    // ========== 边界条件 ==========

    @Test
    void speedYZero_doesNotTrigger() {
        // revert 后 speed 为零向量
        assertFalse(shouldTrigger(0, 30, 5),
                "speed.y=0 时不应触发校正（revert 保护）");
    }

    @Test
    void earlyAirTicks_expectedVelocityPositive_doesNotTrigger() {
        // 起跳阶段 startAirTicks 较大，使 expectedVelocity > 0
        int inAirTicks = 21;
        int startAirTicks = 30; // 模拟跳跃后 startAirTicks > inAirTicks
        double ev = expectedVelocity(inAirTicks, startAirTicks);

        assertTrue(ev > 0, "跳跃上升阶段 expectedVelocity 应为正");
        assertFalse(shouldTrigger(-1.0, inAirTicks, startAirTicks),
                "expectedVelocity > 0 时不应触发（玩家在跳跃上升阶段）");
    }
}
