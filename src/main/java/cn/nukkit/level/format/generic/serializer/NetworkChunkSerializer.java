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
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ThreadCache;
import cn.nukkit.utils.Utils;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class NetworkChunkSerializer {

    private static final byte[] PAD_256 = new byte[256];

    private static final int EXTENDED_NEGATIVE_SUB_CHUNKS = 4;

    private static final byte[] negativeSubChunksV8;
    private static final byte[] negativeSubChunksV9;
    private static final byte[][] negativeSubChunkBlobsV8;
    private static final byte[][] negativeSubChunkBlobsV9;

    static {
        // Build up 4 SubChunks for the extended negative height
        BinaryStream stream = new BinaryStream();
        for (int i = 0; i < EXTENDED_NEGATIVE_SUB_CHUNKS; i++) {
            stream.putByte((byte) 8); // SubChunk version
            stream.putByte((byte) 0); // 0 layers
        }
        negativeSubChunksV8 = stream.getBuffer();

        stream = new BinaryStream();
        for (int i = 0; i < EXTENDED_NEGATIVE_SUB_CHUNKS; i++) {
            stream.putByte((byte) 9);
            stream.putByte((byte) 0); // 0 layers
            stream.putByte((byte) (-4 + i)); // section y
        }
        negativeSubChunksV9 = stream.getBuffer();

        negativeSubChunkBlobsV8 = new byte[EXTENDED_NEGATIVE_SUB_CHUNKS][];
        negativeSubChunkBlobsV9 = new byte[EXTENDED_NEGATIVE_SUB_CHUNKS][];
        for (int i = 0; i < EXTENDED_NEGATIVE_SUB_CHUNKS; i++) {
            negativeSubChunkBlobsV8[i] = new byte[]{(byte) 8, (byte) 0};
            negativeSubChunkBlobsV9[i] = new byte[]{(byte) 9, (byte) 0, (byte) (-4 + i)};
        }
    }

    @Deprecated
    public static void serialize(IntSet protocols, BaseChunk chunk, Consumer<NetworkChunkSerializerCallback> callback, boolean antiXray, DimensionData dimensionData) {
        Server.mvw("NetworkChunkSerializer#serialize(IntSet, BaseChunk, Consumer<NetworkChunkSerializerCallback>, boolean, DimensionData) is deprecated, please use NetworkChunkSerializer#serialize(ObjectSet<GameVersion>, BaseChunk, Consumer<NetworkChunkSerializerCallback>, boolean, DimensionData) instead.");
        serialize(Utils.intSet2GameVersionSet(protocols, false), chunk, callback, antiXray, dimensionData);
    }

    public static void serialize(ObjectSet<GameVersion> protocols, BaseChunk chunk, Consumer<NetworkChunkSerializerCallback> callback, boolean antiXray, DimensionData dimensionData) {
        serialize(protocols, chunk, callback, antiXray, dimensionData, false);
    }

    /**
     * serialize 并可选捕获结构化产物：≥v1_18_0 时逐子区块字节、biomes、方块实体 NBT，
     * 供客户端缓存（blob hash）与子区块拉取装配使用；捕获与整列 payload 同流同序。
     * <p>
     * Serializes, optionally capturing structured output for ≥v1_18_0: per-sub-chunk bytes,
     * biomes and block entity NBT, used by client-cache (blob hash) and sub-chunk pull
     * assembly. Capture reuses the same stream pass as the full-column payload.
     */
    public static void serialize(ObjectSet<GameVersion> protocols, BaseChunk chunk, Consumer<NetworkChunkSerializerCallback> callback, boolean antiXray, DimensionData dimensionData, boolean captureBlobs) {
        for (GameVersion gameVersion : protocols) {
            int protocolId = gameVersion.getProtocol();
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

            BinaryStream stream = ThreadCache.binaryStream.get().reset();
            NetworkChunkData networkChunkData = new NetworkChunkData(gameVersion, subChunkCount, antiXray, dimensionData);
            List<byte[]> subChunkBlobs = captureBlobs && protocolId >= ProtocolInfo.v1_18_0 ? new ArrayList<>() : null;
            byte[] biomes = null;
            if (protocolId >= ProtocolInfo.v1_18_30) {
                biomes = serialize1_18_30(stream, chunk, sections, networkChunkData, subChunkBlobs);
            } else if (protocolId >= ProtocolInfo.v1_18_0) {
                biomes = serialize1_18_0(stream, chunk, sections, networkChunkData, subChunkBlobs);
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

            callback.accept(new NetworkChunkSerializerCallback(gameVersion, stream, networkChunkData.getChunkSections(), subChunkBlobs, biomes, blockEntities));
        }
    }

    private static byte[] serialize1_18_30(BinaryStream stream, BaseChunk chunk, ChunkSection[] sections, NetworkChunkData chunkData, List<byte[]> blobSink) {
        DimensionData dimensionData = chunkData.getDimensionData();
        int maxDimensionSections = dimensionData.getHeight() >> 4;
        int subChunkCount = Math.min(maxDimensionSections, chunkData.getChunkSections());

        byte[] biomePalettes = serialize3DBiomes(chunk, chunkData.getGameVersion(), maxDimensionSections);
        stream.reset();

        // Overworld has negative coordinates, But the anvil world does not support it
        int writtenSections = subChunkCount;
        if (dimensionData.getDimensionId() == Level.DIMENSION_OVERWORLD && chunk.getSectionOffset() == 0) {
            boolean v9 = chunkData.getGameVersion().getProtocol() >= ProtocolInfo.v1_19_80;
            stream.put(v9 ? negativeSubChunksV9 : negativeSubChunksV8);
            if (blobSink != null) {
                blobSink.addAll(Arrays.asList(v9 ? negativeSubChunkBlobsV9 : negativeSubChunkBlobsV8));
            }
            writtenSections += EXTENDED_NEGATIVE_SUB_CHUNKS;
        }

        int start = stream.getOffset();
        for (int i = 0; i < subChunkCount; i++) {
            sections[i].writeTo(chunkData.getGameVersion(), stream, chunkData.isAntiXray());
            if (blobSink != null) {
                blobSink.add(Arrays.copyOfRange(stream.getBufferUnsafe(), start, stream.getOffset()));
                start = stream.getOffset();
            }
        }

        stream.put(biomePalettes);
        stream.putByte((byte) 0); // Border blocks

        chunkData.setChunkSections(writtenSections);
        return biomePalettes;
    }

    private static byte[] serialize1_18_0(BinaryStream stream, BaseChunk chunk, ChunkSection[] sections, NetworkChunkData chunkData, List<byte[]> blobSink) {
        DimensionData dimensionData = chunkData.getDimensionData();
        int maxDimensionSections = dimensionData.getHeight() >> 4;
        int subChunkCount = Math.min(maxDimensionSections, chunkData.getChunkSections());

        byte[] biomePalettes = serialize3DBiomes(chunk, chunkData.getGameVersion(), 25);
        stream.reset();

        // Overworld has negative coordinates, But the anvil world does not support it
        int writtenSections = subChunkCount;
        if (dimensionData.getDimensionId() == Level.DIMENSION_OVERWORLD && chunk.getSectionOffset() == 0) {
            stream.put(negativeSubChunksV8);
            if (blobSink != null) {
                blobSink.addAll(Arrays.asList(negativeSubChunkBlobsV8));
            }
            writtenSections += EXTENDED_NEGATIVE_SUB_CHUNKS;
        }

        int start = stream.getOffset();
        for (int i = 0; i < subChunkCount; i++) {
            sections[i].writeTo(chunkData.getGameVersion(), stream, chunkData.isAntiXray());
            if (blobSink != null) {
                blobSink.add(Arrays.copyOfRange(stream.getBufferUnsafe(), start, stream.getOffset()));
                start = stream.getOffset();
            }
        }

        stream.put(biomePalettes);
        stream.putByte((byte) 0); // Border blocks

        chunkData.setChunkSections(writtenSections);
        return biomePalettes;
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
            return NBTIO.write(tagList, ByteOrder.LITTLE_ENDIAN, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 序列化一份方块实体引用快照（主线程取得）的 NBT；异步子区块路径直接传入快照，不再
     * 触碰区块的 lazy 分节索引（该索引仅主线程重建）。
     * <p>
     * Serializes the NBT of a block-entity reference snapshot (taken on the main
     * thread); the async sub-chunk path passes the snapshot directly instead of touching
     * the chunk's lazy per-section index (rebuilt on the main thread only).
     */
    public static byte[] serializeBlockEntities(List<BlockEntity> blockEntities, int protocol) {
        List<CompoundTag> tagList = null;
        for (BlockEntity blockEntity : blockEntities) {
            if (blockEntity instanceof BlockEntitySpawnable) {
                if (tagList == null) {
                    tagList = new ObjectArrayList<>();
                }
                tagList.add(((BlockEntitySpawnable) blockEntity).getSpawnCompound(protocol));
            }
        }
        if (tagList == null) {
            return new byte[0];
        }
        try {
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
        /**
         * ≥v1_18_0 且开启捕获时非 null：逐子区块字节（含主世界负坐标 4 个空子区块），与 payload 同源同序。
         * <p>
         * Non-null when ≥v1_18_0 and capture is on: per-sub-chunk bytes (including the 4
         * negative empty sub-chunks in the overworld), same pass and order as the payload.
         */
        private List<byte[]> subChunkBlobs;
        /**
         * ≥v1_18_0 且开启捕获时非 null：3D biomes 序列化字节 / Non-null when captured: serialized 3D biomes.
         */
        private byte[] biomes;
        /**
         * 方块实体网络 NBT（可能为空数组，未捕获时为 null）/ Block entity network NBT
         * (possibly empty; null when not captured).
         */
        private byte[] blockEntities;

        public NetworkChunkSerializerCallback(GameVersion gameVersion, BinaryStream stream, Integer subchunks) {
            this(gameVersion, stream, subchunks, null, null, null);
        }
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
        private List<byte[]> subChunkBlobs;
        private byte[] biomes;
        private byte[] blockEntities;

        public NetworkChunkSerializerCallbackData(GameVersion gameVersion, long timestamp, int x, int z, int subChunkCount, byte[] payload) {
            this(gameVersion, timestamp, x, z, subChunkCount, payload, null, null, null);
        }
    }
}
