package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockWater;
import cn.nukkit.block.custom.CustomBlockManager;
import cn.nukkit.entity.mob.EntityZombie;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot;
import cn.nukkit.level.format.leveldb.structure.ChunkState;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntitySubmergedTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void onlyTheWaterIdsMaterialiseAsWater() {
        // The fast path answers "dry" from the raw ids; it relies on no other registered id being water.
        for (int id = 0; id < Block.MAX_BLOCK_ID; id++) {
            if (Entity.mayMaterialiseAsWater(id) || Block.list[id] == null) {
                continue;
            }
            for (int meta = 0; meta < 16; meta++) {
                Block block;
                try {
                    block = Block.get(id, meta);
                } catch (Throwable t) {
                    continue;
                }
                assertFalse(block instanceof BlockWater, "id " + id + " meta " + meta + " is water");
            }
        }
    }

    @Test
    void sameAnswerAsMaterialisingBothLayers() {
        int[] layer0 = {BlockID.AIR, BlockID.STONE, BlockID.WATER, BlockID.STILL_WATER, BlockID.SEAGRASS,
                BlockID.BLOCK_KELP, BlockID.LAVA, 2040, CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID + 991};
        int[] layer1 = {BlockID.AIR, BlockID.WATER, BlockID.STILL_WATER, BlockID.STONE};
        for (int bottom : layer0) {
            for (int top : layer1) {
                for (int meta : new int[]{0, 7, 15}) {
                    Fixture fixture = new Fixture(bottom, top, meta);
                    boolean expected = fixture.reference();
                    clearInvocations(fixture.level);
                    assertEquals(expected, fixture.entity.isSubmerged(), bottom + "/" + top + ":" + meta);
                    if (!Entity.mayMaterialiseAsWater(bottom) && !Entity.mayMaterialiseAsWater(top)) {
                        verify(fixture.level, never()).getBlock(any(Vector3.class));
                        verify(fixture.level, never()).getBlock(any(Vector3.class), anyInt());
                    }
                }
            }
        }
    }

    @Test
    void foreignChunkTakesTheOriginalPath() {
        Fixture fixture = new Fixture(BlockID.STILL_WATER, BlockID.AIR, 0);
        fixture.entity.chunk = null;
        assertTrue(fixture.entity.isSubmerged());
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final EntityZombie entity = mock(EntityZombie.class, Mockito.withSettings().defaultAnswer(CALLS_REAL_METHODS));
        final LevelDBChunk chunk;

        Fixture(int bottom, int top, int meta) {
            LevelDBProvider provider = mock(LevelDBProvider.class);
            when(provider.getLevel()).thenReturn(level);
            when(level.getProvider()).thenReturn(provider);
            when(level.getDimensionData()).thenReturn(DimensionData.LEGACY_DIMENSION);
            when(level.getMinBlockY()).thenReturn(0);
            when(level.getMaxBlockY()).thenReturn(255);
            when(level.isYInRange(anyInt())).thenCallRealMethod();
            StateBlockStorage layer0 = new StateBlockStorage();
            StateBlockStorage layer1 = new StateBlockStorage();
            // The eye cell of a zombie standing at (3.5, 64, 5.5).
            layer0.set(3, 65 & 0xF, 5, BlockStateSnapshot.builder().legacyId(bottom).legacyData(meta).build());
            layer1.set(3, 65 & 0xF, 5, BlockStateSnapshot.builder().legacyId(top).legacyData(0).build());
            ChunkSection[] sections = new ChunkSection[16];
            sections[4] = new LevelDBChunkSection(4, new StateBlockStorage[]{layer0, layer1}, false);
            chunk = new LevelDBChunk(provider, 0, 0, sections, null, null, null, null, null, ChunkState.FINISHED);
            when(level.getChunk(anyInt(), anyInt())).thenAnswer(inv -> (int) inv.getArgument(0) == 0 && (int) inv.getArgument(1) == 0 ? chunk : null);
            when(level.getBlock(any(Vector3.class))).thenCallRealMethod();
            when(level.getBlock(any(Vector3.class), anyInt())).thenCallRealMethod();
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenCallRealMethod();
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenCallRealMethod();
            when(level.getBlock(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenCallRealMethod();
            entity.level = level;
            entity.chunk = chunk;
            entity.x = 3.5;
            entity.y = 64;
            entity.z = 5.5;
            entity.temporalVector = new Vector3();
        }

        /** Entity.isSubmerged before the fast path. */
        boolean reference() {
            double y = entity.y + entity.getEyeHeight();
            Block block = level.getBlock(new Vector3(Math.floor(entity.x), Math.floor(y), Math.floor(entity.z)));
            return block instanceof BlockWater || level.getBlock(block, 1) instanceof BlockWater;
        }
    }
}
