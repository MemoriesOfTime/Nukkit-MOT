package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.nbt.tag.CompoundTag;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.WriteBatch;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Provider fence and ACK tests; native inventory callers and process recovery have separate tests. */
class ContainerChunkSaveGroupsTest {
    @TempDir Path directory;
    Level level;
    LevelDBProvider provider;
    DB disk, observed;
    final AtomicInteger writes = new AtomicInteger();
    @BeforeAll static void server() { MockServer.init(); Server.getInstance().useNativeLevelDB = false; Server.getInstance().levelDbCache = 8; }
    @BeforeEach void setup() throws Exception {
        Server.getInstance().asyncChunkSending = true;
        LevelDBProvider.generate(directory.toString(), "container-group", 404L, Flat.class);
        level = mock(Level.class);
        when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
        when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD); when(level.getServer()).thenReturn(Server.getInstance());
        provider = new LevelDBProvider(level, directory.toString());
        when(level.getProvider()).thenReturn(provider); when(level.requireProvider()).thenReturn(provider);
        disk = provider.getDatabase(); observed = spy(disk);
        doAnswer(call -> { writes.incrementAndGet(); disk.write(call.getArgument(0)); return null; }).when(observed).write(any(WriteBatch.class));
        Field field = LevelDBProvider.class.getDeclaredField("db"); field.setAccessible(true); field.set(provider, observed);
    }
    @AfterEach void close() throws Exception { provider.close(); Field f=BlockEntity.class.getDeclaredField("knownBlockEntities");f.setAccessible(true); if(f.get(null) instanceof java.util.Map<?,?> registry && registry.get("CaptureCallbackTest")==CaptureCallbackBlockEntity.class) registry.remove("CaptureCallbackTest"); }
    ContainerChunkSaveGroups groups() throws Exception { Field f=LevelDBProvider.class.getDeclaredField("containerGroups");f.setAccessible(true);return (ContainerChunkSaveGroups)f.get(provider); }
    LevelDBChunk chunk(int x) { LevelDBChunk c=provider.getEmptyChunk(x,0);c.setGenerated(true);provider.setChunk(x,0,c);c.setBlock(0,64,0,1);return c; }
    void accept(LevelDBChunk... chunks) { var scope=provider.beginContainerTransfer(List.of(chunks));assertNotNull(scope);scope.accepted();scope.close(); }
    void await(LevelDBChunk chunk) throws Exception { provider.saveChunkFuture(chunk.getX(),0,chunk).get(10,TimeUnit.SECONDS); }

    @Test void captureAndWriteOneTransitiveGroupWithoutAdmissionIo() throws Exception {
        LevelDBChunk a=chunk(0), b=chunk(1), c=chunk(2);
        accept(a,b); accept(b,c);
        assertEquals(0,writes.get()); assertEquals(3,provider.containerCaptureCost(Level.chunkHash(0,0)));
        await(c);
        assertEquals(1,writes.get(),"A-B-C must share one native batch");
        for(int i=0;i<3;i++)assertNotNull(disk.get(LevelDBKey.VERSION.getKey(i,0,0)));
        assertFalse(a.hasChanged()); assertFalse(b.hasChanged()); assertFalse(c.hasChanged());
    }

    @Test void boundedAutosaveDoesNotStageContainerMembersIndependently() throws Exception {
        LevelDBChunk a = chunk(0), b = chunk(1);
        accept(a, b);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(call -> {
            writes.incrementAndGet();
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            disk.write(call.getArgument(0));
            return null;
        }).when(observed).write(any(WriteBatch.class));
        CompletableFuture<Void> ack;
        try {
            provider.saveChunks();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            ack = provider.saveChunkFuture(a.getX(), a.getZ(), a);
            assertFalse(ack.isDone());
            assertEquals(0, provider.getPendingWriteCount(), "members must not enter standalone autosave slots");
            assertTrue(provider.deferContainerChunkDetach(b.getX(), b.getZ()));
        } finally {
            release.countDown();
        }
        ack.get(10, TimeUnit.SECONDS);
        assertEquals(1, writes.get(), "bulk autosave must keep the container group in one DB write");
        assertNotNull(disk.get(LevelDBKey.VERSION.getKey(0, 0, 0)));
        assertNotNull(disk.get(LevelDBKey.VERSION.getKey(1, 0, 0)));
        assertFalse(a.hasChanged());
        assertFalse(b.hasChanged());
    }

    @Test void preparingStandaloneSaveRejectsAdmissionWithoutDiskWait() throws Exception {
        LevelDBChunk a=chunk(0), b=chunk(1);
        try(AutoCloseable reservation=groups().reserveStandalone(Level.chunkHash(0,0))) {
            assertNull(provider.beginContainerTransfer(List.of(a,b)));
            assertEquals(0,writes.get());
        }
        accept(a,b); await(a); assertEquals(1,writes.get());
    }

    @Test void earlierInflightStandaloneMustAckBeforeAdmission() throws Exception {
        LevelDBChunk a=chunk(0), b=chunk(1);
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        doAnswer(call -> {entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));disk.write(call.getArgument(0));return null;}).when(observed).write(any(WriteBatch.class));
        CompletableFuture<Void> earlier=provider.saveChunkFuture(0,0,a);
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertTimeout(java.time.Duration.ofMillis(250),()->assertNull(provider.beginContainerTransfer(List.of(a,b))));
            assertFalse(earlier.isDone());
        } finally {release.countDown();}
        earlier.get(10,TimeUnit.SECONDS); accept(a,b);await(b);
    }

    @Test void noHalfPairSaveOrLifecycleCloseDuringProvisionalMutation() throws Exception {
        LevelDBChunk a=chunk(0), b=chunk(1);
        var scope=provider.beginContainerTransfer(List.of(a,b));assertNotNull(scope);
        try {
            assertTrue(provider.saveChunkFuture(0,0,a).isCompletedExceptionally());
            assertThrows(DBException.class,provider::close);
            assertTrue(provider.isContainerLifecycleBlocked());doCallRealMethod().when(level).close();assertThrows(IllegalStateException.class,level::close);
            assertEquals(0,writes.get()); assertSame(level,provider.getLevel());
            assertFalse(a.unload(true,false));
        } finally {scope.close();}
        assertFalse(provider.isContainerLifecycleBlocked()); accept(a,b);await(a);
    }

    static final class CaptureCallbackBlockEntity extends BlockEntity {
        private final Runnable check;
        CaptureCallbackBlockEntity(LevelDBChunk chunk, Runnable check) { super(chunk,new CompoundTag().putInt("x",0).putInt("y",64).putInt("z",0));this.check=check; }
        @Override public boolean isBlockEntityValid() {return true;}
        @Override public void saveNBT() {check.run();super.saveNBT();}
    }

    @Test void captureCallbackCannotCloseOrReadHalfPair() throws Exception {
        LevelDBChunk a=chunk(0), b=chunk(1);
        AtomicBoolean seen=new AtomicBoolean();
        BlockEntity.registerBlockEntity("CaptureCallbackTest",CaptureCallbackBlockEntity.class);
        new CaptureCallbackBlockEntity(a,()-> {
            if(provider.isContainerLifecycleBlocked()) {
                seen.set(true);assertTrue(provider.saveChunkFuture(1,0,b).isCompletedExceptionally());
                assertThrows(DBException.class,provider::close);doCallRealMethod().when(level).close();assertThrows(IllegalStateException.class,level::close);assertSame(level,provider.getLevel());
            }
        });
        accept(a,b);await(a);assertTrue(seen.get());assertEquals(1,writes.get());
    }

    @Test void capturedAckCannotAcknowledgeNewerLocalRevision() throws Exception {
        LevelDBChunk a=chunk(0), b=chunk(1);accept(a,b);
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));disk.write(call.getArgument(0));return null;}).when(observed).write(any(WriteBatch.class));
        CompletableFuture<Void> old=provider.saveChunkFuture(0,0,a);
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));a.setBlock(1,64,0,3);
            assertTrue(provider.saveChunkFuture(0,0,a).isCompletedExceptionally());
            assertNull(provider.beginContainerTransfer(List.of(a,b)));
            assertFalse(provider.unloadChunk(0,0,false));
            assertThrows(DBException.class,()->provider.setChunk(0,0,provider.getEmptyChunk(0,0)));
            assertThrows(DBException.class,()->provider.saveChunkFuture(1,0,a));assertEquals(0,a.getX());
            assertSame(a,provider.getChunk(0,0,false));
        } finally {release.countDown();}
        old.get(10,TimeUnit.SECONDS); assertTrue(a.hasChanged());assertFalse(b.hasChanged());await(a);assertFalse(a.hasChanged());
    }

    @Test void unknownAckRetainsWholeGroupAndRetriesWholeBatch() throws Exception {
        LevelDBChunk a=chunk(0), b=chunk(1);groups().retryMillis=0;accept(a,b);
        AtomicBoolean fail=new AtomicBoolean(true);
        doAnswer(call->{writes.incrementAndGet();disk.write(call.getArgument(0));if(fail.get())throw new DBException("injected lost ACK after atomic native write");return null;}).when(observed).write(any(WriteBatch.class));
        assertThrows(ExecutionException.class,()->await(a));assertEquals(3,writes.get());
        assertNull(provider.beginContainerTransfer(List.of(a,b)));
        assertThrows(CancellationException.class,()->provider.openChunkRead(0,0));assertTrue(a.hasChanged());assertTrue(b.hasChanged());
        fail.set(false);provider.maintainContainerGroups();await(b);assertEquals(4,writes.get());
        assertFalse(a.hasChanged());assertFalse(b.hasChanged());
    }

    @Test void groupCapFlushesThenAdmitsBridgeWithoutIndependentSave() throws Exception {
        LevelDBChunk a=chunk(0), b=chunk(1), c=chunk(2);groups().maxChunks=2;accept(a,b);
        assertNull(provider.beginContainerTransfer(List.of(b,c)));assertEquals(0,writes.get());
        provider.maintainContainerGroups();await(a);assertEquals(1,writes.get());
        accept(b,c);await(c);assertEquals(2,writes.get());
    }

    @Test void cancellingOneCallerCannotCancelAnotherGroupAck() throws Exception {
        LevelDBChunk a=chunk(0),b=chunk(1);accept(a,b);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));disk.write(call.getArgument(0));return null;}).when(observed).write(any(WriteBatch.class));
        CompletableFuture<Void> first=provider.saveChunkFuture(0,0,a);
        try {assertTrue(entered.await(5,TimeUnit.SECONDS));assertTrue(first.cancel(false));}
        finally {release.countDown();}
        await(b);assertFalse(a.hasChanged());assertFalse(b.hasChanged());
    }

    @Test void globalGroupLimitFlushesAndRetries() throws Exception {
        LevelDBChunk a=chunk(0),b=chunk(1),c=chunk(2),d=chunk(3);groups().maxGroups=1;accept(a,b);
        assertNull(provider.beginContainerTransfer(List.of(c,d)));assertEquals(0,writes.get());
        provider.maintainContainerGroups();await(a);accept(c,d);await(d);assertEquals(2,writes.get());
    }

    @Test void oldReadTicketRemainsInvalidAfterGroupRetires() throws Exception {
        LevelDBChunk a=chunk(0), b=chunk(1);await(a);await(b);
        var ticket=provider.openChunkRead(0,0);accept(a,b);await(a);
        assertThrows(CancellationException.class,ticket::read);ticket.close();
    }

    @Test void sharedActorCleanupKeepsMovedActors() throws Exception {
        LevelDBChunk a=chunk(0), b=chunk(1);
        byte[] key=new byte[]{1,2,3,4,5,6,7,8}, opaque={10,11,12};
        a.setPreservedEntityActors(List.of(new LevelDBChunk.PreservedEntityActor(key,opaque)));await(a);await(b);
        accept(a,b);a.setPreservedEntityActors(List.of());b.setPreservedEntityActors(List.of(new LevelDBChunk.PreservedEntityActor(key,opaque)));
        a.setChanged();b.setChanged();await(b);
        byte[] actorKey=new byte[LevelDBKey.ACTOR_PREFIX.length+key.length];
        System.arraycopy(LevelDBKey.ACTOR_PREFIX,0,actorKey,0,LevelDBKey.ACTOR_PREFIX.length);
        System.arraycopy(key,0,actorKey,LevelDBKey.ACTOR_PREFIX.length,key.length);
        assertArrayEquals(opaque,disk.get(actorKey),"source cleanup must retain destination actor write");
    }
}
