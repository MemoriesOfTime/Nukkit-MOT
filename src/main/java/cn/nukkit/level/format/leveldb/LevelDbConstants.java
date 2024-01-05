package cn.nukkit.level.format.leveldb;

import cn.nukkit.nbt.tag.IntTag;
import cn.nukkit.utils.Binary;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Collections;
import java.util.List;

public final class LevelDbConstants {
    public static final int SUB_CHUNK_2D_SIZE = 16 * 16;
    public static final int SUB_CHUNK_SIZE = 16 * SUB_CHUNK_2D_SIZE;

    public static final byte FINALISATION_NEEDS_INSTATICKING = 0;
    public static final byte FINALISATION_NEEDS_POPULATION = 1;
    public static final byte FINALISATION_DONE = 2;

    public static final byte CURRENT_STORAGE_VERSION = 8;
    public static final byte CURRENT_LEVEL_CHUNK_VERSION = 40; // 1.18.0.25 beta
    public static final byte CURRENT_LEVEL_SUBCHUNK_VERSION = 8;

    private static final byte LATEST_STORAGE_VERSION = 10; // 1.19.40
    private static final byte LATEST_LEVEL_CHUNK_VERSION = 40; // 1.18.30
    private static final byte LATEST_LEVEL_SUBCHUNK_VERSION = 9; // 1.18

    public static final int CURRENT_NUKKIT_DATA_VERSION = 8;
    public static final long NUKKIT_DATA_MAGIC = 0x20221231fe0100ffL;

    public static final byte[] CHUNK_VERSION_SAVE_DATA = new byte[]{CURRENT_LEVEL_CHUNK_VERSION};
    public static final byte[] FINALISATION_GENERATION_SAVE_DATA = Binary.writeLInt(FINALISATION_NEEDS_INSTATICKING);
    public static final byte[] FINALISATION_POPULATION_SAVE_DATA = Binary.writeLInt(FINALISATION_NEEDS_POPULATION);
    public static final byte[] FINALISATION_DONE_SAVE_DATA = Binary.writeLInt(FINALISATION_DONE);

    public static final List<IntTag> CURRENT_COMPATIBLE_CLIENT_VERSION = Collections.unmodifiableList(ObjectArrayList.of(
            new IntTag("", 1), // major
            new IntTag("", 18), // minor
            new IntTag("", 30), // patch
            new IntTag("", 0), // revision
            new IntTag("", 0))); // beta

    public static final String DEFAULT_FLAT_WORLD_LAYERS = "{\"biome_id\":1,\"block_layers\":[{\"block_name\":\"minecraft:bedrock\",\"count\":1},{\"block_name\":\"minecraft:dirt\",\"count\":2},{\"block_name\":\"minecraft:grass\",\"count\":1}],\"encoding_version\":6,\"structure_options\":null,\"world_version\":\"version.post_1_18\"}";

    private LevelDbConstants() {
        throw new IllegalStateException();
    }
}
