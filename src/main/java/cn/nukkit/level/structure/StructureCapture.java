package cn.nukkit.level.structure;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityStructureBlock;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从世界区域抓取结构。玩家永远不会被写入结构（服务端无法序列化玩家实体）。
 * <p>
 * Captures a structure from a live level region. Players are never captured —
 * the server cannot serialize player entities.
 */
@Log4j2
public final class StructureCapture {

    private StructureCapture() {
    }

    public static Structure capture(Level level, StructureBox box, boolean includeEntities) {
        if (box.sizeX > StructureIO.MAX_SIZE_X || box.sizeY > StructureIO.MAX_SIZE_Y || box.sizeZ > StructureIO.MAX_SIZE_Z) {
            throw new IllegalArgumentException("Structure exceeds the maximum size");
        }
        return new Capture(level, box, includeEntities).run();
    }

    private static final class Capture {

        private final Level level;
        private final StructureBox box;
        private final boolean includeEntities;
        private final PaletteBuilder palette = new PaletteBuilder(StructureIO.statePalette());
        private final int[] layer0;
        private final int[] layer1;
        private final int minY;
        private final int maxY;
        private final Map<Integer, CompoundTag> blockEntities = new HashMap<>();

        private Capture(Level level, StructureBox box, boolean includeEntities) {
            this.level = level;
            this.box = box;
            this.includeEntities = includeEntities;
            int volume = Structure.volume(box.sizeX, box.sizeY, box.sizeZ);
            this.layer0 = new int[volume];
            this.layer1 = new int[volume];
            this.minY = level.getMinBlockY();
            this.maxY = level.getMaxBlockY();
        }

        private Structure run() {
            this.captureBlocks();
            this.captureBlockEntities();
            List<CompoundTag> entities = this.includeEntities ? this.captureEntities() : List.of();
            return new Structure(this.box.sizeX, this.box.sizeY, this.box.sizeZ,
                    this.palette.getTags(), this.palette.getLegacyIds().toIntArray(),
                    this.layer0, this.layer1, this.blockEntities, entities, this.box.getOrigin());
        }

        private void captureBlocks() {
            for (int x = 0; x < this.box.sizeX; x++) {
                for (int z = 0; z < this.box.sizeZ; z++) {
                    FullChunk chunk = this.level.getChunk((this.box.x + x) >> 4, (this.box.z + z) >> 4);
                    this.captureColumn(chunk, x, z);
                }
            }
        }

        private void captureColumn(FullChunk chunk, int x, int z) {
            int worldX = this.box.x + x;
            int worldZ = this.box.z + z;
            for (int y = 0; y < this.box.sizeY; y++) {
                int index = this.box.index(x, y, z);
                int worldY = this.box.y + y;
                if (worldY < this.minY || worldY > this.maxY) {
                    this.layer0[index] = this.palette.airIndex();
                    this.layer1[index] = Structure.VOID;
                    continue;
                }
                int localX = worldX & 0x0f;
                int localZ = worldZ & 0x0f;
                int firstId = chunk.getBlockId(localX, worldY, localZ, 0);
                int firstMeta = chunk.getBlockData(localX, worldY, localZ, 0);
                if (firstId == BlockID.STRUCTURE_BLOCK) {
                    // 结构方块的 meta 是模式(0-5)，换成空气时必须清零，否则查不到 air 状态
                    // A structure block's meta is its mode (0-5); clear it with the air swap or no state matches.
                    firstId = BlockID.AIR;
                    firstMeta = 0;
                }
                this.layer0[index] = firstId == BlockID.STRUCTURE_VOID ? Structure.VOID
                        : this.palette.index(firstId, firstMeta);
                int secondId = chunk.getBlockId(localX, worldY, localZ, 1);
                this.layer1[index] = secondId == BlockID.STRUCTURE_BLOCK || secondId == BlockID.AIR ? Structure.VOID
                        : this.palette.index(secondId, chunk.getBlockData(localX, worldY, localZ, 1));
            }
        }

        private void captureBlockEntities() {
            int minChunkX = this.box.x >> 4;
            int maxChunkX = (this.box.x + this.box.sizeX - 1) >> 4;
            int minChunkZ = this.box.z >> 4;
            int maxChunkZ = (this.box.z + this.box.sizeZ - 1) >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    for (BlockEntity blockEntity : this.level.getChunkBlockEntities(chunkX, chunkZ).values()) {
                        this.captureBlockEntity(blockEntity);
                    }
                }
            }
        }

        private void captureBlockEntity(BlockEntity blockEntity) {
            int x = blockEntity.getFloorX();
            int y = blockEntity.getFloorY();
            int z = blockEntity.getFloorZ();
            if (!this.box.contains(x, y, z) || blockEntity instanceof BlockEntityStructureBlock) {
                return;
            }
            if (!blockEntity.isBlockEntityValid()) {
                return;
            }
            blockEntity.saveNBT();
            int index = this.box.index(x - this.box.x, y - this.box.y, z - this.box.z);
            this.blockEntities.put(index, new CompoundTag()
                    .putCompound("block_entity_data", blockEntity.namedTag.copy()));
        }

        private List<CompoundTag> captureEntities() {
            List<CompoundTag> result = new ArrayList<>();
            for (Entity entity : this.level.getEntities()) {
                CompoundTag tag = this.captureEntity(entity);
                if (tag != null) {
                    result.add(tag);
                }
            }
            return result;
        }

        private CompoundTag captureEntity(Entity entity) {
            if (entity instanceof Player || entity.isClosed()) {
                return null;
            }
            if (!this.box.contains(entity.getFloorX(), entity.getFloorY(), entity.getFloorZ())) {
                return null;
            }
            entity.saveNBT();
            CompoundTag tag = entity.namedTag.copy();
            if (entity.getIdentifier() != null) {
                tag.putString("identifier", entity.getIdentifier().toString());
            }
            return tag;
        }
    }

    private static final class PaletteBuilder {

        private final BlockPalette palette;
        private final List<CompoundTag> tags = new ArrayList<>();
        private final IntArrayList legacyIds = new IntArrayList();
        private final Map<Integer, Integer> indexByLegacyId = new HashMap<>();

        private PaletteBuilder(BlockPalette palette) {
            this.palette = palette;
        }

        private int airIndex() {
            return this.index(BlockID.AIR, 0);
        }

        private int index(int id, int meta) {
            int legacyId = id << Block.DATA_BITS | meta;
            Integer cached = this.indexByLegacyId.get(legacyId);
            if (cached != null) {
                return cached;
            }
            CompoundTag tag = this.palette.getBlockStateTag(legacyId);
            if (tag == null) {
                log.warn("Structure save: no palette state for block {}:{}, storing minecraft:info_update",
                        id, meta);
                legacyId = BlockID.INFO_UPDATE << Block.DATA_BITS;
                tag = this.palette.getBlockStateTag(legacyId);
            }
            if (tag == null) {
                tag = new CompoundTag()
                        .putString("name", "minecraft:info_update")
                        .putCompound("states", new CompoundTag())
                        .putInt("version", 0);
            }
            int index = this.tags.size();
            this.tags.add(tag);
            this.legacyIds.add(legacyId);
            this.indexByLegacyId.put(id << Block.DATA_BITS | meta, index);
            return index;
        }

        private List<CompoundTag> getTags() {
            return this.tags;
        }

        private IntArrayList getLegacyIds() {
            return this.legacyIds;
        }
    }
}
