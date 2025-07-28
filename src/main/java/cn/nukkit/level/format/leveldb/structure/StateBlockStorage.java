package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.anvil.util.BlockStorage;
import cn.nukkit.level.format.anvil.util.NibbleArray;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.utils.BinaryStream;
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
import java.util.Arrays;
import java.util.List;

import static cn.nukkit.level.format.leveldb.LevelDBConstants.SUB_CHUNK_SIZE;

@Log4j2
public class StateBlockStorage {

    private static final int SECTION_SIZE = 16 * 16 * 16;

    private List<BlockStateSnapshot> palette;
    private BitArray bitArray;

    //用于兼容1.13以下版本
    private byte[] blockIds;
    private NibbleArray blockData;

    public StateBlockStorage() {
        this(BitArrayVersion.V2);
    }

    public StateBlockStorage(BitArrayVersion version) {
        this.bitArray = version.createPalette();
        this.palette = new ObjectArrayList<>(16);
        this.palette.add(BlockStateMapping.get().getState(0, 0));

        this.blockIds = null;
        this.blockData = null;
    }

    protected StateBlockStorage(BitArray bitArray, List<BlockStateSnapshot> palette, byte[] blockIds, NibbleArray blockData) {
        this.palette = palette;
        this.bitArray = bitArray;
        this.blockIds = blockIds;
        this.blockData = blockData;
    }

