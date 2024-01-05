package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

/**
 * @author LT_Name
 */
public class ChunkSerializerV3 implements ChunkSerializer {

    public static final ChunkSerializer INSTANCE = new ChunkSerializerV3();

    @Override
    public LevelDBChunkSection deserialize(DB db) {
        //TODO
        return null;

        /*boolean hasBeenUpgraded = chunkVersion < CURRENT_LEVEL_CHUNK_VERSION;

        LevelDbSubChunk[] subChunks = new LevelDbSubChunk[16];
        short[] heightmap = null;
        byte[] biome = null;
        PalettedBlockStorage[] biomes3d = null;

        int subChunkKeyOffset = chunkVersion >= 24 && chunkVersion <= 26 ? 4 : 0;

        PalettedBlockStorage[] convertedLegacyExtraData = this.deserializeLegacyExtraData(chunkX, chunkZ, chunkVersion);

        for (int y = 0; y <= 15; ++y) {
            byte[] subChunkValue = this.db.get(SUBCHUNK_PREFIX.getSubKey(chunkX, chunkZ, y + subChunkKeyOffset));
            if (subChunkValue == null) {
                continue;
            }
            if (subChunkValue.length == 0) {
                throw new ChunkException("Unexpected empty data for subchunk " + y);
            }
            BinaryStream stream = new BinaryStream(subChunkValue);

            int subChunkVersion = stream.getByte();
            if (subChunkVersion < CURRENT_LEVEL_SUBCHUNK_VERSION) {
                hasBeenUpgraded = true;
            }

            switch (subChunkVersion) {
                case 8:
                case 9:
                    int storageCount = stream.getByte();

                    if (subChunkVersion >= 9) {
                        int indexY = stream.getByte();
                        if (indexY != y) {
                            throw new ChunkException("Unexpected Y index (" + indexY + ") for subchunk " + y);
                        }
                    }

                    if (storageCount > 0) {
                        PalettedBlockStorage[] storages = new PalettedBlockStorage[storageCount];
                        for (int i = 0; i < storageCount; ++i) {
                            storages[i] = *//*PalettedBlockStorage.createFromBlockPalette();*//* PalettedBlockStorage.ofBlock(stream);
                        }

                        subChunks[y] = new LevelDbSubChunk(y, storages);
                    }
                    break;
                case 0:
                case 2: //these are all identical to version 0, but vanilla respects these so we should also
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    byte[] blocks = stream.get(4096);
                    NibbleArray blockData = new NibbleArray(stream.get(2048));

                    if (chunkVersion < 4) {
                        stream.setOffset(stream.getOffset() + 4096); //legacy light info, discard it
                        hasBeenUpgraded = true;
                    }

                    PalettedBlockStorage[] storages = new PalettedBlockStorage[2];
                    PalettedBlockStorage storage = PalettedBlockStorage.createFromBlockPalette();
                    for (int i = 0; i < SUB_CHUNK_SIZE; i++) {
                        storage.setBlock(i, (blocks[i] & 0xff) << Block.DATA_BITS | blockData.get(i));
                    }
                    storages[0] = storage;

                    if (convertedLegacyExtraData != null && convertedLegacyExtraData.length > y) {
                        storages[1] = convertedLegacyExtraData[y];
                    }

                    subChunks[y] = new LevelDbSubChunk(y, storages);
                    break;
                case 1: //paletted v1, has a single block storage
                    storages = new PalettedBlockStorage[2];
                    storages[0] = PalettedBlockStorage.ofBlock(stream);

                    if (convertedLegacyExtraData != null && convertedLegacyExtraData.length > y) {
                        storages[1] = convertedLegacyExtraData[y];
                    }

                    subChunks[y] = new LevelDbSubChunk(y, storages);
                    break;
                default:
                    //TODO: set chunks read-only so the version on disk doesn't get overwritten
                    throw new ChunkException("don't know how to decode LevelDB subchunk format version " + subChunkVersion);
            }
        }

        byte[] maps2d = this.db.get(DATA_2D.getKey(chunkX, chunkZ));
        if (maps2d != null && maps2d.length >= SUB_CHUNK_2D_SIZE * 2 + SUB_CHUNK_2D_SIZE) {
            heightmap = new short[SUB_CHUNK_2D_SIZE];
            biome = new byte[SUB_CHUNK_2D_SIZE];

            ByteBuf buf = Unpooled.wrappedBuffer(maps2d);
            try {
                for (int i = 0; i < SUB_CHUNK_2D_SIZE; i++)  {
                    heightmap[i] = buf.readShortLE();
                }
                buf.readBytes(biome);
            } finally {
                buf.release();
            }
        }*/
    }

    @Override
    public void serializer(WriteBatch writeBatch, LevelDBChunkSection subChunk) {

    }
}
