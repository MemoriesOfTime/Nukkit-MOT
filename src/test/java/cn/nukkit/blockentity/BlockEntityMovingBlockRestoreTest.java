package cn.nukkit.blockentity;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class BlockEntityMovingBlockRestoreTest {

    private static final String TEST_BLOCK_ENTITY = "MovingBlockRestoreTest";

    @BeforeAll
    static void initRegistries() {
        Block.init();
        BlockEntity.registerBlockEntity(TEST_BLOCK_ENTITY, RestoredTestBlockEntity.class);
    }

    @Test
    void orphanedMovingBlockRestoresItsSavedBlockEntity() {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().when(provider.getLevel()).thenReturn(level);
        lenient().when(level.getServer()).thenReturn(server);
        lenient().when(level.getChunk(0, 0)).thenReturn(chunk);
        lenient().when(level.isChunkLoaded(0, 0)).thenReturn(false);

        CompoundTag movingEntity = new CompoundTag()
                .putString("id", TEST_BLOCK_ENTITY)
                .putInt("x", 0)
                .putInt("y", 64)
                .putInt("z", 0)
                .putInt("payload", 42);
        CompoundTag nbt = BlockEntity.getDefaultCompound(new Vector3(0, 64, 0), BlockEntity.MOVING_BLOCK)
                .putInt("pistonPosX", 0)
                .putInt("pistonPosY", 64)
                .putInt("pistonPosZ", 0)
                .putCompound("movingBlock", new CompoundTag()
                        .putInt("id", BlockID.CHEST)
                        .putInt("meta", 0))
                .putCompound("movingEntity", movingEntity);

        BlockEntityMovingBlock movingBlock = new BlockEntityMovingBlock(chunk, nbt);
        clearInvocations(chunk);

        movingBlock.onUpdate();

        ArgumentCaptor<BlockEntity> restoredCaptor = ArgumentCaptor.forClass(BlockEntity.class);
        verify(chunk).addBlockEntity(restoredCaptor.capture());
        RestoredTestBlockEntity restored = (RestoredTestBlockEntity) restoredCaptor.getValue();
        assertEquals(42, restored.namedTag.getInt("payload"));
        assertEquals(0, restored.getFloorX());
        assertEquals(64, restored.getFloorY());
        assertEquals(0, restored.getFloorZ());
    }

    public static final class RestoredTestBlockEntity extends BlockEntity {

        public RestoredTestBlockEntity(FullChunk chunk, CompoundTag nbt) {
            super(chunk, nbt);
        }

        @Override
        public boolean isBlockEntityValid() {
            return true;
        }
    }
}
