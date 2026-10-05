package cn.nukkit.level;

import cn.nukkit.block.BlockID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CreativePlacementDropGuardTest {

    private static final int MOSS_CARPET = BlockID.MOSS_CARPET;

    private final Level world = mock(Level.class);

    @Test
    void suppressesAnyAutomaticDropFromTheCreativeBlockBeingPlaced() {
        try (CreativePlacementDropGuard.Scope ignored =
                     CreativePlacementDropGuard.enter(world, true, MOSS_CARPET, 5, 10, 5)) {
            assertTrue(
                    CreativePlacementDropGuard.suppresses(
                            world, true, MOSS_CARPET, 5, 10, 5, false));
        }
    }

    @Test
    void keepsTheSameAutomaticDropFromSurvivalPlacement() {
        try (CreativePlacementDropGuard.Scope ignored =
                     CreativePlacementDropGuard.enter(world, false, MOSS_CARPET, 5, 10, 5)) {
            assertFalse(
                    CreativePlacementDropGuard.suppresses(
                            world, true, MOSS_CARPET, 5, 10, 5, false));
        }
    }

    @Test
    void keepsHonestDropsFromAdjacentBlocksAndOtherWorlds() {
        Level otherWorld = mock(Level.class);
        try (CreativePlacementDropGuard.Scope ignored =
                     CreativePlacementDropGuard.enter(world, true, MOSS_CARPET, 5, 10, 5)) {
            assertFalse(
                    CreativePlacementDropGuard.suppresses(
                            world, true, MOSS_CARPET, 6, 10, 5, false));
            assertFalse(
                    CreativePlacementDropGuard.suppresses(
                            world, true, BlockID.TORCH, 5, 10, 5, false));
            assertFalse(
                    CreativePlacementDropGuard.suppresses(
                            otherWorld, true, MOSS_CARPET, 5, 10, 5, false));
        }
    }

    @Test
    void keepsPlayerAuthoredAndShulkerDrops() {
        try (CreativePlacementDropGuard.Scope ignored =
                     CreativePlacementDropGuard.enter(world, true, MOSS_CARPET, 5, 10, 5)) {
            assertFalse(
                    CreativePlacementDropGuard.suppresses(
                            world, false, MOSS_CARPET, 5, 10, 5, false));
            assertFalse(
                    CreativePlacementDropGuard.suppresses(
                            world, true, MOSS_CARPET, 5, 10, 5, true));
        }
    }

    @Test
    void innerSurvivalPlacementMasksAnOuterCreativePlacement() {
        try (CreativePlacementDropGuard.Scope outer =
                     CreativePlacementDropGuard.enter(world, true, MOSS_CARPET, 5, 10, 5)) {
            try (CreativePlacementDropGuard.Scope inner =
                         CreativePlacementDropGuard.enter(
                                 world, false, MOSS_CARPET, 5, 10, 5)) {
                assertFalse(
                        CreativePlacementDropGuard.suppresses(
                                world, true, MOSS_CARPET, 5, 10, 5, false));
            }
            assertTrue(
                    CreativePlacementDropGuard.suppresses(
                            world, true, MOSS_CARPET, 5, 10, 5, false));
        }
    }

    @Test
    void closingAfterFailureCannotLeakCreativeOriginIntoTheNextDrop() {
        try {
            try (CreativePlacementDropGuard.Scope ignored =
                         CreativePlacementDropGuard.enter(world, true, MOSS_CARPET, 5, 10, 5)) {
                throw new IllegalStateException("placement failed");
            }
        } catch (IllegalStateException expected) {
            // The scope must be closed by try-with-resources before the next drop is inspected.
        }

        assertFalse(
                CreativePlacementDropGuard.suppresses(
                        world, true, MOSS_CARPET, 5, 10, 5, false));
    }
}
