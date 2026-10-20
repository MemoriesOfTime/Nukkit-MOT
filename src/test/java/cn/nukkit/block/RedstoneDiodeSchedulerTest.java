package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedstoneDiodeSchedulerTest {
    @Test
    void futureQueueIsDistinctFromActiveBucketAndDoesNotRescanForAllDelays() throws Exception {
        for (int meta : new int[]{0,4,8,12}) {
            Fixture f = new Fixture(meta);
            f.live.updateState();
            assertEquals(1, f.pendingCount());
            assertTrue(f.level.isUpdateScheduled(f.live, f.live));
            assertFalse(f.level.isBlockTickPending(f.live, f.live), "future queue is not the active bucket");
            f.reads.set(0); f.powerReads.set(0);
            for (int n=0;n<100;n++) f.live.updateState();
            assertEquals(0, f.reads.get());
            assertEquals(0, f.powerReads.get());
            assertEquals(1, f.pendingCount());
            int delay=(1+(meta>>2))<<1;
            f.checkActive=true;
            f.tick(delay-1);
            assertFalse(f.live.isPowered());
            f.tick(delay);
            assertTrue(f.live.isPowered());
            assertEquals(List.of(delay+":on"),f.transitions);
            assertEquals(0,f.pendingCount());
            assertEquals(1,f.activeProbes.get());
        }
    }

    @Test
    void activeBucketGuardStillAppliesAfterRemovalFromFutureIndex() throws Exception {
        Fixture f=new Fixture(0);
        f.live.updateState();
        f.checkActive=true;
        f.tick(2);
        assertTrue(f.live.isPowered());
        assertEquals(1,f.activeProbes.get());
        assertEquals(0,f.pendingCount());
    }

    @Test
    void shortPulseKeepsPowerOnAndOffDeadlinesAndSamplesFreshInput() throws Exception {
        for (int meta : new int[]{0,4,8,12}) {
            Fixture f=new Fixture(meta);
            int delay=(1+(meta>>2))<<1;
            f.live.updateState();
            f.signal=0;
            f.tick(delay);
            assertTrue(f.live.isPowered(),"existing scheduled handler preserves short pulse");
            assertEquals(1,f.pendingCount());
            assertTrue(f.level.isUpdateScheduled(f.live,f.live));
            f.tick(delay*2);
            assertFalse(f.live.isPowered());
            assertEquals(List.of(delay+":on",(delay*2)+":off"),f.transitions);
            assertEquals(0,f.pendingCount());
        }
    }

    @Test
    void lockAtDeadlinePreventsPowerOnAndUnlockSchedulesFreshTick() throws Exception {
        Fixture f=new Fixture(0);
        f.live.updateState();
        f.locked=true;
        f.tick(2);
        assertFalse(f.live.isPowered());
        assertEquals(0,f.pendingCount());
        f.locked=false;
        f.live.updateState();
        assertEquals(1,f.pendingCount());
        f.tick(4);
        assertTrue(f.live.isPowered());
        assertEquals(List.of("4:on"),f.transitions);
    }

    private static final class TestRepeater extends BlockRedstoneRepeaterUnpowered {
        final Fixture fixture;
        TestRepeater(Fixture fixture,int meta,boolean powered) {
            super(meta); this.fixture=fixture; this.isPowered=powered;
        }
        @Override public int getId() {return isPowered ? POWERED_REPEATER : UNPOWERED_REPEATER;}
        @Override protected Block getPowered() {return new TestRepeater(fixture,getDamage(),true);}
        @Override protected Block getUnpowered() {return new TestRepeater(fixture,getDamage(),false);}
        @Override public boolean isLocked() {return fixture.locked || super.isLocked();}
        @Override public int onUpdate(int type) {
            if(type==Level.BLOCK_UPDATE_SCHEDULED && fixture.checkActive) {
                assertTrue(level.isBlockTickPending(this,this),"real scheduler exposes active bucket");
                assertFalse(level.isUpdateScheduled(this,this),"scheduler already removed this bucket from future index");
                int reads=fixture.reads.get(),power=fixture.powerReads.get();
                updateState();
                assertEquals(reads,fixture.reads.get(),"active bucket update must not scan blocks");
                assertEquals(power,fixture.powerReads.get(),"active bucket update must not scan input");
                fixture.activeProbes.incrementAndGet();
            }
            return super.onUpdate(type);
        }
    }

    private static final class Fixture {
        final Level level=mock(Level.class);
        final BlockUpdateScheduler scheduler=new BlockUpdateScheduler(level,0);
        final AtomicInteger reads=new AtomicInteger(),powerReads=new AtomicInteger(),activeProbes=new AtomicInteger();
        final List<String> transitions=new ArrayList<>();
        TestRepeater live;
        int signal=10,tick;
        boolean locked,checkActive;
        Fixture(int meta)throws Exception {
            setField("updateQueue",scheduler);
            live=place(new TestRepeater(this,meta,false));
            when(level.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            when(level.isAreaLoaded(any(AxisAlignedBB.class))).thenReturn(true);
            when(level.getMinBlockY()).thenReturn(0);
            when(level.getMaxBlockY()).thenReturn(255);
            doCallRealMethod().when(level).scheduleUpdate(any(Block.class),any(Vector3.class),anyInt());
            doCallRealMethod().when(level).scheduleUpdate(any(Block.class),any(Vector3.class),anyInt(),anyInt(),anyBoolean());
            doCallRealMethod().when(level).isBlockTickPending(any(Vector3.class),any(Block.class));
            doCallRealMethod().when(level).isUpdateScheduled(any(Vector3.class),any(Block.class));
            doAnswer(i->{reads.incrementAndGet();return new BlockAir();}).when(level).getBlock(any(Vector3.class));
            doAnswer(i->live).when(level).getBlock(any(Vector3.class),anyInt());
            doAnswer(i->{powerReads.incrementAndGet();return signal;}).when(level).getRedstonePower(any(Vector3.class),any(BlockFace.class));
            doAnswer(i->{live=place(i.getArgument(1));transitions.add(tick+":"+(live.isPowered()?"on":"off"));return true;})
                .when(level).setBlock(any(Vector3.class),any(Block.class),anyBoolean(),anyBoolean());
        }
        TestRepeater place(TestRepeater block) {block.level=level;block.x=0;block.y=64;block.z=0;return block;}
        int pendingCount() { return scheduler.getPendingBlockUpdates(new SimpleAxisAlignedBB(-1, 0, -1, 2, 256, 2)).size(); }
        void tick(int now)throws Exception {tick=now;setField("levelCurrentTick",(long)now);scheduler.tick(now);}
        void setField(String name,Object value)throws Exception {Field f=Level.class.getDeclaredField(name);f.setAccessible(true);f.set(level,value);}
    }
}
