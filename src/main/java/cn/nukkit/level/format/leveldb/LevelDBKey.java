package cn.nukkit.level.format.leveldb;

import cn.nukkit.level.Level;

public enum LevelDBKey {
    DATA_3D('+'),
    DATA_2D('-'),
    DATA_2D_LEGACY('.'),
    SUBCHUNK_PREFIX('/'),
    LEGACY_TERRAIN('0'),
    BLOCK_ENTITIES('1'),
    ENTITIES('2'),
    PENDING_TICKS('3'),
    BLOCK_EXTRA_DATA('4'),
    BIOME_STATE('5'),
    STATE_FINALIZATION('6'),
    BORDER_BLOCKS('8'),
    HARDCODED_SPAWNERS('9'),
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
                this.encoded,
        };
    }

    public byte[] getKey(int chunkX, int chunkZ, int dimension) {
        if (dimension == Level.DIMENSION_OVERWORLD) {
            return getKey(chunkX, chunkZ);
        }
        return new byte[]{
                (byte) (chunkX & 0xff),
                (byte) ((chunkX >>> 8) & 0xff),
                (byte) ((chunkX >>> 16) & 0xff),
                (byte) ((chunkX >>> 24) & 0xff),
                (byte) (chunkZ & 0xff),
                (byte) ((chunkZ >>> 8) & 0xff),
                (byte) ((chunkZ >>> 16) & 0xff),
                (byte) ((chunkZ >>> 24) & 0xff),
                (byte) (dimension & 0xff),
                this.encoded,
        };
    }

    public byte[] getSubKey(int chunkX, int chunkZ, int chunkY) {
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
                (byte) (chunkY & 0xff),
        };
    }

    public byte[] getSubKey(int chunkX, int chunkZ, int dimension, int chunkY) {
        if (dimension == Level.DIMENSION_OVERWORLD) {
            return getSubKey(chunkX, chunkZ, chunkY);
        }
        return new byte[]{
                (byte) (chunkX & 0xff),
                (byte) ((chunkX >>> 8) & 0xff),
                (byte) ((chunkX >>> 16) & 0xff),
                (byte) ((chunkX >>> 24) & 0xff),
                (byte) (chunkZ & 0xff),
                (byte) ((chunkZ >>> 8) & 0xff),
                (byte) ((chunkZ >>> 16) & 0xff),
                (byte) ((chunkZ >>> 24) & 0xff),
                (byte) (dimension & 0xff),
                this.encoded,
                (byte) (chunkY & 0xff),
        };
    }
}
