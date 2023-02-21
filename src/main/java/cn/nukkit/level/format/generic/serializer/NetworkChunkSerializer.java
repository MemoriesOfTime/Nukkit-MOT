package cn.nukkit.level.format.generic.serializer;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.biome.Biome;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ThreadCache;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.Consumer;

public class NetworkChunkSerializer {

    private static final byte[] PAD_256 = new byte[256];

    private static final int EXTENDED_NEGATIVE_SUB_CHUNKS = 4;

    private static final byte[] negativeSubChunks;

    static {
        // Build up 4 SubChunks for the extended negative height
        BinaryStream stream = new BinaryStream();
        for (int i = 0; i < EXTENDED_NEGATIVE_SUB_CHUNKS; i++) {
            // SubChunk version
            stream.putByte((byte) 8);
            // 0 layers
            stream.putByte((byte) 0);
        }
        negativeSubChunks = stream.getBuffer();
    }

    public static void serialize(IntSet protocols, BaseChunk chunk, Consumer<NetworkChunkSerializerCallback> callback, DimensionData dimensionData) {
        byte[] blockEntities;
        if (chunk.getBlockEntities().isEmpty()) {
            blockEntities = new byte[0];
        } else {
            blockEntities = serializeEntities(chunk);
        }

        int subChunkCount = 0;
        ChunkSection[] sections = chunk.getSections();
        for (int i = sections.length - 1; i >= 0; i--) {
            if (!sections[i].isEmpty()) {
                subChunkCount = i + 1;
                break;
            }
        }

        for (int protocolId : protocols) {
            int protocolSubChunkCount = subChunkCount;
            int maxDimensionSections = dimensionData.getHeight() >> 4;
            if (protocolId >= ProtocolInfo.v1_18_0) {
                protocolSubChunkCount = Math.min(maxDimensionSections, subChunkCount);
            }

            BinaryStream stream = ThreadCache.binaryStream.get().reset();
            if (protocolId < ProtocolInfo.v1_12_0) {
                stream.putByte((byte) protocolSubChunkCount);
            }

            byte[] biomePalettes = null;
            int writtenSections = protocolSubChunkCount;
            if (protocolId >= ProtocolInfo.v1_18_0) {
                // In 1.18 3D biome palettes were introduced. However, current world format
                // used internally doesn't support them, so we need to convert from legacy 2D
                biomePalettes = convert2DBiomesTo3D(protocolId, chunk, maxDimensionSections);

                stream = ThreadCache.binaryStream.get().reset();

                if (dimensionData.getDimensionId() == Level.DIMENSION_OVERWORLD && protocolSubChunkCount < maxDimensionSections) {
                    stream.put(negativeSubChunks);
                    writtenSections += EXTENDED_NEGATIVE_SUB_CHUNKS;
                }
            }

            for (int i = 0; i < protocolSubChunkCount; i++) {
                if (protocolId < ProtocolInfo.v1_13_0) {
                    stream.putByte((byte) 0);
                    stream.put(sections[i].getBytes(protocolId));
                }else {
                    sections[i].writeTo(protocolId, stream);
                }
            }

            if (protocolId < ProtocolInfo.v1_12_0) {
                for (byte height : chunk.getHeightMapArray()) {
                    stream.putByte(height);
                }
                stream.put(PAD_256);
            }
            if (protocolId >= ProtocolInfo.v1_18_0) {
                stream.put(biomePalettes);
            }else {
                stream.put(chunk.getBiomeIdArray());
            }
            // Border blocks
            stream.putByte((byte) 0);
            if (protocolId < ProtocolInfo.v1_16_100) {
                // There is no extra data anymore but idk when it was removed
                stream.putVarInt(0);
            }
            stream.put(blockEntities);

            callback.accept(new NetworkChunkSerializerCallback(protocolId, stream, writtenSections));
        }
    }

    private static byte[] serializeEntities(BaseChunk chunk) {
        List<CompoundTag> tagList = new ObjectArrayList<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof BlockEntitySpawnable) {
                tagList.add(((BlockEntitySpawnable) blockEntity).getSpawnCompound());
            }
        }

        try {
            return NBTIO.write(tagList, ByteOrder.LITTLE_ENDIAN, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] convert2DBiomesTo3D(int protocolId, BaseFullChunk chunk, int sections) {
        PalettedBlockStorage palette = PalettedBlockStorage.createWithDefaultState(Biome.getBiomeIdOrCorrect(protocolId, chunk.getBiomeId(0, 0)));
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int biomeId = Biome.getBiomeIdOrCorrect(protocolId, chunk.getBiomeId(x, z));
                for (int y = 0; y < 16; y++) {
                    palette.setBlock(x, y, z, biomeId);
                }
            }
        }

        BinaryStream stream = ThreadCache.binaryStream.get().reset();
        palette.writeTo(protocolId, stream);
        byte[] bytes = stream.getBuffer();
        stream.reset();

        for (int i = 0; i < sections; i++) {
            stream.put(bytes);
        }
        return stream.getBuffer();
    }

    @AllArgsConstructor
    @Data
    public static class NetworkChunkSerializerCallback {
        private int protocolId;
        private BinaryStream stream;
        private Integer subchunks;
    }

    @AllArgsConstructor
    @Data
    public static class NetworkChunkSerializerCallbackData {
        private int protocol;
        private long timestamp;
        private int x;
        private int z;
        private int subChunkCount;
        private byte[] payload;
    }
}
