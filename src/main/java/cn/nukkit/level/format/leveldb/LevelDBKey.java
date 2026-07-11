package cn.nukkit.level.format.leveldb;

import java.nio.charset.StandardCharsets;

public enum LevelDBKey {
    DATA_3D('+'),
    VERSION(','),
    DATA_2D('-'),
    DATA_2D_LEGACY('.'),
    /**
     * Block data for a 16x16x16 sub-chunk section.
     */
    SUB_CHUNK_PREFIX('/'),
    LEGACY_TERRAIN('0'),
    BLOCK_ENTITIES('1'),
    ENTITIES('2'),
    PENDING_TICKS('3'),
    BLOCK_EXTRA_DATA('4'),
    BIOME_STATE('5'),
    STATE_FINALIZATION('6'),
    CONVERSION_DATA('7'),
    BORDER_BLOCKS('8'),
    HARDCODED_SPAWNERS('9'),
    RANDOM_TICKS(':'),
    CHECKSUMS(';'),
    GENERATION_SEED('<'),
    GENERATED_PRE_CAVES_AND_CLIFFS_BLENDING('='),
    BLENDING_BIOME_HEIGHT('>'),
    METADATA_HASH('?'),
    BLENDING_DATA('@'),
    ACTOR_DIGEST_VERSION('A'),
    FLAGS('f'),
    VERSION_OLD('v'),
    NUKKIT_BLOCK_LIGHT((char) 1000),
    NUKKIT_SKY_LIGHT((char) 1001),
    ;

    private final byte encoded;

    public static final byte[] ACTOR_PREFIX = "actorprefix".getBytes(StandardCharsets.UTF_8);
    public static final byte[] BIOME_IDS_TABLE = "BiomeIdsTable".getBytes(StandardCharsets.UTF_8);
    public static final byte[] DIGP_PREFIX = "digp".getBytes(StandardCharsets.UTF_8);
    public static final byte[] DIMENSION_NAME_ID_TABLE = "DimensionNameIdTable".getBytes(StandardCharsets.UTF_8);
    public static final byte[] LOCAL_PLAYER = "~local_player".getBytes(StandardCharsets.UTF_8);
    public static final byte[] MAP_PREFIX = "map_".getBytes(StandardCharsets.UTF_8);
    public static final byte[] PORTALS = "portals".getBytes(StandardCharsets.UTF_8);
    public static final byte[] POS_TRACK_DB = "PosTrackDB-0x".getBytes(StandardCharsets.UTF_8);
    public static final byte[] POS_TRACK_DB_LAST_ID = "PositionTrackDB-LastId".getBytes(StandardCharsets.UTF_8);

    LevelDBKey(char encoded) {
        this.encoded = (byte) encoded;
    }

    public byte getCode() {
        return this.encoded;
    }

    /**
     * Creates a chunk-column key.
     */
    public byte[] getKey(int chunkX, int chunkZ, int dimension) {
        if (dimension == 0) {
            return this.getKey(chunkX, chunkZ, false, 0);
        } else {
            return this.getKey(chunkX, chunkZ, dimension, false, 0);
        }
    }

    /**
     * Creates a sub-chunk key and appends the section Y byte after the type.
     */
    public byte[] getKey(int chunkX, int chunkZ, int y, int dimension) {
        if (dimension == 0) {
            return this.getKey(chunkX, chunkZ, true, y);
        } else {
            return this.getKey(chunkX, chunkZ, dimension, true, y);
        }
    }

