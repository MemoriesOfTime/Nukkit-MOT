package cn.nukkit.block;

import cn.nukkit.blockentity.BlockEntityComparator;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedstoneComparatorQueuedP16Test {
    @Test
    void futureCompareTickSkipsOneHundredRedundantInputScans() throws Exception {
        queuedScans(0);
    }

    @Test
    void futureSubtractTickSkipsOneHundredRedundantInputScans() throws Exception {
        queuedScans(4);
    }

    private void queuedScans(int meta) throws Exception {
        Fixture f = new Fixture(meta);
        f.live.updateState();
        assertEquals(1, f.scheduler.getPendingCount());
        assertTrue(f.level.isUpdateScheduled(f.live, f.live));
        assertFalse(f.level.isBlockTickPending(f.live, f.live));
        f.clearReads();
        for (int n = 0; n < 100; n++) f.live.updateState();
        System.out.printf("queued mode=%s notifications=100 blocks=%d input=%d entity=%d%n",
                f.live.getMode(), f.blockReads, f.powerReads, f.entityReads);
        assertEquals(0, f.blockReads, "Future tick already owns this update; no block scans are useful");
        assertEquals(0, f.powerReads);
        assertEquals(0, f.entityReads);
        assertEquals(1, f.scheduler.getPendingCount());
    }

    @Test
    void dueTickUsesLatestCompareInputAndKeepsTwoTickDeadline() throws Exception {
        Fixture f = new Fixture(0);
        f.live.updateState();
        f.input = 3;
        f.live.updateState();
        f.tick(1);
        assertEquals(0, f.output);
        f.tick(2);
        assertEquals(3, f.output);
        assertTrue(f.live.isPowered());
        assertEquals(List.of(2), f.writes);
        assertEquals(0, f.scheduler.getPendingCount());
    }

    @Test
    void dueTickUsesLatestSubtractInputs() throws Exception {
        Fixture f = new Fixture(4);
        f.side = 3;
        f.live.updateState();
        f.input = 7;
        f.side = 5;
        f.live.updateState();
        f.tick(2);
        assertEquals(2, f.output);
        assertTrue(f.live.isPowered());
        assertEquals(List.of(2), f.writes);
    }

    @Test
    void activeBucketStillSkipsScansAfterLeavingFutureIndex() throws Exception {
        Fixture f = new Fixture(0);
        f.live.updateState();
        f.probeActive = true;
        f.tick(2);
        assertEquals(1, f.activeProbes);
        assertEquals(10, f.output);
    }

    @Test
    void outputWriteFeedbackIsSampledAgainAtTheSameDeadline() throws Exception {
        Fixture f = new Fixture(0);
        f.input = 5;
        f.live.updateState();
        f.feedback = true;
        f.inputs.clear();
        f.tick(2);
        assertEquals(5, f.output);
        assertEquals(List.of(5, 11), f.inputs, "Do not cache power across output writes");
        assertTrue(f.live.isPowered());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    void everyInputPairKeepsVanillaPower(int meta) throws Exception {
        Fixture f = new Fixture(meta);
        for (int input = 0; input <= 15; input++) {
            for (int side = 0; side <= 15; side++) {
                f.input = input;
                f.side = side;
                f.live.updateState();
                f.tick(f.tick + 2);
                int expected = meta == 4 ? Math.max(input - side, 0) : input >= side ? input : 0;
                assertEquals(expected, f.live.getWeakPower(f.live.getFacing()),
                        "input=" + input + " side=" + side + " mode=" + f.live.getMode());
                assertEquals(0, f.live.getWeakPower(f.live.getFacing().getOpposite()));
                assertEquals(0, f.scheduler.getPendingCount());
            }
        }
    }

    private static final class TestComparator extends BlockRedstoneComparatorUnpowered {
        final Fixture fixture;
        TestComparator(Fixture fixture, int meta, boolean powered) {
            super(meta);
            this.fixture = fixture;
            this.isPowered = powered;
        }
        @Override public int getId() { return isPowered ? POWERED_COMPARATOR : UNPOWERED_COMPARATOR; }
        @Override protected BlockRedstoneComparator getPowered() {
            return new TestComparator(fixture, getDamage(), true);
        }
        @Override protected BlockRedstoneComparator getUnpowered() {
            return new TestComparator(fixture, getDamage(), false);
        }
        @Override public int onUpdate(int type) {
            if (type == Level.BLOCK_UPDATE_SCHEDULED && fixture.probeActive) {
                assertTrue(level.isBlockTickPending(this, this));
                assertFalse(level.isUpdateScheduled(this, this));
                int blocks = fixture.blockReads, power = fixture.powerReads, entity = fixture.entityReads;
                updateState();
                assertEquals(blocks, fixture.blockReads);
                assertEquals(power, fixture.powerReads);
                assertEquals(entity, fixture.entityReads);
                fixture.activeProbes++;
            }
            return super.onUpdate(type);
        }
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final BlockEntityComparator entity = mock(BlockEntityComparator.class);
        final BlockUpdateScheduler scheduler = new BlockUpdateScheduler(level, 0);
        final List<Integer> writes = new ArrayList<>(), inputs = new ArrayList<>();
        TestComparator live;
        int input = 10, side, output, tick, blockReads, powerReads, entityReads, activeProbes;
        boolean feedback, probeActive;
        Fixture(int meta) throws Exception {
            setField("updateQueue", scheduler);
            live = place(new TestComparator(this, meta, false));
            when(level.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            when(level.isAreaLoaded(any(AxisAlignedBB.class))).thenReturn(true);
            when(level.getMinBlockY()).thenReturn(0);
            when(level.getMaxBlockY()).thenReturn(255);
            doCallRealMethod().when(level).scheduleUpdate(any(Block.class), any(Vector3.class), anyInt());
            doCallRealMethod().when(level).scheduleUpdate(any(Block.class), any(Vector3.class), anyInt(), anyInt(), anyBoolean());
            doCallRealMethod().when(level).isBlockTickPending(any(Vector3.class), any(Block.class));
            doCallRealMethod().when(level).isUpdateScheduled(any(Vector3.class), any(Block.class));
            doAnswer(i -> {
                blockReads++;
                Vector3 pos = i.getArgument(0);
                BlockFace facing = live.getFacing();
                return pos.equals(live.getLocation().getSide(facing.rotateY()))
                        || pos.equals(live.getLocation().getSide(facing.rotateYCCW()))
                        ? new BlockRedstoneWire(side) : new BlockAir();
            }).when(level).getBlock(any(Vector3.class));
            doAnswer(i -> live).when(level).getBlock(any(Vector3.class), anyInt());
            doAnswer(i -> { blockReads++; return new BlockAir(); })
                    .when(level).getBlock(anyInt(), anyInt(), anyInt(), anyInt());
            doAnswer(i -> { powerReads++; inputs.add(input); return input; })
                    .when(level).getRedstonePower(any(Vector3.class), any(BlockFace.class));
            doAnswer(i -> { entityReads++; return entity; }).when(level).getBlockEntity(any(Vector3.class));
            when(entity.getOutputSignal()).thenAnswer(i -> output);
            doAnswer(i -> { output = i.getArgument(0); writes.add(tick); if (feedback) input = 11; return null; })
                    .when(entity).setOutputSignal(anyInt());
            doAnswer(i -> { live = place(i.getArgument(1)); return true; })
                    .when(level).setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean());
        }
        TestComparator place(TestComparator block) {
            block.level = level; block.x = 0; block.y = 64; block.z = 0; return block;
        }
        void clearReads() { blockReads = powerReads = entityReads = 0; }
        void tick(int now) throws Exception { tick = now; setField("levelCurrentTick", (long) now); scheduler.tick(now); }
        void setField(String name, Object value) throws Exception {
            Field field = Level.class.getDeclaredField(name); field.setAccessible(true); field.set(level, value);
        }
    }
}
