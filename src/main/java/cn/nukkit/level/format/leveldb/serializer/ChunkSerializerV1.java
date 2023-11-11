package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

/**
 * @author LT_Name
 */
public class ChunkSerializerV1 implements ChunkSerializer {

    public static final ChunkSerializer INSTANCE = new ChunkSerializerV1();

    @Override
    public LevelDBChunkSection deserialize(DB db) {
        //TODO
        return null;
        /*boolean hasBeenUpgraded = chunkVersion < CURRENT_LEVEL_CHUNK_VERSION;

        LevelDbSubChunk[] subChunks = new LevelDbSubChunk[16];
        short[] heightmap = null;
        byte[] biome = null;
        PalettedBlockStorage[] biomes3d = null;

        PalettedBlockStorage[] convertedLegacyExtraData = this.deserializeLegacyExtraData(chunkX, chunkZ, chunkVersion);

        byte[] legacyTerrain = db.get(LEGACY_TERRAIN.getKey(chunkX, chunkZ));
        if (legacyTerrain == null || legacyTerrain.length == 0) {
            throw new ChunkException("Missing expected legacy terrain data for format version " + chunkVersion);
        }

        BinaryStream stream = new BinaryStream(legacyTerrain);
        // max height 128
        byte[] blocks = stream.get(8 * SUB_CHUNK_SIZE);
        NibbleArray blockData = new NibbleArray(stream.get(8 * SUB_CHUNK_SIZE / 2));

        for (int y = 0; y < 8; y++) {
            PalettedBlockStorage[] storages = new PalettedBlockStorage[2];
            PalettedBlockStorage storage = PalettedBlockStorage.createFromBlockPalette();
            for (int i = 0; i < SUB_CHUNK_SIZE; i++) {
                storage.setBlock(i, (blocks[i] & 0xff) << 4 | blockData.get(i));
            }

            if (convertedLegacyExtraData != null && convertedLegacyExtraData.length > y) {
                storages[1] = convertedLegacyExtraData[y];
            }

            subChunks[y] = new LevelDbSubChunk(y, storages);
        }

        // Discard skyLight and blockLight
        stream.skip(8 * SUB_CHUNK_SIZE / 2 + 8 * SUB_CHUNK_SIZE / 2);

                *//*heightmap = new short[SUB_CHUNK_2D_SIZE];
                for (int i = 0; i < SUB_CHUNK_2D_SIZE; i++) {
                    heightmap[i] = (short) (stream.getByte() & 0xff);
                }*//*
        stream.skip(SUB_CHUNK_2D_SIZE); // recalculate heightmap

        biome = new byte[SUB_CHUNK_2D_SIZE];
        for (int i = 0; i < SUB_CHUNK_2D_SIZE; i++) {
            biome[i] = (byte) (Biome.getBiomeIdOrCorrect(ProtocolInfo.CURRENT_LEVEL_PROTOCOL, stream.getInt() >> 24) & 0xff);
        }*/
    }

    @Override
    public void serializer(WriteBatch writeBatch, LevelDBChunkSection subChunk) {

    }

}
