package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.anvil.util.NibbleArray;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static cn.nukkit.level.format.generic.EmptyChunkSection.*;

public class LevelDBChunkSection implements ChunkSection {
    protected static final StateBlockStorage[] EMPTY = new StateBlockStorage[0];

    private LevelDBChunk parent;

    protected final int y;
    protected StateBlockStorage[] storages;
    protected NibbleArray skyLight; //TODO: lighting
    protected NibbleArray blockLight; //TODO: lighting
    protected boolean dirty;

    protected ReadWriteLock lock = new ReentrantReadWriteLock();
    protected Lock readLock = lock.readLock();
    protected Lock writeLock = lock.writeLock();

    public LevelDBChunkSection(int y) {
        this(null, y);
    }

    public LevelDBChunkSection(LevelDBChunk parent, int y) {
        this.parent = parent;
        this.y = y;
        this.storages = EMPTY;
    }

    public LevelDBChunkSection(int y, @Nullable StateBlockStorage[] storages) {
        this(null, y, storages);
    }

    public LevelDBChunkSection(LevelDBChunk parent, int y, @Nullable StateBlockStorage[] storages) {
        this.parent = parent;
        this.y = y;

        if (storages == null || storages.length == 0) {
            this.storages = EMPTY;
            return;
        }

        int maxLayer = -1;
        for (int i = storages.length - 1; i >= 0; i--) {
            if (storages[i] != null) {
                if (maxLayer == -1) {
                    maxLayer = i;
                }
                continue;
            }

            if (maxLayer == -1) {
                continue;
            }

            storages[i] = StateBlockStorage.ofBlock();
        }

        if (maxLayer == -1) {
            this.storages = EMPTY;
            return;
        }

        int count = maxLayer + 1;
        if (count == storages.length) {
            this.storages = storages;
        }

        this.storages = Arrays.copyOf(this.storages, count);
    }

    @Nullable
    public LevelDBChunk getParent() {
        return parent;
    }

