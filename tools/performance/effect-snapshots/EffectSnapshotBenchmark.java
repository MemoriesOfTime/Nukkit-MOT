import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.generic.BaseLevelProvider;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.potion.Effect;
import com.sun.management.ThreadMXBean;
import org.objenesis.ObjenesisStd;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Whole native entityBaseTick; callback witnesses are in a different source and JVM. */
public final class EffectSnapshotBenchmark {
    private static final int[] IDS = {1,2,3,4,5,6,7,8,9,11,12,13,14,15,16,17};
    private static final int INITIAL_DURATION = 1_000_000_000;
    private static final int ITERATIONS = 200_000;
    private static volatile long sink;

    private static final class FixtureEntity extends Entity {
        private FixtureEntity() { super(null, new CompoundTag()); }
        @Override public int getNetworkId() { return -1; }
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static FixtureEntity[] fixture(int count, ObjenesisStd allocator) throws Exception {
        Level level = allocator.newInstance(Level.class);
        Anvil provider = allocator.newInstance(Anvil.class);
        set(Level.class, level, "provider", provider);
        if (level.getClass() != Level.class || provider.getClass() != Anvil.class
                || level.getMinBlockY() != 0
                || Level.class.getMethod("getMinBlockY").getDeclaringClass() != Level.class
                || FixtureEntity.class.getMethod("entityBaseTick", int.class).getDeclaringClass() != Entity.class) {
            throw new AssertionError("Native environment/method origin");
        }
        FixtureEntity[] result = new FixtureEntity[256];
        for (int i=0; i<result.length; i++) {
            FixtureEntity entity = allocator.newInstance(FixtureEntity.class);
            set(Entity.class, entity, "effects", new ConcurrentHashMap<Integer,Effect>());
            set(Entity.class, entity, "passengers", new ArrayList<Entity>());
            set(Entity.class, entity, "health", 20f);
            entity.level = level;
            entity.y = 64;
            entity.noClip = true;
            for (int e=0; e<count; e++) {
                Effect effect = new Effect(IDS[e % IDS.length], "native", 0, 0, 0).setDuration(INITIAL_DURATION);
                if (effect.getClass() != Effect.class || effect.canTick()) throw new AssertionError("Native non-ticking effect");
                entity.getEffects().put(IDS[e % IDS.length] + (e / IDS.length) * 64, effect);
            }
            if (entity.getEffects().getClass() != ConcurrentHashMap.class) throw new AssertionError("Native map");
            result[i] = entity;
        }
        return result;
    }

    private static long loop(FixtureEntity[] entities) {
        long sum=0;
        for (int i=0; i<ITERATIONS; i++) {
            FixtureEntity entity=entities[i&255];
            boolean update=entity.entityBaseTick(1);
            sum += (long) entity.age + entity.ticksLived + (update ? 1_000_000_000L : 0);
        }
        return sum;
    }

    private static long expected(int previousLoops) {
        long sum=0;
        for (int i=0; i<256; i++) {
            long calls=ITERATIONS/256+(i<ITERATIONS%256 ? 1 : 0);
            long prior=calls*previousLoops;
            sum += 2*calls*prior + calls*(calls+1);
        }
        return sum;
    }

    private static void verify(FixtureEntity[] entities, int count, int loops) {
        for (int i=0; i<entities.length; i++) {
            FixtureEntity entity=entities[i];
            int calls=loops*(ITERATIONS/256+(i<ITERATIONS%256 ? 1 : 0));
            if (entity.age!=calls || entity.ticksLived!=calls || !entity.isAlive()
                    || entity.closed || entity.getEffects().size()!=count) throw new AssertionError("Whole native state");
            for (Effect effect:entity.getEffects().values()) {
                if (effect.getClass()!=Effect.class || effect.canTick()
                        || effect.getDuration()!=INITIAL_DURATION-calls || effect.getDuration()<=0) {
                    throw new AssertionError("Duration/native effect state");
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        for (Class<?> type:new Class<?>[]{Entity.class, Effect.class, Level.class, Anvil.class,
                BaseLevelProvider.class, FixtureEntity.class, EffectSnapshotBenchmark.class}) {
            System.out.printf("origin=%s\t%s%n",type.getName(),type.getProtectionDomain().getCodeSource().getLocation());
        }
        ThreadMXBean bean=(ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported() || !bean.isCurrentThreadCpuTimeSupported()) throw new AssertionError("Counters required");
        bean.setThreadAllocatedMemoryEnabled(true);bean.setThreadCpuTimeEnabled(true);
        long thread=Thread.currentThread().getId();
        ObjenesisStd allocator=new ObjenesisStd(true);
        for (int count:new int[]{0,1,4,8,16,17,64,128}) {
            FixtureEntity[] entities=fixture(count,allocator);
            int loops=0;
            for (int warmup=0; warmup<8; warmup++) {
                sink=loop(entities);
                if (sink!=expected(loops++)) throw new AssertionError("Warm checksum");
            }
            verify(entities,count,loops);
            for (int sample=0; sample<7; sample++) {
                long cpuStart=bean.getCurrentThreadCpuTime();
                long bytesStart=bean.getThreadAllocatedBytes(thread);
                long start=System.nanoTime();
                sink=loop(entities);
                long elapsed=System.nanoTime()-start;
                long bytes=bean.getThreadAllocatedBytes(thread)-bytesStart;
                long cpu=bean.getCurrentThreadCpuTime()-cpuStart;
                if (sink!=expected(loops++)) throw new AssertionError("Measured checksum");
                verify(entities,count,loops);
                System.out.printf(Locale.ROOT,"{\"effects\":%d,\"sample\":%d,\"iterations\":%d,\"bytes_per_op\":%.9f,\"cpu_ns_per_op\":%.9f,\"wall_ns_per_op\":%.9f,\"checksum\":%d}%n",count,sample,ITERATIONS,bytes/(double)ITERATIONS,cpu/(double)ITERATIONS,elapsed/(double)ITERATIONS,sink);
            }
        }
    }
}
