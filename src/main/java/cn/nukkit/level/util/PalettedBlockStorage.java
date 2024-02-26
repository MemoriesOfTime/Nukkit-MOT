package cn.nukkit.level.util;

import cn.nukkit.Server;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ChunkException;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

public class PalettedBlockStorage {

    protected static final int SIZE = 4096; // 16 * 16 * 16

    protected IntList palette;
    protected BitArray bitArray;

    public static PalettedBlockStorage createFromBlockPalette() {
        return createFromBlockPalette(BitArrayVersion.V2, 0);
    }

    public static PalettedBlockStorage createFromBlockPalette(int protocol) {
        return PalettedBlockStorage.createFromBlockPalette(BitArrayVersion.V2, protocol);
    }

    public static PalettedBlockStorage createFromBlockPalette(BitArrayVersion version, int protocol) {
        int runtimeId;
        if (protocol >= ProtocolInfo.v1_16_100) {
            runtimeId = GlobalBlockPalette.getOrCreateRuntimeId(protocol, 0); // Air is first
        } else {
            runtimeId = 0;
        }
        return new PalettedBlockStorage(version, runtimeId);
    }

    public static PalettedBlockStorage createWithDefaultState(int defaultState) {
        return createWithDefaultState(BitArrayVersion.V2, defaultState);
    }

    public static PalettedBlockStorage createWithDefaultState(BitArrayVersion version, int defaultState) {
        return new PalettedBlockStorage(version, defaultState);
    }

    protected PalettedBlockStorage(BitArrayVersion version, int defaultState) {
        this.bitArray = version.createPalette(SIZE);
        this.palette = new IntArrayList(16);
        this.palette.add(defaultState);
    }

    protected PalettedBlockStorage(BitArray bitArray, IntList palette) {
        this.palette = palette;
        this.bitArray = bitArray;
    }

    protected int getPaletteHeader(BitArrayVersion version) {
        return getPaletteHeader(version, true);
    }

    protected int getPaletteHeader(BitArrayVersion version, boolean runtime) {
        return (version.getId() << 1) | (runtime ? 1 : 0);
    }

    protected int getIndex(int x, int y, int z) {
        return (x << 8) | (z << 4) | y;
    }

    public int getBlock(int index) {
        return this.palette.getInt(this.bitArray.get(index));
    }

    public int getBlock(int x, int y, int z) {
        return this.getBlock(getIndex(x, y, z));
    }

    public int getBlock(BlockVector3 pos) {
        return this.getBlock(getIndex(pos.x, pos.y, pos.z));
    }

    public void setBlock(int x, int y, int z, int runtimeId) {
        this.setBlock(this.getIndex(x, y, z), runtimeId);
    }

    public void setBlock(int index, int runtimeId) {
        try {
            int id = this.idFor(runtimeId);
            this.bitArray.set(index, id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to set block runtime ID: " + runtimeId + ", palette: " + palette, e);
        }
    }

    public void setBlock(BlockVector3 pos, int value) {
        this.setBlock(getIndex(pos.x, pos.y, pos.z), value);
    }

    public void readFromStorage(ByteBuf byteBuf) {
        short header = byteBuf.readUnsignedByte();

        BitArrayVersion version  = BitArrayVersion.get(header >> 1, true);

        this.palette.clear();

        int paletteSize = 1;
        if (version == BitArrayVersion.V0) {
            this.bitArray = version.createPalette(SIZE, null);
        } else {
            int expectedWordSize = version.getWordsForSize(SIZE);
            int[] words = new int[expectedWordSize];
            int i2 = 0;
            for (int i = 0; i < expectedWordSize; ++i) {
                words[i] = byteBuf.readIntLE();
            }
            this.bitArray = version.createPalette(SIZE, words);
            paletteSize = byteBuf.readIntLE();
        }

        if (version.getMaxEntryValue() < paletteSize - 1) {
            throw new ChunkException("Invalid paletteSize size: " + paletteSize + ", max: " + version.getMaxEntryValue());
        }

        for (int i = 0; i < paletteSize; i++) {
            int runtimeId = byteBuf.readIntLE();
            this.palette.add(runtimeId);
            if (runtimeId < 0) {
                Server.getInstance().getLogger().warning("Invalid runtimeId: " + runtimeId + ", palette: " + palette);
            }
        }
    }

    public void writeToStorage(ByteBuf byteBuf) {
        int paletteSize = this.palette.size();
        BitArrayVersion version = paletteSize <= 1 ? BitArrayVersion.V0 : this.bitArray.getVersion();
        byteBuf.writeByte(this.getPaletteHeader(version, false));

        if (version != BitArrayVersion.V0) {
            for (int i : this.bitArray.getWords()) {
                byteBuf.writeIntLE(i);
            }
            byteBuf.writeIntLE(paletteSize);
        }

        for (int id : this.palette) {
            byteBuf.writeIntLE(id);
        }
    }

    public void writeTo(BinaryStream stream) {
        this.writeTo(stream, id -> id);
    }

    public void writeTo(BinaryStream stream, Int2IntFunction idConvert) {
        stream.putByte((byte) getPaletteHeader(bitArray.getVersion()));

        if (bitArray.getVersion() != BitArrayVersion.V0) {
            for (int word : bitArray.getWords()) {
                stream.putLInt(word);
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

    private void onResize(BitArrayVersion version) {
        BitArray newBitArray = version.createPalette();

        for (int i = 0; i < SIZE; i++) {
            newBitArray.set(i, this.bitArray.get(i));
        }
        this.bitArray = newBitArray;
    }

    private int idFor(int runtimeId) {
        int index = this.palette.indexOf(runtimeId);
        if (index != -1) {
            return index;
        }

        index = this.palette.size();
        BitArrayVersion version = this.bitArray.getVersion();
        if (index > version.getMaxEntryValue()) {
            BitArrayVersion next = version.next();
            if (next != null) {
                this.onResize(next);
            }
        }
        this.palette.add(runtimeId);
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

    public PalettedBlockStorage copy() {
        return new PalettedBlockStorage(this.bitArray.copy(), new IntArrayList(this.palette));
    }
}
