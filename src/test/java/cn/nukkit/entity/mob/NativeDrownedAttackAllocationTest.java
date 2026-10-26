package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.math.Vector3;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/**
 * Actual EntityDrowned.attackEntity and actual event constructors. Constructors of fixture
 * entities/inventory are deliberately bypassed: no server, chunks, AI, health application,
 * packet emission or real inventory cloning is measured. Damage is fixed at 3; no RNG.
 * Armor Item.getId and BaseEntity.getArmorPoints remain real. An overridden armor inventory
 * returns one prebuilt array. All effects are absent. Each real event escapes via a volatile
 * static field, so its modifiers/originals cannot be discarded as an unobserved result.
 * Uses no mocks, instrumentation agent, replacement map, or copied attack implementation.
 */
public final class NativeDrownedAttackAllocationTest {
    @org.junit.jupiter.api.Test
    void nativeMeleeAllocationStaysWithinMeasuredBudgets(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporaryDirectory) throws Exception {
        runChild(temporaryDirectory, "junit-child", "500000", "50000", "7", "560", "560");
    }

    @org.junit.jupiter.api.Test
    void refusedAttacksAndNativeTargetEligibilityKeepTheirCallbacks(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporaryDirectory) throws Exception {
        runChild(temporaryDirectory, "controls", "junit-child");
    }

