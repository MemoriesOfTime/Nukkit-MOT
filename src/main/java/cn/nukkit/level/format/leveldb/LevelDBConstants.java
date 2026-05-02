package cn.nukkit.level.format.leveldb;

import cn.nukkit.GameVersion;
import cn.nukkit.level.format.leveldb.structure.ChunkState;
import cn.nukkit.nbt.tag.IntTag;
import cn.nukkit.utils.Binary;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

import static org.cloudburstmc.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext.makeVersion;

public final class LevelDBConstants {
    public static final int SUB_CHUNK_2D_SIZE = 16 * 16;
    public static final int SUB_CHUNK_SIZE = 16 * SUB_CHUNK_2D_SIZE;

    public static final byte FINALISATION_NEEDS_INSTATICKING = 0;
    public static final byte FINALISATION_NEEDS_POPULATION = 1;
    public static final byte FINALISATION_DONE = 2;

    public static final byte CURRENT_STORAGE_VERSION = 9;
    public static final byte CURRENT_LEVEL_CHUNK_VERSION = 41; // 1.21.40
    public static final byte CURRENT_LEVEL_SUBCHUNK_VERSION = 9;
    public static final byte CURRENT_LEVEL_LEGACY_CHUNK_VERSION = 7;
    public static final byte CURRENT_LEVEL_BLENDING_VERSION = 8; // 1.21.100+

    private static final byte LATEST_STORAGE_VERSION = 10; // 1.19.40
    private static final byte LATEST_LEVEL_CHUNK_VERSION = 41; // 1.21.40
    private static final byte LATEST_LEVEL_SUBCHUNK_VERSION = 9; // 1.18

    public static final int CURRENT_NUKKIT_DATA_VERSION = 8;
    public static final long NUKKIT_DATA_MAGIC = 0x20221231fe0100ffL;

    public static final byte[] CHUNK_VERSION_SAVE_DATA = new byte[]{CURRENT_LEVEL_CHUNK_VERSION};
    public static final byte[] LEGACY_CHUNK_VERSION_SAVE_DATA = new byte[]{CURRENT_LEVEL_LEGACY_CHUNK_VERSION};
    public static final byte[] GENERATED_PRE_CAVES_AND_CLIFFS_BLENDING_SAVE_DATA = new byte[]{0};
    public static final byte[] BLENDING_DATA_SAVE_DATA = new byte[]{0, CURRENT_LEVEL_BLENDING_VERSION};

    public static ChunkState deserializeFinalizationState(@Nullable byte[] finalized) {
        if (finalized == null || finalized.length == 0) {
            return ChunkState.FINISHED;
        }

        int finalizationState = readFinalizationStateValue(finalized);
        return switch (finalizationState) {
            case FINALISATION_NEEDS_INSTATICKING -> ChunkState.GENERATED;
            case FINALISATION_NEEDS_POPULATION -> ChunkState.POPULATED;
            case FINALISATION_DONE, 3 -> ChunkState.FINISHED;
            default -> throw new IllegalArgumentException("Unsupported chunk finalization state: " + finalizationState);
        };
    }

    public static int readFinalizationStateValue(@Nullable byte[] finalized) {
        if (finalized == null || finalized.length == 0) {
            return -1;
        }

        return finalized.length >= Integer.BYTES ? Binary.readLInt(finalized) : finalized[0] & 0xFF;
    }

    public static byte[] serializeFinalizationState(ChunkState state) {
        return switch (state) {
            case GENERATED -> Binary.writeLInt(FINALISATION_NEEDS_INSTATICKING);
            case POPULATED -> Binary.writeLInt(FINALISATION_NEEDS_POPULATION);
            case FINISHED -> Binary.writeLInt(FINALISATION_DONE);
            case NEW -> throw new IllegalArgumentException("Ungenerated chunks do not have a Bedrock finalization state");
        };
    }

    /**
     * This is protocol version if block palette used in storage
     */
    public static final int PALETTE_VERSION = GameVersion.getFeatureVersion().getProtocol();

    public static final int STATE_MAYOR_VERSION = 1;
    public static final int STATE_MINOR_VERSION = 21;
    public static final int STATE_PATCH_VERSION = 60;

    /**
     * Block state schema version written inside block state NBT and consumed by
     * block state updaters. This is independent from the world/game version
     * stored in level.dat. Keep this value in sync with the first palette
     * version stored in leveldb_palette.nbt; LevelDBConstantsTest verifies that
     * contract.
     */
    public static final int STATE_VERSION = makeVersion(STATE_MAYOR_VERSION, STATE_MINOR_VERSION, STATE_PATCH_VERSION) + 33; //33 update

    /**
     * Game/client version written to level.dat fields such as
     * MinimumCompatibleClientVersion and lastOpenedWithVersion.
     * Do not derive this from STATE_*; block state schema versions can lag behind
     * the current world/game version.
     */
    public static final List<IntTag> CURRENT_LEVEL_VERSION = Collections.unmodifiableList(ObjectArrayList.of(
            new IntTag("", 1), // major
            new IntTag("", 26), // minor
            new IntTag("", 10), // patch
            new IntTag("", 0), // revision
            new IntTag("", 0))); // beta

    public static final String DEFAULT_FLAT_WORLD_LAYERS = "{\"biome_id\":1,\"block_layers\":[{\"block_name\":\"minecraft:bedrock\",\"count\":1},{\"block_name\":\"minecraft:dirt\",\"count\":2},{\"block_name\":\"minecraft:grass\",\"count\":1}],\"encoding_version\":6,\"structure_options\":null,\"world_version\":\"version.post_1_18\"}";

    private LevelDBConstants() {
        throw new IllegalStateException();
    }
}
