package cn.nukkit.event.block;

import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AutomaticBlockDropsCauseTest {

    @Test
    void legacyConstructorRetainsAutomaticBreakAndTerminalBookkeeping() {
        Block source = mock(Block.class);
        Block[] changed = new Block[] {source};
        AutomaticBlockDropsEvent legacy =
                new AutomaticBlockDropsEvent(source, new Item[0], changed, true);
        AutomaticBlockDropsEvent explicit = new AutomaticBlockDropsEvent(
                source, new Item[0], changed, true,
                AutomaticBlockDropsEvent.Cause.AUTOMATIC_BREAK);

        for (AutomaticBlockDropsEvent event : new AutomaticBlockDropsEvent[] {legacy, explicit}) {
            assertEquals(AutomaticBlockDropsEvent.Cause.AUTOMATIC_BREAK, event.getCause());
            assertSame(source, event.getBlock());
            assertSame(changed, event.getChangedBlocks());
            assertTrue(event.isOutermostBreak());
        }
    }

    @Test
    void interactionRetainsSourceAndMutableBatchWithoutBreakCompletion() {
        Block source = mock(Block.class);
        Item seed = mock(Item.class);
        Item replacement = mock(Item.class);
        AutomaticBlockDropsEvent event = new AutomaticBlockDropsEvent(
                source, new Item[] {seed}, Block.EMPTY_ARRAY, false,
                AutomaticBlockDropsEvent.Cause.BLOCK_INTERACTION);

        assertEquals(AutomaticBlockDropsEvent.Cause.BLOCK_INTERACTION, event.getCause());
        assertSame(source, event.getBlock());
        assertArrayEquals(new Item[] {seed}, event.getDrops());
        assertEquals(0, event.getChangedBlocks().length);
        assertFalse(event.isOutermostBreak());
        assertFalse(event.isCancelled());
        event.setDrops(new Item[] {replacement});
        event.setCancelled();
        assertArrayEquals(new Item[] {replacement}, event.getDrops());
        assertTrue(event.isCancelled());
    }

    @Test
    void interactionCannotClaimChangedCellsOrTerminalCleanup() {
        Block source = mock(Block.class);
        assertThrows(IllegalArgumentException.class, () -> new AutomaticBlockDropsEvent(
                source, new Item[0], new Block[] {source}, false,
                AutomaticBlockDropsEvent.Cause.BLOCK_INTERACTION));
        assertThrows(IllegalArgumentException.class, () -> new AutomaticBlockDropsEvent(
                source, new Item[0], Block.EMPTY_ARRAY, true,
                AutomaticBlockDropsEvent.Cause.BLOCK_INTERACTION));
        assertThrows(IllegalArgumentException.class, () -> new AutomaticBlockDropsEvent(
                source, new Item[0], null, false,
                AutomaticBlockDropsEvent.Cause.BLOCK_INTERACTION));
    }

    @Test
    void explicitCauseCannotBeMissing() {
        assertThrows(NullPointerException.class, () -> new AutomaticBlockDropsEvent(
                mock(Block.class), new Item[0], Block.EMPTY_ARRAY, false, null));
    }
}
