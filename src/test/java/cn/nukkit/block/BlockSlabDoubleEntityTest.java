package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.CollisionHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockSlabDoubleEntityTest {

    // An entity standing on a bottom slab at (0, 64, 0): it is outside the slab, but inside the empty upper half.
    private static final AxisAlignedBB STANDING_ON_SLAB = new SimpleAxisAlignedBB(0.2, 64.5, 0.2, 0.8, 66.3, 0.8);

    @BeforeAll
    static void initialize() {
        Block.init();
    }

    @Test
    void clickingTheTopFaceDoesNotMergeOverAnEntity() {
        assertFalse(placeOnTopFace(colliding(true)));
    }

    @Test
    void clickingTheTopFaceMergesWithoutAnEntity() {
        assertTrue(placeOnTopFace(null));
    }

    @Test
    void clickingANeighbourDoesNotMergeOverAnEntity() {
        assertFalse(placeFromSide(colliding(true)));
    }

    @Test
    void clickingANeighbourMergesWithoutAnEntity() {
        assertTrue(placeFromSide(null));
    }

    @Test
    void entitiesWithoutCollisionDoNotBlockTheMerge() {
        assertTrue(placeOnTopFace(colliding(false)));
        assertTrue(placeOnTopFace(mock(EntityItem.class)));
    }

    private static Entity colliding(boolean canCollide) {
        Entity entity = mock(Entity.class);
        when(entity.canCollide()).thenReturn(canCollide);
        return entity;
    }

    private static boolean placeOnTopFace(Entity entity) {
        return place(entity, BlockFace.UP, true);
    }

    private static boolean placeFromSide(Entity entity) {
        return place(entity, BlockFace.WEST, false);
    }

    private static boolean place(Entity entity, BlockFace face, boolean targetIsSlab) {
        Level level = mock(Level.class);
        doCallRealMethod().when(level).isBlockPlacementObstructed(any(), any());
        Block slab = Block.get(Block.SLAB, 0, level, 0, 64, 0);
        Block neighbour = Block.get(Block.STONE, 0, level, 1, 64, 0);
        Block target = targetIsSlab ? slab : neighbour;
        Block hand = Block.get(Block.SLAB, 0, level, 0, targetIsSlab ? 65 : 64, 0);
        Block placedInto = targetIsSlab ? Block.get(Block.AIR, 0, level, 0, 65, 0) : slab;

        try (MockedStatic<CollisionHelper> collisions = mockStatic(CollisionHelper.class)) {
            collisions.when(() -> CollisionHelper.getCollidingEntities(eq(level), any(AxisAlignedBB.class))).thenAnswer(inv -> {
                List<Entity> found = new ArrayList<>();
                if (entity != null && inv.<AxisAlignedBB>getArgument(1).intersectsWith(STANDING_ON_SLAB)) {
                    found.add(entity);
                }
                return found;
            });
            boolean placed = hand.place(Item.get(Item.SLAB), placedInto, target, face, 0.5, 0.5, 0.5, mock(Player.class));
            verify(level, placed ? times(1) : never()).setBlock(any(Vector3.class), argThat(b -> b.getId() == Block.DOUBLE_SLAB), eq(true));
            return placed;
        }
    }
}
