package cn.nukkit.entity.mob;

import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/**
 * {@code EntityZombie.attackEntity} 的分配量基准，<b>不是单元测试</b>。
 * <p>
 * 类名不以 {@code Test} 结尾，surefire 默认规则不会拾取；需要时用
 * {@code mvn -Pperf test-compile exec:exec@attack-benchmark} 或
 * {@code java -XX:-DoEscapeAnalysis -cp <test-classpath> cn.nukkit.entity.mob.EntityZombieAttackBenchmark} 运行。
 * 关闭逃逸分析是为了让每次攻击构造的事件真正分配，否则标量替换会把事件分配优化掉。
 * <p>
 * 从测试套件移出的原因：原先以每次攻击 600/630 字节的绝对预算作为断言，该数值取决于 JIT 内联、
 * 逃逸分析、JDK 版本与 CPU 架构，会让无关改动随机变红。伤害行为本身由
 * {@link EntityZombieAttackBehaviorTest} 断言。
 * <p>
 * Allocation benchmark for zombie melee attacks. Not named {@code *Test} on purpose: byte budgets
 * depend on JIT decisions and belong in review output, not in a CI gate.
 */
public final class EntityZombieAttackBenchmark {

    private static final Item[] IRON_ARMOR = {
            new Item(Item.IRON_HELMET),
            new Item(Item.IRON_CHESTPLATE),
            new Item(Item.IRON_LEGGINGS),
            new Item(Item.IRON_BOOTS)};

    /** 保证每次攻击产生的事件逃逸，避免被标量替换。 */
    private static volatile Object blackhole;

    private EntityZombieAttackBenchmark() {
    }

    public static void main(String[] args) {
        int warmup = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;
        int rounds = args.length > 2 ? Integer.parseInt(args[2]) : 7;

        if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) {
            System.out.println("SKIP: this VM has no per-thread allocation counter");
            return;
        }
        bean.setThreadAllocatedMemoryEnabled(true);

        System.out.printf(Locale.ROOT, "metadata,java=%s,vm=%s,warmup=%d,iterations=%d,rounds=%d%n",
                System.getProperty("java.version"), System.getProperty("java.vm.name"),
                warmup, iterations, rounds);

        report("nonplayer", bean, warmup, iterations, rounds, false);
        report("player-iron-armor", bean, warmup, iterations, rounds, true);
    }

    private static void report(String scene, ThreadMXBean bean, int warmup, int iterations,
                               int rounds, boolean playerTarget) {
        double[] bytesPerAttack = new double[rounds];
        for (int round = 0; round < rounds; round++) {
            // 每个窗口都从冷启动开始热身，避免把上一次窗口的 JIT 状态算进来。
            runAttacks(warmup, playerTarget);
            long before = bean.getCurrentThreadAllocatedBytes();
            runAttacks(iterations, playerTarget);
            long window = bean.getCurrentThreadAllocatedBytes() - before;
            bytesPerAttack[round] = window / (double) iterations;
        }
        Arrays.sort(bytesPerAttack);
        System.out.printf(Locale.ROOT, "scene,%s,median_bytes=%.3f,min=%.3f,max=%.3f%n",
                scene, bytesPerAttack[rounds / 2], bytesPerAttack[0], bytesPerAttack[rounds - 1]);
    }

    private static void runAttacks(int count, boolean playerTarget) {
        EntityZombieAttackBehaviorTest.Attacker zombie = EntityZombieAttackBehaviorTest.attacker();
        Entity target;
        if (playerTarget) {
            EntityZombieAttackBehaviorTest.ArmoredPlayer player =
                    EntityZombieAttackBehaviorTest.armoredPlayer(IRON_ARMOR);
            target = player;
            for (int i = 0; i < count; i++) {
                zombie.cooldown(24);
                zombie.attackEntity(player);
            }
            blackhole = player.captured;
        } else {
            EntityZombieAttackBehaviorTest.NonPlayerTarget mob =
                    EntityZombieAttackBehaviorTest.target(0.5);
            target = mob;
            for (int i = 0; i < count; i++) {
                zombie.cooldown(24);
                zombie.attackEntity(mob);
            }
            blackhole = mob.captured;
        }
        blackhole = blackhole == null ? target : blackhole;
    }
}
