package cn.nukkit.entity;

import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.utils.CollisionHelper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class EntityCollisionLayerTest {

    @Test
    void emptySecondLayerSkipsMaterializationAndKeepsBaseCallback() {
        Fixture fixture = new Fixture(Block.AIR);

        fixture.entity.checkBlockCollision();

        verify(fixture.base).onEntityCollide(fixture.entity);
        verify(fixture.base, never()).getLevelBlockAtLayer(1);
        verify(fixture.secondLayer, never()).onEntityCollide(fixture.entity);
    }

    @Test
    void nonEmptySecondLayerStillReceivesCollisionCallback() {
        Fixture fixture = new Fixture(Block.WATER);

        fixture.entity.checkBlockCollision();

        verify(fixture.base).onEntityCollide(fixture.entity);
        verify(fixture.base).getLevelBlockAtLayer(1);
        verify(fixture.secondLayer).onEntityCollide(fixture.entity);
    }

    private static final class Fixture {
        final Entity entity = mock(Entity.class, CALLS_REAL_METHODS);
        final CollisionHelper collisionHelper = mock(CollisionHelper.class);
        final Level level = mock(Level.class);
        final Block base = mock(Block.class);
        final Block secondLayer = mock(Block.class);

        Fixture(int secondLayerId) {
            doReturn(collisionHelper).when(entity).getCollisionHelper();
            when(collisionHelper.getCollisionBlocks()).thenReturn(new Block[]{base});
            when(base.getId()).thenReturn(Block.STONE);
            when(base.getLevel()).thenReturn(level);
            when(base.getFloorX()).thenReturn(1);
            when(base.getFloorY()).thenReturn(64);
            when(base.getFloorZ()).thenReturn(2);
            when(base.getLevelBlockAtLayer(1)).thenReturn(secondLayer);
            when(level.getBlockIdAt(1, 64, 2, 1)).thenReturn(secondLayerId);
        }
    }
}
