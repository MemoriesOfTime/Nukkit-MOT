package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Factory-contract controls in an isolated JVM; only registry slot 55 is temporarily replaced. */
@Isolated("Temporarily replaces the wire prototype slot; restores exact references")
class BlockRedstoneWirePrototypeP16Test {
    private static Snapshot initialRegistry;
    @BeforeAll static void init() throws Exception { MockServer.init(); initialRegistry=new Snapshot(); }
    @AfterAll static void verifyFinalRegistry() { initialRegistry.assertRestored(); }

    enum Query {
        INDIRECT("getIndirectPower",11), STRONG("getStrongPower",13);
        final String method; final int expectedPower;
        Query(String method,int expectedPower) { this.method=method; this.expectedPower=expectedPower; }
    }

    @ParameterizedTest @EnumSource(Query.class)
    void defaultCloneSideEffectRunsBeforeWireExclusion(Query query) throws Exception {
        Snapshot original=new Snapshot(); ProbePrototype prototype=new ProbePrototype(0,() -> new BlockRedstoneWire());
        try (RegistryScope scope=new RegistryScope(original)) {
            scope.defaults[Block.REDSTONE_WIRE]=prototype;
            assertEquals(0,new Fixture().query(query)); assertEquals(1,prototype.clones);
        }
        original.assertRestored();
    }

    @ParameterizedTest @EnumSource(Query.class)
    void cachedCloneSideEffectCannotBeSkippedUsingPublicClassList(Query query) throws Exception {
        Snapshot original=new Snapshot(); ProbePrototype prototype=new ProbePrototype(7,() -> new BlockRedstoneWire(7));
        try (RegistryScope scope=new RegistryScope(original)) {
            scope.cache.put(7,prototype);
            assertSame(BlockRedstoneWire.class,Block.list[Block.REDSTONE_WIRE]);
            assertEquals(BlockRedstoneWire.class,scope.defaults[Block.REDSTONE_WIRE].getClass());
            Fixture fixture=new Fixture(); fixture.meta=7;
            assertEquals(0,fixture.query(query)); assertEquals(1,prototype.clones);
        }
        original.assertRestored();
    }

    @ParameterizedTest @EnumSource(Query.class)
    void cachedPrototypeCloneReturnedIdControlsPowerInsteadOfRawId(Query query) throws Exception {
        Snapshot original=new Snapshot(); ProbePrototype prototype=new ProbePrototype(7,() -> new PowerResult(7));
        try (RegistryScope scope=new RegistryScope(original)) {
            scope.cache.put(7,prototype); Fixture fixture=new Fixture(); fixture.meta=7;
            assertEquals(Block.REDSTONE_WIRE,prototype.getId());
            assertSame(BlockRedstoneWire.class,Block.list[Block.REDSTONE_WIRE]);
            assertEquals(query.expectedPower,fixture.query(query));
            assertEquals(Block.STONE,fixture.materialized.get(0).getId()); assertEquals(1,prototype.clones);
        }
        original.assertRestored();
    }

    @ParameterizedTest @EnumSource(Query.class)
    void defaultPrototypeCloneMayReturnAnotherId(Query query) throws Exception {
        Snapshot original=new Snapshot(); ProbePrototype prototype=new ProbePrototype(0,() -> new PowerResult(0));
        try (RegistryScope scope=new RegistryScope(original)) {
            scope.defaults[Block.REDSTONE_WIRE]=prototype;
            assertEquals(query.expectedPower,new Fixture().query(query)); assertEquals(1,prototype.clones);
        }
        original.assertRestored();
    }

    @ParameterizedTest @EnumSource(Query.class)
    void cloneFailurePropagatesAndRegistryIsRestored(Query query) throws Exception {
        Snapshot original=new Snapshot(); IllegalArgumentException failure=new IllegalArgumentException("prototype clone failure");
        ProbePrototype prototype=new ProbePrototype(0,() -> { throw failure; });
        InvocationTargetException thrown=assertThrows(InvocationTargetException.class,() -> {
            try (RegistryScope scope=new RegistryScope(original)) {
                scope.defaults[Block.REDSTONE_WIRE]=prototype; new Fixture().query(query);
            }
        });
        assertSame(failure,thrown.getCause()); assertEquals(1,prototype.clones); original.assertRestored();
    }

