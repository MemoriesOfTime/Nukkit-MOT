package cn.nukkit.entity;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockStone;
import cn.nukkit.entity.mob.EntityWitch;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.BlockFace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntityWitchRouteDistanceTest {
    @Test
    void approachesNearbyWaypointWhileFollowingDistantEnemy() {
        EntityWitch witch = witch(2, enemy(10));
        witch.updateMove(1);
        assertTrue(witch.motionX > 0, "a route node two blocks away must not use the combat radius");
    }

    @Test
    void approachesNearbyWanderPointWithoutEnemy() {
        EntityWitch witch = witch(2, null);
        witch.updateMove(1);
        assertTrue(witch.motionX > 0, "wandering must approach the point within the route arrival radius");
    }

    @Test
    void keepsCombatDistanceEvenWhenWaypointIsFartherAway() {
        EntityWitch witch = witch(10, enemy(4));
        witch.updateMove(1);
        assertEquals(0, witch.motionX, "the waypoint branch must preserve the live target stop");
    }

    @Test
    void reachedWaypointDoesNotCauseExtraMovement() {
        EntityWitch witch = witch(0.4, null);
        witch.updateMove(1);
        assertEquals(0, witch.motionX);
    }

    @Test
    void directLivingTargetStillUsesCombatDistance() {
        EntityWitch witch = witch(4, null);
        witch.target = enemy(4);
        witch.updateMove(1);
        assertEquals(0, witch.motionX);
    }

    @Test
    void deadFollowTargetDoesNotStopWandering() {
        EntityCreature enemy = enemy(4);
        when(enemy.isAlive()).thenReturn(false);
        EntityWitch witch = witch(10, enemy);
        witch.updateMove(1);
        assertTrue(witch.motionX > 0);
    }

    @Test
    void stoppedWitchDoesNotJumpAtAdjacentObstacle() {
        EntityWitch witch = jumpingWitch();
        assertFalse(witch.checkJump(0, 0));
        assertEquals(0, witch.motionY);
    }

    @Test
    void movingWitchStillJumpsOverObstacle() {
        EntityWitch witch = jumpingWitch();
        assertTrue(witch.checkJump(0.15, 0));
        assertTrue(witch.motionY > 0);
    }

    @Test
    void stationaryWitchStillFloatsInWater() {
        EntityWitch witch = jumpingWitch();
        when(witch.canSwimIn(anyInt())).thenReturn(true);
        assertTrue(witch.checkJump(0, 0));
        assertEquals(witch.getGravity() * 2, witch.motionY);
    }

    private static EntityWitch jumpingWitch() {
        EntityWitch witch = witch(2, enemy(10));
        Block origin = mock(Block.class);
        Block obstacle = mock(Block.class);
        when(witch.getLevel()).thenReturn(witch.level);
        when(witch.getGravity()).thenReturn(0.08f);
        when(witch.getHorizontalFacing()).thenReturn(BlockFace.SOUTH);
        when(witch.level.getBlock(any(Vector3.class))).thenReturn(origin);
        when(origin.getSide(BlockFace.SOUTH)).thenReturn(obstacle);
        when(origin.up(2)).thenReturn(new BlockAir());
        when(obstacle.up()).thenReturn(new BlockAir());
        when(witch.checkJump(anyDouble(), anyDouble())).thenCallRealMethod();
        return witch;
    }

    private static EntityCreature enemy(double x) {
        EntityCreature enemy = mock(EntityCreature.class);
        enemy.x = x;
        enemy.y = 64;
        when(enemy.isAlive()).thenReturn(true);
        when(enemy.canBeFollowed()).thenReturn(true);
        return enemy;
    }

    private static EntityWitch witch(double waypointX, EntityCreature enemy) {
        EntityWitch witch = mock(EntityWitch.class);
        Server server = mock(Server.class);
        Level level = mock(Level.class);
        Block ground = new BlockStone();
        witch.level = level;
        witch.y = 64;
        witch.age = 1;
        witch.onGround = true;
        witch.noRotateTicks = 1;
        witch.target = new Vector3(waypointX, 64, 0);
        witch.followTarget = enemy;
        witch.moveMultiplier = 1;
        when(witch.getServer()).thenReturn(server);
        when(server.getMobAiEnabled()).thenReturn(true);
        when(witch.isInTickingRange()).thenReturn(true);
        when(witch.getLevelBlock()).thenReturn(new BlockAir());
        when(witch.getFloorY()).thenReturn(64);
        when(level.getBlock(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(), eq(false))).thenReturn(ground);
        when(witch.getSpeed()).thenReturn(1.0);
        when(witch.getWidth()).thenCallRealMethod();
        when(witch.nearbyDistanceMultiplier()).thenCallRealMethod();
        when(witch.distance(any(Vector3.class))).thenCallRealMethod();
        when(witch.distanceSquared(any(Vector3.class))).thenCallRealMethod();
        when(witch.updateMove(anyInt())).thenCallRealMethod();
        return witch;
    }
}
