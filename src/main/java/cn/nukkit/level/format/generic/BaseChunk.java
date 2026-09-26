package cn.nukkit.level.format.generic;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.utils.ChunkException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author MagicDroidX
 * Nukkit Project
 */

public abstract class BaseChunk extends BaseFullChunk implements Chunk {

    public static final int CONTENT_VERSION = 1;

    protected ChunkSection[] sections;

    private static final byte[] emptyIdArray = new byte[4096];
    private static final byte[] emptyDataArray = new byte[2048];

    @Override
    public BaseChunk clone() {
        BaseChunk chunk = (BaseChunk) super.clone();
        if (this.biomes != null) chunk.biomes = this.biomes.clone();
        chunk.heightMap = this.getHeightMapArray().clone();
        if (sections != null && sections[0] != null) {
            chunk.sections = new ChunkSection[sections.length];
            for (int i = 0; i < sections.length; i++) {
                chunk.sections[i] = sections[i].copy();
            }
        }
        return chunk;
    }

    @Override
    public BaseChunk cloneForChunkSending() {
        BaseChunk chunk = (BaseChunk) super.cloneForChunkSending();
        if (sections != null && sections[0] != null) {
            chunk.sections = new ChunkSection[sections.length];
            for (int i = 0; i < sections.length; i++) {
                chunk.sections[i] = sections[i].copy();
            }
        }
        return chunk;
    }

    /**
     * 写入共享空 section 时物化真实 section 的工厂；默认走 provider 反射，子类可直接覆写避免反射。
     * <p>
     * Factory that materializes a real section when writing into a shared empty
     * section; defaults to provider reflection, subclasses may override directly.
     */
    protected ChunkSection materializeSection(int sectionY) {
        try {
            return (ChunkSection) this.providerClass.getMethod("createChunkSection", int.class).invoke(this.providerClass, sectionY);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            Server.getInstance().getLogger().logException(e);
            return null;
        }
    }

    private void removeInvalidTile(int x, int y, int z) {
        BlockEntity entity = getTile(x, y, z);
        if (entity != null && !entity.isBlockEntityValid()) {
            removeBlockEntity(entity);
        }
        // A retained unknown/modded tile NBT must also be dropped once the block here can no
        // longer host a block entity, otherwise it would be re-written (resurrected) on save.
        removeInvalidUnknownTile(x, y, z);
    }

    @Override
    public int getFullBlock(int x, int y, int z) {
        return getFullBlock(x, y, z, 0);
    }

    @Override
    public int getFullBlock(int x, int y, int z, int layer) {
        return this.getSection(y >> 4).getFullBlock(x, y & 0x0f, z, layer);
    }

    @Override
    public int[] getBlockState(int x, int y, int z, int layer) {
        return this.getSection(y >> 4).getBlockState(x, y & 0x0f, z, layer);
    }

    @Override
    public boolean setBlockAtLayer(int x, int y, int z, int layer, int blockId) {
        return this.setBlockAtLayer(x, y, z, layer, blockId, 0);
    }

    @Override
    public boolean setBlock(int x, int y, int z, int blockId) {
        return this.setBlock(x, y, z, blockId, 0);
    }

    @Override
    public Block getAndSetBlock(int x, int y, int z, Block block) {
        return getAndSetBlock(x, y, z, 0, block);
    }

    @Override
    public Block getAndSetBlock(int x, int y, int z, int layer, Block block) {
        int Y = y >> 4;
        try {
            setChanged();
            return this.getSection(Y).getAndSetBlock(x, y & 0x0f, z, layer, block);
        } catch (ChunkException e) {
            ChunkSection materialized = this.materializeSection(Y);
            if (materialized != null) {
                this.setInternalSection(Y, materialized);
            }
            return this.getSection(Y).getAndSetBlock(x, y & 0x0f, z, layer, block);
        } finally {
            removeInvalidTile(x, y, z);
        }
    }

    @Override
    public boolean setFullBlockId(int x, int y, int z, int fullId) {
        return this.setFullBlockId(x, y, z, 0, fullId);
    }

