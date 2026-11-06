package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.BlockFace;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class LeafSearchScratchTest {
    private static final MethodHandle SEARCH;
    private static final List<String> PROOFS = new ArrayList<>();
    static {
        try {
            SEARCH = MethodHandles.privateLookupIn(BlockLeaves.class, MethodHandles.lookup())
                    .findVirtual(BlockLeaves.class, "findLog", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @FunctionalInterface
    interface Search { boolean run(BlockLeaves root) throws Throwable; }
    private static boolean actual(BlockLeaves root) throws Throwable { return (boolean) SEARCH.invokeExact(root); }

    // Exact pre-change b216 findLog body, including collection types and callback order.
    private static boolean original(BlockLeaves root) {
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        Map<Block, Integer> distance = new HashMap<>();
        queue.offer(root);
        visited.add(root);
        distance.put(root, 0);
        while (!queue.isEmpty()) {
            Block currentBlock = queue.poll();
            int currentDistance = distance.get(currentBlock);
            if (currentDistance > 4) return false;
            for (BlockFace face : BlockFace.values()) {
                Block nextBlock = currentBlock.getSideIfLoadedOrNull(face);
                if (nextBlock == null || root.isLog(nextBlock.getId(), nextBlock.getDamage())) return true;
                if (root.isLeaves(nextBlock.getId()) && !visited.contains(nextBlock)) {
                    queue.offer(nextBlock);
                    visited.add(nextBlock);
                    distance.put(nextBlock, currentDistance + 1);
                }
            }
        }
        return false;
    }

    record Result(Boolean supported, String error, List<String> trace) { }
    private static Result run(Graph g, Search search) {
        g.search = search;
        try { return new Result(search.run(g.root), null, List.copyOf(g.trace)); }
        catch (Throwable failure) {
            // Only our explicit callback exception messages are contract witnesses.
            return new Result(null, failure.getClass().getName() + ":" + failure.getMessage(), List.copyOf(g.trace));
        }
    }
    private static Result verify(String name, Supplier<Graph> fixture, Boolean expected) {
        Result before = run(fixture.get(), LeafSearchScratchTest::original);
        Result after = run(fixture.get(), LeafSearchScratchTest::actual);
        assertEquals(before, after, name);
        assertEquals(expected, after.supported(), name);
        if (expected == null) assertEquals(Marker.class.getName() + ":callback", after.error());
        else assertNull(after.error(), name);
        PROOFS.add(name + ":" + after);
        return after;
    }

    @Test
    void nullAndLogAtEveryFaceKeepEnumOrderAndEarlyExit() {
        for (BlockFace face : BlockFace.values()) {
            for (boolean unloaded : new boolean[]{true, false}) {
                Result result = verify(face + (unloaded ? "-null" : "-log"), () -> {
                    Graph g = new Graph();
                    g.root.s.sides[face.ordinal()] = unloaded ? null : g.node("log", BlockID.LOG);
                    return g;
                }, true);
                assertEquals(Arrays.stream(BlockFace.values()).limit(face.ordinal() + 1L)
                                .map(f -> "side:root:" + f.name()).toList(),
                        result.trace().stream().filter(s -> s.startsWith("side:root:")).toList());
            }
        }
    }

    @Test
    void distanceFourStillExaminesNeighborsAndDistanceFiveIsNotExpanded() {
        for (int logDistance = 1; logDistance <= 6; logDistance++) {
            final int depth = logDistance;
            Result result = verify("distance-" + depth, () -> chain(depth), depth <= 5);
            if (depth == 6) assertTrue(result.trace().stream().noneMatch(s -> s.startsWith("side:leaf5:")));
        }
    }

    private static Graph chain(int logDistance) {
        Graph g = new Graph();
        State previous = g.root.s;
        for (int distance = 1; distance < logDistance; distance++) {
            Node leaf = g.node("leaf" + distance, BlockID.LEAVES);
            previous.sides[BlockFace.DOWN.ordinal()] = leaf;
            previous = leaf.s;
        }
        previous.sides[BlockFace.DOWN.ordinal()] = g.node("log", BlockID.LOG);
        return g;
    }

    @Test
    void cyclesDuplicatesAndCustomHashEqualsRetainAllCalls() {
        Result result = verify("colliding-duplicates", () -> {
            Graph g = new Graph(); g.colliding = true;
            Node a = g.node("a", BlockID.LEAVES), b = g.node("b", BlockID.LEAVES);
            Node alias = g.node("alias", BlockID.LEAVES); alias.s.key = a.s.key;
            g.root.s.sides[0] = a; g.root.s.sides[1] = b; g.root.s.sides[2] = alias;
            a.s.sides[0] = g.root; b.s.sides[0] = a;
            return g;
        }, false);
        assertTrue(result.trace().stream().anyMatch(s -> s.startsWith("equals:alias:")));
        assertTrue(result.trace().stream().noneMatch(s -> s.startsWith("side:alias:")));
        assertTrue(result.trace().stream().anyMatch(s -> s.startsWith("side:b:")));
    }

    @Test
    void customPredicatesAndDamageRemainVirtual() {
        verify("custom-predicates", () -> {
            Graph g = new Graph(); g.custom = true;
            Node leaf = g.node("leaf", 77), log = g.node("custom-log", 42); log.s.damage = 9;
            g.root.s.sides[0] = leaf; leaf.s.sides[4] = log;
            return g;
        }, true);
        verify("stripped-bark-is-not-support", () -> {
            Graph g = new Graph(); Node bark = g.node("bark", BlockID.WOOD_BARK);
            bark.s.damage = BlockWoodBark.STRIPPED_BIT; g.root.s.sides[0] = bark; return g;
        }, false);
    }

    @Test
    void callbackExceptionsHaveTheSamePrefixAndFailure() {
        for (String trigger : List.of("side:root:UP", "hash:root", "log:1:0", "leaves:1", "equals:b:")) {
            verify("exception-" + trigger, () -> {
                Graph g = new Graph(); g.colliding = true;
                g.root.s.sides[0] = g.node("a", BlockID.LEAVES);
                g.root.s.sides[1] = g.node("b", BlockID.LEAVES);
                g.hook = event -> { if (event.startsWith(trigger)) throw new Marker(); };
                return g;
            }, null);
        }
    }

    @Test
    void reentryFromNeighborHashEqualsAndPredicateHasIndependentScratch() {
        for (String trigger : List.of("side:root:DOWN", "hash:root", "log:18:0", "equals:b:")) {
            Result result = verify("reenter-" + trigger, () -> {
                Graph g = new Graph(); g.colliding = true;
                g.root.s.sides[0] = g.node("a", BlockID.LEAVES);
                g.root.s.sides[1] = g.node("b", BlockID.LEAVES);
                Graph nested = new Graph(g.trace, "nested"); nested.root.s.sides[0] = null;
                g.hook = new Consumer<>() {
                    boolean entered;
                    @Override public void accept(String event) {
                        if (!entered && event.startsWith(trigger)) {
                            entered = true;
                            try { g.trace.add("nested-result:" + g.search.run(nested.root)); }
                            catch (Throwable failure) { throw new AssertionError(failure); }
                        }
                    }
                };
                return g;
            }, false);
            assertTrue(result.trace().contains("nested-result:true"), trigger);
        }
    }

    @Test
    void callbackMutationAndCallerOwnedValuesArrayCannotChangeTraversal() {
        verify("mutate-up-after-down", () -> {
            Graph g = new Graph(); Node log = g.node("later-log", BlockID.LOG);
            BlockFace[] callerArray = BlockFace.values(); Arrays.fill(callerArray, null);
            g.hook = event -> { if (event.equals("side:root:DOWN")) g.root.s.sides[1] = log; };
            return g;
        }, true);
    }

    @Test
    void nativeSideAccessUsesOnlyLoadedChunkAndNeverLoadTrue() throws Throwable {
        for (int missingFace = 0; missingFace < 6; missingFace++) {
            final int missing = missingFace;
            List<String> before = nativeAccess(missing, LeafSearchScratchTest::original);
            List<String> after = nativeAccess(missing, LeafSearchScratchTest::actual);
            assertEquals(before, after);
            assertEquals(missing + 1, after.stream().filter(s -> s.startsWith("chunk:")).count());
            assertEquals(missing, after.stream().filter(s -> s.startsWith("block:")).count());
            PROOFS.add("native-loaded-" + missing + ":" + after);
        }
    }

    private static List<String> nativeAccess(int missing, Search search) throws Throwable {
        LoadedLevel level = new ObjenesisStd().newInstance(LoadedLevel.class);
        level.trace = new ArrayList<>(); level.missing = missing;
        level.chunk = new ObjenesisStd().newInstance(LevelDBChunk.class);
        level.solid = new Node(new State(null, "solid", BlockID.STONE, 0));
        BlockLeaves root = new BlockLeaves(); root.level = level; root.x = 15; root.y = 64; root.z = 15;
        assertTrue(search.run(root));
        return List.copyOf(level.trace);
    }

    private static final class LoadedLevel extends Level {
        List<String> trace; int probes, missing; BaseFullChunk chunk; Block solid;
        private LoadedLevel() { super((Server) null, "unused", "unused", (Class<? extends LevelProvider>) null); }
        @Override public BaseFullChunk getChunkIfLoaded(int x, int z) {
            trace.add("chunk:" + x + ":" + z); return probes++ == missing ? null : chunk;
        }
        @Override public BaseFullChunk getChunk(int x, int z, boolean create) { throw new AssertionError("loading getter"); }
        @Override public Block getBlock(FullChunk chunk, int x, int y, int z, int layer, boolean load) {
            assertSame(this.chunk, chunk); assertFalse(load); assertEquals(0, layer);
            trace.add("block:" + x + ":" + y + ":" + z + ":" + load); return solid;
        }
    }

    interface Named { State state(); }
    private static final class State {
        final Graph g; final String name; final int ordinal; final Block[] sides = new Block[6];
        String key; int id, damage;
        State(Graph g, String name, int id, int ordinal) { this.g = g; this.name = name; this.id = id; this.ordinal = ordinal; key = name; }
        void event(String kind) { if (g != null) g.event(kind + ":" + name); }
        Block side(BlockFace face) { if (g != null) g.event("side:" + name + ":" + face.name()); return sides[face.ordinal()]; }
        int hash() { event("hash"); return g != null && g.colliding ? 7 : ordinal; }
        boolean equal(Object other) {
            String target = other instanceof Named n ? n.state().name : "other";
            if (g != null) g.event("equals:" + name + ":" + target);
            return other instanceof Named n && key.equals(n.state().key);
        }
    }
    private static final class Node extends Block implements Named {
        final State s;
        Node(State s) { this.s = s; }
        @Override public State state() { return s; }
        @Override public String getName() { return s.name; }
        @Override public int getId() { s.event("id"); return s.id; }
        @Override public int getDamage() { s.event("damage"); return s.damage; }
        @Override protected Block getSideIfLoadedOrNull(BlockFace face) { return s.side(face); }
        @Override public Block getSide(BlockFace face) { throw new AssertionError("wrong loading accessor"); }
        @Override public int hashCode() { return s.hash(); }
        @Override public boolean equals(Object other) { return s.equal(other); }
    }
    private static final class Root extends BlockLeaves implements Named {
        final State s;
        Root(State s) { this.s = s; }
        @Override public State state() { return s; }
        @Override public int getId() { s.event("id"); return s.id; }
        @Override protected Block getSideIfLoadedOrNull(BlockFace face) { return s.side(face); }
        @Override public Block getSide(BlockFace face) { throw new AssertionError("wrong loading accessor"); }
        @Override public int hashCode() { return s.hash(); }
        @Override public boolean equals(Object other) { return s.equal(other); }
        @Override protected boolean isLeaves(int id) { s.g.event("leaves:" + id); return s.g.custom ? id == 77 : super.isLeaves(id); }
        @Override protected boolean isLog(int id, int damage) {
            s.g.event("log:" + id + ":" + damage); return s.g.custom ? id == 42 && damage == 9 : super.isLog(id, damage);
        }
    }
    private static final class Graph {
        final List<String> trace; final Root root; final Node solid; int sequence = 1;
        boolean colliding, custom; Consumer<String> hook = event -> { }; Search search;
        Graph() { this(new ArrayList<>(), "root"); }
        Graph(List<String> trace, String name) {
            this.trace = trace; solid = new Node(new State(this, "solid", BlockID.STONE, sequence++));
            root = new Root(new State(this, name, BlockID.LEAVES, sequence++)); Arrays.fill(root.s.sides, solid);
        }
        Node node(String name, int id) { Node n = new Node(new State(this, name, id, sequence++)); Arrays.fill(n.s.sides, solid); return n; }
        void event(String event) { trace.add(event); hook.accept(event); }
    }
    private static final class Marker extends RuntimeException { Marker() { super("callback"); } }

    public static void main(String[] args) throws Throwable {
        LeafSearchScratchTest test = new LeafSearchScratchTest();
        test.nullAndLogAtEveryFaceKeepEnumOrderAndEarlyExit();
        test.distanceFourStillExaminesNeighborsAndDistanceFiveIsNotExpanded();
        test.cyclesDuplicatesAndCustomHashEqualsRetainAllCalls();
        test.customPredicatesAndDamageRemainVirtual();
        test.callbackExceptionsHaveTheSamePrefixAndFailure();
        test.reentryFromNeighborHashEqualsAndPredicateHasIndependentScratch();
        test.callbackMutationAndCallerOwnedValuesArrayCannotChangeTraversal();
        test.nativeSideAccessUsesOnlyLoadedChunkAndNeverLoadTrue();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.join("\n", PROOFS).getBytes(StandardCharsets.UTF_8));
        System.out.println("{\"tests\":8,\"cases\":" + PROOFS.size() + ",\"trace_sha256\":\"" + HexFormat.of().formatHex(digest) + "\"}");
    }
}