    private static int getPaletteHeader(BitArrayVersion version, boolean runtime) {
        return (version.getId() << 1) | (runtime ? 1 : 0);
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

            for (BlockStateSnapshot state : this.palette) {
                outputStream.writeTag(state.getVanillaState());
            }
        } catch (Exception e) {
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

    public void readFromStorage(ByteBuf buffer, ChunkBuilder chunkBuilder) {
        short header = buffer.readUnsignedByte();

        if (header == -1) {
            return;
        }

        this.palette.clear();

        BitArrayVersion version = BitArrayVersion.get(header >> 1, true);

        int paletteSize = 1;
        if (version == BitArrayVersion.V0) {
            this.bitArray = version.createPalette(SUB_CHUNK_SIZE, null);
        } else {
            int expectedWordSize = version.getWordsForSize(SUB_CHUNK_SIZE);
            int[] words = new int[expectedWordSize];
            for (int i = 0; i < expectedWordSize; ++i) {
                words[i] = buffer.readIntLE();
            }
            paletteSize = buffer.readIntLE();
            this.bitArray = version.createPalette(SUB_CHUNK_SIZE, words);
        }

        if (version.getMaxEntryValue() < paletteSize - 1) {
            throw new IllegalArgumentException(
                    chunkBuilder.debugString() + " Palette (version " + version.name() + ") is too large. Max size " + version.getMaxEntryValue() + ". Actual size " + paletteSize
            );
        }

        NBTInputStream inputStream = null;
        try {
            inputStream = NbtUtils.createReaderLE(new ByteBufInputStream(buffer));
            for (int i = 0; i < paletteSize; ++i) {
                try {
                    NbtMap state = (NbtMap) inputStream.readTag();
                    //noinspection ResultOfMethodCallIgnored
                    state.hashCode(); // cache hashCode

                    BlockStateSnapshot blockState = BlockStateMapping.get().getStateUnsafe(state);
                    if (blockState == null) {
                        NbtMap updatedState = BlockStateMapping.get().updateVanillaState(state);
                        blockState = BlockStateMapping.get().getUpdatedOrCustom(state, updatedState);
                        if (!blockState.isCustom()) {
                            if (Nukkit.DEBUG > 1) {
                                log.info("[{}] Updated unmapped block state: {} => {}", chunkBuilder.debugString(), state, blockState.getVanillaState());
                            }
                            chunkBuilder.dirty();
                        }

                        if (Nukkit.DEBUG > 1 && blockState.getRuntimeId() == BlockStateMapping.get().getDefaultRuntimeId()) {
                            log.info("[{}] Chunk contains unknown block {}  => {}", chunkBuilder.debugString(), state, updatedState);
                        }
                    }

                    if (Nukkit.DEBUG > 1 && this.palette.contains(blockState)) {
                        log.info("[{}] Palette contains block state twice: {}", chunkBuilder.debugString(), state);
                    }
                    this.palette.add(blockState);
                } catch (Exception e) {
                    log.error("[{}] Unable to deserialize chunk block state", chunkBuilder.debugString(), e);
                }
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

    public BlockStateSnapshot getBlockState(int index) {
        return this.palette.get(this.bitArray.get(index));
    }

    public BlockStateSnapshot getBlockState(int x, int y, int z) {
        return this.getBlockState(elementIndex(x, y, z));
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
            if(this.blockIds != null && this.blockData != null) {
                this.blockIds[index] = (byte) (value.getLegacyId() & 0xff);
                this.blockData.set(index, (byte) (value.getLegacyData() & 0xf));
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to set value: " + value + ", palette: " + palette, e);
        }
    }

    public void set(int x, int y, int z, BlockStateSnapshot value) {
        this.set(elementIndex(x, y, z), value);
    }

    public void set(int x, int y, int z, int value) {
        this.set(elementIndex(x, y, z), BlockStateMapping.get().getBlockStateFromFullId(value));
    }

    public void set(BlockVector3 pos, int value) {
        this.set(elementIndex(pos.x, pos.y, pos.z), BlockStateMapping.get().getBlockStateFromFullId(value));
    }

    @Deprecated
    public void writeTo(int protocol, BinaryStream stream, boolean antiXray) {
        this.writeTo(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode), stream, antiXray);
    }

    public void writeTo(GameVersion protocol, BinaryStream stream, boolean antiXray) {
        PalettedBlockStorage palettedBlockStorage = PalettedBlockStorage.createFromBlockPalette(protocol);

        for (int i = 0; i < SECTION_SIZE; i++) {
            int fullId = get(i);
            int id = fullId >> Block.DATA_BITS;
            int meta = fullId & Block.DATA_MASK;
            if (antiXray && id < Block.MAX_BLOCK_ID && Level.xrayableBlocks[id]) {
                id = Block.STONE;
                meta = 0;
            }
            int runtimeId = GlobalBlockPalette.getOrCreateRuntimeId(protocol, id, meta);
            palettedBlockStorage.setBlock(i, runtimeId);
        }

        palettedBlockStorage.writeTo(stream);
    }

    private void grow(BitArrayVersion version) {
        BitArray newBitArray = version.createPalette(SECTION_SIZE);
        for (int i = 0; i < SECTION_SIZE; i++) {
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
            if (Integer.toUnsignedLong(word) != 0L) {
                return false;
            }
        }
        return true;
    }

    public byte[] getBlockIds() {
        if (!this.isEmpty()) {
            this.computeOldData();
            return Arrays.copyOf(blockIds, blockIds.length);
        } else {
            return new byte[BlockStorage.SECTION_SIZE];
        }
    }

    public byte[] getBlockData() {
        if (!this.isEmpty()) {
            this.computeOldData();
            return this.blockData.getData();
        } else {
            return new byte[2048];
        }
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
            this.bitArray = BitArrayVersion.V1.createPalette(SECTION_SIZE);
            return true;
        }

        BitArrayVersion version = BitArrayVersion.V2;
        BitArray newArray = version.createPalette(SECTION_SIZE);
        List<BlockStateSnapshot> newPalette = new ObjectArrayList<>(count);
        newPalette.add(this.palette.get(0));
        for (int i = 0; i < SECTION_SIZE; i++) {
            int paletteIndex = this.bitArray.get(i);
            BlockStateSnapshot snapshot = this.palette.get(paletteIndex);
            int newIndex = newPalette.indexOf(snapshot);

            if (newIndex == -1) {
                newIndex = newPalette.size();
                newPalette.add(snapshot);

                if (newIndex > version.getMaxEntryValue()) {
                    version = version.next();
                    BitArray growArray = version.createPalette(SECTION_SIZE);
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

    protected void computeOldData() {
        if (this.blockIds == null || this.blockData == null) {
            this.blockIds = new byte[SECTION_SIZE];
            this.blockData = new NibbleArray(BlockStorage.SECTION_SIZE);

            for (int i = 0; i < this.bitArray.size(); i++) {
                int fullId = this.get(i);
                this.blockIds[i] = (byte) ((fullId >> Block.DATA_BITS) & 0xff);
                this.blockData.set(i, (byte) (fullId & 0xf));
            }
        }
    }

    public StateBlockStorage copy() {
        return new StateBlockStorage(
                this.bitArray.copy(),
                new ObjectArrayList<>(this.palette),
                this.blockIds != null ? this.blockIds.clone() : null,
                this.blockData != null ? this.blockData.copy(): null
        );
    }

    public static int elementIndex(int x, int y, int z) {
        int index = (x << 8) | (z << 4) | y;
        if (index < 0 || index >= SUB_CHUNK_SIZE) {
            throw new IllegalArgumentException("Invalid index: " + x + ", " + y + ", " + z);
        }
        return index;
    }
}
