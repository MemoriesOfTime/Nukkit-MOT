package cn.nukkit.event.block;

import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AutomaticBlockDropsCauseTest {

    @Test
    void breakCausesRetainSourceAndTerminalBookkeeping() {
        Block source = mock(Block.class);
        Block[] changed = new Block[] {source};

        for (AutomaticBlockDropsEvent.Cause cause : new AutomaticBlockDropsEvent.Cause[] {
                AutomaticBlockDropsEvent.Cause.AUTOMATIC_BREAK,
                AutomaticBlockDropsEvent.Cause.PLAYER_BREAK}) {
            AutomaticBlockDropsEvent event =
                    new AutomaticBlockDropsEvent(source, new Item[0], changed, true, cause);
            assertEquals(cause, event.getCause());
            assertSame(source, event.getBlock());
            assertSame(changed, event.getChangedBlocks());
            assertTrue(event.isOutermostBreak());
        }
    }

    @Test
    void playerBreakKeepsNestedBatchesCancellable() {
        Block source = mock(Block.class);
        Item drop = mock(Item.class);
        AutomaticBlockDropsEvent event = new AutomaticBlockDropsEvent(
                source, new Item[] {drop}, new Block[] {source}, false,
                AutomaticBlockDropsEvent.Cause.PLAYER_BREAK);

        assertFalse(event.isOutermostBreak());
        assertArrayEquals(new Item[] {drop}, event.getDrops());
        event.setCancelled();
        assertTrue(event.isCancelled());
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