    @ParameterizedTest @EnumSource(Query.class)
    void lazyStateConstructorRunsOnceButCloneRunsPerLookup(Query query) throws Exception {
        Snapshot original=new Snapshot(); LazyPrototype.constructors=LazyPrototype.clones=0;
        try (RegistryScope scope=new RegistryScope(original)) {
            scope.constructors[Block.REDSTONE_WIRE]=LazyPrototype.class.getConstructor(int.class);
            Fixture fixture=new Fixture(); fixture.meta=7;
            assertFalse(scope.cache.containsKey(7));
            assertEquals(0,fixture.query(query)); assertEquals(0,fixture.query(query));
            assertEquals(1,LazyPrototype.constructors); assertEquals(2,LazyPrototype.clones);
            assertInstanceOf(LazyPrototype.class,scope.cache.get(7));
            assertNull(scope.cache.get(7).getLevel()); assertEquals(0,scope.cache.get(7).x);
        }
        original.assertRestored();
    }

    @ParameterizedTest @EnumSource(Query.class)
    void rejectedStateConstructorStillRunsBeforeUnknownWireIsExcluded(Query query) throws Exception {
        Snapshot original=new Snapshot(); RejectedPrototype.constructors=0;
        try (RegistryScope scope=new RegistryScope(original)) {
            scope.constructors[Block.REDSTONE_WIRE]=RejectedPrototype.class.getConstructor(int.class);
            Fixture fixture=new Fixture(); fixture.meta=7;
            assertEquals(0,fixture.query(query)); assertEquals(1,RejectedPrototype.constructors);
            assertInstanceOf(BlockUnknown.class,fixture.materialized.get(0)); assertFalse(scope.cache.containsKey(7));
        }
        original.assertRestored();
    }

    @ParameterizedTest @EnumSource(Query.class)
    void factoryAssignsContextAndPreservesCloneMetadataBeforePower(Query query) throws Exception {
        Snapshot original=new Snapshot(); PowerResult result=new PowerResult(3);
        ProbePrototype prototype=new ProbePrototype(7,() -> result);
        try (RegistryScope scope=new RegistryScope(original)) {
            scope.cache.put(7,prototype); Fixture fixture=new Fixture(); fixture.meta=7;
            assertEquals(query.expectedPower,fixture.query(query)); assertSame(fixture.first,result.getLevel());
            assertEquals(-1,result.x); assertEquals(64,result.y); assertEquals(16,result.z); assertEquals(0,result.layer);
            assertEquals(3,result.getDamage()); assertNull(prototype.getLevel()); assertEquals(0,prototype.x);
            assertSame(fixture.first,result.observedLevel); assertEquals(3,result.observedMeta); assertEquals(0,result.observedLayer);
            assertEquals(-1,result.observedX); assertEquals(64,result.observedY); assertEquals(16,result.observedZ);
        }
        original.assertRestored();
    }

    @ParameterizedTest @EnumSource(Query.class)
    void outOfRangeMetadataIsAssignedAfterDefaultCloneBeforePower(Query query) throws Exception {
        for (int requested : new int[]{-1,Block.DATA_SIZE+17}) {
            Snapshot original=new Snapshot(); PowerResult result=new PowerResult(3);
            ProbePrototype prototype=new ProbePrototype(0,() -> result);
            try (RegistryScope scope=new RegistryScope(original)) {
                scope.defaults[Block.REDSTONE_WIRE]=prototype; Fixture fixture=new Fixture(); fixture.meta=requested;
                assertEquals(query.expectedPower,fixture.query(query)); assertEquals(1,prototype.clones);
                assertEquals(requested,result.getDamage()); assertEquals(requested,result.observedMeta);
                assertSame(fixture.first,result.observedLevel); assertEquals(0,result.observedLayer);
            }
            original.assertRestored();
        }
    }

