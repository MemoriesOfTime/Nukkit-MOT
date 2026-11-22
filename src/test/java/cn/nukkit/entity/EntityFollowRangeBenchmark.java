package cn.nukkit.entity;

import cn.nukkit.entity.mob.EntityCreeper;
import cn.nukkit.entity.mob.EntityEnderman;
import cn.nukkit.entity.mob.EntityZombie;
import cn.nukkit.entity.mob.EntityZombieVillagerV2;
import cn.nukkit.entity.passive.EntityAllay;
import cn.nukkit.entity.passive.EntityCow;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.entity.passive.EntityVillagerV2;
import cn.nukkit.entity.passive.EntityWanderingTrader;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import com.sun.management.ThreadMXBean;
import org.objenesis.ObjenesisStd;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/** Plain-JVM benchmark; run the same class against the original and patched EntityRanges. */
public final class EntityFollowRangeBenchmark {
    private static volatile long sink;

    private EntityFollowRangeBenchmark() { }

    private static long run(Entity[] entities, int iterations, boolean local) {
        long sum = 0;
        for (int i = 0; i < iterations; i++) {
            for (Entity entity : entities) {
                sum += local ? EntityRanges.getTargetSearchRange(entity) : EntityRanges.getFollowRange(entity);
            }
        }
        sink = sum;
        return sum;
    }

    public static void main(String[] args) {
        int iterations = args.length == 0 ? 500_000 : Integer.parseInt(args[0]);
        int rounds = args.length < 2 ? 9 : Integer.parseInt(args[1]);
        if (iterations <= 0 || rounds <= 0 || rounds % 2 == 0) {
            throw new IllegalArgumentException("Positive iteration count and odd round count required");
        }
        ObjenesisStd instances = new ObjenesisStd();
        Entity[] entities = {
                instances.newInstance(EntityCow.class), instances.newInstance(EntityVillager.class),
                instances.newInstance(EntityZombie.class), instances.newInstance(EntityEnderman.class),
                instances.newInstance(EntityVillagerV2.class), instances.newInstance(EntityZombieVillagerV2.class),
                instances.newInstance(EntityCreeper.class), instances.newInstance(EntityWanderingTrader.class),
                instances.newInstance(EntityAllay.class), instances.newInstance(CustomCow.class),
                instances.newInstance(DeepCow.class), instances.newInstance(CustomCreeper.class)};
        ThreadMXBean allocation = ManagementFactory.getThreadMXBean() instanceof ThreadMXBean mx
                && mx.isThreadAllocatedMemorySupported() ? mx : null;
        if (allocation != null) allocation.setThreadAllocatedMemoryEnabled(true);
        System.out.printf(Locale.ROOT, "runtime,%s,%s,%s%n", System.getProperty("java.version"),
                System.getProperty("os.arch"), EntityRanges.class.getProtectionDomain().getCodeSource().getLocation());
        for (boolean local : new boolean[]{false, true}) {
            long checksum = run(entities, 1, local);
            run(entities, iterations, local);
            run(entities, iterations, local);
            double[] times = new double[rounds];
            double[] bytes = new double[rounds];
            double calls = (double) iterations * entities.length;
            for (int round = 0; round < rounds; round++) {
                long beforeBytes = allocation == null ? 0 : allocation.getCurrentThreadAllocatedBytes();
                long before = System.nanoTime();
                long result = run(entities, iterations, local);
                times[round] = (System.nanoTime() - before) / calls;
                bytes[round] = allocation == null ? Double.NaN
                        : (allocation.getCurrentThreadAllocatedBytes() - beforeBytes) / calls;
                if (result != checksum * iterations) throw new AssertionError("Range result changed");
                System.out.printf(Locale.ROOT, "%s,%d,ns=%.4f,bytes=%.6f,checksum=%d%n",
                        local ? "target" : "follow", round, times[round], bytes[round], result);
            }
            Arrays.sort(times);
            Arrays.sort(bytes);
            System.out.printf(Locale.ROOT, "summary,%s,ns=%.4f,bytes=%.6f%n",
                    local ? "target" : "follow", times[rounds / 2], bytes[rounds / 2]);
        }
    }

    private static class CustomCow extends EntityCow {
        CustomCow(FullChunk chunk, CompoundTag tag) { super(chunk, tag); }
    }

    private static final class DeepCow extends CustomCow {
        DeepCow(FullChunk chunk, CompoundTag tag) { super(chunk, tag); }
    }

    private static final class CustomCreeper extends EntityCreeper {
        CustomCreeper(FullChunk chunk, CompoundTag tag) { super(chunk, tag); }
    }
}
