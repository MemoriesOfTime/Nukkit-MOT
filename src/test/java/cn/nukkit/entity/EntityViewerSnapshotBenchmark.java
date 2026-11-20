package cn.nukkit.entity;

import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.util.Locale;

/**
 * {@code despawnFromAll()} 与 {@code checkChunks()} 的分配量基准，<b>不是单元测试</b>。
 * <p>
 * 类名不以 {@code Test} 结尾，surefire 的默认包含规则不会拾取它；需要时用
 * {@code mvn -Pperf test-compile exec:exec@viewer-benchmark} 或直接
 * {@code java -cp ... cn.nukkit.entity.EntityViewerSnapshotBenchmark} 运行。
 * <p>
 * 之所以从测试套件里移出：断言"每次调用多分配不超过 16 字节"依赖于 C2 是否内联、是否做标量替换，
 * 会随 JDK 版本和无关注释级改动漂移；把它当作 CI 门禁会让正确的小重构随机变红。分配量的趋势应由
 * 人工评审基准输出，而不是 blocking 断言。
 * <p>
 * Allocation benchmark for viewer snapshot traversal. Deliberately not named {@code *Test} so the
 * default surefire run never picks it up; allocation thresholds belong in review, not in CI gates.
 */
public final class EntityViewerSnapshotBenchmark {

    private static volatile long blackhole;

    private EntityViewerSnapshotBenchmark() {
    }

    public static void main(String[] args) {
        int viewers = args.length > 0 ? Integer.parseInt(args[0]) : 8;
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;
        int windows = args.length > 2 ? Integer.parseInt(args[2]) : 5;

        if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) {
            System.out.println("SKIP: this VM has no per-thread allocation counter");
            return;
        }
        bean.setThreadAllocatedMemoryEnabled(true);

        System.out.printf(Locale.ROOT, "metadata,java=%s,vm=%s,viewers=%d,iterations=%d%n",
                System.getProperty("java.version"), System.getProperty("java.vm.name"), viewers, iterations);

        EntityViewerSyncTest.Fixture fixture = new EntityViewerSyncTest.Fixture(viewers);
        fixture.recording = false;   // 只测量分配，不保留事件记录
        fixture.keepRegistered = true; // despawnFrom 在生产会移除注册；基准需保持满员以重复测量

        // 控制组：手工遍历快照并逐个脱敏，代表"不建第二个容器"的成本下界。
        run(fixture, iterations, false);
        run(fixture, iterations, true);

        for (int window = 0; window < windows; window++) {
            long despawnBytes = measure(bean, fixture, iterations, Mode.DESPAWN);
            long controlBytes = measure(bean, fixture, iterations, Mode.CONTROL);
            long crossingBytes = measure(bean, fixture, iterations, Mode.CROSSING);
            System.out.printf(Locale.ROOT,
                    "window,%d,despawn=%.3f,control=%.3f,crossing=%.3f,excess_per_call=%.3f%n",
                    window,
                    despawnBytes / (double) iterations,
                    controlBytes / (double) iterations,
                    crossingBytes / (double) iterations,
                    (despawnBytes - controlBytes) / (double) iterations);
        }

        System.out.println("blackhole=" + blackhole);
    }

    private enum Mode { DESPAWN, CONTROL, CROSSING }

    private static void run(EntityViewerSyncTest.Fixture fixture, int iterations, boolean crossing) {
        for (int i = 0; i < iterations; i++) {
            if (crossing) {
                fixture.actor.x = 1;
                fixture.actor.chunk = fixture.chunks[0];
                fixture.chunks[0].registered = fixture.actor;
                fixture.actor.x = 17;
                fixture.actor.crossChunks();
            } else {
                fixture.actor.despawnFromAll();
            }
        }
        blackhole = fixture.actor.despawns + fixture.recorded.size();
    }

    private static long measure(ThreadMXBean bean, EntityViewerSyncTest.Fixture fixture,
                               int iterations, Mode mode) {
        long thread = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(thread);
        switch (mode) {
            case DESPAWN -> run(fixture, iterations, false);
            case CROSSING -> run(fixture, iterations, true);
            case CONTROL -> {
                for (int i = 0; i < iterations; i++) {
                    for (Object viewer : fixture.actor.hasSpawned.values().toArray()) {
                        fixture.actor.despawnFrom((cn.nukkit.Player) viewer);
                    }
                }
                blackhole = fixture.actor.despawns + fixture.recorded.size();
            }
        }
        return bean.getThreadAllocatedBytes(thread) - before;
    }
}
