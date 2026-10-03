package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedstoneWireReadReuseTest {
    @Test
    void flatConnectionsKeepEveryDirectionAndAvoidAboveReads() {
        for (int mask = 0; mask < 16; mask++) {
            Fixture fixture = new Fixture();
            int bit = 0;
            for (BlockFace face : BlockFace.Plane.HORIZONTAL) {
                if ((mask & (1 << bit++)) != 0) {
                    fixture.put(face.getXOffset(), 0, face.getZOffset(), new BlockRedstoneWire(7));
                }
            }
            BlockRedstoneWire wire = new BlockRedstoneWire(8);
            wire.level = fixture.level;
            for (BlockFace side : BlockFace.values()) {
                assertEquals(expected(mask, side), wire.getWeakPower(side), "mask=" + mask + " side=" + side);
            }
            // Five directions scan connections; UP returns directly. Each unconnected
            // horizontal neighbor requires one read below, not a second read of that block.
            assertEquals(5 * (8 - Integer.bitCount(mask)), fixture.reads.get(), "mask=" + mask);
            assertEquals(0, fixture.aboveReads.get(), "flat wire does not need the block above");
        }
    }

    @Test
    void uphillConnectionsStillReadAbovePerDirection() {
        Fixture fixture = new Fixture();
        for (BlockFace face : BlockFace.Plane.HORIZONTAL) {
            fixture.put(face.getXOffset(), 0, face.getZOffset(), new BlockStone());
            fixture.put(face.getXOffset(), 1, face.getZOffset(), new BlockRedstoneWire(7));
        }
        BlockRedstoneWire wire = new BlockRedstoneWire(8);
        wire.level = fixture.level;
        for (BlockFace side : BlockFace.values()) {
            assertEquals(side == BlockFace.DOWN ? 0 : 8, wire.getWeakPower(side));
        }
        assertEquals(60, fixture.reads.get());
        assertEquals(20, fixture.aboveReads.get());
    }

    private static int expected(int mask, BlockFace side) {
        if (side == BlockFace.UP) return 8;
        if (side == BlockFace.DOWN) return 0;
        if (mask == 0) return 8;
        BlockFace output = side.getOpposite();
        int bit = 0;
        for (BlockFace connected : BlockFace.Plane.HORIZONTAL) {
            if ((mask & (1 << bit++)) == 0) continue;
            if (output == connected || (Integer.bitCount(mask) == 1 && output == connected.getOpposite())) return 8;
        }
        return 0;
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Map<String, Block> blocks = new HashMap<>();
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger aboveReads = new AtomicInteger();
        Fixture() {
            doAnswer(i -> {Vector3 p = i.getArgument(0);return get(p.getFloorX(),p.getFloorY(),p.getFloorZ());})
                    .when(level).getBlock(any(Vector3.class));
            doAnswer(i -> get(i.getArgument(0),i.getArgument(1),i.getArgument(2)))
                    .when(level).getBlock(anyInt(),anyInt(),anyInt(),anyInt());
        }
        void put(int x,int y,int z,Block block) {blocks.put(key(x,y,z),block);}
        Block get(int x,int y,int z) {
            reads.incrementAndGet();
            if(x==0&&y==1&&z==0) aboveReads.incrementAndGet();
            Block prototype = blocks.get(key(x,y,z));
            Block block = prototype == null ? new BlockAir() : prototype.clone();
            block.level=level;block.x=x;block.y=y;block.z=z;
            return block;
        }
        String key(int x,int y,int z) {return x+":"+y+":"+z;}
    }
}
