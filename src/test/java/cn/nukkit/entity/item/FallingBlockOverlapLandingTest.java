package cn.nukkit.entity.item;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.entity.EntityDamageByBlockEvent;
import org.junit.jupiter.api.Test;
import cn.nukkit.item.Item;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FallingBlockOverlapLandingTest {
    @BeforeAll
    static void initialize() {
        MockServer.init();
    }

    @ParameterizedTest
    @ValueSource(ints = {Block.SAND, Block.GRAVEL, Block.ANVIL, Block.CONCRETE_POWDER})
    void denseLandingNeverLiftsNeighboursAndConservesEveryBlock(int blockId) {
        Fixture fixture = new Fixture(blockId);
        List<EntityFallingBlock> others = new ArrayList<>();
        for (int i = 0; i < 32; i++) others.add(fixture.entity(blockId, 64.1));
        EntityFallingBlock first = fixture.entity(blockId, 64);
        first.onUpdate(1);

        for (EntityFallingBlock other : others) {
            assertEquals(64.1, other.y, 0, "landing must not build a tower above the source");
            assertEquals(-0.2, other.motionY, 0, "another landing must not change falling motion");
            verify(other, never()).setPosition(any(Vector3.class));
            other.onUpdate(1);
            assertTrue(other.closed);
            assertFalse(other.onUpdate(2), "a closed entity cannot emit another item");
        }
        assertEquals(1, fixture.placements);
        assertEquals(32, fixture.drops.stream().mapToInt(Item::getCount).sum());
        assertTrue(fixture.drops.stream().allMatch(item -> item.getId() == Block.get(blockId).toItem().getId()));
    }

    @ParameterizedTest
    @ValueSource(ints = {Block.SAND, Block.GRAVEL, Block.ANVIL, Block.CONCRETE_POWDER})
    void disabledDropsStillPreventTowerAndExtraOutputs(int blockId) {
        Fixture fixture = new Fixture(blockId);
        fixture.rules.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        EntityFallingBlock second = fixture.entity(blockId, 64.1);
        fixture.entity(blockId, 64).onUpdate(1);
        second.onUpdate(1);
        assertEquals(64.1, second.y, 0);
        assertEquals(1, fixture.placements);
        assertTrue(fixture.drops.isEmpty());
    }

    @Test
    void anvilKeepsItsExistingImpactBoundaryWithoutLiftingNeighbours() {
        Fixture fixture = new Fixture(Block.ANVIL);
        EntityLiving victim = mock(EntityLiving.class);
        when(victim.getBoundingBox()).thenReturn(new SimpleAxisAlignedBB(0, 65, 0, 0.6, 66.8, 0.6));
        fixture.entities.put(1000L, victim);
        EntityFallingBlock anvil = fixture.entity(Block.ANVIL, 64);
        anvil.highestPosition = 74;
        when(anvil.canCollide()).thenCallRealMethod();
        when(anvil.canCollideWith(any(Entity.class))).thenCallRealMethod();
        anvil.onUpdate(1);
        verify(victim).attack(argThat(damage -> damage instanceof EntityDamageByBlockEvent
                && damage.getDamage() == 20));
        assertEquals(1, fixture.placements);
        assertTrue(fixture.drops.isEmpty());
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final GameRules rules = GameRules.getDefault();
        final List<Item> drops = new ArrayList<>();
        final Map<Long, Entity> entities = new HashMap<>();
        Block cell = Block.get(Block.AIR);
        int placements;
        long nextId;

        Fixture(int blockId) {
            reset(Server.getInstance().getPluginManager());
            when(level.getGameRules()).thenReturn(rules);
            DimensionData dimension = mock(DimensionData.class);
            when(dimension.getMinHeight()).thenReturn(-64);
            when(dimension.getMaxHeight()).thenReturn(320);
            when(level.getDimensionData()).thenReturn(dimension);
            cell.y = 64;
            when(level.getBlock(any(Vector3.class))).thenAnswer(call -> cell);
            when(level.getChunkEntities(anyInt(), anyInt(), eq(false))).thenAnswer(call ->
                    (int) call.getArgument(0) == 0 && (int) call.getArgument(1) == 0 ? entities : Map.of());
            when(level.setBlock(any(Vector3.class), any(Block.class), eq(true), eq(true))).thenAnswer(call -> {
                placements++;
                cell = ((Block) call.getArgument(1)).clone();
                cell.y = 64;
                return true;
            });
            doAnswer(call -> { drops.add(((Item) call.getArgument(1)).clone()); return null; })
                    .when(level).dropItem(any(Vector3.class), any(Item.class));
        }

        EntityFallingBlock entity(int blockId, double y) {
            EntityFallingBlock entity = mock(EntityFallingBlock.class);
            entity.level = level;
            try {
                var server = Entity.class.getDeclaredField("server");
                server.setAccessible(true);
                server.set(entity, Server.getInstance());
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            entity.blockId = blockId;
            entity.onGround = true;
            entity.x = 0.5;
            entity.y = y;
            entity.z = 0.5;
            entity.motionY = -0.2;
            entity.boundingBox = new SimpleAxisAlignedBB(0, y, 0, 0.98, y + 0.98, 0.98);
            long id = ++nextId;
            entities.put(id, entity);
            when(entity.getId()).thenReturn(id);
            when(entity.onUpdate(anyInt())).thenCallRealMethod();
            when(entity.isAlive()).thenReturn(true);
            when(entity.getBlock()).thenCallRealMethod();
            when(entity.getDamage()).thenCallRealMethod();
            when(entity.getLevel()).thenReturn(level);
            when(entity.getBoundingBox()).thenReturn(entity.boundingBox);
            when(entity.getFloorY()).thenReturn(64);
            when(entity.add(anyDouble(), anyDouble(), anyDouble())).thenCallRealMethod();
            when(entity.relocateForContinuedFalling(any(Vector3.class))).thenCallRealMethod();
            when(entity.setPosition(any(Vector3.class))).thenAnswer(call -> {
                Vector3 target = call.getArgument(0);
                entity.y = target.y;
                return true;
            });
            doAnswer(call -> { entity.closed = true; return null; }).when(entity).close();
            return entity;
        }
    }
}
