package cn.nukkit.event.block;

import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AutomaticBlockDropsEventTest {

    @Test
    void exposesTheSourceBlockAndCanReplaceOrCancelTheWholeDropBatch() {
        Block source = mock(Block.class);
        Block companion = mock(Block.class);
        Item first = mock(Item.class);
        Item replacement = mock(Item.class);

        AutomaticBlockDropsEvent event =
                new AutomaticBlockDropsEvent(
                        source, new Item[] {first}, new Block[] {source, companion}, true);

        assertSame(source, event.getBlock());
        assertArrayEquals(new Item[] {first}, event.getDrops());
        assertArrayEquals(new Block[] {source, companion}, event.getChangedBlocks());
        assertTrue(event.isOutermostBreak());
        assertFalse(event.isCancelled());

        event.setDrops(new Item[] {replacement});
        event.setCancelled();

        assertArrayEquals(new Item[] {replacement}, event.getDrops());
        assertTrue(event.isCancelled());
    }
}
