package cn.nukkit.entity.projectile;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityProjectileZeroMotionScanTest {
    private static EntityProjectile projectile(Level level) {
        EntityProjectile projectile = mock(EntityProjectile.class);
        when(projectile.onUpdate(anyInt())).thenCallRealMethod();
        when(projectile.isAlive()).thenReturn(true);
        when(projectile.getIntermediateWithXValue(any(Vector3.class), anyDouble())).thenCallRealMethod();
        when(projectile.getIntermediateWithYValue(any(Vector3.class), anyDouble())).thenCallRealMethod();
        when(projectile.getIntermediateWithZValue(any(Vector3.class), anyDouble())).thenCallRealMethod();
        when(projectile.getLevel()).thenReturn(level);
        projectile.level = level;
        projectile.y = 64;
        projectile.boundingBox = new SimpleAxisAlignedBB(0, 64, 0, 0.25, 64.25, 0.25);
        projectile.isCollided = true;
        projectile.hadCollision = true;
        when(level.getCollidingEntities(any(AxisAlignedBB.class), same(projectile))).thenReturn(new Entity[0]);
        return projectile;
    }

    @Test
    void zeroLengthSegmentCannotHitEvenAnOverlappingBox() {
        AxisAlignedBB box = new SimpleAxisAlignedBB(-1, 63, -1, 1, 65, 1);
        Vector3 position = new Vector3(0, 64, 0);
        assertNull(box.calculateIntercept(position, position));
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 100, 500})
    void skipsOnlyCollisionSearchWhileStillRunningTheTick(int count) {
        Level level = mock(Level.class);
        for (int i = 0; i < count; i++) {
            EntityProjectile projectile = projectile(level);
            for (int tick = 1; tick <= 20; tick++) projectile.onUpdate(tick);
            verify(projectile, times(20)).entityBaseTick(1);
            verify(projectile, times(20)).move(0, 0, 0);
            verify(projectile, times(20)).updateMovement();
        }
        verify(level, never()).getCollidingEntities(any(AxisAlignedBB.class), any(Entity.class));
    }

    @ParameterizedTest
    @CsvSource({"0.001,0,0", "0,-0.001,0", "0,0,0.001", "0.000000000001,0,0"})
    void anyNonzeroMotionStillQueriesCandidates(double x, double y, double z) {
        Level level = mock(Level.class);
        EntityProjectile projectile = projectile(level);
        projectile.motionX = x;
        projectile.motionY = y;
        projectile.motionZ = z;
        projectile.onUpdate(1);
        verify(level).getCollidingEntities(any(AxisAlignedBB.class), same(projectile));
    }

    @Test
    void movingProjectileStillHitsAnEntity() {
        Level level = mock(Level.class);
        EntityProjectile projectile = projectile(level);
        EntityLiving target = mock(EntityLiving.class);
        target.boundingBox = new SimpleAxisAlignedBB(0.5, 63.5, -0.2, 0.75, 64.5, 0.2);
        projectile.motionX = 1;
        when(level.getCollidingEntities(any(AxisAlignedBB.class), same(projectile))).thenReturn(new Entity[]{target});
        projectile.onUpdate(1);
        verify(projectile).onCollideWithEntity(target);
    }

    @Test
    void gravityCanStartMovementBeforeTheZeroCheck() {
        Level level = mock(Level.class);
        EntityProjectile projectile = projectile(level);
        projectile.isCollided = false;
        doAnswer(invocation -> { projectile.motionY = -0.03; return null; }).when(projectile).updateMotion();
        projectile.onUpdate(1);
        verify(level).getCollidingEntities(any(AxisAlignedBB.class), same(projectile));
        verify(projectile).move(0, -0.03, 0);
    }
}