    @ParameterizedTest @EnumSource(Query.class)
    void canonicalWireStillContributesZero(Query query) throws Exception {
        Snapshot original=new Snapshot();
        try (RegistryScope ignored=new RegistryScope(original)) { assertEquals(0,new Fixture().query(query)); }
        original.assertRestored();
    }

    @Test void nestedCloneHookKeepsOriginSnapshotAndLiveReceiverLevel() throws Exception {
        Snapshot original=new Snapshot(); Fixture fixture=new Fixture();
        fixture.wire.position(new Position(2,64,3,fixture.first));
        ProbePrototype prototype=new ProbePrototype(0,() -> new BlockRedstoneWire());
        List<String> order=new ArrayList<>();
        prototype.hook=() -> {
            int sequence=prototype.clones; order.add("enter"+sequence);
            if (sequence==1) {
                try { assertEquals(0,fixture.query(Query.STRONG)); }
                catch (Exception failure) { throw new RuntimeException(failure); }
                fixture.wire.position(new Position(900,80,900,fixture.second));
            }
            order.add("exit"+sequence);
        };
        try (RegistryScope scope=new RegistryScope(original)) {
            scope.defaults[Block.REDSTONE_WIRE]=prototype;
            Method method=BlockRedstoneWire.class.getDeclaredMethod("getIndirectPower"); method.setAccessible(true);
            assertEquals(0,method.invoke(fixture.wire)); assertEquals(7,prototype.clones);
            assertEquals(List.of("enter1","enter2","exit2","exit1"),order.subList(0,4));
            assertEquals(7,fixture.reads.size()); assertSame(fixture.first,fixture.receivers.get(0));
            assertSame(fixture.first,fixture.receivers.get(1));
            List<Vector3> outer=new ArrayList<>(); outer.add(fixture.reads.get(0)); outer.addAll(fixture.reads.subList(2,7));
            Position snapshot=new Position(2,64,3,fixture.first);
            for (int i=0;i<6;i++) {
                Vector3 expected=snapshot.getSide(BlockFace.values()[i]),actual=outer.get(i);
                assertEquals(expected.x,actual.x); assertEquals(expected.y,actual.y); assertEquals(expected.z,actual.z);
                assertSame(fixture.first,((Position)actual).getLevel());
                if (i>0) assertSame(fixture.second,fixture.receivers.get(i+1));
            }
        }
        original.assertRestored();
    }

    @Test void partialScopeInstallationFailureRestoresAllSixSlotReferences() throws Exception {
        Snapshot original=new Snapshot(); IllegalStateException failure=new IllegalStateException("scope installation failure");
        assertSame(failure,assertThrows(IllegalStateException.class,() -> new RegistryScope(original,() -> { throw failure; })));
        original.assertRestored();
    }

    private static final class Fixture {
        final Level first=mock(Level.class),second=mock(Level.class);
        final BlockRedstoneWire wire=new BlockRedstoneWire();
        final List<Vector3> reads=new ArrayList<>(); final List<Level> receivers=new ArrayList<>();
        final List<Block> materialized=new ArrayList<>(); int meta;
        Fixture() {
            wire.position(new Position(0,64,0,first));
            for (Level level : List.of(first,second)) {
                when(level.getBlock(any(Vector3.class))).thenAnswer(call -> {
                    Vector3 pos=call.getArgument(0); reads.add(pos); receivers.add(level);
                    Block result=Block.get(Block.REDSTONE_WIRE,meta,level,pos.getFloorX(),pos.getFloorY(),pos.getFloorZ(),0);
                    materialized.add(result); return result;
                });
                when(level.getBlockIdAt(anyInt(),anyInt(),anyInt())).thenReturn(Block.REDSTONE_WIRE);
            }
        }
        int query(Query query) throws Exception {
            Method method=BlockRedstoneWire.class.getDeclaredMethod(query.method,Vector3.class,BlockFace.class); method.setAccessible(true);
            return (int)method.invoke(wire,new Position(-0.1,64.75,16.1,first),BlockFace.EAST);
        }
    }

