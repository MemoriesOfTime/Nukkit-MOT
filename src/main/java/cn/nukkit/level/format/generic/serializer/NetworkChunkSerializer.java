package cn.nukkit.level.format.generic.serializer;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.biome.Biome;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.*;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
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
            stream.putByte((byte) 8); // SubChunk version
            stream.putByte((byte) 0); // 0 layers
        }
        negativeSubChunks = stream.getBuffer();
    }

    @Deprecated
    public static void serialize(IntSet protocols, BaseChunk chunk, Consumer<NetworkChunkSerializerCallback> callback, boolean antiXray, DimensionData dimensionData) {
        Server.mvw("NetworkChunkSerializer#serialize(IntSet, BaseChunk, Consumer<NetworkChunkSerializerCallback>, boolean, DimensionData) is deprecated, please use NetworkChunkSerializer#serialize(ObjectSet<GameVersion>, BaseChunk, Consumer<NetworkChunkSerializerCallback>, boolean, DimensionData) instead.");
        serialize(Utils.intSet2GameVersionSet(protocols, false), chunk, callback, antiXray, dimensionData);
    }

    public static void serialize(ObjectSet<GameVersion> protocols, BaseChunk chunk, Consumer<NetworkChunkSerializerCallback> callback, boolean antiXray, DimensionData dimensionData) {
        for (GameVersion gameVersion : protocols) {
            int protocolId = gameVersion.getProtocol();
            // 0.11 ~ 1.0.0
            if (protocolId <= ProtocolInfo.v_1_0_0) {
                serialize_011___100(protocolId, chunk, callback, antiXray, dimensionData);
                continue;
            }
            byte[] blockEntities;
            if (chunk.getBlockEntities().isEmpty()) {
                blockEntities = new byte[0];
            } else {
                blockEntities = serializeEntities(chunk, protocolId);
            }

            int subChunkCount = 0;
            // 如果某一高度的区块存在, 那么它下面的所有区块都一定存在
            ChunkSection[] sections = chunk.getSections();
            for (int i = sections.length - 1; i >= 0; i--) {
                if (!sections[i].isEmpty()) {
                    subChunkCount = i + 1;
                    break;
                }
            }

            BinaryStream stream = ThreadCache.binaryStream.get().reset();
            NetworkChunkData networkChunkData = new NetworkChunkData(gameVersion, subChunkCount, antiXray, dimensionData);
            if (protocolId >= ProtocolInfo.v1_18_30) {
                serialize1_18_30(stream, chunk, sections, networkChunkData);
            } else if (protocolId >= ProtocolInfo.v1_18_0) {
                serialize1_18_0(stream, chunk, sections, networkChunkData);
            } else {
                int offset = chunk.getSectionOffset();
                subChunkCount = Math.max(1, subChunkCount - offset);
                // Pre-1.18 clients only support 256-block height (16 sub-chunks)
                int maxSubChunkCount = 16;
                subChunkCount = Math.min(maxSubChunkCount, subChunkCount);
                networkChunkData.setChunkSections(subChunkCount);

                if (protocolId < ProtocolInfo.v1_12_0) {
                    stream.putByte((byte) subChunkCount);
                }

                for (int i = offset; i < subChunkCount + offset; i++) {
                    if (protocolId < ProtocolInfo.v1_13_0) {
                        stream.putByte((byte) 0);
                        stream.put(sections[i].getBytes(gameVersion));
                    } else {
                        sections[i].writeTo(gameVersion, stream, antiXray);
                    }
                }

                if (protocolId < ProtocolInfo.v1_12_0) {
                    // heightMap is stored relative to minBlockY (offset << 4); convert back to absolute Y and clamp to 0-255
                    int heightOffset = offset << 4;
                    for (short height : chunk.getHeightMapArray()) {
                        int clamped = height - heightOffset;
                        if (clamped < 0) clamped = 0;
                        if (clamped > 255) clamped = 255;
                        stream.putByte((byte) clamped);
                    }
                    stream.put(PAD_256);
                }
                stream.put(chunk.getBiomeIdArray());
            }
            // Border blocks
            stream.putByte((byte) 0);
            if (protocolId < ProtocolInfo.v1_16_100) {
                // There is no extra data anymore but idk when it was removed
                stream.putVarInt(0);
            }
            stream.put(blockEntities);

            callback.accept(new NetworkChunkSerializerCallback(gameVersion, stream, networkChunkData.getChunkSections()));
        }
    }

    public static void serialize_011___100(int protocolId, BaseChunk chunk,
                                           Consumer<NetworkChunkSerializerCallback> callback, boolean antiXray, DimensionData dimensionData) {
        if (chunk == null) {
            throw new ChunkException("Invalid Chunk sent");
        }

        byte[] blockEntities;
        if (chunk.getBlockEntities().isEmpty()) {
            blockEntities = new byte[0];
        } else {
            blockEntities = serializeEntities(chunk, protocolId);
        }

        int subChunkCount = 0;
        ChunkSection[] sections = chunk.getSections();
        for (int i = sections.length - 1; i >= 0; i--) {
            if (!sections[i].isEmpty()) {
                subChunkCount = i + 1;
                break;
            }
        }

        NetworkChunkData networkChunkData = new NetworkChunkData(GameVersion.byProtocol(protocolId, false), subChunkCount, antiXray, dimensionData);
        subChunkCount = Math.max(1, subChunkCount - chunk.getSectionOffset());
        networkChunkData.setChunkSections(subChunkCount);

        BinaryStream stream = ThreadCache.binaryStream.get().reset();

        int SECTION_COUNT_014 = 8;
        ByteBuffer blockIdArray = ByteBuffer.allocate(4096 * SECTION_COUNT_014);
        ByteBuffer blockDataArray = ByteBuffer.allocate(2048 * SECTION_COUNT_014);
        ByteBuffer blockSkyLightArray = ByteBuffer.allocate(2048 * SECTION_COUNT_014);
        ByteBuffer blockLightArray = ByteBuffer.allocate(2048 * SECTION_COUNT_014);

        int offset = chunk.getSectionOffset();
        byte[] orderBlockIds = new byte[4096];
        byte[] orderBlockData = new byte[2048];
        byte[] orderBlockSkyLight = new byte[2048];
        byte[] orderBlockLight = new byte[2048];

        Arrays.fill(orderBlockLight, (byte) 0xff);

        // leveldb 转为 anvil
        LevelDBChunkSection section;
        for (int i = offset; i < subChunkCount + offset; ++i) {
            section = (LevelDBChunkSection) sections[i];
            byte[] dbBlockIds = section.getSectionBlockIds();
            byte[] dbBlockData = section.getSectionBlockData();
            byte[] dbBlockSkyLight = section.getSkyLightArray();
            byte[] dbBlockLight = section.getLightArray();

            // 转换leveldb的 xzy 为 anvil的 yzx
            for (int posY = 0; posY < 16; ++posY) {
                for (int posX = 0; posX < 16; ++posX) {
                    for (int posZ = 0; posZ < 16; ++posZ) {
                        if (dbBlockIds[(posX << 8) | (posZ << 4) | posY] > 200 && protocolId >= ProtocolInfo.v_0_14_3) {
                            orderBlockIds[(posY << 8) + (posZ << 4) + posX] = 0;
                        } else {
                            orderBlockIds[(posY << 8) + (posZ << 4) + posX] = dbBlockIds[(posX << 8) | (posZ << 4)
                                    | posY];
                        }

                        int anvilIndex = (posY << 7) + (posZ << 3) + (posX >> 1);
                        int dbsl = dbBlockSkyLight[anvilIndex] & 0xff;
                        int dbl = dbBlockLight[anvilIndex] & 0xff;
                        if ((posX & 1) == 0) {
                            dbsl = dbsl & 0x0f;
                            dbl = dbl & 0x0f;
                        } else {
                            dbsl = dbsl >> 4;
                            dbl = dbl >> 4;
                        }
                        orderBlockData[anvilIndex] = dbBlockData[((posX << 8) | (posZ << 4) | posY) >> 1];
                        orderBlockSkyLight[anvilIndex] = (byte) (dbsl & 0xff);
                        // orderBlockLight[anvilIndex] = (byte) (dbl & 0xff);
                    }
                }
            }
            blockIdArray.put(orderBlockIds);
            blockDataArray.put(orderBlockData);
            blockSkyLightArray.put(orderBlockSkyLight);
            blockLightArray.put(orderBlockLight);
        }

        byte[] targetBlockIds ,targetBlockData, targetBlockSkyLight,targetBlockLight;
        // 0.11 将mca转为mcr
        if (protocolId <= ProtocolInfo.v_0_11_0) {
            targetBlockIds = mca_to_mcr_blockIds(blockIdArray.array());
            targetBlockData = mca_to_mcr_blockData(blockDataArray.array());
            targetBlockSkyLight = mca_to_mcr_blockData(blockDataArray.array());
            targetBlockLight = mca_to_mcr_blockData(blockDataArray.array());
        }else{
            targetBlockIds = blockIdArray.array();
            targetBlockData = blockDataArray.array();
            targetBlockSkyLight = blockSkyLightArray.array();
            targetBlockLight = blockLightArray.array();
        }

        if(protocolId <= ProtocolInfo.v_0_10_0){
            stream.put(Binary.writeLInt(chunk.getX()));
            stream.put(Binary.writeLInt(chunk.getZ()));
        }

        stream.put(targetBlockIds);
        stream.put(targetBlockData);
        stream.put(targetBlockSkyLight);
        stream.put(targetBlockLight);

        // 三种颜色
        // 0x6a7039 swamp (正常?)
        // 0x000000 normal (黑色)
        // -3394765 hell (红色)
        // biome color
        int[] biomeColors = new int[256];
        Arrays.fill(biomeColors, Binary.readInt(new byte[] { (byte) 0xff, (byte) 0x00, (byte) 0x00, (byte) 0x00 }));
        final int _color_ = 0x6a7039;
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                int R = (_color_ >> 16);
                int G = (_color_ >> 8) & 0xff;
                int B = (_color_ & 0xff);
                biomeColors[(z << 4) + x] = (biomeColors[(z << 4) + x] & 0xFF000000) | ((R & 0xFF) << 16)
                        | ((G & 0xFF) << 8) | (B & 0XFF);
            }
        }

        if(protocolId >= ProtocolInfo.v_0_11_0){
            // height map
            for (short height : chunk.getHeightMapArray()) {
                byte heightByte = (byte)height;
                stream.putByte(heightByte);
            }
        }else{
            // biome id
            byte[] biomeid = new byte[2048];
            for (int i = 0; i < biomeColors.length; i++) {
                int d = biomeColors[i];
                biomeid[i] = (byte) ((d & 0xFF000000) >> 24);
            }
            stream.put(biomeid);
        }

        for (int color : biomeColors) {
            stream.put(Binary.writeInt(color));
        }

        // There is no extra data anymore but idk when it was removed
        if (protocolId > ProtocolInfo.v_0_11_0) {
            stream.putInt(0);// 0.11没有
        }

        stream.put(blockEntities);

        if(protocolId <= ProtocolInfo.v_0_10_0){
            try {
                stream.setBuffer(Zlib.deflate(stream.getBuffer(), Server.getInstance().networkCompressionLevel));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        callback.accept(new NetworkChunkSerializerCallback(GameVersion.byProtocol(protocolId, false), stream, networkChunkData.getChunkSections()));
        return;
    }

    private static void serialize1_18_30(BinaryStream stream, BaseChunk chunk, ChunkSection[] sections,
            NetworkChunkData chunkData) {
        DimensionData dimensionData = chunkData.getDimensionData();
        int maxDimensionSections = dimensionData.getHeight() >> 4;
        int subChunkCount = Math.min(maxDimensionSections, chunkData.getChunkSections());

        byte[] biomePalettes = serialize3DBiomes(chunk, chunkData.getGameVersion(), maxDimensionSections);
        stream.reset();

        // Overworld has negative coordinates, But the anvil world does not support it
        int writtenSections = subChunkCount;
        if (dimensionData.getDimensionId() == Level.DIMENSION_OVERWORLD && chunk.getSectionOffset() == 0) {
            stream.put(negativeSubChunks);
            writtenSections += EXTENDED_NEGATIVE_SUB_CHUNKS;
        }

        for (int i = 0; i < subChunkCount; i++) {
            sections[i].writeTo(chunkData.getGameVersion(), stream, chunkData.isAntiXray());
        }

        stream.put(biomePalettes);
        stream.putByte((byte) 0); // Border blocks

        chunkData.setChunkSections(writtenSections);
    }

    private static void serialize1_18_0(BinaryStream stream, BaseChunk chunk, ChunkSection[] sections,
            NetworkChunkData chunkData) {
        DimensionData dimensionData = chunkData.getDimensionData();
        int maxDimensionSections = dimensionData.getHeight() >> 4;
        int subChunkCount = Math.min(maxDimensionSections, chunkData.getChunkSections());

        byte[] biomePalettes = serialize3DBiomes(chunk, chunkData.getGameVersion(), 25);
        stream.reset();

        // Overworld has negative coordinates, But the anvil world does not support it
        int writtenSections = subChunkCount;
        if (dimensionData.getDimensionId() == Level.DIMENSION_OVERWORLD && chunk.getSectionOffset() == 0) {
            stream.put(negativeSubChunks);
            writtenSections += EXTENDED_NEGATIVE_SUB_CHUNKS;
        }

        for (int i = 0; i < subChunkCount; i++) {
            sections[i].writeTo(chunkData.getGameVersion(), stream, chunkData.isAntiXray());
        }

        stream.put(biomePalettes);
        stream.putByte((byte) 0); // Border blocks

        chunkData.setChunkSections(writtenSections);
    }

    private static byte[] serialize3DBiomes(BaseFullChunk chunk, GameVersion protocolId, int maxDimensionSections) {
        if (chunk.has3dBiomes()) {
            BinaryStream binaryStream = ThreadCache.binaryStream.get().reset();
            for (int y = 0; y < maxDimensionSections; y++) {
                PalettedBlockStorage storage = chunk.getBiomeStorage(y);
                storage.writeTo(binaryStream, id -> Biome.getBiomeIdOrCorrect(protocolId, id));
            }
            return binaryStream.getBuffer();
        } else {
            // In 1.18 3D biome palettes were introduced. However, current world format
            // used internally doesn't support them, so we need to convert from legacy 2D
            return convert2DBiomesTo3D(protocolId, chunk, maxDimensionSections);
        }
    }

    private static byte[] serializeEntities(BaseChunk chunk, int protocol) {
        List<CompoundTag> tagList = new ObjectArrayList<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof BlockEntitySpawnable) {
                tagList.add(((BlockEntitySpawnable) blockEntity).getSpawnCompound(protocol));
            }
        }

        try {
            if (protocol <= ProtocolInfo.v_0_14_3) {
                return NBTIO.write_old(tagList, ByteOrder.LITTLE_ENDIAN);
            }
            return NBTIO.write(tagList, ByteOrder.LITTLE_ENDIAN, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] convert2DBiomesTo3D(GameVersion protocolId, BaseFullChunk chunk, int sections) {
        int defaultBiome = chunk.getBiomeId(0, 0);
        PalettedBlockStorage palette = PalettedBlockStorage.createWithDefaultState(Biome.getBiomeIdOrCorrect(protocolId, defaultBiome));
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int biome = chunk.getBiomeId(x, z);
                if (biome != defaultBiome) {
                    int corrected = Biome.getBiomeIdOrCorrect(protocolId, biome);
                    for (int y = 0; y < 16; y++) {
                        palette.setBlock(x, y, z, corrected);
                    }
                }
            }
        }

        BinaryStream stream = ThreadCache.binaryStream.get().reset();
        palette.writeTo(stream, id -> Biome.getBiomeIdOrCorrect(protocolId, id));
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
        private GameVersion gameVersion;
        private BinaryStream stream;
        private Integer subchunks;
    }

    @AllArgsConstructor
    @Data
    public static class NetworkChunkSerializerCallbackData {
        private GameVersion gameVersion;
        private long timestamp;
        private int x;
        private int z;
        private int subChunkCount;
        private byte[] payload;
    }

    /**
     * @param anvilBlockIds
     * @return
     */
    public static byte[] mca_to_mcr_blockIds(byte[] anvilBlockIds){
        // anvil划分子区块，子区块与子区块之间堆叠起来，由于旧版y最大为128，因此划分为8个子区块
        // mcr不会划分子区块
        byte[] mcrBlockIds = new byte[16 * 16 * 128];
        for(int y = 0;y<128;++y){
            // 这里最好优化一下
            byte[] section_y = Binary.subBytes(anvilBlockIds, (y >> 4) * 4096, 4096);
            for(int x=0;x<16;++x){
                for(int z=0;z<16;++z){
                    // mcr采用 (x << 11) | (z << 7) | y， 而anvil采用 (y << 8) + (z << 4) + x
                    mcrBlockIds[(x << 11) | (z << 7) | y] = (byte) (section_y[((y % 16) << 8) + (z << 4) + x] & 0xff);
                }
            }
        }

        return mcrBlockIds;
    }

    /**
     * @param anvilBlockData
     * @return
     */
    public static byte[] mca_to_mcr_blockData(byte[] anvilBlockData) {
        // anvil划分子区块，子区块与子区块之间堆叠起来，由于旧版y最大为128，因此划分为8个子区块
        // mcr不会划分子区块
        byte[] mcrBlockIds = new byte[16 * 16 * 64];
        for(int x=0;x<16;x++){
            if ((x & 1) == 0) {
                for(int y = 0;y<128;y+=2){
                    // 这里最好优化一下
                    byte[] section_y = Binary.subBytes(anvilBlockData, (y >> 4) * 2048, 2048);
                    for(int z=0;z<16;z++){
                        // mcr采用 (x << 11) | (z << 7) | y， 而anvil采用 (y << 8) + (z << 4) + x
                        mcrBlockIds[((x << 10) | (z << 6) | (y << 1))/2] = (byte) ((section_y[((y % 16) << 7) + (z << 3) + (x >> 1)] & 0xff ) & 0x0f);
                    }
                }
            } else {
                for(int y = 0;y<128;y+=2){
                    // 这里最好优化一下
                    byte[] section_y = Binary.subBytes(anvilBlockData, (y >> 4) * 2048, 2048);
                    for(int z=0;z<16;z++){
                        // mcr采用 (x << 11) | (z << 7) | y， 而anvil采用 (y << 8) + (z << 4) + x
                        mcrBlockIds[((x << 10) | (z << 6) | (y << 1))/2] = (byte) ((((section_y[((y % 16) << 7) + (z << 3) + (x >> 1)] & 0xff) & 0xf0) & 0xff) >> 4);
                    }
                }
            }
        }
        return mcrBlockIds;
    }
}
