package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.blockentity.BlockEntitySkull;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSkull;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkullDropRecoveryTest {
    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void missingTileDropsTheTypeEncodedByTheBlock() {
        for (int type = 0; type <= 6; type++) {
            BlockSkullSkeleton head = head(type);
            assertDrop(head, type);
        }
    }

    @Test
    void existingTileKeepsItsSkullTypeEvenWhenItDiffersFromTheBlock() {
        for (int type = 0; type <= 6; type++) {
            BlockSkullSkeleton head = head(type);
            int savedType = (type + 1) % 7;
            BlockEntitySkull tile = mock(BlockEntitySkull.class);
            tile.namedTag = new CompoundTag().putByte("SkullType", savedType);
            when(head.level.getBlockEntity(head)).thenReturn(tile);
            assertDrop(head, savedType);
        }
    }

    @Test
    void existingTileWithoutTypeKeepsLegacyDefaultRatherThanInventingData() {
        for (int type = 0; type <= 6; type++) {
            BlockSkullSkeleton head = head(type);
            BlockEntitySkull tile = mock(BlockEntitySkull.class);
            tile.namedTag = new CompoundTag();
            when(head.level.getBlockEntity(head)).thenReturn(tile);
            assertDrop(head, 0);
        }
    }

    private static BlockSkullSkeleton head(int type) {
        BlockSkullSkeleton head = (BlockSkullSkeleton) new ItemSkull(type).getBlock();
        head.level = mock(Level.class);
        return head;
    }

    private static void assertDrop(BlockSkullSkeleton head, int expectedType) {
        Item[] drops = head.getDrops(Item.AIR_ITEM);
        assertEquals(1, drops.length);
        assertEquals(Item.SKULL, drops[0].getId());
        assertEquals(1, drops[0].getCount());
        assertEquals(expectedType, drops[0].getDamage(), "block " + head.getId());
        assertFalse(drops[0].hasCompoundTag(), "missing custom NBT must not be fabricated");
    }
}