    public static class ProbePrototype extends BlockRedstoneWire {
        final Supplier<Block> result; int clones; Runnable hook=() -> {};
        ProbePrototype(int meta,Supplier<Block> result) { super(meta); this.result=result; }
        @Override public Block clone() { clones++; hook.run(); return result.get(); }
    }

    public static class LazyPrototype extends BlockRedstoneWire {
        static int constructors,clones;
        public LazyPrototype(int meta) { super(meta); constructors++; }
        @Override public Block clone() { clones++; return new BlockRedstoneWire(getDamage()); }
    }

    public static class RejectedPrototype extends BlockRedstoneWire {
        static int constructors;
        public RejectedPrototype(int meta) { super(meta+1); constructors++; }
    }

    public static class PowerResult extends BlockStone {
        Level observedLevel; int observedMeta,observedLayer; double observedX,observedY,observedZ;
        PowerResult(int meta) { super(meta); }
        @Override public boolean isNormalBlock() { return false; }
        private void observe() {
            observedLevel=getLevel(); observedMeta=getDamage(); observedLayer=layer;
            observedX=x; observedY=y; observedZ=z;
        }
        @Override public int getWeakPower(BlockFace face) { observe(); return 11; }
        @Override public int getStrongPower(BlockFace face) { observe(); return 13; }
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static final class Snapshot {
        final Class[] classes=Block.list; final boolean[] hasMeta=Block.hasMeta;
        final Block[] defaults; final Constructor[] constructors;
        final Int2ObjectOpenHashMap<Block>[] caches; final Object[] locks;
        final Class originalClass; final boolean originalHasMeta; final Block originalDefault;
        final Constructor originalConstructor; final Int2ObjectOpenHashMap<Block> originalCache; final Object originalLock;
        Snapshot() throws Exception {
            defaults=(Block[])field("defaultStateList"); constructors=(Constructor[])field("metaConstructors");
            caches=(Int2ObjectOpenHashMap<Block>[])field("statePrototypes"); locks=(Object[])field("statePrototypeLocks");
            int id=Block.REDSTONE_WIRE; originalClass=classes[id]; originalHasMeta=hasMeta[id]; originalDefault=defaults[id];
            originalConstructor=constructors[id]; originalCache=caches[id]; originalLock=locks[id];
        }
        private static Object field(String name) throws Exception { Field field=Block.class.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
        void restore() {
            int id=Block.REDSTONE_WIRE; classes[id]=originalClass; hasMeta[id]=originalHasMeta; defaults[id]=originalDefault;
            constructors[id]=originalConstructor; caches[id]=originalCache; locks[id]=originalLock;
        }
        void assertRestored() {
            int id=Block.REDSTONE_WIRE; assertSame(originalClass,classes[id]); assertEquals(originalHasMeta,hasMeta[id]);
            assertSame(originalDefault,defaults[id]); assertSame(originalConstructor,constructors[id]);
            assertSame(originalCache,caches[id]); assertSame(originalLock,locks[id]);
        }
    }

    private static final class RegistryScope implements AutoCloseable {
        final Snapshot original; final Block[] defaults; final Constructor[] constructors;
        final Int2ObjectOpenHashMap<Block> cache=new Int2ObjectOpenHashMap<>();
        RegistryScope(Snapshot original) throws Exception { this(original,() -> {}); }
        RegistryScope(Snapshot original,Runnable afterPartialInstall) throws Exception {
            this.original=original; defaults=original.defaults; constructors=original.constructors;
            Constructor<?> constructor=BlockRedstoneWire.class.getConstructor(int.class);
            Block canonical=new BlockRedstoneWire(); Object lock=new Object(); int id=Block.REDSTONE_WIRE;
            try {
                original.classes[id]=BlockRedstoneWire.class; original.hasMeta[id]=true; defaults[id]=canonical;
                constructors[id]=constructor; original.caches[id]=cache;
                afterPartialInstall.run(); original.locks[id]=lock;
            } catch (RuntimeException | Error failure) { original.restore(); throw failure; }
        }
        @Override public void close() { original.restore(); original.assertRestored(); }
    }
}
