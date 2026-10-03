package cn.nukkit.block;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockBushTest {

    @Test
    void dirtLikeBlocksSupportBushes() {
        List<Block> supports = List.of(
                new BlockGrass(),
                new BlockMycelium(),
                new BlockPodzol(),
                new BlockDirt(),
                new BlockDirt(1),
                new BlockDirtRooted(),
                new BlockFarmland(),
                new BlockMud(),
                new BlockMangroveRootsMuddy(),
                new BlockMoss(),
                new BlockMossPale()
        );

        assertAll(supports.stream()
                .map(support -> () -> assertTrue(BlockBush.isSupportValid(support), support.getName())));
    }

    @Test
    void nonDirtBlocksDoNotSupportBushes() {
        assertFalse(BlockBush.isSupportValid(new BlockStone()));
    }
}
