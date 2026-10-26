package cn.nukkit.entity;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockStone;
import cn.nukkit.entity.mob.EntityWitch;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        when(witch.getGravity()).thenReturn(0.08f);
        when(witch.getHorizontalFacing()).thenReturn(BlockFace.SOUTH);
        // The obstacle is the cell one block south of the witch at foot level; everything else is air.
        when(witch.level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            int x = inv.getArgument(0);
            int y = inv.getArgument(1);
            int z = inv.getArgument(2);
            Block block = y == 64 && z == 1 ? new BlockStone() : new BlockAir();
            block.level = witch.level;
            block.setComponents(x, y, z);
            return block;
        });
        when(witch.level.getBlock(any(Vector3.class))).thenAnswer(inv -> {
            Vector3 position = inv.getArgument(0);
            return witch.level.getBlock(position.getFloorX(), position.getFloorY(), position.getFloorZ());
        });
        when(witch.level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
                witch.level.getBlock((int) inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
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
        when(witch.getLevel()).thenReturn(level);
        when(server.getMobAiEnabled()).thenReturn(true);
        when(witch.isInTickingRange()).thenReturn(true);
        when(witch.getLevelBlock()).thenReturn(new BlockAir());
        when(witch.getBuiltinBlockIdAt(anyInt(), anyInt(), anyInt(), anyInt())).thenCallRealMethod();
        when(level.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(new BlockAir());
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
