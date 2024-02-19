package cn.nukkit.level.format.leveldb;

import cn.nukkit.level.DimensionData;
import cn.nukkit.level.DimensionEnum;

public enum LevelDBKey {
    DATA_3D('+'),
    DATA_2D('-'),
    DATA_2D_LEGACY('.'),
    /**
     * Block data for a 16×16×16 chunk section
     */
    CHUNK_SECTION_PREFIX('/'),
    LEGACY_TERRAIN('0'),
    BLOCK_ENTITIES('1'),
    ENTITIES('2'),
    PENDING_TICKS('3'),
    BLOCK_EXTRA_DATA('4'),
    BIOME_STATE('5'),
    STATE_FINALIZATION('6'),
    BORDER_BLOCKS('8'),
    HARDCODED_SPAWNERS('9'),
    PENDING_RANDOM_TICKS(':'),
    FLAGS('f'),
    VERSION_OLD('v'),
    VERSION(',')
    ;

    private final byte encoded;

    LevelDBKey(char encoded) {
        this.encoded = (byte) encoded;
    }

    public byte getCode() {
        return this.encoded;
    }

    public byte[] getKey(int chunkX, int chunkZ) {
        return new byte[]{
                (byte) (chunkX & 0xff),
                (byte) ((chunkX >>> 8) & 0xff),
                (byte) ((chunkX >>> 16) & 0xff),
                (byte) ((chunkX >>> 24) & 0xff),
                (byte) (chunkZ & 0xff),
                (byte) ((chunkZ >>> 8) & 0xff),
                (byte) ((chunkZ >>> 16) & 0xff),
                (byte) ((chunkZ >>> 24) & 0xff),
                this.encoded
        };
    }

    public byte[] getKey(int chunkX, int chunkZ, DimensionData dimension) {
        if (dimension.equals(DimensionEnum.OVERWORLD.getDimensionData())) {
            return getKey(chunkX, chunkZ);
        } else {
            byte dimensionId = (byte) dimension.getDimensionId();
            return new byte[]{
                    (byte) (chunkX & 0xff),
                    (byte) ((chunkX >>> 8) & 0xff),
                    (byte) ((chunkX >>> 16) & 0xff),
                    (byte) ((chunkX >>> 24) & 0xff),
                    (byte) (chunkZ & 0xff),
                    (byte) ((chunkZ >>> 8) & 0xff),
                    (byte) ((chunkZ >>> 16) & 0xff),
                    (byte) ((chunkZ >>> 24) & 0xff),
                    (byte) (dimensionId & 0xff),
                    (byte) ((dimensionId >>> 8) & 0xff),
                    (byte) ((dimensionId >>> 16) & 0xff),
                    (byte) ((dimensionId >>> 24) & 0xff),
                    this.encoded
            };
        }
    }

    public byte[] getKey(int chunkX, int chunkZ, int chunkSectionY) {
        if (this.encoded != CHUNK_SECTION_PREFIX.encoded)
            throw new IllegalArgumentException("The method must be used with CHUNK_SECTION_PREFIX!");
        return new byte[]{
                (byte) (chunkX & 0xff),
                (byte) ((chunkX >>> 8) & 0xff),
                (byte) ((chunkX >>> 16) & 0xff),
                (byte) ((chunkX >>> 24) & 0xff),
                (byte) (chunkZ & 0xff),
                (byte) ((chunkZ >>> 8) & 0xff),
                (byte) ((chunkZ >>> 16) & 0xff),
                (byte) ((chunkZ >>> 24) & 0xff),
                this.encoded,
                (byte) chunkSectionY
        };
    }

    public byte[] getKey(int chunkX, int chunkZ, int chunkSectionY, DimensionData dimension) {
        if (this.encoded != CHUNK_SECTION_PREFIX.encoded)
            throw new IllegalArgumentException("The method must be used with CHUNK_SECTION_PREFIX!");
        if (dimension.equals(DimensionEnum.OVERWORLD.getDimensionData())) {
            return new byte[]{
                    (byte) (chunkX & 0xff),
                    (byte) ((chunkX >>> 8) & 0xff),
                    (byte) ((chunkX >>> 16) & 0xff),
                    (byte) ((chunkX >>> 24) & 0xff),
                    (byte) (chunkZ & 0xff),
                    (byte) ((chunkZ >>> 8) & 0xff),
                    (byte) ((chunkZ >>> 16) & 0xff),
                    (byte) ((chunkZ >>> 24) & 0xff),
                    this.encoded,
                    (byte) chunkSectionY
            };
        } else {
            byte dimensionId = (byte) dimension.getDimensionId();
            return new byte[]{
                    (byte) (chunkX & 0xff),
                    (byte) ((chunkX >>> 8) & 0xff),
                    (byte) ((chunkX >>> 16) & 0xff),
                    (byte) ((chunkX >>> 24) & 0xff),
                    (byte) (chunkZ & 0xff),
                    (byte) ((chunkZ >>> 8) & 0xff),
                    (byte) ((chunkZ >>> 16) & 0xff),
                    (byte) ((chunkZ >>> 24) & 0xff),
                    (byte) (dimensionId & 0xff),
                    (byte) ((dimensionId >>> 8) & 0xff),
                    (byte) ((dimensionId >>> 16) & 0xff),
                    (byte) ((dimensionId >>> 24) & 0xff),
                    this.encoded,
                    (byte) chunkSectionY
            };
        }
    }
}
