package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.format.leveldb.updater.BlockUpgrader;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ChunkException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static cn.nukkit.level.format.leveldb.LevelDbConstants.SUB_CHUNK_SIZE;

/**
 * @author LT_Name
 */
@Log4j2
public class StateBlockStorage extends PalettedBlockStorage {

    public static StateBlockStorage createFromBlockPalette() {
        return createFromBlockPalette(BitArrayVersion.V2, 0);
    }

    public static StateBlockStorage createFromBlockPalette(int protocol) {
        return createFromBlockPalette(BitArrayVersion.V2, protocol);
    }

    public static StateBlockStorage createFromBlockPalette(BitArrayVersion version, int protocol) {
        int runtimeId;
        if (protocol >= ProtocolInfo.v1_16_100) {
            runtimeId = GlobalBlockPalette.getOrCreateRuntimeId(protocol, 0); // Air is first
        } else {
            runtimeId = 0;
        }
        return new StateBlockStorage(version, runtimeId);
    }

    public static StateBlockStorage createWithDefaultState(int defaultState) {
        return createWithDefaultState(BitArrayVersion.V2, defaultState);
    }

    public static StateBlockStorage createWithDefaultState(BitArrayVersion version, int defaultState) {
        return new StateBlockStorage(version, defaultState);
    }

    protected StateBlockStorage(BitArrayVersion version, int defaultState) {
        super(version, defaultState);
    }

    protected StateBlockStorage(BitArray bitArray, IntList palette) {
        super(bitArray, palette);
    }

    @Nullable
    public static StateBlockStorage ofBlock(BinaryStream stream) {
        byte header = stream.getSingedByte();

        if (header == -1) {
            return null;
        }

        BitArrayVersion version = BitArrayVersion.get(header >> 1, true);
        BitArray bitArray;
        int paletteSize;
        if (version == BitArrayVersion.V0) {
            bitArray = version.createPalette(SUB_CHUNK_SIZE, null);

            paletteSize = 1;
        } else {
            int expectedWordSize = version.getWordsForSize(SUB_CHUNK_SIZE);
            int[] words = new int[expectedWordSize];
            for (int i = 0; i < expectedWordSize; ++i) {
                words[i] = stream.getLInt();
            }
            bitArray = version.createPalette(SUB_CHUNK_SIZE, words);

            paletteSize = stream.getLInt();
        }

        int[] palette = new int[paletteSize];
        ByteArrayInputStream bais = new ByteArrayInputStream(stream.getBufferUnsafe());
        bais.skip(stream.getOffset());
        for (int i = 0; i < paletteSize; ++i) {
            NbtMap nbtMap;
            try {
                nbtMap = (NbtMap) NbtUtils.createReaderLE(bais).readTag();
                //tag = NBTIO.read(bais, ByteOrder.LITTLE_ENDIAN, false);
            } catch (IOException e) {
                throw new ChunkException("Invalid blockstate NBT at offset " + i + " in paletted storage", e);
            }

            CompoundTag tag = BlockUpgrader.upgrade(nbtMap);
            palette[i] = GlobalBlockPalette.getLegacyFullId(ProtocolInfo.CURRENT_LEVEL_PROTOCOL, tag);
        }
        stream.setOffset(stream.getCount() - bais.available());

        if (paletteSize == 0) {
            // corrupted
            return createWithDefaultState(BlockID.AIR);
        }

        return new StateBlockStorage(bitArray, IntArrayList.wrap(palette));
    }


    public int getBlock(int index) {
        int fullId = this.palette.getInt(this.bitArray.get(index));
        if (fullId < 0) {
            return BlockID.AIR;
        }
        return fullId;
    }

    public int getBlock(int x, int y, int z) {
        return this.getBlock(getIndex(x, y, z));
    }

    public int getBlock(BlockVector3 pos) {
        return this.getBlock(getIndex(pos.x, pos.y, pos.z));
    }

    @Override
    public void writeTo(BinaryStream stream) {
        BitArrayVersion version = bitArray.getVersion();
        stream.putByte((byte) getPaletteHeader(version));

        if (version == BitArrayVersion.EMPTY) {
            return;
        }

        if (version != BitArrayVersion.V0) {
            for (int word : bitArray.getWords()) {
                stream.putLInt(word);
            }

            stream.putVarInt(palette.size());
        }

        //palette.forEach((IntConsumer) stream::putVarInt);
        for (int i = 0; i < this.palette.size(); i++) {
            int id = this.palette.getInt(i);
            stream.putVarInt(GlobalBlockPalette.getOrCreateRuntimeId(ProtocolInfo.CURRENT_LEVEL_PROTOCOL, id));
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

    @Override
    public StateBlockStorage copy() {
        return new StateBlockStorage(this.bitArray.copy(), new IntArrayList(this.palette));
    }
}