    public void setParent(LevelDBChunk parent) {
        this.parent = parent;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getBlockId(int x, int y, int z) {
        return getBlockId(0, x, y, z);
    }

    @Override
    public int getBlockId(int layer, int x, int y, int z) {
        try {
            this.readLock.lock();

            if (!this.hasLayerUnsafe(layer)) {
                return BlockID.AIR;
            }

            return (this.storages[layer].get(x, y, z)) >> Block.DATA_MASK;
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public void setBlockId(int x, int y, int z, int id) {
        this.setBlockId(0, x, y, z, id);
    }

    @Override
    public void setBlockId(int layer, int x, int y, int z, int id) {
        try {
            this.writeLock.lock();

            if (!this.hasLayerUnsafe(layer)) {
                if (id == BlockID.AIR) {
                    return;
                }

                this.createLayerUnsafe(layer);
            }

            StateBlockStorage storage = this.storages[layer];
            int previous = storage.get(x, y, z);
            int fullId = (id << Block.DATA_BITS) | (previous & Block.DATA_MASK);

            if (previous == fullId) {
                return;
            }

            storage.set(x, y, z, fullId);

            dirty = true;
            parent.onSubChunkBlockChanged(this, layer, x, y, z, previous, fullId);
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    public boolean setFullBlockId(int x, int y, int z, int fullId) {
        return this.setFullBlockId(0, x, y, z, fullId);
    }

    @Override
    public int getBlockData(int layer, int x, int y, int z) {
        try {
            this.readLock.lock();

            if (!this.hasLayerUnsafe(layer)) {
                return 0;
            }

            return (this.storages[layer].get(x, y, z)) & Block.DATA_MASK;
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public void setBlockData(int x, int y, int z, int data) {
        this.setBlockData(0, x, y, z, data);
    }

    @Override
    public void setBlockData(int layer, int x, int y, int z, int data) {
        try {
            this.writeLock.lock();

            if (!this.hasLayerUnsafe(layer)) {
                return;
            }

            StateBlockStorage storage = this.storages[layer];
            int previous = storage.get(x, y, z);
            int fullId = (previous & Block.DATA_BITS) | (data & Block.DATA_MASK);

            if (previous == fullId) {
                return;
            }

            storage.set(x, y, z, fullId);

            dirty = true;
            parent.onSubChunkBlockChanged(this, layer, x, y, z, previous, fullId);
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    public int getFullBlock(int x, int y, int z) {
        return getFullBlock(0, x, y, z);
    }

    @Override
    public int getFullBlock(int layer, int x, int y, int z) {
        try {
            this.readLock.lock();

            if (!this.hasLayerUnsafe(layer)) {
                return BlockID.AIR;
            }

            return this.storages[layer].get(x, y, z);
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public int[] getBlockState(int x, int y, int z, int layer) {
        int full = this.getFullBlock(layer, x, y, z);
        return new int[] { full >> Block.DATA_BITS, full & Block.DATA_MASK };
    }

    @Override
    public boolean setBlock(int x, int y, int z, int blockId) {
        return setBlockAtLayer(x, y, z, 0, blockId, 0);
    }

    @Override
    public boolean setBlock(int x, int y, int z, int blockId, int meta) {
        return setBlock(0, x, y, z, blockId, meta);
    }

    @Override
    public boolean setBlockAtLayer(int x, int y, int z, int layer, int blockId) {
        return setBlockAtLayer(x, y, z, layer, blockId, 0);
    }

    @Override
    public boolean setBlockAtLayer(int x, int y, int z, int layer, int blockId, int meta) {
        return setBlock(layer, x, y, z, blockId, meta);
    }

    @Override
    public Block getAndSetBlock(int layer, int x, int y, int z, Block block) {
        int fullId;
        int previous;
        try {
            this.writeLock.lock();

            StateBlockStorage storage;
            if (!this.hasLayerUnsafe(layer)) {
                if (block.getId() == BlockID.AIR) {
                    return Block.get(BlockID.AIR);
                }
                previous = BlockID.AIR;

                this.createLayerUnsafe(layer);

                storage = this.storages[layer];

                fullId = block.getFullId();
            } else {
                storage = this.storages[layer];
                previous = storage.get(x, y, z);

                fullId = block.getFullId();

                if (previous == fullId) {
                    return Block.fromFullId(previous);
                }
            }

            storage.set(x, y, z, fullId);

            dirty = true;
            parent.onSubChunkBlockChanged(this, layer, x, y, z, previous, fullId);
        } finally {
            this.writeLock.unlock();
        }
        return Block.fromFullId(previous);
    }

    @Override
    public Block getAndSetBlock(int x, int y, int z, Block block) {
        return getAndSetBlock(0, x, y, z, block);
    }

    @Override
    public boolean setFullBlockId(int layer, int x, int y, int z, int fullId) {
        try {
            this.writeLock.lock();

            StateBlockStorage storage;
            int previous;
            if (!this.hasLayerUnsafe(layer)) {
                if (fullId == BlockID.AIR) {
                    return false;
                }

                this.createLayerUnsafe(layer);

                storage = this.storages[layer];
                previous = BlockID.AIR;
            } else {
                storage = this.storages[layer];
                previous = storage.get(x, y, z);

                if (previous == fullId) {
                    return false;
                }
            }

            storage.set(x, y, z, fullId);

            dirty = true;
            parent.onSubChunkBlockChanged(this, layer, x, y, z, previous, fullId);
            return true;
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    public int getBlockData(int x, int y, int z) {
        return getBlockData(0, x, y, z);
    }

    public boolean setBlock(int layer, int x, int y, int z, int blockId, int meta) {
        return setFullBlockId(layer, x, y, z, (blockId << Block.DATA_BITS) | (meta & Block.DATA_MASK));
    }

    @Override
    public int getBlockSkyLight(int x, int y, int z) {
        //TODO: lighting
        return this.parent != null && ((this.y << 4) | y) >= this.parent.getHighestBlockAt(x, z) ? 15 : 0;
    }

    @Override
    public void setBlockSkyLight(int x, int y, int z, int level) {
        //TODO: lighting

    }

    @Override
    public int getBlockLight(int x, int y, int z) {
        //TODO: lighting
        return Block.light[this.getBlockId(0, x, y, z)];
    }

    @Override
    public void setBlockLight(int x, int y, int z, int level) {
        //TODO: lighting
    }

    @Deprecated
    @Override
    public byte[] getIdExtraArray(int layer) {
        // no longer supported
        return EMPTY_ID_ARRAY;
    }

    @Deprecated
    @Override
    public byte[] getIdArray(int layer) {
        // no longer supported
        return EMPTY_ID_ARRAY;
    }

    @Deprecated
    @Override
    public byte[] getIdArray() {
        // no longer supported
        return EMPTY_ID_ARRAY;
    }

    @Deprecated
    @Override
    public byte[] getDataArray() {
        // no longer supported
        return EMPTY_DATA_ARRAY;
    }

    @Deprecated
    @Override
    public byte[] getDataArray(int layer) {
        // no longer supported
        return EMPTY_DATA_ARRAY;
    }

    @Deprecated
    @Override
    public byte[] getDataExtraArray(int layer) {
        // no longer supported
        return EMPTY_DATA_ARRAY;
    }

    @Override
    public byte[] getSkyLightArray() {
        //TODO: lighting
        return EMPTY_SKY_LIGHT_ARR;
    }

    @Override
    public byte[] getLightArray() {
        //TODO: lighting
        return EMPTY_LIGHT_ARR;
    }

    @Override
    public boolean isEmpty() {
        try {
            this.readLock.lock();

            for (StateBlockStorage storage : this.storages) {
                if (storage == null) {
                    continue;
                }

                if (!storage.isEmpty()) {
                    return false;
                }
            }

            return true;
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public boolean hasLayer(int layer) {
        try {
            this.readLock.lock();

            return this.hasLayerUnsafe(layer);
        } finally {
            this.readLock.unlock();
        }
    }

    @Deprecated
    @Override
    public byte[] getBytes(int protocolId) {
        return new byte[0];
    }

    @Override
    public int getMaximumLayer() {
        return 1;
    }

    @Deprecated
    @Override
    public CompoundTag toNBT() {
        return null;
    }

    protected boolean hasLayerUnsafe(int layer) {
        if (layer >= this.storages.length) {
            return false;
        }
        return this.storages[layer] != null;
    }

    protected void createLayer(int layer) {
        try {
            this.writeLock.lock();

            this.createLayerUnsafe(layer);
        } finally {
            this.writeLock.unlock();
        }
    }

    protected void createLayerUnsafe(int layer) {
        StateBlockStorage[] storages;
        if (this.storages.length <= layer) {
            storages = Arrays.copyOf(this.storages, layer + 1);
            this.storages = storages;
        } else {
            storages = this.storages;
        }

        for (int i = layer; i >= 0; i--) {
            if (storages[i] != null) {
                continue;
            }
            storages[i] = StateBlockStorage.ofBlock();
        }
    }

    @Override
    public void writeTo(int protocol, BinaryStream stream, boolean antiXray) {
        try {
            this.readLock.lock();

            int layers = Math.max(2, this.storages.length);

            if (protocol >= ProtocolInfo.v1_18_0) {
                stream.putByte((byte) 9);
                stream.putByte((byte) layers);
                stream.putByte((byte) this.y);
            } else {
                stream.putByte((byte) 8);
                stream.putByte((byte) layers);
            }

            for (int i = 0; i < layers; i++) {
                if (!this.hasLayerUnsafe(i)) {
                    StateBlockStorage.ofBlock(BitArrayVersion.V1, GlobalBlockPalette.getOrCreateRuntimeId(protocol, Block.AIR, 0))
                            .writeTo(stream);
                    continue;
                }

                this.storages[i].writeTo(stream);
            }
        } finally {
            this.readLock.unlock();
        }
    }

    /*@Override
    public boolean writeToCache(BinaryStream stream) {
        return writeToCache(stream, GlobalBlockPalette::getNameByBlockId);
    }

    @Override
    public boolean writeToCache(BinaryStream stream, IntFunction<String> blockIdToName) {
        boolean hasBlock = false;
        stream.putByte((byte) 8);

        try {
            this.readLock.lock();

            int layers = Math.max(2, this.storages.length);
            stream.putByte((byte) layers);

            for (int i = 0; i < layers; i++) {
                if (!this.hasLayerUnsafe(i)) {
                    EMPTY_STORAGE.writeToCache(stream);
                    continue;
                }

                StateBlockStorage storage = this.storages[i];
                storage.writeToCache(stream, blockIdToName);

                if (!hasBlock) {
                    hasBlock = !storage.isEmpty(true);
                }
            }
        } finally {
            this.readLock.unlock();
        }

        return !hasBlock;
    }

    @Override
    public void writeToDisk(BinaryStream stream) {
//        stream.putByte((byte) 9);
        stream.putByte((byte) 8);

        try {
            this.readLock.lock();

            stream.putByte((byte) this.storages.length);
//            stream.putByte((byte) this.y);

            for (StateBlockStorage storage : this.storages) {
                if (storage != null) {
                    storage.writeToDisk(stream);
                    continue;
                }
                EMPTY_STORAGE.writeToDisk(stream);
            }
        } finally {
            this.readLock.unlock();
        }
    }*/

    public StateBlockStorage[] getStorages() {
        return storages;
    }

    @Override
    public boolean compress() {
        try {
            this.writeLock.lock();

            if (this.isEmpty()) {
                return false;
            }

            boolean dirty = false;
            boolean checkRemove = true;
            for (int i = this.storages.length - 1; i >= 0; i--) {
                StateBlockStorage storage = this.storages[i];
                if (storage == null) {
                    continue;
                }
                dirty |= storage.compress();

                if (checkRemove) {
                    if (storage.isEmpty() && i > 0) {
                        this.storages = Arrays.copyOfRange(this.storages, 0, i);
                    } else {
                        checkRemove = false;
                    }
                }
            }
            this.dirty |= dirty;
            return dirty;
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    public ChunkSection copy() {
        try {
            this.readLock.lock();

            int count = this.storages.length;
            StateBlockStorage[] storages = new StateBlockStorage[count];
            for (int i = 0; i < count; i++) {
                StateBlockStorage storage = this.storages[i];
                if (storage == null) {
                    continue;
                }
                storages[i] = storage.copy();
            }
            return new LevelDBChunkSection(null, this.y, storages);
        } finally {
            this.readLock.unlock();
        }
    }
}