    @Override
    public boolean setFullBlockId(int x, int y, int z, int layer, int fullId) {
        int Y = y >> 4;
        try {
            setChanged();
            return this.getSection(Y).setFullBlockId(x, y & 0x0f, z, layer, fullId);
        } catch (ChunkException e) {
            ChunkSection materialized = this.materializeSection(Y);
            if (materialized != null) {
                this.setInternalSection(Y, materialized);
            }
            return this.getSection(Y).setFullBlockId(x, y & 0x0f, z, layer, fullId);
        } finally {
            removeInvalidTile(x, y, z);
        }
    }

    @Override
    public boolean setBlock(int x, int y, int z, int blockId, int meta) {
        return this.setBlockAtLayer(x, y, z, 0, blockId, meta);
    }

    @Override
    public boolean setBlockAtLayer(int x, int y, int z, int layer, int blockId, int meta) {
        int Y = y >> 4;
        try {
            setChanged();
            return this.getSection(Y).setBlockAtLayer(x, y & 0x0f, z, layer, blockId, meta);
        } catch (ChunkException e) {
            ChunkSection materialized = this.materializeSection(Y);
            if (materialized != null) {
                this.setInternalSection(Y, materialized);
            }
            return this.getSection(Y).setBlockAtLayer(x, y & 0x0f, z, layer, blockId, meta);
        } finally {
            removeInvalidTile(x, y, z);
        }
    }

    @Override
    public void setBlockId(int x, int y, int z, int id) {
        setBlockId(x, y, z, 0, id);
    }

    @Override
    public void setBlockId(int x, int y, int z, int layer, int id) {
        int Y = y >> 4;
        try {
            this.getSection(Y).setBlockId(x, y & 0x0f, z, layer, id);
            setChanged();
        } catch (ChunkException e) {
            ChunkSection materialized = this.materializeSection(Y);
            if (materialized != null) {
                this.setInternalSection(Y, materialized);
            }
            this.getSection(Y).setBlockId(x, y & 0x0f, z, layer, id);
        } finally {
            removeInvalidTile(x, y, z);
        }
    }

    @Override
    public int getBlockId(int x, int y, int z) {
        return getBlockId(x, y, z, 0);
    }

    @Override
    public int getBlockId(int x, int y, int z, int layer) {
        return this.getSection(y >> 4).getBlockId(x, y & 0x0f, z, layer);
    }

    @Override
    public int getBlockData(int x, int y, int z) {
        return getBlockData(x, y, z, 0);
    }

    @Override
    public int getBlockData(int x, int y, int z, int layer) {
        return this.getSection(y >> 4).getBlockData(x, y & 0x0f, z, layer);
    }

    @Override
    public void setBlockData(int x, int y, int z, int data) {
        setBlockData(x, y, z, 0, data);
    }

    @Override
    public void setBlockData(int x, int y, int z, int layer, int data) {
        int Y = y >> 4;
        try {
            this.getSection(Y).setBlockData(x, y & 0x0f, z, layer, data);
            setChanged();
        } catch (ChunkException e) {
            ChunkSection materialized = this.materializeSection(Y);
            if (materialized != null) {
                this.setInternalSection(Y, materialized);
            }
            this.getSection(Y).setBlockData(x, y & 0x0f, z, layer, data);
        } finally {
            removeInvalidTile(x, y, z);
        }
    }

    @Override
    public int getBlockSkyLight(int x, int y, int z) {
        return this.getSection(y >> 4).getBlockSkyLight(x, y & 0x0f, z);
    }

    @Override
    public void setBlockSkyLight(int x, int y, int z, int level) {
        int Y = y >> 4;
        try {
            this.getSection(Y).setBlockSkyLight(x, y & 0x0f, z, level);
            setChanged();
        } catch (ChunkException e) {
            ChunkSection materialized = this.materializeSection(Y);
            if (materialized != null) {
                this.setInternalSection(Y, materialized);
            }
            this.getSection(Y).setBlockSkyLight(x, y & 0x0f, z, level);
        }
    }