    private byte[] getKey(int chunkX, int chunkZ, boolean extend, int y) {
        byte[] bytes = new byte[extend ? 10 : 9];
        bytes[0] = (byte) (chunkX & 0xff);
        bytes[1] = (byte) ((chunkX >>> 8) & 0xff);
        bytes[2] = (byte) ((chunkX >>> 16) & 0xff);
        bytes[3] = (byte) ((chunkX >>> 24) & 0xff);
        bytes[4] = (byte) (chunkZ & 0xff);
        bytes[5] = (byte) ((chunkZ >>> 8) & 0xff);
        bytes[6] = (byte) ((chunkZ >>> 16) & 0xff);
        bytes[7] = (byte) ((chunkZ >>> 24) & 0xff);
        bytes[8] = this.encoded;
        if (extend) {
            bytes[9] = (byte) y;
        }
        return bytes;
    }

    private byte[] getKey(int chunkX, int chunkZ, int dimension, boolean extend, int y) {
        byte[] bytes = new byte[extend ? 14 : 13];
        bytes[0] = (byte) (chunkX & 0xff);
        bytes[1] = (byte) ((chunkX >>> 8) & 0xff);
        bytes[2] = (byte) ((chunkX >>> 16) & 0xff);
        bytes[3] = (byte) ((chunkX >>> 24) & 0xff);
        bytes[4] = (byte) (chunkZ & 0xff);
        bytes[5] = (byte) ((chunkZ >>> 8) & 0xff);
        bytes[6] = (byte) ((chunkZ >>> 16) & 0xff);
        bytes[7] = (byte) ((chunkZ >>> 24) & 0xff);
        bytes[8] = (byte) (dimension & 0xff);
        bytes[9] = (byte) ((dimension >>> 8) & 0xff);
        bytes[10] = (byte) ((dimension >>> 16) & 0xff);
        bytes[11] = (byte) ((dimension >>> 24) & 0xff);
        bytes[12] = this.encoded;
        if (extend) {
            bytes[13] = (byte) y;
        }
        return bytes;
    }

    /**
     * Creates a prefixed chunk-column key such as digp + chunk position.
     */
    public static byte[] getKey(byte[] prefix, int chunkX, int chunkZ, int dimension) {
        byte[] bytes = new byte[prefix.length + 8 + (dimension == 0 ? 0 : 4)];
        System.arraycopy(prefix, 0, bytes, 0, prefix.length);
        int offset = prefix.length;
        bytes[offset] = (byte) (chunkX & 0xff);
        bytes[offset + 1] = (byte) ((chunkX >>> 8) & 0xff);
        bytes[offset + 2] = (byte) ((chunkX >>> 16) & 0xff);
        bytes[offset + 3] = (byte) ((chunkX >>> 24) & 0xff);
        bytes[offset + 4] = (byte) (chunkZ & 0xff);
        bytes[offset + 5] = (byte) ((chunkZ >>> 8) & 0xff);
        bytes[offset + 6] = (byte) ((chunkZ >>> 16) & 0xff);
        bytes[offset + 7] = (byte) ((chunkZ >>> 24) & 0xff);
        if (dimension != 0) {
            bytes[offset + 8] = (byte) (dimension & 0xff);
            bytes[offset + 9] = (byte) ((dimension >>> 8) & 0xff);
            bytes[offset + 10] = (byte) ((dimension >>> 16) & 0xff);
            bytes[offset + 11] = (byte) ((dimension >>> 24) & 0xff);
        }
        return bytes;
    }

    /**
     * Concatenates a LevelDB key prefix and suffix.
     */
    public static byte[] getKey(byte[] prefix, byte[] suffix) {
        byte[] bytes = new byte[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, bytes, 0, prefix.length);
        System.arraycopy(suffix, 0, bytes, prefix.length, suffix.length);
        return bytes;
    }

    /**
     * Checks whether a raw LevelDB key starts with a non-chunk prefix such as map_.
     */
    public static boolean startsWith(byte[] input, byte[] prefix) {
        if (input.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (input[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts a UTF-8 suffix from a prefixed LevelDB key.
     */
    public static String extractSuffix(byte[] input, byte[] prefix) {
        return new String(input, prefix.length, input.length - prefix.length, StandardCharsets.UTF_8);
    }
}
