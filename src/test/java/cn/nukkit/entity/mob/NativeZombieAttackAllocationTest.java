package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.math.Vector3;
import com.sun.management.ThreadMXBean;
import sun.misc.Unsafe;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;

/**
 * Actual EntityZombie.attackEntity and actual event constructors. Constructors of fixture
 * entities/inventory are deliberately bypassed: no server, chunks, AI, health application,
 * packet emission or real inventory cloning is measured. Damage is fixed at 3; no RNG.
 * Armor Item.getId and BaseEntity.getArmorPoints remain real. An overridden armor inventory
 * returns one prebuilt array. All effects are absent. Each real event escapes via a volatile
 * static field, so its modifiers/originals cannot be discarded as an unobserved result.
 * This fixture uses no mocks, replacement map, or copied attack implementation.
 * Standalone measurements run without a javaagent; Maven may load its test agent.
 */
public final class NativeZombieAttackAllocationTest {
    @org.junit.jupiter.api.Test
    void nativeAttackStaysWithinMeasuredAllocationBudgets(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporaryDirectory) throws Exception {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean instanceof ThreadMXBean mx
                && mx.isThreadAllocatedMemorySupported() && mx.isCurrentThreadCpuTimeSupported());
        // MockServerExtension instruments core classes in every JUnit JVM. Measure native
        // classes in a bounded child using the same test classpath, without that extension.
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        java.nio.file.Path outputFile = temporaryDirectory.resolve("allocation.log");
        Process process = new ProcessBuilder(
                java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms256m", "-Xmx512m", "-cp", classpath,
                NativeZombieAttackAllocationTest.class.getName(), "junit-child", "100000", "100000", "7", "600", "630")
                .redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
        try {
            org.junit.jupiter.api.Assertions.assertTrue(
                    process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS), "Native allocation probe timed out");
            String output = java.nio.file.Files.readString(outputFile);
            System.out.print(output);
            org.junit.jupiter.api.Assertions.assertEquals(0, process.exitValue(), output);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
    }

    private static volatile EntityDamageByEntityEvent sink;

    private static final class Attacker extends EntityZombie {
        long damageCalls, armorCalls, effectChecks, animations;
        private Attacker() { super(null, null); }
        void ready() { attackDelay = 24; }
        int delay() { return attackDelay; }
        @Override public int getDamage() { damageCalls++; return 3; }
        @Override protected float getArmorPoints(int id) { armorCalls++; return super.getArmorPoints(id); }
        @Override public boolean hasEffect(int id) { effectChecks++; return false; }
        @Override protected void playAttack() { animations++; }
    }

    private static final class NonPlayer extends EntityZombie {
        long attacks, effectChecks, distanceCalls;
        private NonPlayer() { super(null, null); }
        @Override public boolean hasEffect(int id) { effectChecks++; return false; }
        @Override public double distanceSquared(Vector3 pos) { distanceCalls++; return super.distanceSquared(pos); }
        @Override public boolean attack(EntityDamageEvent event) {
            attacks++;
            sink = (EntityDamageByEntityEvent) event;
            return true;
        }
    }

    private static final class ArmoredPlayer extends Player {
        FixedInventory fixedInventory;
        long attacks, effectChecks, distanceCalls, inventoryCalls;
        private ArmoredPlayer() { super(null, 0L, null); }
        @Override public PlayerInventory getInventory() { inventoryCalls++; return fixedInventory; }
        @Override public boolean hasEffect(int id) { effectChecks++; return false; }
        @Override public double distanceSquared(Vector3 pos) { distanceCalls++; return super.distanceSquared(pos); }
        @Override public boolean attack(EntityDamageEvent event) {
            attacks++;
            sink = (EntityDamageByEntityEvent) event;
            return true;
        }
    }

    private static final class FixedInventory extends PlayerInventory {
        Item[] armor;
        long reads;
        private FixedInventory() { super(null); }
        @Override public Item[] getArmorContents() { reads++; return armor; }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkEvent(EntityDamageByEntityEvent event, Attacker attacker,
                                   Entity target, boolean player) {
        check(event != null && event.getClass() == EntityDamageByEntityEvent.class, "real event type");
        check(event.getDamager() == attacker && event.getEntity() == target, "event ownership");
        check(event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK, "cause");
        check(event.getDamage() == 3f && event.getOriginalDamage() == 3f, "BASE current/original");
        check(event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR) == player, "ARMOR presence");
        float armor = player ? -1f : 0f; // 15 iron points: -floor(3 * 15 * 0.04) == -1.
        check(event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) == armor, "ARMOR current");
        check(event.getOriginalDamage(EntityDamageEvent.DamageModifier.ARMOR) == armor, "ARMOR original");
        check(event.getFinalDamage() == 3f + armor, "final damage");
        check(event.getKnockBack() == 0.3f, "default knockback");
        check(event.getWeaponEnchantments() == Enchantment.EMPTY_ARRAY, "map-constructor enchantments");
        check(!event.isApplicable(EntityDamageEvent.DamageModifier.STRENGTH)
                && !event.isApplicable(EntityDamageEvent.DamageModifier.WEAKNESS)
                && !event.isApplicable(EntityDamageEvent.DamageModifier.RESISTANCE), "effect-free fixture");
        check(attacker.delay() == 0, "attack delay reset");
    }

    private static void runAttacks(Attacker attacker, Entity target, int iterations) {
        for (int i = 0; i < iterations; i++) {
            attacker.ready();
            attacker.attackEntity(target);
        }
    }

    private static double measure(String label, String scene, Unsafe unsafe, ThreadMXBean mx,
                                int warmup, int iterations, int rounds) throws Exception {
        Attacker attacker = (Attacker) unsafe.allocateInstance(Attacker.class);
        boolean player = scene.equals("player-base-armor");
        Entity target;
        FixedInventory inventory = null;
        if (player) {
            ArmoredPlayer p = (ArmoredPlayer) unsafe.allocateInstance(ArmoredPlayer.class);
            inventory = (FixedInventory) unsafe.allocateInstance(FixedInventory.class);
            // Only numeric identity is used by native attackEntity, never typed item behavior.
            inventory.armor = new Item[]{new Item(Item.IRON_HELMET), new Item(Item.IRON_CHESTPLATE),
                    new Item(Item.IRON_LEGGINGS), new Item(Item.IRON_BOOTS)};
            p.fixedInventory = inventory;
            target = p;
        } else {
            target = (NonPlayer) unsafe.allocateInstance(NonPlayer.class);
        }
        target.x = 0.5; // Native distanceSquared = 0.25, safely inside <= 1 gate.

        runAttacks(attacker, target, 1);
        EntityDamageByEntityEvent retained = sink;
        checkEvent(retained, attacker, target, player);
        retained.setDamage(17f);
        runAttacks(attacker, target, 1);
        check(sink != retained && retained.getDamage() == 17f && retained.getOriginalDamage() == 3f,
                "independent retained events");
        checkEvent(sink, attacker, target, player);

        runAttacks(attacker, target, warmup);
        double[] allocations = new double[rounds];
        for (int round = 0; round < rounds; round++) {
            long beforeBytes = mx.getCurrentThreadAllocatedBytes();
            long beforeCpu = mx.getCurrentThreadCpuTime();
            long beforeWall = System.nanoTime();
            runAttacks(attacker, target, iterations);
            long wall = System.nanoTime() - beforeWall;
            long cpu = mx.getCurrentThreadCpuTime() - beforeCpu;
            long bytes = mx.getCurrentThreadAllocatedBytes() - beforeBytes;
            allocations[round] = (double) bytes / iterations;
            checkEvent(sink, attacker, target, player); // Outside measured bounds.
            System.out.printf(Locale.ROOT, "round,%s,%s,%d,%d,%.6f,%.6f,%.6f%n", label, scene, round,
                    iterations, allocations[round], (double) cpu / iterations, (double) wall / iterations);
        }

        long expected = 2L + warmup + (long) iterations * rounds;
        check(attacker.damageCalls == expected && attacker.animations == expected, "attack/getDamage counts");
        check(attacker.effectChecks == expected * 2, "attacker effect check count");
        check(attacker.armorCalls == (player ? expected * 4 : 0), "native armor point lookup count");
        if (player) {
            ArmoredPlayer p = (ArmoredPlayer) target;
            check(p.attacks == expected && p.effectChecks == expected && p.distanceCalls == expected,
                    "player attack/effect/distance counts");
            check(p.inventoryCalls == expected && inventory.reads == expected, "armor inventory reads");
        } else {
            NonPlayer p = (NonPlayer) target;
            check(p.attacks == expected && p.effectChecks == expected && p.distanceCalls == expected,
                    "nonplayer attack/effect/distance counts");
        }
        Arrays.sort(allocations);
        System.out.printf(Locale.ROOT, "summary,%s,%s,attacks=%d,median_bytes=%.6f,min_bytes=%.6f,max_bytes=%.6f,invariants=PASS%n",
                label, scene, expected, allocations[rounds / 2], allocations[0], allocations[rounds - 1]);
        return allocations[rounds / 2];
    }

    public static void main(String[] args) throws Exception {
        String label = args.length > 0 ? args[0] : "baseline";
        int warmup = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 100_000;
        int rounds = args.length > 3 ? Integer.parseInt(args[3]) : 7;
        double maxNonplayerBytes = args.length > 4 ? Double.parseDouble(args[4]) : Double.POSITIVE_INFINITY;
        double maxPlayerBytes = args.length > 5 ? Double.parseDouble(args[5]) : Double.POSITIVE_INFINITY;
        runRegression(label, warmup, iterations, rounds, maxNonplayerBytes, maxPlayerBytes);
    }

    /** JUnit wrapper may call this same fixture with fixed measured budgets. */
    public static void runRegression(String label, int warmup, int iterations, int rounds,
                                     double maxNonplayerBytes, double maxPlayerBytes) throws Exception {
        check(warmup >= 1 && iterations >= 1 && rounds > 0 && rounds % 2 == 1, "positive inputs, odd rounds");
        check(maxNonplayerBytes > 0 && maxPlayerBytes > 0, "positive allocation budgets");
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        check(mx.isThreadAllocatedMemorySupported() && mx.isCurrentThreadCpuTimeSupported(), "VM counters supported");
        mx.setThreadAllocatedMemoryEnabled(true);
        mx.setThreadCpuTimeEnabled(true);
        System.out.printf(Locale.ROOT, "metadata,java=%s,vm=%s,arch=%s,source=%s,warmup=%d,iterations=%d,rounds=%d%n",
                System.getProperty("java.version"), System.getProperty("java.vm.name"),
                System.getProperty("os.arch"), EntityZombie.class.getProtectionDomain().getCodeSource().getLocation(),
                warmup, iterations, rounds);
        double nonplayerBytes = measure(label, "nonplayer-base", unsafe, mx, warmup, iterations, rounds);
        double playerBytes = measure(label, "player-base-armor", unsafe, mx, warmup, iterations, rounds);
        System.out.printf(Locale.ROOT, "gate,%s,nonplayer=%.6f/%.6f,player=%.6f/%.6f%n", label,
                nonplayerBytes, maxNonplayerBytes, playerBytes, maxPlayerBytes);
        check(nonplayerBytes <= maxNonplayerBytes && playerBytes <= maxPlayerBytes,
                "Allocation budget exceeded: nonplayer=" + nonplayerBytes + "/" + maxNonplayerBytes
                        + ", player=" + playerBytes + "/" + maxPlayerBytes);
    }
}
