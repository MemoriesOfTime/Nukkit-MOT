package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockSkullSkeleton;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySkull;
import cn.nukkit.item.ItemSkull;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.Hash;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacySkullCompatibilityTest {
    private static final int[] HEADS = {Block.WITHER_SKELETON_SKULL, Block.ZOMBIE_HEAD,
            Block.PLAYER_HEAD, Block.CREEPER_HEAD, Block.DRAGON_HEAD, Block.PIGLIN_HEAD};

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void legacyAndModernPalettesKeepNativeStatesAndCanonicalReverseMappings() throws Exception {
        for (GameVersion version : new GameVersion[]{GameVersion.V1_20_80, GameVersion.V1_21_2,
                GameVersion.V1_21_2_NETEASE, GameVersion.V1_21_50, GameVersion.V1_21_80,
                GameVersion.V1_26_40}) {
            BlockPalette palette = new BlockPalette(GlobalBlockPalette.getPaletteByProtocol(version).getGameVersion());
            Map<Integer, CompoundTag> raw = rawStates(palette.getGameVersion());
            for (int head : HEADS) {
                for (int meta = 0; meta < 6; meta++) {
                    int fullId = head << Block.DATA_BITS | meta;
                    CompoundTag nativeHead = raw.get(fullId);
                    CompoundTag expected = nativeHead != null ? nativeHead
                            : raw.get(Block.SKULL_BLOCK << Block.DATA_BITS | meta);
                    assertNotNull(expected, version + " " + head + ':' + meta);
                    int runtime = expected.getInt("runtimeId");
                    int nativeFullId = expected.getInt("id") << Block.DATA_BITS | expected.getShort("data");
                    CompoundTag state = expected.clone().remove("id").remove("data")
                            .remove("runtimeId").remove("stateOverload");
                    int hash = Hash.hashBlock(state);
                    assertEquals(runtime, palette.getRuntimeId(head, meta), version + " " + head + ':' + meta);
                    assertEquals(runtime, palette.getRuntimeIdByFullId(fullId));
                    assertEquals(hash, palette.getHashId(head, meta));
                    assertEquals(hash, palette.getHashIdByFullId(fullId));
                    assertEquals(nativeFullId, palette.getLegacyFullId(runtime));
                    assertEquals(nativeFullId, palette.getLegacyFullIdFromHashId(hash));
                    assertNotEquals(palette.getRuntimeId(Block.INFO_UPDATE), runtime);
                }
            }
            if (version == GameVersion.V1_21_2) {
                assertFalse(raw.containsKey(Block.PLAYER_HEAD << Block.DATA_BITS | 1),
                        "regression must exercise the reported client's missing split head");
            }
            if (version == GameVersion.V1_26_40) {
                assertTrue(raw.containsKey(Block.PLAYER_HEAD << Block.DATA_BITS | 1),
                        "modern palette must keep its native player head");
            }
        }
    }

    @Test
    void paletteRebuildRecreatesAliasesWithoutOverwritingNativeStates() {
        BlockPalette palette = new BlockPalette(GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_21_2).getGameVersion());
        palette.clearStates();
        CompoundTag skull = new CompoundTag().putString("name", "minecraft:skull")
                .putCompound("states", new CompoundTag().putInt("facing_direction", 1));
        palette.registerState(Block.SKULL_BLOCK, 1, 70001, skull);
        CompoundTag player = new CompoundTag().putString("name", "minecraft:player_head")
                .putCompound("states", new CompoundTag().putInt("facing_direction", 1));
        palette.registerState(Block.PLAYER_HEAD, 1, 70002, player);
        palette.lock();
        assertEquals(70001, palette.getRuntimeId(Block.ZOMBIE_HEAD, 1));
        assertEquals(Hash.hashBlock(skull), palette.getHashId(Block.ZOMBIE_HEAD, 1));
        assertEquals(70002, palette.getRuntimeId(Block.PLAYER_HEAD, 1));
        assertEquals(Hash.hashBlock(player), palette.getHashId(Block.PLAYER_HEAD, 1));
        assertEquals(Block.SKULL_BLOCK << Block.DATA_BITS | 1, palette.getLegacyFullId(70001));
        assertEquals(Block.PLAYER_HEAD << Block.DATA_BITS | 1, palette.getLegacyFullId(70002));
        assertFalse(palette.getLegacyToRuntimeIdMap().containsKey(Block.ZOMBIE_HEAD << Block.DATA_BITS | 2));
        palette.lock();
        assertEquals(70002, palette.getRuntimeId(Block.PLAYER_HEAD, 1));
        assertThrows(IllegalStateException.class, () -> palette.registerState(Block.STONE, 0, 70003, skull));
    }

    @Test
    void placedHeadsRetainTypeAndRotationInSpawnNbtAndStayValid() {
        Level level = mock(Level.class);
        FullChunk chunk = mock(FullChunk.class);
        Block position = Block.get(Block.AIR);
        for (int type = 0; type <= 6; type++) {
            ItemSkull item = new ItemSkull(type);
            BlockSkullSkeleton head = spy((BlockSkullSkeleton) item.getBlock());
            head.level = level;
            doReturn(chunk).when(head).getChunk();
            BlockEntitySkull entity = mock(BlockEntitySkull.class, CALLS_REAL_METHODS);
            doReturn(head).when(entity).getBlock();
            doNothing().when(entity).spawnToAll();
            try (MockedStatic<BlockEntity> factory = mockStatic(BlockEntity.class)) {
                factory.when(() -> BlockEntity.createBlockEntity(eq(BlockEntity.SKULL), eq(chunk), any(CompoundTag.class)))
                        .thenAnswer(invocation -> {
                            entity.namedTag = invocation.getArgument(2);
                            return entity;
                        });
                assertTrue(head.place(item, position, position, BlockFace.UP, 0, 0, 0, null));
                assertEquals(type, entity.getSpawnCompound().getByte("SkullType"));
                assertEquals(0, entity.getSpawnCompound().getByte("Rot"));
                assertTrue(entity.isBlockEntityValid(), "placed skull type " + type);
            }
        }
        BlockEntitySkull stale = mock(BlockEntitySkull.class, CALLS_REAL_METHODS);
        doReturn(Block.get(Block.STONE)).when(stale).getBlock();
        assertFalse(stale.isBlockEntityValid(), "a replaced skull must still be removed");
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, CompoundTag> rawStates(GameVersion version) throws Exception {
        String name = (version.isNetEase() ? "runtime_block_states_netease_" : "runtime_block_states_")
                + version.getProtocol() + ".dat";
        try (InputStream stream = LegacySkullCompatibilityTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(stream, name);
            ListTag<CompoundTag> states = (ListTag<CompoundTag>) NBTIO.readTag(
                    new BufferedInputStream(new GZIPInputStream(stream)), ByteOrder.BIG_ENDIAN, false);
            Map<Integer, CompoundTag> result = new HashMap<>();
            for (CompoundTag state : states.getAll()) {
                result.put(state.getInt("id") << Block.DATA_BITS | state.getShort("data"), state);
            }
            return result;
        }
    }
}