    @Override
    public int getBlockLight(int x, int y, int z) {
        return this.getSection(y >> 4).getBlockLight(x, y & 0x0f, z);
    }

    @Override
    public void setBlockLight(int x, int y, int z, int level) {
        int Y = y >> 4;
        try {
            this.getSection(Y).setBlockLight(x, y & 0x0f, z, level);
            setChanged();
        } catch (ChunkException e) {
            ChunkSection materialized = this.materializeSection(Y);
            if (materialized != null) {
                this.setInternalSection(Y, materialized);
            }
            this.getSection(Y).setBlockLight(x, y & 0x0f, z, level);
        }
    }

    @Override
    public void populateBlockLight() {
        int minY = this.getProvider().getMinBlockY();
        int maxY = this.getProvider().getMaxBlockY();
        int sectionOffset = this.getSectionOffset();
        IntArrayFIFOQueue pending = new IntArrayFIFOQueue();
        for (int i = 0; i < this.sections.length; i++) {
            ChunkSection section = this.sections[i];
            if (section == null || section instanceof EmptyChunkSection) continue;
            int baseY = (i - sectionOffset) << 4;
            if (section.maybeHasLightSource()) {
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 16; y++) {
                    if (baseY + y < minY || baseY + y > maxY) continue;
                    int light = Block.getBlockLight(section.getBlockId(x, y, z));
                    if (light > 0) section.setBlockLight(x, y, z, light);
                }
            }
            // A dark section has no wavefront. Keep saved cross-chunk light as seeds:
            // absence of a local emitter does not mean that the section is dark.
            byte[] lights = section.getLightArray();
            boolean lit = false;
            for (byte value : lights) {
                if (value != 0) { lit = true; break; }
            }
            if (!lit) continue;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 16; y++) {
                int worldY = baseY + y;
                if (worldY >= minY && worldY <= maxY && section.getBlockLight(x, y, z) > 1) {
                    pending.enqueue(((worldY - minY) << 8) | (z << 4) | x);
                }
            }
        }
        propagateBlockLight(pending, minY, maxY);
    }

    @Override
    public boolean isSectionEmpty(float fY) {
        return this.sections[this.getSectionOffset() + (int) fY] instanceof EmptyChunkSection;
    }

    @Override
    public ChunkSection getSection(float fY) {
        int y = (int) (this.getSectionOffset() + fY);
        if (y >= this.sections.length) {
            this.getProvider().getLevel().getServer().getLogger().logException(new ChunkException("Invalid section " + y + " in chunk " + this.getX() + ", " + this.getZ()));
            y = this.sections.length - 1;
        }
        return this.sections[y];
    }

    @Override
    public boolean setSection(float fY, ChunkSection section) {
        if (Arrays.equals(emptyIdArray, section.getIdArray()) && Arrays.equals(emptyDataArray, section.getDataArray())) {
            this.sections[this.getSectionOffset() + (int) fY] = EmptyChunkSection.bySectionY((int) fY);
        } else {
            this.sections[this.getSectionOffset() + (int) fY] = section;
        }
        setChanged();
        return true;
    }

    protected void setInternalSection(float fY, ChunkSection section) {
        this.sections[this.getSectionOffset() + (int) fY] = section;
        setChanged();
    }

    @Override
    public boolean load() throws IOException {
        return this.load(true);
    }

    @Override
    public boolean load(boolean generate) throws IOException {
        return this.getProvider() != null && this.getProvider().getChunk(this.getX(), this.getZ(), true) != null;
    }

    @Override
    public byte[] getBlockIdArray(int layer) {
        ByteBuffer buffer = ByteBuffer.allocate(65536);
        for (int y = 0; y < SECTION_COUNT; y++) {
            buffer.put(this.getSection(y).getIdArray(layer));
        }
        return buffer.array();
    }

    @Override
    public byte[] getBlockDataArray(int layer) {
        ByteBuffer buffer = ByteBuffer.allocate(32768);
        for (int y = 0; y < SECTION_COUNT; y++) {
            buffer.put(this.getSection(y).getDataArray(layer));
        }
        return buffer.array();
    }

    @Override
    public byte[] getBlockSkyLightArray() {
        ByteBuffer buffer = ByteBuffer.allocate(32768);
        for (int y = 0; y < SECTION_COUNT; y++) {
            buffer.put(this.getSection(y).getSkyLightArray());
        }
        return buffer.array();
    }

    @Override
    public byte[] getBlockLightArray() {
        ByteBuffer buffer = ByteBuffer.allocate(32768);
        for (int y = 0; y < SECTION_COUNT; y++) {
            buffer.put(this.getSection(y).getLightArray());
        }
        return buffer.array();
    }

    @Override
    public ChunkSection[] getSections() {
        return sections;
    }

    @Override
    public short[] getHeightMapArray() {
        return this.heightMap;
    }

    @Override
    public LevelProvider getProvider() {
        return this.provider;
    }

    /*private boolean walk(ChunkSection section, Updater updater) {
        int offsetX = getX() << 4;
        int offsetZ = getZ() << 4;
        int offsetY = section.getY() << 4;
        boolean updated = false;
        for (int x = 0; x <= 0xF; x++) {
            for (int z = 0; z <= 0xF; z++) {
                for (int y = 0; y <= 0xF; y++) {
                    int[] state = section.getBlockState(x, y, z, 0);
                    updated |= updater.update(offsetX, offsetY, offsetZ, x, y, z, state[0], state[1]);
                }
            }
        }
        return updated;
    }


    @FunctionalInterface
    private interface Updater {
        boolean update(int offsetX, int offsetY, int offsetZ, int x, int y, int z, int blockId, int meta);
    }

    @RequiredArgsConstructor
    private class WallUpdater implements Updater {
        private final Level level;
        private final ChunkSection section;

        @Override
        public boolean update(int offsetX, int offsetY, int offsetZ, int x, int y, int z, int blockId, int meta) {
            if (blockId != BlockID.COBBLE_WALL) {
                return false;
            }

            //BlockWall blockWall = (BlockWall) Block.get(blockId, meta, level, offsetX + x, offsetY + y, offsetZ + z, 0); //layer check
            BlockWall blockWall = (BlockWall) Block.get(blockId, meta, level, offsetX + x, offsetY + y, offsetZ + z);
            if (blockWall.autoConfigureState()) {
                section.setBlockData(x, y, z, 0, blockWall.getDamage());
                return true;
            }

            return false;
        }
    }

    @RequiredArgsConstructor
    private class StemUpdater implements Updater {
        private final Level level;
        private final ChunkSection section;
        private final int stemId;
        private final int productId;

        @Override
        public boolean update(int offsetX, int offsetY, int offsetZ, int x, int y, int z, int blockId, int meta) {
            if (blockId != stemId) {
                return false;
            }

            for (BlockFace blockFace : BlockFace.Plane.HORIZONTAL) {
                int sideId = level.getBlockIdAt(
                        offsetX + x + blockFace.getXOffset(),
                        offsetY + y,
                        offsetZ + z + blockFace.getZOffset()
                );
                if (sideId == productId) {
                    Block blockStem = Block.get(blockId, meta, level, offsetX + x, offsetY + y, offsetZ + z, 0);
                    ((Faceable) blockStem).setBlockFace(blockFace);
                    section.setBlockData(x, y, z, 0, blockStem.getDamage());
                    return true;
                }
            }

            return false;
        }
    }

    private class GroupedUpdaters implements Updater {
        private final Updater[] updaters;

        public GroupedUpdaters(Updater... updaters) {
            this.updaters = updaters;
        }

        @Override
        public boolean update(int offsetX, int offsetY, int offsetZ, int x, int y, int z, int blockId, int meta) {
            for (Updater updater : updaters) {
                if (updater.update(offsetX, offsetY, offsetZ, x, y, z, blockId, meta)) {
                    return true;
                }
            }
            return false;
        }
    }*/
}