    private static void runChild(java.nio.file.Path directory, String... arguments) throws Exception {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean instanceof ThreadMXBean mx
                && mx.isThreadAllocatedMemorySupported() && mx.isCurrentThreadCpuTimeSupported());
        // JUnit's global MockServerExtension instruments classes. Use the same compiled
        // classes in a bounded child without that extension or its instrumentation.
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        java.nio.file.Path outputFile = directory.resolve("allocation.log");
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
                java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms256m", "-Xmx256m",
                "-Dexpected.drowned.source=" + java.nio.file.Path.of(EntityDrowned.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()),
                "-cp", classpath, NativeDrownedAttackAllocationTest.class.getName()));
        command.addAll(java.util.List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
        try {
            org.junit.jupiter.api.Assertions.assertTrue(process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS),
                    "Native allocation probe timed out");
            String output = java.nio.file.Files.readString(outputFile);
            System.out.print(output);
            org.junit.jupiter.api.Assertions.assertEquals(0, process.exitValue(), output);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                org.junit.jupiter.api.Assertions.assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS),
                        "Native allocation child did not terminate");
            }
        }
    }

    private static volatile EntityDamageByEntityEvent sink;
    private static final class Allocator {
        private final org.objenesis.Objenesis objenesis = new org.objenesis.ObjenesisStd();
        Object allocateInstance(Class<?> type) {
            return objenesis.newInstance(type);
        }
    }

    private static final class Attacker extends EntityDrowned {
        long damageCalls, armorCalls, effectChecks, animations;
        private Attacker() { super(null, null); }
        void ready() { attackDelay = 24; }
        void setDelay(int value) { attackDelay = value; }
        void fixtureServer(cn.nukkit.Server value) { this.server = value; }
        Entity eligibleTarget(Vector3 value) { return super.getAttackTarget(value); }
        int delay() { return attackDelay; }
        @Override public int getDamage() { damageCalls++; return 3; }
        @Override protected float getArmorPoints(int id) { armorCalls++; return super.getArmorPoints(id); }
        @Override public boolean hasEffect(int id) { effectChecks++; return false; }
        @Override protected void playAttack() { animations++; }
    }

    private static final class NonPlayer extends EntityDrowned {
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

    @SuppressWarnings("overrides") // Existing PlayerInventory array override conflicts with Inventory varargs.
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

    private static double measure(String label, String scene, Allocator unsafe, ThreadMXBean mx,
                                int warmup, int iterations, int rounds) throws Exception {
        Attacker attacker = (Attacker) unsafe.allocateInstance(Attacker.class);
        check(attacker.getClass().getMethod("attackEntity", Entity.class).getDeclaringClass() == EntityDrowned.class,
                "inherited unmodified Drowned attackEntity");
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


    private static void runRefused(Attacker attacker, Entity target, String scene, int count) {
        for (int i = 0; i < count; i++) {
            if (scene.equals("canAttack")) {
                check(attacker.eligibleTarget(target) == null, "native canAttack rejection");
            } else {
                attacker.attackEntity(target);
            }
        }
    }

    private static void runControls(String label) throws Exception {
        Allocator allocator = new Allocator();
        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        check(mx.isThreadAllocatedMemorySupported(), "allocation counter");
        mx.setThreadAllocatedMemoryEnabled(true);
        for (boolean player : new boolean[]{false, true}) {
            for (String scene : new String[]{"delay", "range", "canAttack"}) {
                Attacker attacker = (Attacker) allocator.allocateInstance(Attacker.class);
                Entity target = (Entity) allocator.allocateInstance(player ? ArmoredPlayer.class : NonPlayer.class);
                target.x = scene.equals("range") ? 2 : 0.5;
                attacker.setDelay(scene.equals("delay") ? 23 : 24);
                cn.nukkit.Server server = (cn.nukkit.Server) allocator.allocateInstance(cn.nukkit.Server.class);
                server.mobAiEnabled = true;
                attacker.fixtureServer(server);
                attacker.setTarget(target, false);
                if (scene.equals("canAttack")) {
                    check(attacker.eligibleTarget(target) == null, "same-follow-target refused");
                    attacker.setTarget(target, true);
                    check(attacker.eligibleTarget(target) == target, "canAttack true admitted");
                    attacker.setTarget(null, false);
                    check(attacker.eligibleTarget(target) == target, "different target unaffected by false flag");
                    attacker.setTarget(target, false);
                }
                sink = null;
                final int warmup = 500_000, iterations = 50_000, rounds = 7;
                runRefused(attacker, target, scene, warmup);
                double[] windows = new double[rounds];
                for (int w = 0; w < rounds; w++) {
                    long start = mx.getCurrentThreadAllocatedBytes();
                    runRefused(attacker, target, scene, iterations);
                    windows[w] = (mx.getCurrentThreadAllocatedBytes() - start) / (double) iterations;
                    System.out.printf(Locale.ROOT, "control-window,%s,%s,%s,%d,%.6f%n", label,
                            player ? "Player" : "nonPlayer", scene, w, windows[w]);
                }
                long calls = warmup + (long) iterations * rounds;
                long expectedDistance = scene.equals("range") ? calls : 0;
                check(sink == null && attacker.damageCalls == 0 && attacker.armorCalls == 0
                        && attacker.effectChecks == 0 && attacker.animations == 0, "refused event callbacks");
                check(attacker.delay() == (scene.equals("delay") ? 23 : 24), "refused delay unchanged");
                if (player) {
                    ArmoredPlayer t = (ArmoredPlayer) target;
                    check(t.attacks == 0 && t.effectChecks == 0 && t.inventoryCalls == 0
                            && t.distanceCalls == expectedDistance, "refused Player callbacks");
                } else {
                    NonPlayer t = (NonPlayer) target;
                    check(t.attacks == 0 && t.effectChecks == 0 && t.distanceCalls == expectedDistance,
                            "refused nonPlayer callbacks");
                }
                Arrays.sort(windows);
                System.out.printf(Locale.ROOT, "control-summary,%s,%s,%s,calls=%d,median_bytes=%.6f,invariants=PASS%n",
                        label, player ? "Player" : "nonPlayer", scene, calls, windows[rounds / 2]);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String expected = System.getProperty("expected.drowned.source");
        check(expected != null && EntityDrowned.class.getProtectionDomain().getCodeSource().getLocation().toURI()
                .equals(java.nio.file.Path.of(expected).toUri()), "expected native overlay loaded");
        System.out.printf("overlay,java=%s,source=%s%n", System.getProperty("java.version"), expected);
        if (args.length > 0 && args[0].equals("controls")) {
            runControls(args[1]);
            return;
        }

        String label = args.length > 0 ? args[0] : "baseline";
        int warmup = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 100_000;
        int rounds = args.length > 3 ? Integer.parseInt(args[3]) : 7;
        double maxNonplayerBytes = args.length > 4 ? Double.parseDouble(args[4]) : Double.POSITIVE_INFINITY;
        double maxPlayerBytes = args.length > 5 ? Double.parseDouble(args[5]) : Double.POSITIVE_INFINITY;
        if (args.length > 6) {
            Allocator allocator = new Allocator();
            ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            check(mx.isThreadAllocatedMemorySupported() && mx.isCurrentThreadCpuTimeSupported(), "VM counters supported");
            mx.setThreadAllocatedMemoryEnabled(true);
            mx.setThreadCpuTimeEnabled(true);
            measure(label, args[6], allocator, mx, warmup, iterations, rounds);
        } else {
            runRegression(label, warmup, iterations, rounds, maxNonplayerBytes, maxPlayerBytes);
        }
    }

    /** JUnit wrapper may call this same fixture with fixed measured budgets. */
    public static void runRegression(String label, int warmup, int iterations, int rounds,
                                     double maxNonplayerBytes, double maxPlayerBytes) throws Exception {
        check(warmup >= 1 && iterations >= 1 && rounds > 0 && rounds % 2 == 1, "positive inputs, odd rounds");
        check(maxNonplayerBytes > 0 && maxPlayerBytes > 0, "positive allocation budgets");
        Allocator unsafe = new Allocator();
        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        check(mx.isThreadAllocatedMemorySupported() && mx.isCurrentThreadCpuTimeSupported(), "VM counters supported");
        mx.setThreadAllocatedMemoryEnabled(true);
        mx.setThreadCpuTimeEnabled(true);
        System.out.printf(Locale.ROOT, "metadata,java=%s,vm=%s,arch=%s,source=%s,warmup=%d,iterations=%d,rounds=%d%n",
                System.getProperty("java.version"), System.getProperty("java.vm.name"),
                System.getProperty("os.arch"), EntityDrowned.class.getProtectionDomain().getCodeSource().getLocation(),
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
