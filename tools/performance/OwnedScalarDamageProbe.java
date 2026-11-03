import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.event.entity.EntityDamageEvent.DamageModifier;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.EventException;
import com.sun.management.ThreadMXBean;
import sun.misc.Unsafe;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Standalone actual-source probe: no server, event bus, test framework or instrumentation. */
public final class OwnedScalarDamageProbe {
    private static volatile EntityDamageEvent published;
    private static final MessageDigest DIGEST;
    private static long checks;
    private static long pairs;
    static {
        try { DIGEST = MessageDigest.getInstance("SHA-256"); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }

    public static void main(String[] args) throws Exception {
        parity();
        aliases();
        callbacksAndExceptions();
        System.out.println("PARITY pairs=" + pairs + " checks=" + checks + " sha256="
                + HexFormat.of().formatHex(DIGEST.digest()));
        allocation();
    }

    private static void parity() throws Exception {
        int[] edgeBits = {0, 0x80000000, 1, 0x80000001, 0x007fffff, 0x00800000,
                0x7f7fffff, 0xff7fffff, 0x7f800000, 0xff800000, 0x7fc01234,
                0xffc05678, 0x7fa01234, 0xffa05678, Float.floatToRawIntBits(17.25f)};
        Random random = new Random(0xDADAFEEDL);
        int[] bits = Arrays.copyOf(edgeBits, edgeBits.length + 64);
        for (int i = edgeBits.length; i < bits.length; i++) bits[i] = random.nextInt();
        for (DamageCause cause : DamageCause.values()) {
            for (int raw : bits) {
                float damage = Float.intBitsToFloat(raw);
                for (int amp : new int[]{-1, 0, 1, 4, 127, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                    FixtureEntity entity = entity();
                    entity.resistance = amp != -1;
                    entity.effect = new Effect(Effect.DAMAGE_RESISTANCE, "fixture", 0, 0, 0).setAmplifier(amp);
                    EntityDamageEvent scalar = new EntityDamageEvent(entity, cause, damage);
                    EntityDamageEvent mapped = new EntityDamageEvent(entity, cause, base(damage));
                    equivalent(mapped, scalar);
                    bits(damage, scalar.getOriginalDamage());
                    bits(0f, scalar.getOriginalDamage(DamageModifier.RESISTANCE));
                    if (entity.resistance) {
                        // Intentionally retain the production double expression and int amplifier overflow.
                        bits((float) -(damage * 0.20 * (entity.effect.getAmplifier() + 1)),
                                scalar.getDamage(DamageModifier.RESISTANCE));
                    }
                    for (DamageModifier modifier : DamageModifier.values()) {
                        float value = (modifier.ordinal() - 3) * 0.125f;
                        scalar.setDamage(value, modifier);
                        mapped.setDamage(value, modifier);
                        equivalent(mapped, scalar);
                        bits(modifier == DamageModifier.BASE ? damage : 0, scalar.getOriginalDamage(modifier));
                    }
                    scalar.setAttackCooldown(-4);
                    mapped.setAttackCooldown(-4);
                    scalar.setBreakShield(true);
                    mapped.setBreakShield(true);
                    scalar.setShieldBreakCoolDown(987);
                    mapped.setShieldBreakCoolDown(987);
                    scalar.setCancelled(true);
                    mapped.setCancelled(true);
                    equivalent(mapped, scalar);
                    pairs++;
                }
            }
        }
        EntityDamageEvent noCause = new EntityDamageEvent(entity(), null, -0f);
        equal(null, noCause.getCause());
        bits(-0f, noCause.getOriginalDamage());
        exception(NullPointerException.class, noCause::canBeReducedByArmor);
    }

    private static void aliases() throws Exception {
        for (boolean hash : new boolean[]{false, true}) {
            FixtureEntity entity = entity();
            entity.resistance = true;
            entity.effect = new Effect(Effect.DAMAGE_RESISTANCE, "fixture", 0, 0, 0).setAmplifier(1);
            Map<DamageModifier, Float> input = hash ? new HashMap<>() : new EnumMap<>(DamageModifier.class);
            for (DamageModifier modifier : DamageModifier.values()) input.put(modifier, -modifier.ordinal() - 0.125f);
            input.put(DamageModifier.BASE, 17.25f);
            Map<DamageModifier, Float> snapshot = new EnumMap<>(input);
            EntityDamageEvent event = new EntityDamageEvent(entity, DamageCause.ENTITY_ATTACK, input);
            input.replaceAll((key, value) -> 1000f);
            for (DamageModifier modifier : DamageModifier.values()) {
                bits(snapshot.get(modifier), event.getOriginalDamage(modifier));
                bits(modifier == DamageModifier.RESISTANCE ? (float) -(17.25f * 0.20 * 2)
                        : snapshot.get(modifier), event.getDamage(modifier));
                event.setDamage(500f, modifier);
                bits(1000f, input.get(modifier));
                bits(snapshot.get(modifier), event.getOriginalDamage(modifier));
            }
            input.clear();
            bits(500f, event.getDamage());
            bits(17.25f, event.getOriginalDamage());
        }
        FixtureEntity entity = entity();
        EnumMap<DamageModifier, Float> input = base(9f);
        entity.onHasEffect = () -> input.put(DamageModifier.BASE, 300f);
        EntityDamageEvent event = new EntityDamageEvent(entity, DamageCause.CUSTOM, input);
        bits(9f, event.getDamage());
        bits(9f, event.getOriginalDamage());
        bits(300f, input.get(DamageModifier.BASE));
        EntityDamageEvent immutable = new EntityDamageEvent(entity(), DamageCause.CUSTOM, Map.of(DamageModifier.BASE, 2f));
        immutable.setDamage(3f);
        bits(2f, immutable.getOriginalDamage());
        bits(3f, immutable.getDamage());
        Field originals = EntityDamageEvent.class.getDeclaredField("originals");
        originals.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<DamageModifier, Float> originalMap = (Map<DamageModifier, Float>) originals.get(immutable);
        exception(UnsupportedOperationException.class, () -> originalMap.put(DamageModifier.BASE, 0f));
    }

    private static void callbacksAndExceptions() throws Exception {
        for (boolean scalar : new boolean[]{false, true}) {
            FixtureEntity entity = entity();
            entity.trace = new ArrayList<>();
            entity.resistance = true;
            entity.effect = new TracedEffect(entity.trace);
            TracedEvent event = traced(entity, scalar);
            equal(List.of("has", "get:BASE", "effect", "amplifier", "set:RESISTANCE"), entity.trace);
            bits(17.25f, event.getOriginalDamage());
            bits((float) -(19.25f * 0.20 * 2), event.getDamage(DamageModifier.RESISTANCE));

            for (String throwing : new String[]{"has", "get:BASE", "effect", "amplifier", "set:RESISTANCE"}) {
                FixtureEntity throwingEntity = entity();
                throwingEntity.trace = new ArrayList<>();
                throwingEntity.resistance = true;
                throwingEntity.throwAt = throwing;
                throwingEntity.effect = new TracedEffect(throwingEntity.trace, throwing);
                exception(ProbeException.class, () -> traced(throwingEntity, scalar));
                List<String> full = List.of("has", "get:BASE", "effect", "amplifier", "set:RESISTANCE");
                equal(full.subList(0, full.indexOf(throwing) + 1), throwingEntity.trace);
            }
            entity.trace.clear();
            entity.effect = null;
            exception(NullPointerException.class, () -> traced(entity, scalar));
            equal(List.of("has", "get:BASE", "effect"), entity.trace);
        }

        FixtureEntity entity = entity();
        entity.trace = new ArrayList<>();
        EventException missing = exception(EventException.class,
                () -> new EntityDamageEvent(entity, DamageCause.CUSTOM, new EnumMap<>(DamageModifier.class)));
        equal("BASE Damage modifier missing", missing.getMessage());
        exception(IllegalArgumentException.class, () -> new EntityDamageEvent(entity, DamageCause.CUSTOM, new HashMap<>()));
        exception(NullPointerException.class,
                () -> new EntityDamageEvent(entity, DamageCause.CUSTOM, (Map<DamageModifier, Float>) null));
        for (DamageModifier modifier : DamageModifier.values()) {
            EnumMap<DamageModifier, Float> nullValue = base(10f);
            nullValue.put(modifier, null);
            exception(NullPointerException.class, () -> new EntityDamageEvent(entity, DamageCause.CUSTOM, nullValue));
        }
        equal(List.of(), entity.trace);
        exception(EventException.class,
                () -> new EntityDamageEvent(null, null, new EnumMap<>(DamageModifier.class)));
        exception(NullPointerException.class, () -> new EntityDamageEvent(null, null, 10f));
        exception(NullPointerException.class, () -> new EntityDamageEvent(null, null, base(10f)));
    }

    private static void equivalent(EntityDamageEvent expected, EntityDamageEvent actual) {
        check(expected.getEntity() == actual.getEntity());
        equal(expected.getCause(), actual.getCause());
        equal(expected.getAttackCooldown(), actual.getAttackCooldown());
        equal(expected.canBeReducedByArmor(), actual.canBeReducedByArmor());
        equal(expected.isBreakShield(), actual.isBreakShield());
        equal(expected.getShieldBreakCoolDown(), actual.getShieldBreakCoolDown());
        equal(expected.isCancelled(), actual.isCancelled());
        bits(expected.getFinalDamage(), actual.getFinalDamage());
        for (DamageModifier modifier : DamageModifier.values()) {
            bits(expected.getDamage(modifier), actual.getDamage(modifier));
            bits(expected.getOriginalDamage(modifier), actual.getOriginalDamage(modifier));
            equal(expected.isApplicable(modifier), actual.isApplicable(modifier));
        }
    }

    private static void allocation() throws Exception {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        check(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        FixtureEntity entity = entity();
        EnumMap<DamageModifier, Float> input = base(10f);
        int operations = 20_000;
        // Equivalent one fresh Float per event on both paths. Every actual event escapes through volatile.
        for (int pass = 0; pass < 3; pass++) {
            allocate(entity, input, true, operations);
            allocate(entity, input, false, operations);
        }
        long[] scalar = new long[5];
        long[] mapped = new long[5];
        long thread = Thread.currentThread().threadId();
        for (int pass = 0; pass < scalar.length; pass++) {
            long before = bean.getThreadAllocatedBytes(thread);
            allocate(entity, input, true, operations);
            scalar[pass] = bean.getThreadAllocatedBytes(thread) - before;
            before = bean.getThreadAllocatedBytes(thread);
            allocate(entity, input, false, operations);
            mapped[pass] = bean.getThreadAllocatedBytes(thread) - before;
        }
        System.out.println("ALLOCATION operations=" + operations + " scalar=" + Arrays.toString(scalar)
                + " publicMap=" + Arrays.toString(mapped));
        System.out.println("SOURCE " + EntityDamageEvent.class.getProtectionDomain().getCodeSource().getLocation());
    }

    private static void allocate(FixtureEntity entity, EnumMap<DamageModifier, Float> input,
                                 boolean scalar, int operations) {
        for (int i = 0; i < operations; i++) {
            float damage = 10.25f + (i & 1);
            if (scalar) published = new EntityDamageEvent(entity, DamageCause.ENTITY_ATTACK, damage);
            else {
                input.put(DamageModifier.BASE, damage);
                published = new EntityDamageEvent(entity, DamageCause.ENTITY_ATTACK, input);
            }
        }
    }

    private static EnumMap<DamageModifier, Float> base(float damage) {
        EnumMap<DamageModifier, Float> result = new EnumMap<>(DamageModifier.class);
        result.put(DamageModifier.BASE, damage);
        return result;
    }

    private static FixtureEntity entity() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (FixtureEntity) ((Unsafe) field.get(null)).allocateInstance(FixtureEntity.class);
    }

    private static TracedEvent traced(FixtureEntity entity, boolean scalar) {
        return scalar ? new TracedEvent(entity, 17.25f) : new TracedEvent(entity, base(17.25f));
    }

    private static void bits(float expected, float actual) {
        int left = Float.floatToRawIntBits(expected), right = Float.floatToRawIntBits(actual);
        if (left != right) throw new AssertionError("float bits " + Integer.toHexString(left) + " != " + Integer.toHexString(right));
        checks++;
        for (int shift = 0; shift < 32; shift += 8) DIGEST.update((byte) (right >>> shift));
    }

    private static void equal(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
        checks++;
        DIGEST.update(String.valueOf(actual).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        DIGEST.update((byte) 0);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
        checks++;
    }

    private static <T extends Throwable> T exception(Class<T> expected, Runnable action) {
        try { action.run(); }
        catch (Throwable failure) {
            if (!expected.isInstance(failure)) throw new AssertionError("expected " + expected + ", got " + failure, failure);
            equal(expected.getName(), failure.getClass().getName());
            return expected.cast(failure);
        }
        throw new AssertionError("missing exception " + expected);
    }

    private static final class ProbeException extends RuntimeException { }

    private static final class FixtureEntity extends Entity {
        boolean resistance;
        Effect effect;
        List<String> trace;
        String throwAt;
        Runnable onHasEffect;
        FixtureEntity() { super(null, null); }
        @Override public int getNetworkId() { return -1; }
        @Override public boolean hasEffect(int effectId) {
            hook("has");
            if (onHasEffect != null) onHasEffect.run();
            return resistance;
        }
        @Override public Effect getEffect(int effectId) { hook("effect"); return effect; }
        void hook(String name) {
            if (trace != null) trace.add(name);
            if (name.equals(throwAt)) throw new ProbeException();
        }
    }

    private static final class TracedEffect extends Effect {
        private final List<String> trace;
        private final String throwAt;
        TracedEffect(List<String> trace) { this(trace, null); }
        TracedEffect(List<String> trace, String throwAt) {
            super(Effect.DAMAGE_RESISTANCE, "fixture", 0, 0, 0);
            this.trace = trace;
            this.throwAt = throwAt;
        }
        @Override public int getAmplifier() {
            trace.add("amplifier");
            if ("amplifier".equals(throwAt)) throw new ProbeException();
            return 1;
        }
    }

    private static final class TracedEvent extends EntityDamageEvent {
        TracedEvent(FixtureEntity entity, float damage) { super(entity, DamageCause.CUSTOM, damage); }
        TracedEvent(FixtureEntity entity, Map<DamageModifier, Float> map) { super(entity, DamageCause.CUSTOM, map); }
        @Override public float getDamage(DamageModifier modifier) {
            ((FixtureEntity) getEntity()).hook("get:" + modifier);
            return super.getDamage(modifier) + (modifier == DamageModifier.BASE ? 2f : 0f);
        }
        @Override public void setDamage(float damage, DamageModifier modifier) {
            ((FixtureEntity) getEntity()).hook("set:" + modifier);
            check(!isApplicable(DamageModifier.RESISTANCE));
            bits(0f, getOriginalDamage(DamageModifier.RESISTANCE));
            super.setDamage(damage, modifier);
        }
    }
}
