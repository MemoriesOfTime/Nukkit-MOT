package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.leveldb.updater.BlockUpgrader;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.stream.NBTInputStream;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ChunkException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.extern.log4j.Log4j2;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;

import static cn.nukkit.level.format.leveldb.LevelDbConstants.SUB_CHUNK_SIZE;

@Log4j2
public class StateBlockStorage {

    private static final int SIZE = 16 * 16 * 16;

    private IntList palette;
    private BitArray bitArray;

    public StateBlockStorage() {
        this(BitArrayVersion.V2);
    }

    public StateBlockStorage(BitArrayVersion version) {
        this(version, Block.AIR);
    }

    public StateBlockStorage(BitArrayVersion version, int firstId) {
        this.bitArray = version.createPalette(SIZE);
        if (version == BitArrayVersion.EMPTY) {
            this.palette = IntLists.EMPTY_LIST;
            return;
        }
        this.palette = new IntArrayList(version.isSingleton() ? 1 : IntArrayList.DEFAULT_INITIAL_CAPACITY);
        this.palette.add(firstId); // Air is at the start of every block palette.
    }

    public StateBlockStorage(BitArray bitArray, IntList palette) {
        this.palette = palette;
        this.bitArray = bitArray;
    }

    public static StateBlockStorage ofBlock() {
        return new StateBlockStorage(BitArrayVersion.V2, Block.AIR);
    }

    public static StateBlockStorage ofBlock(BitArrayVersion version) {
        return new StateBlockStorage(version, Block.AIR);
    }

    public static StateBlockStorage ofBlock(int airBlockId) {
        return new StateBlockStorage(BitArrayVersion.V2, airBlockId);
    }

    public static StateBlockStorage ofBlock(BitArrayVersion version, int airBlockId) {
        return new StateBlockStorage(version, airBlockId);
    }

