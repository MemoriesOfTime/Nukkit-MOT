package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.DimensionData;
import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.leveldb.LevelDBKey;
import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import cn.nukkit.utils.ChunkException;
import cn.nukkit.utils.Utils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

import static cn.nukkit.level.format.leveldb.LevelDbConstants.CURRENT_LEVEL_SUBCHUNK_VERSION;

public class ChunkSerializerV3 implements ChunkSerializer {

    public static final ChunkSerializer INSTANCE = new ChunkSerializerV3();

    @Override
    public void serializer(WriteBatch writeBatch, Chunk chunk) {
        DimensionData dimensionData = chunk.getProvider().getLevel().getDimensionData();
        for (int ySection = dimensionData.getMinSectionY(); ySection <= dimensionData.getMaxSectionY(); ++ySection) {
            LevelDBChunkSection section = (LevelDBChunkSection) chunk.getSection(ySection);
            if (section == null) {
                continue;
            }

            ByteBuf byteBuf = ByteBufAllocator.DEFAULT.ioBuffer();
            try {
                byteBuf.writeByte(CURRENT_LEVEL_SUBCHUNK_VERSION);
                ChunkSectionSerializers.serializer(byteBuf, section.getStorages(), ySection, CURRENT_LEVEL_SUBCHUNK_VERSION);
                writeBatch.put(
                        LevelDBKey.CHUNK_SECTION_PREFIX.getSubKey(
                                chunk.getX(), chunk.getZ(), ySection, chunk.getProvider().getLevel().getDimension()
                        ), Utils.convertByteBuf2Array(byteBuf));
            } finally {
                byteBuf.release();
            }
        }
    }

    @Override
    public void deserialize(DB db, ChunkBuilder chunkBuilder) {
        int chunkX = chunkBuilder.getChunkX();
        int chunkZ = chunkBuilder.getChunkZ();

        Int2ShortOpenHashMap extraBlocks = null;
        byte[] extraRawData = db.get(LevelDBKey.BLOCK_EXTRA_DATA.getKey(chunkX, chunkZ, chunkBuilder.getDimensionData()));
        if (extraRawData != null) {
            extraBlocks = new Int2ShortOpenHashMap();
            ByteBuf extraData = Unpooled.wrappedBuffer(extraRawData);
            int count = extraData.readIntLE();
            for (int i = 0; i < count; ++i) {
                extraBlocks.put(extraData.readIntLE(), extraData.readShortLE());
            }
        }

        DimensionData dimensionInfo = chunkBuilder.getDimensionData();
        LevelDBChunkSection[] sections = new LevelDBChunkSection[dimensionInfo.getHeight() >> 4];
        for (int ySection = dimensionInfo.getMinSectionY(); ySection <= dimensionInfo.getMaxSectionY(); ++ySection) {
            StateBlockStorage[] stateBlockStorageArray;
            byte[] bytes = db.get(LevelDBKey.CHUNK_SECTION_PREFIX.getKey(chunkX, chunkZ, ySection, chunkBuilder.getDimensionData()));
            if (bytes != null) {
                ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes);
                if (!byteBuf.isReadable()) {
                    throw new ChunkException("Chunk section " + ySection + " is empty");
                }
                short subChunkVersion = byteBuf.readUnsignedByte();
                stateBlockStorageArray = ChunkSectionSerializers.deserialize(byteBuf, chunkBuilder, subChunkVersion);

                if (stateBlockStorageArray[1] == null) {
                    stateBlockStorageArray[1] = new StateBlockStorage();
                }

                //TODO 解析extraBlocks
                /*if (extraBlocks != null) {
                    for (int i3 = 0; i3 < 16; ++i3) {
                        for (int i4 = 0; i4 < 16; ++i4) {
                            int n7;
                            int n8 = n7 + 16;
                            for (n7 = ySection << 4; n7 < n8; ++n7) {
                                short s4 = ChunkBuilder.a(i3, n7, i4);
                                if (!extraBlocks.containsKey(s4)) continue;
                                short s5 = extraBlocks.get(ChunkBuilder.a(i3, n7, i4));
                                int n9 = s5 & 0xFF;
                                int n10 = s5 >> 8 & 0xF;
                                stateBlockStorageArray[1].a(ChunkBuilder.b(i3, n7, i4), BlockStateMapping.get().b(n9, n10, l7), l5);
                            }
                        }
                    }
                }*/
                sections[ySection + dimensionInfo.getSectionOffset()] = new LevelDBChunkSection(ySection, stateBlockStorageArray);
            }
        }
        chunkBuilder.sections(sections);
    }
}
