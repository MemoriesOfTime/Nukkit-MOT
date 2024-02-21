package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ChunkException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.IOException;
import java.util.List;

import static cn.nukkit.level.format.anvil.util.BlockStorage.SECTION_SIZE;
import static cn.nukkit.level.format.leveldb.LevelDbConstants.SUB_CHUNK_SIZE;

@Log4j2
public class StateBlockStorage {

    private static final int SIZE = 16 * 16 * 16;

    private List<BlockStateSnapshot> palette;
    private BitArray bitArray;

    public StateBlockStorage() {
        this(BitArrayVersion.V2);
    }

    public StateBlockStorage(BitArrayVersion version) {
        this.bitArray = version.createPalette();
        this.palette = new ObjectArrayList<>(16);
        this.palette.add(BlockStateMapping.get().getBlockState(0, 0));
    }

    public StateBlockStorage(BitArray bitArray, List<BlockStateSnapshot> palette) {
        this.palette = palette;
        this.bitArray = bitArray;
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

        NBTInputStream inputStream = null;
        try {
            inputStream = NbtUtils.createReaderLE(new ByteBufInputStream(byteBuf));
            for (int i = 0; i < paletteSize; ++i) {
                NbtMap tag;
                BlockStateSnapshot stateSnapshot;
                try {
                    tag = (NbtMap) inputStream.readTag();
                    //noinspection ResultOfMethodCallIgnored
                    tag.hashCode();
                    stateSnapshot = BlockStateMapping.get().getOrUpdateBlockState(tag);
                } catch (IOException e) {
                    throw new ChunkException("Invalid blockstate NBT at offset " + i + " in paletted storage", e);
                }

                this.palette.add(stateSnapshot);
            }
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

    private static int getPaletteHeader(BitArrayVersion version, boolean runtime) {
        return (version.getId() << 1) | (runtime ? 1 : 0);
    }

    public int get(int index) {
        BlockStateSnapshot snapshot = this.palette.get(this.bitArray.get(index));
        return snapshot.getLegacyId() << Block.DATA_BITS | snapshot.getLegacyData();
    }

    public int get(int x, int y, int z) {
        return this.get(elementIndex(x, y, z));
    }

    public int get(BlockVector3 pos) {
        return this.get(elementIndex(pos.x, pos.y, pos.z));
    }

    public void set(int index, BlockStateSnapshot value) {
        try {
            int paletteIndex = this.getOrAdd(value);
            this.bitArray.set(index, paletteIndex);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to set value: " + value + ", palette: " + palette, e);
        }
    }

    public void set(int x, int y, int z, int value) {
        this.set(elementIndex(x, y, z), BlockStateMapping.get().getBlockStateFromFullId(value));
    }

    public void set(BlockVector3 pos, int value) {
        this.set(elementIndex(pos.x, pos.y, pos.z), BlockStateMapping.get().getBlockStateFromFullId(value));
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

    public void writeToStorage(ByteBuf byteBuf) {
        int paletteSize = this.palette.size();
        BitArrayVersion version = paletteSize <= 1 ? BitArrayVersion.V0 : bitArray.getVersion();
        byteBuf.writeByte(getPaletteHeader(version, false));

        if (version != BitArrayVersion.V0) {
            for (int word : bitArray.getWords()) {
                byteBuf.writeIntLE(word);
            }

            byteBuf.writeIntLE(paletteSize);
        }

        NBTOutputStream outputStream = null;
        try {
            outputStream = NbtUtils.createWriterLE(new ByteBufOutputStream(byteBuf));

            for (int i = 0; i < paletteSize; i++) {
                BlockStateSnapshot snapshot = this.palette.get(i);
                outputStream.writeTag(snapshot.getVamillaState());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    log.error("Failed to close NBT stream", e);
                }
            }
        }
    }

    public void writeTo(int protocol, BinaryStream stream, boolean antiXray) {
        PalettedBlockStorage palettedBlockStorage = PalettedBlockStorage.createFromBlockPalette(protocol);

        for (int i = 0; i < SECTION_SIZE; i++) {
            int fullId = get(i);
            int id = fullId >> Block.DATA_BITS;
            int meta = fullId & Block.DATA_MASK;
            if (antiXray && id < Block.MAX_BLOCK_ID && Level.xrayableBlocks[id]) {
                fullId = Block.STONE << Block.DATA_BITS;
            }
            int runtimeId = GlobalBlockPalette.getOrCreateRuntimeId(protocol, id, meta);
            palettedBlockStorage.setBlock(i, runtimeId);
        }

        palettedBlockStorage.writeTo(stream);
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
    private int getOrAdd(BlockStateSnapshot snapshot) {
        int index = this.palette.indexOf(snapshot);
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
        this.palette.add(snapshot);
        return index;
    }

    public boolean isEmpty() {
        if (this.palette.size() == 1) {
            return true;
        }

        for (int word : this.bitArray.getWords()) {
            if (Integer.toUnsignedLong(word) == 0L) {
                continue;
            }
            return false;
        }
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
        if (count == 1 && this.palette.get(0).getLegacyId() == BlockID.AIR) {
            return false;
        }

        boolean noBlock = true;
        for (int i = 0; i < count; i++) {
            int id = this.palette.get(i).getLegacyId();
            if (id == BlockID.AIR) {
                continue;
            }
            noBlock = false;
            break;
        }
        if (noBlock) {
            BlockStateSnapshot firstId = this.palette.get(0);
            this.palette.clear();
            this.palette.add(firstId);

//            Arrays.fill(this.bitArray.getWords(), 0);
            this.bitArray = BitArrayVersion.V1.createPalette(SIZE);
            return true;
        }

        BitArrayVersion version = BitArrayVersion.V2;
        BitArray newArray = version.createPalette(SIZE);
        List<BlockStateSnapshot> newPalette = new ObjectArrayList<>(count);
        newPalette.add(this.palette.get(0));
        for (int i = 0; i < SIZE; i++) {
            int paletteIndex = this.bitArray.get(i);
            BlockStateSnapshot snapshot = this.palette.get(paletteIndex);
            int newIndex = newPalette.indexOf(snapshot);

            if (newIndex == -1) {
                newIndex = newPalette.size();
                newPalette.add(snapshot);

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
        return new StateBlockStorage(this.bitArray.copy(), new ObjectArrayList<>(this.palette));
    }

    public static int elementIndex(int x, int y, int z) {
        int index = (x << 8) | (z << 4) | y;
        if (index < 0 || index >= SUB_CHUNK_SIZE) {
            throw new IllegalArgumentException("Invalid index: " + x + ", " + y + ", " + z);
        }
        return index;
    }
}
