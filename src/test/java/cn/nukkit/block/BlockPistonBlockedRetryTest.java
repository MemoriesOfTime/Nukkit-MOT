package cn.nukkit.block;

import cn.nukkit.blockentity.BlockEntityPistonArm;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockPistonBlockedRetryTest {

    @BeforeAll
    static void initBlocks() {
        Block.init();
    }

    @Test
    void blockedExtensionDoesNotCommitPoweredStateSoLaterUpdatesCanRetry() {
        Level level = mock(Level.class);
        BlockEntityPistonArm arm = mock(BlockEntityPistonArm.class);
        arm.state = 0;
        arm.powered = false;

        when(level.getBlockEntity(any(Vector3.class))).thenReturn(arm);
        when(level.isSidePowered(any(Block.class), any())).thenReturn(true);
        when(level.getMinBlockY()).thenReturn(-64);
        when(level.getMaxBlockY()).thenReturn(319);
        when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            Vector3 position = new Vector3(
                    ((Number) invocation.getArgument(0)).intValue(),
                    ((Number) invocation.getArgument(1)).intValue(),
                    ((Number) invocation.getArgument(2)).intValue());
            Block block = Block.get(BlockID.BEDROCK);
            block.position(Position.fromObject(position, level));
            block.level = level;
            return block;
        });

        BlockPiston piston = new BlockPiston();
        piston.position(new Position(0, 64, 0, level));
        piston.level = level;

        piston.onUpdate(Level.BLOCK_UPDATE_NORMAL);

        assertFalse(arm.powered,
                "A blocked piston must retain the previous powered state so a later neighbor update retries extension");
    }
}
