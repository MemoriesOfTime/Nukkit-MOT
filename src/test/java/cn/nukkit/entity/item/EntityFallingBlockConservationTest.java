package cn.nukkit.entity.item;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.entity.EntityBlockChangeEvent;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntityFallingBlockConservationTest {
    @BeforeAll
    static void initialize() {
        MockServer.init();
    }

    @Test
    void aFallingStalactiteProducesOneItemAndNoPlacedBlock() {
        Fixture fixture = new Fixture(Block.POINTED_DRIPSTONE);
        fixture.entity.onUpdate(1);
        assertEquals(1, fixture.drops.size());
        assertEquals(Block.get(Block.POINTED_DRIPSTONE).toItem().getId(), fixture.drops.get(0).getId());
        assertEquals(1, fixture.drops.get(0).getCount());
        assertTrue(fixture.placements.isEmpty(), "one falling block must not survive beside its item");
        assertFalse(fixture.entity.onUpdate(2), "closed falling block cannot produce another item");
        assertEquals(1, fixture.drops.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {Block.SAND, Block.GRAVEL, Block.ANVIL})
    void ordinaryGravityBlocksStillPlaceOnceWithoutDroppingAnother(int blockId) {
        Fixture fixture = new Fixture(blockId);
        fixture.entity.onUpdate(1);
        assertEquals(List.of(blockId), fixture.placements);
        assertTrue(fixture.drops.isEmpty());
    }

    @Test
    void disabledEntityDropsDoNotPlaceOrDuplicateTheStalactite() {
        Fixture fixture = new Fixture(Block.POINTED_DRIPSTONE);
        fixture.rules.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        fixture.entity.onUpdate(1);
        assertTrue(fixture.placements.isEmpty());
        assertTrue(fixture.drops.isEmpty());
    }

    @Test
    void cancellingTheLandingProducesNeitherAPlacedBlockNorAnItem() {
        Fixture fixture = new Fixture(Block.POINTED_DRIPSTONE);
        doAnswer(call -> {
            ((EntityBlockChangeEvent) call.getArgument(0)).setCancelled();
            return null;
        }).when(fixture.plugins).callEvent(any(EntityBlockChangeEvent.class));
        try {
            fixture.entity.onUpdate(1);
            assertTrue(fixture.placements.isEmpty());
            assertTrue(fixture.drops.isEmpty());
        } finally {
            reset(fixture.plugins);
        }
    }

    @Test
    void changingTheLandingMetadataDoesNotAlsoPlaceTheDroppedStalactite() {
        Fixture fixture = new Fixture(Block.POINTED_DRIPSTONE);
        doAnswer(call -> {
            ((EntityBlockChangeEvent) call.getArgument(0)).getTo().setDamage(1);
            return null;
        }).when(fixture.plugins).callEvent(any(EntityBlockChangeEvent.class));
        try {
            fixture.entity.onUpdate(1);
            assertTrue(fixture.placements.isEmpty());
            assertEquals(1, fixture.drops.size());
        } finally {
            reset(fixture.plugins);
        }
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final PluginManager plugins = Server.getInstance().getPluginManager();
        final GameRules rules = GameRules.getDefault();
        final List<Item> drops = new ArrayList<>();
        final List<Integer> placements = new ArrayList<>();
        final EntityFallingBlock entity = mock(EntityFallingBlock.class);

        Fixture(int blockId) {
            reset(plugins);
            entity.level = level;
            try {
                java.lang.reflect.Field field = Entity.class.getDeclaredField("server");
                field.setAccessible(true);
                field.set(entity, Server.getInstance());
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
            entity.blockId = blockId;
            entity.onGround = true;
            entity.x = 0.5;
            entity.y = 64;
            entity.z = 0.5;
            entity.boundingBox = new SimpleAxisAlignedBB(0, 64, 0, 0.98, 64.98, 0.98);
            when(entity.onUpdate(anyInt())).thenCallRealMethod();
            when(entity.isAlive()).thenReturn(true);
            when(entity.getBlock()).thenCallRealMethod();
            when(entity.getDamage()).thenCallRealMethod();
            when(entity.getLevel()).thenReturn(level);
            when(entity.getBoundingBox()).thenReturn(entity.boundingBox);
            when(entity.getFloorY()).thenReturn(64);
            when(entity.add(anyDouble(), anyDouble(), anyDouble())).thenCallRealMethod();
            doAnswer(call -> { entity.closed = true; return null; }).when(entity).close();
            when(level.getGameRules()).thenReturn(rules);
            DimensionData dimension = mock(DimensionData.class);
            when(dimension.getMinHeight()).thenReturn(-64);
            when(dimension.getMaxHeight()).thenReturn(320);
            when(level.getDimensionData()).thenReturn(dimension);
            when(level.getBlock(any(Vector3.class))).thenReturn(Block.get(Block.AIR));
            when(level.setBlock(any(Vector3.class), any(Block.class), eq(true), eq(true)))
                    .thenAnswer(call -> {
                        placements.add(((Block) call.getArgument(1)).getId());
                        return true;
                    });
            doAnswer(call -> {
                drops.add(((Item) call.getArgument(1)).clone());
                return null;
            }).when(level).dropItem(any(Vector3.class), any(Item.class));
        }
    }
}
