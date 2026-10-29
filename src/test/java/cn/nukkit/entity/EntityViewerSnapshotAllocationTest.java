package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import com.sun.management.ThreadMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Detached viewer snapshot contracts; fixture callbacks intentionally do not send packets. */
class EntityViewerSnapshotAllocationTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 8})
    void despawnVisitsCapturedMembersInOrder(int count) throws Exception {
        Fixture f = new Fixture(count);
        int[] expected = f.snapshotCodes(600);
        f.actor.despawnFromAll();
        assertArrayEquals(expected, f.trace.entries());
        assertEquals(count, f.actor.despawns);
    }

    @Test
    void despawnDefersAdditionsAndStillVisitsRemovedSnapshotMember() throws Exception {
        Fixture f = new Fixture(2); Viewer first = f.first(), later = f.other(first), added = f.viewer(3);
        f.actor.onDespawn = p -> {
            if (p == first) { f.actor.hasSpawned.remove(later.number); f.actor.hasSpawned.put(3, added); }
        };
        f.actor.despawnFromAll();
        assertArrayEquals(new int[]{600 + first.number, 600 + later.number}, f.trace.entries());
        assertSame(added, f.actor.hasSpawned.get(3));
    }

    @Test
    void nestedDespawnOwnsItsSnapshotAndOuterTraversalResumes() throws Exception {
        Fixture f = new Fixture(2); Viewer first = f.first(), later = f.other(first), added = f.viewer(3);
        f.actor.onDespawn = p -> {
            if (p == first) {
                f.actor.onDespawn = null;
                f.actor.hasSpawned.clear(); f.actor.hasSpawned.put(3, added);
                f.actor.despawnFromAll();
            }
        };
        f.actor.despawnFromAll();
        assertArrayEquals(new int[]{600 + first.number, 603, 600 + later.number}, f.trace.entries());
    }

    @Test
    void despawnCastsEachRawMemberOnlyWhenItsTurnArrives() throws Exception {
        Fixture f = new Fixture(2); Viewer first = f.first(), later = f.other(first);
        rawPut(f.actor.hasSpawned, later.number, "wrong player");
        assertThrows(ClassCastException.class, f.actor::despawnFromAll);
        assertArrayEquals(new int[]{600 + first.number}, f.trace.entries());
    }

    @Test
    void despawnPropagatesCallbackFailureBeforeLaterMembers() throws Exception {
        Fixture f = new Fixture(2); Viewer first = f.first();
        RuntimeException failure = new IllegalStateException("despawn callback");
        f.actor.onDespawn = p -> { throw failure; };
        assertSame(failure, assertThrows(RuntimeException.class, f.actor::despawnFromAll));
        assertArrayEquals(new int[]{600 + first.number}, f.trace.entries());
    }

    @Test
    void unchangedChunkHasNoLookupOrViewerCallbacks() throws Exception {
        Fixture f = new Fixture(2); f.actor.x = 2; f.actor.crossChunks();
        assertArrayEquals(new int[0], f.trace.entries());
        assertSame(f.chunks[0], f.actor.chunk); assertSame(f.actor, f.chunks[0].registered);
        assertEquals(2, f.actor.x);
    }

    @Test
    void unguardedCrossingPreservesRegistryOrderAndViewerSnapshotMutation() throws Exception {
        Fixture f = new Fixture(2); Viewer added = f.viewer(4); f.world.destinations.put(2, f.viewers[1]);
        f.world.destinations.put(3, f.viewer(3));
        f.actor.onDespawn = p -> {
            assertNull(f.chunks[0].registered); assertNull(f.chunks[1].registered);
            assertSame(f.chunks[1], f.actor.chunk);
            f.actor.hasSpawned.remove(2); f.actor.hasSpawned.put(4, added);
        };
        f.actor.x = 17; f.actor.crossChunks();
        assertArrayEquals(new int[]{100,301,401,501,601,502,502,703,201}, f.trace.entries());
        assertSame(f.actor, f.chunks[1].registered); assertNull(f.chunks[0].registered);
        assertEquals(Collections.singleton(3), f.world.destinations.keySet());
        assertEquals(1, f.viewers[0].loaderReads); assertEquals(2, f.viewers[1].loaderReads);
        assertJustCreatedSkipsViewers();
    }

    @Test
    void crossingWithoutAPreviousChunkStillUsesCapturedViewers() throws Exception {
        Fixture f = new Fixture(2); f.actor.chunk = null; f.chunks[0].registered = null;
        f.actor.x = 17; f.actor.crossChunks();
        assertArrayEquals(new int[]{301,401,501,601,502,602,201}, f.trace.entries());
        assertSame(f.chunks[1], f.actor.chunk); assertSame(f.actor, f.chunks[1].registered);
        assertEquals(2, f.actor.despawns); assertEquals(0, f.actor.spawns);
    }

    private static void assertJustCreatedSkipsViewers() throws Exception {
        Fixture f = new Fixture(2);
        f.actor.justCreated = true; f.actor.x = 17; f.actor.crossChunks();
        assertArrayEquals(new int[]{100,301,201}, f.trace.entries());
        assertEquals(0, f.actor.despawns); assertEquals(0, f.actor.spawns);
        assertSame(f.actor, f.chunks[1].registered);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void nativeDestinationFailuresKeepTheirExistingCallbackBoundary(int failure) throws Exception {
        Fixture f = new Fixture(2); f.world.failure = failure;
        f.actor.x = 17; f.actor.y = 70;
        if (failure == 1) {
            f.actor.crossChunks();
            assertNull(f.actor.chunk);
            assertArrayEquals(new int[]{100,301,401,501,601,502,602}, f.trace.entries());
            assertEquals(2, f.actor.despawns);
        } else {
            IllegalStateException thrown = assertThrows(IllegalStateException.class, f.actor::crossChunks);
            assertEquals(failure == 2 ? "viewers unavailable" : "destination unavailable", thrown.getMessage());
            assertSame(f.chunks[failure == 2 ? 1 : 0], f.actor.chunk);
            assertArrayEquals(failure == 2 ? new int[]{100,301,401} : new int[]{100,301}, f.trace.entries());
            assertEquals(0, f.actor.despawns);
        }
        assertNull(f.chunks[0].registered); assertNull(f.chunks[1].registered);
        assertEquals(17, f.actor.x); assertEquals(70, f.actor.y);
        assertEquals(0, f.actor.spawns);
    }

    @Test
    void reentrantCrossingRetainsOuterViewerAndDestinationSnapshots() throws Exception {
        Fixture f = new Fixture(2); f.world.destinations.put(3, f.viewer(3));
        Viewer added = f.viewer(4);
        f.actor.onDespawn = p -> {
            f.actor.onDespawn = null;
            f.actor.hasSpawned.clear(); f.actor.hasSpawned.put(4, added);
            f.actor.x = 33; f.actor.crossChunks();
        };
        f.actor.x = 17; f.actor.crossChunks();
        assertArrayEquals(new int[]{100,301,401,501,601,101,302,402,504,604,202,502,602,703,202}, f.trace.entries());
        assertSame(f.chunks[2], f.actor.chunk); assertSame(f.actor, f.chunks[2].registered);
        assertEquals(3, f.actor.despawns); assertEquals(1, f.actor.spawns);
    }

    @Test
    void crossingCastsRawMemberAfterEarlierCallbacksAndRegistryChanges() throws Exception {
        Fixture f = new Fixture(2); rawPut(f.actor.hasSpawned, 2, "wrong player");
        f.actor.x = 17;
        assertThrows(ClassCastException.class, f.actor::crossChunks);
        assertArrayEquals(new int[]{100,301,401,501,601}, f.trace.entries());
        assertSame(f.chunks[1], f.actor.chunk); assertNull(f.chunks[0].registered); assertNull(f.chunks[1].registered);
    }

    @Test
    void crossingCallbackFailureStopsLaterCallbacksAndDestinationRegistration() throws Exception {
        Fixture f = new Fixture(2); RuntimeException failure = new IllegalStateException("crossing callback");
        f.actor.onDespawn = p -> { throw failure; }; f.actor.x = 17;
        assertSame(failure, assertThrows(RuntimeException.class, f.actor::crossChunks));
        assertArrayEquals(new int[]{100,301,401,501,601}, f.trace.entries());
        assertSame(f.chunks[1], f.actor.chunk); assertNull(f.chunks[0].registered); assertNull(f.chunks[1].registered);
    }

    @Test
    void inheritedDespawnAvoidsTheSecondSnapshotContainer() throws Exception {
        Path log = Files.createTempFile("entity-viewer-allocation-", ".log");
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-ea", "-Xmx128m", "-XX:-DoEscapeAnalysis", "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                AllocationProbe.class.getName(), "regression", "8")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished;
        try { finished = child.waitFor(60, TimeUnit.SECONDS); }
        finally {
            if (child.isAlive()) { child.destroyForcibly(); assertTrue(child.waitFor(10, TimeUnit.SECONDS), "child did not reap"); }
        }
        assertTrue(Files.size(log) <= 65536, "child output exceeded 64KiB: " + log);
        String output = Files.readString(log, StandardCharsets.UTF_8);
        assertTrue(finished, "child timed out: " + log + "\n" + output);
        assertEquals(0, child.exitValue(), "allocation regression: " + log + "\n" + output);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void rawPut(Map<Integer, Player> map, int key, Object value) { ((Map) map).put(key, value); }

    static final class Trace {
        final int[] codes = new int[1024]; int size; boolean record = true; long checksum;
        void add(int code) { checksum = checksum * 31 + code; if (record) codes[size++] = code; }
        void clear() { size = 0; checksum = 0; }
        int[] entries() { return Arrays.copyOf(codes, size); }
    }

    static final class Actor extends Entity {
        Trace trace; long despawns, spawns; Consumer<Player> onDespawn;
        Actor() { super((FullChunk) null, new CompoundTag()); }
        @Override public int getNetworkId() { return -1; }
        @Override public void despawnFrom(Player p) {
            Viewer viewer = (Viewer) p; trace.add(600 + viewer.number); despawns++;
            if (onDespawn != null) onDespawn.accept(p);
        }
        @Override public void spawnTo(Player p) { Viewer viewer = (Viewer) p; trace.add(700 + viewer.number); spawns++; }
        void crossChunks() { super.checkChunks(); }
    }

    static final class Viewer extends Player {
        int number; Trace trace; long loaderReads;
        Viewer() { super(null, 0L, null); }
        @Override public int getLoaderId() { trace.add(500 + number); loaderReads++; return number; }
    }

    static final class Chunk extends LevelDBChunk {
        int coordinate; Trace trace; Entity registered;
        Chunk() { super(null, 0, 0); }
        @Override public void addEntity(Entity e) { trace.add(200 + coordinate); registered = e; }
        @Override public void removeEntity(Entity e) { trace.add(100 + coordinate); registered = null; }
    }

    static final class World extends Level {
        Trace trace; Chunk[] chunks; int failure;
        Map<Integer, Player> destinations; Map<Integer, Player> nestedDestinations;
        World() { super(null, "viewer-fixture", "", null); }
        @Override public BaseFullChunk getChunk(int x, int z, boolean create) {
            trace.add(300 + x);
            if (failure == 1) return null;
            if (failure == 3) throw new IllegalStateException("destination unavailable");
            return chunks[x];
        }
        @Override public Map<Integer, Player> getChunkPlayers(int x, int z) {
            trace.add(400 + x);
            if (failure == 2) throw new IllegalStateException("viewers unavailable");
            return x == 2 ? nestedDestinations : destinations;
        }
    }

    static final class Fixture {
        final Trace trace = new Trace();
        final Actor actor = allocate(Actor.class);
        final World world = allocate(World.class);
        final Chunk[] chunks = new Chunk[3];
        final Viewer[] viewers;
        Fixture(int count) throws Exception {
            actor.trace = trace; actor.level = world;
            set(Entity.class, actor, "hasSpawned", new ConcurrentHashMap<Integer, Player>());
            actor.boundingBox = new SimpleAxisAlignedBB(0,64,0,1,65,1); actor.scale = 1;
            actor.x = 1; actor.y = 64; actor.z = 1;
            for (int i=0;i<chunks.length;i++) { chunks[i]=allocate(Chunk.class); chunks[i].coordinate=i; chunks[i].setPosition(i,0); chunks[i].trace=trace; }
            actor.chunk = chunks[0]; chunks[0].registered = actor;
            world.trace = trace; world.chunks = chunks; world.destinations = new LinkedHashMap<>();
            world.nestedDestinations = new LinkedHashMap<>();
            viewers = new Viewer[count];
            for (int i=0;i<count;i++) { viewers[i]=viewer(i+1); actor.hasSpawned.put(i+1, viewers[i]); }
        }
        Viewer viewer(int number) { Viewer p=allocate(Viewer.class); p.number=number; p.trace=trace; return p; }
        Viewer first() { return (Viewer) actor.hasSpawned.values().iterator().next(); }
        Viewer other(Viewer first) { return viewers[0] == first ? viewers[1] : viewers[0]; }
        int[] snapshotCodes(int prefix) {
            Object[] values=actor.hasSpawned.values().toArray(); int[] result=new int[values.length];
            for(int i=0;i<values.length;i++) result[i]=prefix+((Viewer)values[i]).number;
            return result;
        }
        void resetCrossing() { actor.chunk=chunks[0]; chunks[0].registered=actor; chunks[1].registered=null; actor.x=17; }
    }

    private static <T> T allocate(Class<T> type) {
        try {
            Class<?> unsafeType=Class.forName("sun.misc.Unsafe"); Field field=unsafeType.getDeclaredField("theUnsafe"); field.setAccessible(true);
            return type.cast(unsafeType.getMethod("allocateInstance",Class.class).invoke(field.get(null),type));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void set(Class<?> owner,Object target,String name,Object value) throws Exception {
        Field f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }

    /** Standalone child: real inherited methods, counter callbacks, no server, Mockito or packet delivery. */
    public static final class AllocationProbe {
        private static volatile long blackhole;
        private static void invoke(Fixture f,String mode,int count) {
            for(int i=0;i<count;i++) {
                if (mode.equals("chunks")) { f.resetCrossing(); f.actor.crossChunks(); }
                else if (mode.equals("control")) {
                    for(Object member:f.actor.hasSpawned.values().toArray()) f.actor.despawnFrom((Player)member);
                } else f.actor.despawnFromAll();
            }
            blackhole=f.trace.checksum;
        }
        private static long measure(ThreadMXBean bean,long tid,Fixture f,String mode,int calls) {
            long before=bean.getThreadAllocatedBytes(tid); invoke(f,mode,calls);
            return bean.getThreadAllocatedBytes(tid)-before;
        }
        public static void main(String[] args) throws Exception {
            String mode=args[0]; int viewers=Integer.parseInt(args[1]); Fixture f=new Fixture(viewers);
            f.trace.record=false; f.world.destinations=Collections.emptyMap();
            if(Actor.class.getMethod("despawnFromAll").getDeclaringClass()!=Entity.class) throw new AssertionError("Not inherited despawn");
            for(Method method:Actor.class.getDeclaredMethods()) if(method.getName().equals("checkChunks")) throw new AssertionError("Overridden checkChunks");
            ThreadMXBean bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();
            if(!bean.isThreadAllocatedMemorySupported()) throw new AssertionError("No allocation counter");
            if(!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);
            long tid=Thread.currentThread().getId();
            System.out.println("java.version="+System.getProperty("java.version"));
            System.out.println("java.vm.version="+System.getProperty("java.vm.version"));
            System.out.println("core.location="+Entity.class.getProtectionDomain().getCodeSource().getLocation());
            try(var input=Entity.class.getResourceAsStream("/cn/nukkit/entity/Entity.class")) {
                if(input==null)throw new IOException("Missing Entity class resource");
                System.out.println("Entity.classSHA256="+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
            }
            System.out.println("fixture=inherited Entity methods; counter-only despawn/spawn, Level/chunk callbacks; no network or full-world tick");
            if(mode.equals("regression")) {
                invoke(f,"despawn",300000);invoke(f,"chunks",300000);invoke(f,"control",300000);
                long maxExcess=0;
                for(String actualMode:new String[]{"despawn","chunks"}) {
                    for(int window=0;window<3;window++) {
                        long callbacksBefore=f.actor.despawns;
                        long actual=measure(bean,tid,f,actualMode,100000), control=measure(bean,tid,f,"control",100000);
                        if(f.actor.despawns-callbacksBefore!=(long)viewers*200000)throw new AssertionError("Lost regression callback");
                        long excess=actual-control;maxExcess=Math.max(maxExcess,excess);
                        System.out.println("ALLOC,"+actualMode+","+window+",actual="+actual+",control="+control+",excess="+excess);
                    }
                }
                if(maxExcess>1600000)throw new AssertionError("Extra snapshot container exceeds16 B/call: "+maxExcess/100000.0);
                return;
            }
            if(!mode.equals("despawn")&&!mode.equals("chunks"))throw new IllegalArgumentException(mode);
            f.trace.record=true;f.trace.clear();invoke(f,mode,1);
            long perCallHash=f.trace.checksum,power=1;
            for(int i=0;i<f.trace.size;i++)power*=31;
            f.trace.record=false;f.trace.clear();
            invoke(f,mode,1000000);bean.getThreadAllocatedBytes(tid);
            for(int window=0;window<7;window++) {
                long checksumBefore=f.trace.checksum;
                long before=f.actor.despawns; long bytes=measure(bean,tid,f,mode,100000);
                long expectedChecksum=checksumBefore;
                for(int i=0;i<100000;i++)expectedChecksum=expectedChecksum*power+perCallHash;
                if(f.trace.checksum!=expectedChecksum)throw new AssertionError("Changed callback order checksum");
                long callbacks=f.actor.despawns-before;
                if(callbacks!=(long)viewers*100000)throw new AssertionError("Callback count: "+callbacks);
                if(mode.equals("chunks")&&(f.actor.chunk!=f.chunks[1]||f.chunks[0].registered!=null||f.chunks[1].registered!=f.actor))throw new AssertionError("Wrong registry state");
                System.out.println("MEASURE,"+mode+","+viewers+","+window+",100000,"+callbacks+","+bytes+","+(bytes/100000.0));
            }
            System.out.println("blackhole="+blackhole);
        }
    }
}
