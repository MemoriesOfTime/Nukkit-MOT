package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.BlockFace;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockRedstoneWireDepthP16Test {
    @Test void depthLimitDefersOneRequiredRecompute() throws Exception {
        Fixture f = new Fixture();
        f.atDepth(16, () -> f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
        assertEquals(1, f.scheduler.getPendingCount());
        assertTrue(f.level.isUpdateScheduled(f.wire, f.wire));
        verify(f.powerBlock, never()).getWeakPower(any(BlockFace.class));
        verify(f.level, never()).setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean());
    }
    @Test void repeatedDepthLimitNotificationsKeepOneFutureTask() throws Exception {
        Fixture f = new Fixture();
        f.atDepth(17, () -> { for (int i=0; i<100; i++) f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE); });
        assertEquals(1, f.scheduler.getPendingCount());
    }
    @Test void existingFutureDeadlineIsNotPulledForward() throws Exception {
        Fixture f = new Fixture();
        f.level.scheduleUpdate(f.wire, f.wire, 7);
        f.atDepth(16, () -> f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
        assertEquals(1, f.scheduler.getPendingCount());
        f.tick(1);
        assertEquals(1, f.scheduler.getPendingCount());
        verify(f.powerBlock, never()).getWeakPower(any(BlockFace.class));
    }
    @Test void retryReadsFreshWireAndCurrentPower() throws Exception {
        Fixture f = new Fixture();
        f.atDepth(16, () -> f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
        BlockRedstoneWire fresh = new BlockRedstoneWire();
        fresh.position(new Position(0,64,0,f.level)); f.current = fresh;
        f.signal = 7;
        f.tick(1);
        assertEquals(7, fresh.getDamage());
        assertEquals(0, f.wire.getDamage(), "The queued old object must not write its old state");
    }
    @Test void activeRetryAtDepthLimitLeavesNextTickContinuation() throws Exception {
        Fixture f = new Fixture();
        f.atDepth(16, () -> f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
        f.atDepth(16, () -> {
            try { f.tick(1); } catch (Exception e) { throw new RuntimeException(e); }
        });
        assertEquals(1, f.scheduler.getPendingCount(), "An active entry must not suppress next-tick work");
        f.signal = 7; f.tick(2);
        assertEquals(7, f.current.getDamage());
    }
    @Test void removedCellIsNotRecreatedByRetry() throws Exception {
        Fixture f = new Fixture();
        f.atDepth(16, () -> f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
        f.current = new BlockAir(); f.tick(1);
        assertEquals(0, f.scheduler.getPendingCount());
        verify(f.powerBlock, never()).getWeakPower(any(BlockFace.class));
        verify(f.level, never()).setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean());
    }
    @Test void replacedCellIsNotChangedByRetry() throws Exception {
        Fixture f = new Fixture();
        f.atDepth(16, () -> f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
        f.current = new BlockStone(); f.tick(1);
        assertEquals(0, f.scheduler.getPendingCount());
        verify(f.powerBlock, never()).getWeakPower(any(BlockFace.class));
        verify(f.level, never()).setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean());
    }
    @Test void removedSourceDoesNotQueueItself() throws Exception {
        Fixture f = new Fixture();
        Method calculation = BlockRedstoneWire.class.getDeclaredMethod("calculateCurrentChanges", boolean.class, boolean.class);
        calculation.setAccessible(true);
        f.atDepth(16, () -> { try { calculation.invoke(f.wire,false,false); } catch (Exception e) { throw new RuntimeException(e); } });
        assertEquals(0, f.scheduler.getPendingCount());
    }
    @Test void belowLimitKeepsNormalCalculationAndRestoresDepth() throws Exception {
        Fixture f = new Fixture();
        f.atDepth(15, () -> f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
        verify(f.powerBlock).getWeakPower(any(BlockFace.class));
        assertEquals(0, f.scheduler.getPendingCount());
    }
    static final class Fixture {
        final Level level = mock(Level.class);
        final BlockUpdateScheduler scheduler = new BlockUpdateScheduler(level,0);
        final BlockRedstoneWire wire = new BlockRedstoneWire();
        Block current = wire;
        final Block powerBlock = mock(Block.class);
        int signal;
        Fixture() throws Exception {
            Field queue=Level.class.getDeclaredField("updateQueue"); queue.setAccessible(true); queue.set(level,scheduler);
            Server server=mock(Server.class);
            when(level.getServer()).thenReturn(server);
            when(level.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            when(level.isAreaLoaded(any(AxisAlignedBB.class))).thenReturn(true);
            when(level.getMinBlockY()).thenReturn(-64); when(level.getMaxBlockY()).thenReturn(319);
            doCallRealMethod().when(level).scheduleUpdate(any(Block.class),any(Vector3.class),anyInt());
            doCallRealMethod().when(level).scheduleUpdate(any(Block.class),any(Vector3.class),anyInt(),anyInt(),anyBoolean());
            doCallRealMethod().when(level).isUpdateScheduled(any(Vector3.class),any(Block.class));
            when(powerBlock.getId()).thenReturn(BlockID.REDSTONE_BLOCK);
            when(powerBlock.getWeakPower(any(BlockFace.class))).thenAnswer(i -> signal);
            when(level.getBlock(any(Vector3.class))).thenAnswer(i -> {
                Vector3 pos=i.getArgument(0);
                return pos.getFloorX()==0 && pos.getFloorY()==65 && pos.getFloorZ()==0 ? powerBlock : new BlockAir();
            });
            when(level.getBlock(any(Vector3.class),anyInt())).thenAnswer(i -> current);
            wire.position(new Position(0,64,0,level));
        }
        @SuppressWarnings("unchecked") void atDepth(int value,Runnable action) throws Exception {
            Field field=BlockRedstoneWire.class.getDeclaredField("UPDATE_DEPTH"); field.setAccessible(true);
            ThreadLocal<Integer> depth=(ThreadLocal<Integer>)field.get(null); int previous=depth.get(); depth.set(value);
            try { action.run(); assertEquals(value,depth.get()); } finally { depth.set(previous); }
        }
        void tick(int value) throws Exception {
            Field field=Level.class.getDeclaredField("levelCurrentTick"); field.setAccessible(true); field.set(level,(long)value);
            scheduler.tick(value);
        }
    }
}