    public void readFrom(ByteBuf byteBuf, ChunkBuilder chunkBuilder) {
        short header = byteBuf.readUnsignedByte();

        if (header == -1) {
            return;
        }

        this.palette.clear();

        BitArrayVersion version = BitArrayVersion.get(header >> 1, true);

        int paletteSize;
        if (version == BitArrayVersion.V0) {
            this.bitArray = version.createPalette(SUB_CHUNK_SIZE, null);
            paletteSize = 1;
        } else {
            int expectedWordSize = version.getWordsForSize(SUB_CHUNK_SIZE);
            int[] words = new int[expectedWordSize];
            int i2 = 0;
            for (int i = 0; i < expectedWordSize; ++i) {
                words[i] = byteBuf.readIntLE();
            }
            this.bitArray = version.createPalette(SUB_CHUNK_SIZE, words);
            paletteSize = byteBuf.readIntLE();
        }

        if (version.getMaxEntryValue() < paletteSize - 1) {
            throw new ChunkException("Invalid paletteSize size: " + paletteSize + ", max: " + version.getMaxEntryValue());
        }

        int[] palette = new int[paletteSize];

        NBTInputStream inputStream = null;
        try {
            ByteBufInputStream stream = new ByteBufInputStream(byteBuf);
            inputStream = new NBTInputStream(stream, ByteOrder.LITTLE_ENDIAN);
            //inputStream = NbtUtils.createReaderLE(stream);
            for (int i = 0; i < paletteSize; ++i) {
                CompoundTag tag;
                try {
                    CompoundTag readTag = (CompoundTag) inputStream.readTag();
                    tag = readTag; BlockUpgrader.upgrade(readTag); //TODO 实现方块状态更新
                } catch (IOException e) {
                    throw new ChunkException("Invalid blockstate NBT at offset " + i + " in paletted storage", e);
                }

                int fullId = GlobalBlockPalette.getLegacyFullId(ProtocolInfo.CURRENT_PROTOCOL, tag);
                palette[i] = GlobalBlockPalette.getOrCreateRuntimeId(ProtocolInfo.CURRENT_PROTOCOL, fullId);
            }

            this.palette.addAll(IntList.of(palette));
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e) {
                log.error("Failed to close NBT stream", e);
            }
        }
    }

    public static StateBlockStorage ofBiome(int biomeId) {
        return new StateBlockStorage(BitArrayVersion.V0, biomeId);
    }

    public static StateBlockStorage ofBiome(BitArrayVersion version, int biomeId) {
        return new StateBlockStorage(version, biomeId);
    }

    @Nullable
    public static StateBlockStorage ofBiome(BinaryStream stream) {
        byte header = stream.getSingedByte();

        if (header == -1) {
            return null;
        }

        BitArrayVersion version = BitArrayVersion.get(header >> 1, true);
        int expectedWordSize = version.getWordsForSize(SUB_CHUNK_SIZE);
        int[] words = new int[expectedWordSize];
        for (int i = 0; i < expectedWordSize; ++i) {
            words[i] = stream.getLInt();
        }
        BitArray bitArray = version.createPalette(SUB_CHUNK_SIZE, words);

        int paletteSize = stream.getLInt();
        int[] palette = new int[paletteSize];
        for (int i = 0; i < paletteSize; ++i) {
            palette[i] = stream.getLInt();
        }

        if (paletteSize == 0) {
            // corrupted
            return ofBiome(EnumBiome.OCEAN.id);
        }

        return new StateBlockStorage(bitArray, IntArrayList.wrap(palette));
    }

    private static int getPaletteHeader(BitArrayVersion version, boolean runtime) {
        return (version.getId() << 1) | (runtime ? 1 : 0);
    }

    public int get(int index) {
        return this.palette.getInt(this.bitArray.get(index));
    }

    public int get(int x, int y, int z) {
        return this.get(elementIndex(x, y, z));
    }

    public int get(BlockVector3 pos) {
        return this.get(elementIndex(pos.x, pos.y, pos.z));
    }

    public void set(int index, int value) {
        try {
            int paletteIndex = this.getOrAdd(value);
            this.bitArray.set(index, paletteIndex);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to set value: " + value + ", palette: " + palette, e);
        }
    }

    public void set(int x, int y, int z, int value) {
        this.set(elementIndex(x, y, z), value);
    }

    public void set(BlockVector3 pos, int value) {
        this.set(elementIndex(pos.x, pos.y, pos.z), value);
    }

    /**
     * Fast check.
     */
    public boolean has(int id) {
        return this.palette.contains(id);
    }

    public int indexOf(int id) {
        return this.palette.indexOf(id);
    }

    public void writeTo(ByteBuf byteBuf) {
        this.writeTo(byteBuf, null);
    }

    public void writeTo(ByteBuf byteBuf, Int2IntFunction idConvert) {
        BitArrayVersion version = bitArray.getVersion();
        byteBuf.writeByte((byte) getPaletteHeader(version, true));
        if (version == BitArrayVersion.EMPTY) {
            return;
        }

        if (version != BitArrayVersion.V0) {
            for (int word : bitArray.getWords()) {
                byteBuf.writeIntLE(word);
            }

            byteBuf.writeIntLE(palette.size());
        }

        for (int i = 0; i < this.palette.size(); i++) {
            int id = this.palette.getInt(i);
            if (idConvert != null) {
                id = idConvert.applyAsInt(id);
            }
            byteBuf.writeIntLE(id);
        }
    }

    public void writeTo(BinaryStream stream) {
        this.writeTo(stream, null);
    }

    public void writeTo(BinaryStream stream, Int2IntFunction idConvert) {
        BitArrayVersion version = bitArray.getVersion();
        stream.putByte((byte) getPaletteHeader(version, true));
        if (version == BitArrayVersion.EMPTY) {
            return;
        }

        if (version != BitArrayVersion.V0) {
            for (int word : bitArray.getWords()) {
                stream.putVarInt(word);
            }

            stream.putVarInt(palette.size());
        }

        for (int i = 0; i < this.palette.size(); i++) {
            int id = this.palette.getInt(i);
            if (idConvert != null) {
                id = idConvert.applyAsInt(id);
            }
            stream.putVarInt(id);
        }
    }

   /* public void writeToCache(BinaryStream stream) {
        writeToCache(stream, Blocks::getBlockFullNameById);
    }

    public void writeToCache(BinaryStream stream, IntFunction<String> blockIdToName) {
        if (blockIdToName == null) {
            blockIdToName = Blocks::getBlockFullNameById;
        }

        BitArrayVersion version = bitArray.getVersion();
        stream.putByte((byte) getPaletteHeader(version, false));
        if (version == BitArrayVersion.EMPTY) {
            return;
        }

        if (version != BitArrayVersion.V0) {
            for (int word : bitArray.getWords()) {
                stream.putLInt(word);
            }

            stream.putVarInt(palette.size());
        }

        List<CompoundTag> tagList = new ObjectArrayList<>();
        for (int i = 0; i < this.palette.size(); i++) {
            int fullId = this.palette.getInt(i);
            if (ENABLE_BLOCK_STATE_PERSISTENCE) {
                tagList.add(BlockSerializer.serializeRuntime(fullId));
                continue;
            }

            int id = fullId >> Block.DATA_BITS;
            String name = blockIdToName.apply(id);
            if (name == null) {
                tagList.add(UNKNOWN_BLOCK_TAG);
                log.warn("Unmapped block ID: {}", id);
                continue;
            }
            tagList.add(new CompoundTag()
                    .putString("name", name)
                    .putShort("val", fullId & Block.DATA_MASK));
        }
        try {
            stream.put(NBTIO.write(tagList, ByteOrder.LITTLE_ENDIAN, true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }*/

    public void writeToDisk(BinaryStream stream) {
        BitArrayVersion version = bitArray.getVersion();
        stream.putByte((byte) getPaletteHeader(version, false));
        if (version == BitArrayVersion.EMPTY) {
            return;
        }

        if (version != BitArrayVersion.V0) {
            for (int word : bitArray.getWords()) {
                stream.putLInt(word);
            }

            stream.putLInt(palette.size());
        }

        List<CompoundTag> tagList = new ObjectArrayList<>();
        for (int i = 0; i < this.palette.size(); i++) {
            int fullId = this.palette.getInt(i);
            tagList.add(GlobalBlockPalette.getState(ProtocolInfo.CURRENT_PROTOCOL, fullId));
        }
        try {
            stream.put(NBTIO.write(tagList, ByteOrder.LITTLE_ENDIAN, false));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeToDiskBiome(BinaryStream stream) {
        BitArrayVersion version = bitArray.getVersion();
        stream.putByte((byte) getPaletteHeader(version, true));
        if (version == BitArrayVersion.EMPTY) {
            return;
        }

        if (version != BitArrayVersion.V0) {
            for (int word : bitArray.getWords()) {
                stream.putLInt(word);
            }

            stream.putLInt(palette.size());
        }

        for (int i = 0; i < this.palette.size(); i++) {
            stream.putLInt(this.palette.getInt(i));
        }
    }

    private void grow(BitArrayVersion version) {
        BitArray newBitArray = version.createPalette(SIZE);
        for (int i = 0; i < SIZE; i++) {
            newBitArray.set(i, this.bitArray.get(i));
        }
        this.bitArray = newBitArray;
    }

    /**
     * @return palette index
     */
    private int getOrAdd(int id) {
        int index = this.palette.indexOf(id);
        if (index != -1) {
            return index;
        }

        index = this.palette.size();
        BitArrayVersion version = this.bitArray.getVersion();
        if (index > version.getMaxEntryValue()) {
            BitArrayVersion next = version.next();
            if (next != null) {
                this.grow(next);
            } else if (!this.compress()) {
                throw new IndexOutOfBoundsException("too many elements");
            }
        }
        this.palette.add(id);
        return index;
    }

    public boolean isEmpty() {
        return this.isEmpty(false);
    }

    public boolean isEmpty(boolean fast) {
        if (this.palette.isEmpty()) {
            return true;
        }

        boolean hasBlock = false;
        for (int i = 0; i < this.palette.size(); i++) {
            int id = this.palette.getInt(i);
            if (id != BlockID.AIR) {
                hasBlock = true;
                break;
            }
        }

        if (!hasBlock) {
            return true;
        }
        if (fast) {
            return false;
        }

        int firstId = this.palette.getInt(0);
        if (firstId != BlockID.AIR) {
            // Hive Chunker...
            return false;
        }

        for (int word : this.bitArray.getWords()) {
            if (word != 0) {
                return false;
            }
        }

        this.palette.clear();
        this.palette.add(firstId);
        return true;
    }

    /**
     * @return dirty
     */
    public boolean compress() {
        if (this.palette.isEmpty()) {
            return false;
        }

        int count = this.palette.size();
        if (count == 1 && this.palette.getInt(0) == BlockID.AIR) {
            return false;
        }

        boolean noBlock = true;
        for (int i = 0; i < count; i++) {
            int id = this.palette.getInt(i);
            if (id == BlockID.AIR) {
                continue;
            }
            noBlock = false;
            break;
        }
        if (noBlock) {
            int firstId = this.palette.getInt(0);
            this.palette.clear();
            this.palette.add(firstId);

//            Arrays.fill(this.bitArray.getWords(), 0);
            this.bitArray = BitArrayVersion.V1.createPalette(SIZE);
            return true;
        }

        BitArrayVersion version = BitArrayVersion.V2;
        BitArray newArray = version.createPalette(SIZE);
        IntList newPalette = new IntArrayList(count);
        newPalette.add(this.palette.getInt(0));
        for (int i = 0; i < SIZE; i++) {
            int paletteIndex = this.bitArray.get(i);
            int id = this.palette.getInt(paletteIndex);
            int newIndex = newPalette.indexOf(id);

            if (newIndex == -1) {
                newIndex = newPalette.size();
                newPalette.add(id);

                if (newIndex > version.getMaxEntryValue()) {
                    version = version.next();
                    BitArray growArray = version.createPalette(SIZE);
                    for (int j = 0; j < i; j++) {
                        growArray.set(j, newArray.get(j));
                    }
                    newArray = growArray;
                }
            }

            newArray.set(i, newIndex);
        }
        this.bitArray = newArray;
        this.palette = newPalette;
        return true;
    }

    public StateBlockStorage copy() {
        return new StateBlockStorage(this.bitArray.copy(), new IntArrayList(this.palette));
    }

    protected static int elementIndex(int x, int y, int z) {
        int index = (x << 8) | (z << 4) | y;
        if (index < 0 || index >= SUB_CHUNK_SIZE) {
            throw new IllegalArgumentException("Invalid index: " + x + ", " + y + ", " + z );
        }
        return index;
    }
}
