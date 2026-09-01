package cn.nukkit.level.format.generic;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ChunkException;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EmptyChunkSection implements ChunkSection {

    public static final EmptyChunkSection[] EMPTY = new EmptyChunkSection[16];

    public static final byte[] EMPTY_ID_ARRAY = new byte[4096];
    public static final byte[] EMPTY_DATA_ARRAY = new byte[2048];
    public static byte[] EMPTY_LIGHT_ARR = new byte[2048];
    public static byte[] EMPTY_SKY_LIGHT_ARR = new byte[2048];

    private static final ConcurrentHashMap<Integer, EmptyChunkSection> BY_SECTION_Y = new ConcurrentHashMap<>();

    static {
        for (int y = 0; y < EMPTY.length; y++) {
            EMPTY[y] = new EmptyChunkSection(y);
        }
        Arrays.fill(EMPTY_SKY_LIGHT_ARR, (byte) 255);
    }

    /**
     * 按世界 section Y 获取共享的不可变空 section 单例，支持负数 Y 和超过 16 段的维度高度。
     * <p>
     * Returns a shared immutable empty-section singleton for the given world section Y,
     * supporting negative Y and dimensions taller than 16 sections.
     */
    public static EmptyChunkSection bySectionY(int sectionY) {
        return BY_SECTION_Y.computeIfAbsent(sectionY, EmptyChunkSection::new);
    }

    private final int y;

    public EmptyChunkSection(int y) {
        this.y = y;
    }

    @Override
    public int getY() {
        return this.y;
    }

    @Override
    final public int getBlockId(int x, int y, int z) {
        return 0;
    }

    @Override
    public int getBlockId(int x, int y, int z, int layer) {
        return 0;
    }

    @Override
    public int getFullBlock(int x, int y, int z) throws ChunkException {
        return 0;
    }

    @Override
    public int[] getBlockState(int x, int y, int z, int layer) {
        return new int[]{0,0};
    }

    @Override
    public boolean setBlockAtLayer(int x, int y, int z, int layer, int blockId) {
        if (blockId != 0) throw new ChunkException("Tried to modify an empty Chunk");
        return false;
    }

    @Override
    public Block getAndSetBlock(int x, int y, int z, int layer, Block block) {
        if (block.getId() != 0) throw new ChunkException("Tried to modify an empty Chunk");
        return Block.get(0);
    }

    @Override
    public Block getAndSetBlock(int x, int y, int z, Block block) {
        if (block.getId() != 0) throw new ChunkException("Tried to modify an empty Chunk");
        return Block.get(0);
    }

    @Override
    public void setBlockId(int x, int y, int z, int layer, int id) {
        if (id != 0) throw new ChunkException("Tried to modify an empty Chunk");
    }

    @Override
    public boolean setBlock(int x, int y, int z, int blockId) throws ChunkException {
        if (blockId != 0) throw new ChunkException("Tried to modify an empty Chunk");
        return false;
    }

    @Override
    public boolean setBlock(int x, int y, int z, int blockId, int meta) throws ChunkException {
        if (blockId != 0) throw new ChunkException("Tried to modify an empty Chunk");
        return false;
    }

    @Override
    public boolean setBlockAtLayer(int x, int y, int z, int layer, int blockId, int meta) {
        if (blockId != 0) throw new ChunkException("Tried to modify an empty Chunk");
        return false;
    }

    @Override
    public byte[] getIdArray() {
        return EMPTY_ID_ARRAY;
    }

    @Override
    public byte[] getIdExtraArray(int layer) {
        return EMPTY_ID_ARRAY;
    }

    @Override
    public byte[] getIdArray(int layer) {
        return EMPTY_ID_ARRAY;
    }

    @Override
    public byte[] getDataArray() {
        return EMPTY_DATA_ARRAY;
    }

    @Override
    public byte[] getDataArray(int layer) {
        return EMPTY_DATA_ARRAY;
    }

    @Override
    public byte[] getDataExtraArray(int layer) {
        return EMPTY_DATA_ARRAY;
    }

    @Override
    public byte[] getSkyLightArray() {
        return EMPTY_SKY_LIGHT_ARR;
    }

    @Override
    public byte[] getLightArray() {
        return EMPTY_LIGHT_ARR;
    }

    @Override
    final public void setBlockId(int x, int y, int z, int id) throws ChunkException {
        if (id != 0) throw new ChunkException("Tried to modify an empty Chunk");
    }

    @Override
    public boolean setFullBlockId(int x, int y, int z, int layer, int fullId) {
        if (fullId != 0) throw new ChunkException("Tried to modify an empty Chunk");
        return false;
    }

    @Override
    final public int getBlockData(int x, int y, int z) {
        return 0;
    }

    @Override
    public int getBlockData(int x, int y, int z, int layer) {
        return 0;
    }

    @Override
    public void setBlockData(int x, int y, int z, int data) throws ChunkException {
        if (data != 0) throw new ChunkException("Tried to modify an empty Chunk");
    }

    @Override
    public void setBlockData(int x, int y, int z, int layer, int data) {
        if (data != 0) throw new ChunkException("Tried to modify an empty Chunk");
    }

    @Override
    public boolean setFullBlockId(int x, int y, int z, int fullId) {
        if (fullId != 0) throw new ChunkException("Tried to modify an empty Chunk");
        return false;
    }

    @Override
    public int getFullBlock(int x, int y, int z, int layer) {
        return 0;
    }

    @Override
    public int getBlockLight(int x, int y, int z) {
        return 0;
    }

    @Override
    public void setBlockLight(int x, int y, int z, int level) throws ChunkException {
        if (level != 0) throw new ChunkException("Tried to modify an empty Chunk");
    }

    @Override
    public int getBlockSkyLight(int x, int y, int z) {
        return 15;
    }

    @Override
    public void setBlockSkyLight(int x, int y, int z, int level) throws ChunkException {
        if (level != 15) throw new ChunkException("Tried to modify an empty Chunk");
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean hasLayer(int layer) {
        return false;
    }

    @Override
    public byte[] getBytes(GameVersion gameVersion) {
        if (gameVersion.getProtocol() < ProtocolInfo.v1_2_0) {
            ByteBuffer buffer = ByteBuffer.allocate(10240);
            byte[] skyLight = new byte[2048];
            Arrays.fill(skyLight, (byte) 0xff);
            buffer.position(6144);
            return buffer
                    .put(skyLight)
                    .array();
        }
        return new byte[6145];
    }

    @Override
    public void writeTo(GameVersion gameVersion, BinaryStream stream, boolean antiXray) {
        if (gameVersion.getProtocol() >= ProtocolInfo.v1_19_80) {
            // v9 空段：0 层（与 vanilla BDS 空子区块编码一致）
            // v9 empty sub-chunk: zero layers, matching vanilla BDS empty-subchunk encoding
            stream.putByte((byte) 9);
            stream.putByte((byte) 0);
            stream.putByte((byte) this.y);
            return;
        }
        if (gameVersion.getProtocol() >= ProtocolInfo.v1_13_0) {
            // v8 空段：2 层（与 fresh section 的 hasLayer(1) 一致）V2 单条空气调色板、words 全零，
            // 修复旧实现写 6145 字节原始数组导致的格式不匹配
            // v8 empty sub-chunk: 2 layers (matching fresh sections' hasLayer(1)) with a V2
            // single-air palette and zero words, fixing the old 6145-byte raw-array mismatch
            stream.putByte((byte) 8);
            stream.putByte((byte) 2);
            PalettedBlockStorage palette = PalettedBlockStorage.createWithDefaultState(GlobalBlockPalette.getOrCreateRuntimeId(gameVersion, Block.AIR, 0));
            palette.writeTo(stream);
            palette.writeTo(stream);
            return;
        }
        stream.put(this.getBytes(gameVersion));
    }

    @Override
    public int getMaximumLayer() {
        return 0;
    }

    @Override
    public CompoundTag toNBT() {
        return null;
    }

    @Override
    public EmptyChunkSection copy() {
        return this;
    }

    @Override
    public int getContentVersion() {
        return 0;
    }

    @Override
    public boolean isDirty() {
        return false;
    }
}
