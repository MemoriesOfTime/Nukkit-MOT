package cn.nukkit.level.format.leveldb;

import cn.nukkit.nbt.tag.IntTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Collections;
import java.util.List;

import static org.cloudburstmc.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext.makeVersion;

public final class LevelDBConstants {
    public static final int SUB_CHUNK_2D_SIZE = 16 * 16;
    public static final int SUB_CHUNK_SIZE = 16 * SUB_CHUNK_2D_SIZE;

    public static final byte FINALISATION_NEEDS_INSTATICKING = 0;
    public static final byte FINALISATION_NEEDS_POPULATION = 1;
    public static final byte FINALISATION_DONE = 2;

    public static final byte CURRENT_STORAGE_VERSION = 8;
    public static final byte CURRENT_LEVEL_CHUNK_VERSION = 41; // 1.21.40
    public static final byte CURRENT_LEVEL_SUBCHUNK_VERSION = 8;

    private static final byte LATEST_STORAGE_VERSION = 10; // 1.19.40
    private static final byte LATEST_LEVEL_CHUNK_VERSION = 41; // 1.21.40
    private static final byte LATEST_LEVEL_SUBCHUNK_VERSION = 9; // 1.18

    public static final int CURRENT_NUKKIT_DATA_VERSION = 8;
    public static final long NUKKIT_DATA_MAGIC = 0x20221231fe0100ffL;

    public static final byte[] CHUNK_VERSION_SAVE_DATA = new byte[]{CURRENT_LEVEL_CHUNK_VERSION};


    /**
     * This is protocol version if block palette used in storage
     */
    public static final int PALETTE_VERSION = ProtocolInfo.v1_21_60;

    public static final int STATE_MAYOR_VERSION = 1;
    public static final int STATE_MINOR_VERSION = 21;
    public static final int STATE_PATCH_VERSION = 60;

    public static final int STATE_VERSION = makeVersion(STATE_MAYOR_VERSION, STATE_MINOR_VERSION, STATE_PATCH_VERSION) + 33; //33 update
    public static final List<IntTag> CURRENT_LEVEL_VERSION = Collections.unmodifiableList(ObjectArrayList.of(
            new IntTag("", STATE_MAYOR_VERSION), // major
            new IntTag("", STATE_MINOR_VERSION), // minor
            new IntTag("", STATE_PATCH_VERSION), // patch
            new IntTag("", 0), // revision
            new IntTag("", 0))); // beta

    public static final String DEFAULT_FLAT_WORLD_LAYERS = "{\"biome_id\":1,\"block_layers\":[{\"block_name\":\"minecraft:bedrock\",\"count\":1},{\"block_name\":\"minecraft:dirt\",\"count\":2},{\"block_name\":\"minecraft:grass\",\"count\":1}],\"encoding_version\":6,\"structure_options\":null,\"world_version\":\"version.post_1_18\"}";

    private LevelDBConstants() {
        throw new IllegalStateException();
    }
}
