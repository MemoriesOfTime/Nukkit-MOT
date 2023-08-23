package cn.nukkit.level.generator.structure;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.task.BlockEntitySyncTask;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;

import java.util.Map;
import java.util.Map.Entry;

public class StructureBuilder {
    private final ChunkManager level;

    private final ScatteredStructurePiece structure;

    public StructureBuilder(final ChunkManager level, final ScatteredStructurePiece structure) {
        this.level = level;
        this.structure = structure;
    }

    public void addRandomBlock(final Map<Integer, Integer> blocks, final int weight, final int id) {
        addRandomBlock(blocks, weight, id, 0);
    }

    public void addRandomBlock(final Map<Integer, Integer> blocks, final int weight, final int id, final int meta) {
        blocks.put(id << 4 | meta, weight);
    }

    public int getRandomBlock(final NukkitRandom random, final Map<Integer, Integer> blocks) {
        int totalWeight = 0;
        for (final int weight : blocks.values()) {
            totalWeight += weight;
        }
        int weight = random.nextBoundedInt(totalWeight);
        for (final Entry<Integer, Integer> entry : blocks.entrySet()) {
            weight -= entry.getValue();
            if (weight < 0) {
                return entry.getKey();
            }
        }
        return BlockID.AIR;
    }

    public void setTile(final BlockVector3 pos, final String id) {
        setTile(pos, id, null);
    }

    public void setTile(final BlockVector3 pos, final String id, final CompoundTag data) {
        final BlockVector3 vec = translate(pos);
        final BaseFullChunk chunk = level.getChunk(vec.x >> 4, vec.z >> 4);
        final CompoundTag nbt = BlockEntity.getDefaultCompound(new Vector3(vec.x, vec.y, vec.z), id);
        if (data != null) {
            data.getTags().forEach(nbt::put);
        }
        Server.getInstance().getScheduler().scheduleTask(new BlockEntitySyncTask(id, chunk, nbt));
    }

    public void setBlock(final BlockVector3 pos, final int id) {
        setBlock(pos, id, 0);
    }

    public void setBlock(final BlockVector3 pos, final int id, final int meta) {
        final BlockVector3 vec = translate(pos);
        level.setBlockAt(vec.x, vec.y, vec.z, id, meta);
    }

    public void setBlockDownward(final BlockVector3 pos, final int id) {
        setBlockDownward(pos, id, 0);
    }

    public void setBlockDownward(final BlockVector3 pos, final int id, final int meta) {
        final BlockVector3 vec = translate(pos);
        int y = vec.y;
        while (!Block.solid[level.getBlockIdAt(vec.x, y, vec.z)] && y > 1) {
            level.setBlockAt(vec.x, y, vec.z, id, meta);
            y--;
        }
    }

    public void setBlockWithRandomBlock(final BlockVector3 pos, final NukkitRandom random, final Map<Integer, Integer> blocks) {
        final int fullId = getRandomBlock(random, blocks);
        setBlock(pos, fullId >> 4, fullId & 0xf);
    }

    public void fill(final BlockVector3 min, final BlockVector3 max, final int id) {
        fill(min, max, id, 0);
    }

    public void fill(final BlockVector3 min, final BlockVector3 max, final int id, final int meta) {
        fill(min, max, id, meta, id, meta);
    }

    public void fill(final BlockVector3 min, final BlockVector3 max, final int outerId, final int outerMeta, final int innerId, final int innerMeta) {
        for (int y = min.y; y <= max.y; y++) {
            for (int x = min.x; x <= max.x; x++) {
                for (int z = min.z; z <= max.z; z++) {
                    final int id;
                    final int meta;
                    if (x != min.x && x != max.x && z != min.z && z != max.z && y != min.y && y != max.y) {
                        id = innerId;
                        meta = innerMeta;
                    } else {
                        id = outerId;
                        meta = outerMeta;
                    }
                    setBlock(new BlockVector3(x, y, z), id, meta);
                }
            }
        }
    }

    public void fillWithRandomBlock(final BlockVector3 min, final BlockVector3 max, final NukkitRandom random, final Map<Integer, Integer> blocks) {
        for (int y = min.y; y <= max.y; y++) {
            for (int x = min.x; x <= max.x; x++) {
                for (int z = min.z; z <= max.z; z++) {
                    final int fullId = getRandomBlock(random, blocks);
                    setBlock(new BlockVector3(x, y, z), fullId >> 4, fullId & 0xf);
                }
            }
        }
    }

    private BlockVector3 translate(final BlockVector3 pos) {
        return structure.getBoundingBox().getMin().add(pos);
    }
}
