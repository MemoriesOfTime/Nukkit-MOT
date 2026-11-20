package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockDirt;
import cn.nukkit.block.BlockID;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemFishingRod;
import cn.nukkit.item.ItemHoeStone;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression: activating a block (chest, trapdoor, ...) destroyed any tool in hand that was
 * already at max durability even though the activation never wore it. A fishing rod past its
 * old 65-point limit therefore vanished on the first chest click instead of breaking while
 * reeling.
 */
class ItemUseOnToolBreakTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void activationDoesNotBreakAnUnrelatedMaxedItemInHand() {
        ItemFishingRod rod = new ItemFishingRod();
        rod.setDamage(rod.getMaxDurability());

        Item result = useOnActivatedBlock(rod, 0);

        assertSame(rod, result, "a rod the activation never wore must survive");
        assertEquals(1, result.getCount());
        assertEquals(rod.getMaxDurability(), result.getDamage());
    }

    @Test
    void activationBreaksTheToolItActuallyWorePastItsLimit() {
        ItemFishingRod rod = new ItemFishingRod();
        rod.setDamage(rod.getMaxDurability() - 1);

        Item result = useOnActivatedBlock(rod, 1);

        assertTrue(result.isNull(), "a tool worn past its limit must break");
    }

    @Test
    void realBlockActivationStillBreaksTheToolItWore() {
        Level level = mock(Level.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        BlockDirt dirt = new BlockDirt();
        dirt.level = level;
        BlockAir air = new BlockAir();

        doAnswer(invocation -> invocation.<Integer>getArgument(1) == 1 ? air : dirt)
                .when(level).getBlock(anyInt(), anyInt(), anyInt(), anyInt());
        doReturn(true).when(level).isYInRange(anyInt());
        doReturn(true).when(level).setBlock(any(Vector3.class), any(Block.class), anyBoolean());
        doNothing().when(level).addSoundToViewers(any(Vector3.class), any(Sound.class));
        doNothing().when(level).addParticle(any(Particle.class));

        ItemHoeStone hoe = new ItemHoeStone();
        hoe.setDamage(hoe.getMaxDurability() - 1);

        Item result = level.useItemOn(new Vector3(0, 0, 0), hoe, BlockFace.UP, 0.5f, 0.5f, 0.5f);

        // Tilling the dirt damages the hoe inside BlockDirt#onActivate; the guard must keep that break.
        assertTrue(hoe.getDamage() >= hoe.getMaxDurability(), "tilling must wear the hoe past its limit");
        assertTrue(result.isNull(), "a hoe worn past its limit by tilling must break");
    }

    private static Item useOnActivatedBlock(Item item, int wear) {
        Level level = mock(Level.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        Block target = mock(Block.class);
        Block side = mock(Block.class);

        when(target.getId()).thenReturn(BlockID.CHEST);
        when(target.getSide(any(BlockFace.class))).thenReturn(side);
        when(target.canBeActivated()).thenReturn(true);
        when(target.onActivate(any(Item.class), isNull())).thenAnswer(invocation -> {
            Item used = invocation.getArgument(0);
            used.setDamage(used.getDamage() + wear);
            return true;
        });
        // doReturn: CALLS_REAL_METHODS would run the real lookup while the stub is registered
        doReturn(target).when(level).getBlock(any(Vector3.class));
        doReturn(true).when(level).isYInRange(anyInt());
        doNothing().when(level).addSoundToViewers(any(Vector3.class), any(Sound.class));
        doNothing().when(level).addParticle(any(Particle.class));
        return level.useItemOn(new Vector3(0, 0, 0), item, BlockFace.UP, 0.5f, 0.5f, 0.5f);
    }
}
