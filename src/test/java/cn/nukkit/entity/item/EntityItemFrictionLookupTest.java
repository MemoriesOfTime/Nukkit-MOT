package cn.nukkit.entity.item;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockLiquid;
import cn.nukkit.level.Level;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityItemFrictionLookupTest {
    private static EntityItem item(Level level) {
        EntityItem item = mock(EntityItem.class);
        when(item.onUpdate(anyInt())).thenCallRealMethod();
        when(item.isAlive()).thenReturn(true);
        when(item.getLevel()).thenReturn(level);
        when(item.getFloorX()).thenReturn(5);
        when(item.getFloorZ()).thenReturn(5);
        when(item.getDrag()).thenReturn(0.02f);
        item.level = level;
        item.x = 5;
        item.y = 10;
        item.z = 5;
        item.boundingBox = new SimpleAxisAlignedBB(5, 10, 5, 5.25, 10.25, 5.25);
        item.onGround = true;
        Block air = mock(Block.class);
        when(level.getBlock(5, 10, 5)).thenReturn(air);
        when(item.getLevelBlock()).thenReturn(air);
        when(level.getBlock(any(Vector3.class), eq(1))).thenReturn(air);
        return item;
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0.00001,-0.00001", "-0.00001,0.00001", "NaN,0"})
    void restingOrSubthresholdStackDoesNotLookUpFloor(double x, double z) {
        Level level = mock(Level.class);
        EntityItem item = item(level);
        item.motionX = x;
        item.motionZ = z;
        item.motionY = 0.125;
        item.onUpdate(1);
        verify(level, never()).getBlock(5, 9, 5);
        verify(item).move(x, 0.125, z);
        verify(item).updateMovement();
        verify(item).entityBaseTick(1);
    }

    @ParameterizedTest
    @CsvSource({"true,false,0.6", "false,false,1.0", "true,true,0.8", "false,true,0.8"})
    void preservesGroundAirAndLiquidFriction(boolean grounded, boolean liquid, double expectedFactor) {
        Level level = mock(Level.class);
        EntityItem item = item(level);
        Block floor = liquid ? mock(BlockLiquid.class) : mock(Block.class);
        when(floor.getFrictionFactor()).thenReturn(0.6);
        when(level.getBlock(5, 9, 5)).thenReturn(floor);
        item.onGround = grounded;
        item.motionX = 0.25;
        item.motionZ = -0.5;
        item.onUpdate(1);
        double drag = 1 - 0.02f;
        assertEquals(0.25 * drag * expectedFactor, item.motionX, 1e-12);
        assertEquals(-0.5 * drag * expectedFactor, item.motionZ, 1e-12);
        verify(level).getBlock(5, 9, 5);
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 100, 500})
    void noFrictionReadsForDenseStationaryDrops(int count) {
        Level level = mock(Level.class);
        for (int i = 0; i < count; i++) {
            EntityItem item = item(level);
            for (int tick = 1; tick <= 20; tick++) item.onUpdate(tick);
        }
        verify(level, never()).getBlock(5, 9, 5);
    }

}
