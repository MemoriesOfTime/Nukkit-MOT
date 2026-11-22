package cn.nukkit.level.structure;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.scheduler.TaskHandler;

import java.util.List;

/**
 * 将结构写入世界（旋转、镜像、完整度、动画、方块实体与实体）。
 * <p>
 * Places a structure into the world, applying rotation, mirroring, integrity, vanilla
 * Layer/Blocks animation and restoring block entities and entities. Block states are
 * rotated/mirrored like vanilla (mirror first, then a clockwise rotation).
 */
public final class StructurePlacement {

    private StructurePlacement() {
    }

    public static void place(Structure structure, Level level, StructurePlacementOptions options) {
        Context context = new Context(structure, level, options);
        int durationTicks = Math.round(options.animationSeconds * 20f);
        if (options.ignoringBlocks || options.animationMode == 0 || durationTicks <= 0) {
            context.place();
            return;
        }
        new Animation(context, durationTicks, options.animationMode == 1).start();
    }

    /**
     * 动画加载：按 tick 分批放置方块，结束后再放实体（与原版 Layer/Blocks 模式一致）。
     * <p>
     * Animated loading: places blocks in per-tick batches and restores entities at the end,
     * matching vanilla's Layer/Blocks animation modes.
     */
    private static final class Animation implements Runnable {

        private final Context context;
        private final boolean layerMode;
        private final int limit;
        private final int perTick;
        private final int plane;
        private int cursor;
        private TaskHandler task;

        private Animation(Context context, int durationTicks, boolean layerMode) {
            this.context = context;
            this.layerMode = layerMode;
            this.plane = context.sizeY * context.sizeZ;
            this.limit = layerMode ? context.sizeY : this.plane * context.sizeX;
            this.perTick = Math.max(1, (this.limit + durationTicks - 1) / durationTicks);
        }

        private void start() {
            this.task = Server.getInstance().getScheduler().scheduleRepeatingTask(this, 1);
        }

        @Override
        public void run() {
            int end = Math.min(this.limit, this.cursor + this.perTick);
            for (int i = this.cursor; i < end; i++) {
                if (this.layerMode) {
                    this.context.placeLayer(i);
                } else {
                    this.context.placeIndex(i);
                }
            }
            this.cursor = end;
            if (this.cursor >= this.limit) {
                this.context.placeEntities();
                this.task.cancel();
            }
        }
    }

    private static final class Context {

        private final Structure structure;
        private final Level level;
        private final StructurePlacementOptions options;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int plane;
        private final int[] stateIds;

        private Context(Structure structure, Level level, StructurePlacementOptions options) {
            this.structure = structure;
            this.level = level;
            this.options = options;
            this.sizeX = structure.getSizeX();
            this.sizeY = structure.getSizeY();
            this.sizeZ = structure.getSizeZ();
            this.plane = this.sizeY * this.sizeZ;
            this.stateIds = this.resolveStateIds();
        }

        /**
         * 按当前旋转/镜像预计算每个调色板条目的目标方块 ID，状态变换失败时退回原 ID。
         * <p>
         * Resolves the destination block id of every palette entry for the active
         * rotation/mirror, falling back to the source id when the state cannot be mapped.
         */
        private int[] resolveStateIds() {
            List<CompoundTag> palette = this.structure.getPalette();
            int[] ids = new int[palette.size()];
            for (int i = 0; i < palette.size(); i++) {
                ids[i] = this.structure.getLegacyId(i);
            }
            if (this.options.rotation == 0 && this.options.mirror == 0) {
                return ids;
            }
            BlockPalette blockPalette = StructureIO.statePalette();
            for (int i = 0; i < palette.size(); i++) {
                CompoundTag transformed = StructureStateTransform.transform(palette.get(i),
                        this.options.rotation, this.options.mirror);
                if (transformed == null) {
                    continue;
                }
                int legacyId = blockPalette.getLegacyFullIdWithFallback(transformed);
                if (legacyId >= 0) {
                    ids[i] = legacyId;
                }
            }
            return ids;
        }

        private void place() {
            if (!this.options.ignoringBlocks) {
                this.placeBlocks();
                this.placeBlockEntities();
            }
            this.placeEntities();
        }

        private void placeBlocks() {
            for (int x = 0; x < this.sizeX; x++) {
                for (int y = 0; y < this.sizeY; y++) {
                    for (int z = 0; z < this.sizeZ; z++) {
                        this.placeCell(x, y, z);
                    }
                }
            }
        }

        void placeLayer(int y) {
            for (int x = 0; x < this.sizeX; x++) {
                for (int z = 0; z < this.sizeZ; z++) {
                    this.placeCell(x, y, z);
                    this.placeBlockEntity(this.structure.index(x, y, z));
                }
            }
        }

        void placeIndex(int index) {
            this.placeCell(index / this.plane, index % this.plane / this.sizeZ, index % this.sizeZ);
            this.placeBlockEntity(index);
        }

        void placeCell(int x, int y, int z) {
            int index = this.structure.index(x, y, z);
            if (!selected(this.options.integrity, this.options.integritySeed + index)) {
                return;
            }
            if (this.structure.getBlockIndex(0, index) == Structure.VOID) {
                return;
            }
            int[] dest = this.transform(x, y, z);
            this.placeLayerBlock(index, 1, dest);
            this.placeLayerBlock(index, 0, dest);
        }

        private void placeLayerBlock(int sourceIndex, int layer, int[] dest) {
            int paletteIndex = this.structure.getBlockIndex(layer, sourceIndex);
            if (paletteIndex == Structure.VOID || paletteIndex >= this.stateIds.length) {
                return;
            }
            int legacyId = this.stateIds[paletteIndex];
            int id = legacyId >> Block.DATA_BITS;
            int meta = legacyId & Block.DATA_MASK;
            this.level.setBlock(this.options.position.x + dest[0], this.options.position.y + dest[1],
                    this.options.position.z + dest[2], layer, Block.get(id, meta), false, false);
        }

        private void placeBlockEntities() {
            for (Integer index : this.structure.getBlockEntities().keySet()) {
                this.placeBlockEntity(index);
            }
        }

        void placeBlockEntity(int index) {
            CompoundTag entry = this.structure.getBlockEntities().get(index);
            if (entry == null) {
                return;
            }
            if (!selected(this.options.integrity, this.options.integritySeed + index)) {
                return;
            }
            if (this.structure.getBlockIndex(0, index) == Structure.VOID) {
                return;
            }
            CompoundTag data = entry.getCompound("block_entity_data");
            if (data.isEmpty() || data.getString("id").isEmpty()) {
                return;
            }
            int x = index / this.plane;
            int y = index % this.plane / this.sizeZ;
            int z = index % this.sizeZ;
            int[] dest = this.transform(x, y, z);
            int worldX = this.options.position.x + dest[0];
            int worldY = this.options.position.y + dest[1];
            int worldZ = this.options.position.z + dest[2];

            CompoundTag nbt = data.copy()
                    .putInt("x", worldX)
                    .putInt("y", worldY)
                    .putInt("z", worldZ);
            this.transformPair(nbt, new int[]{x, z}, dest);
            FullChunk chunk = this.level.getChunk(worldX >> 4, worldZ >> 4);
            BlockEntity.createBlockEntity(nbt.getString("id"), chunk, nbt);
        }

        /**
         * 双格方块（箱子）的 pair 坐标跟随旋转/镜像；pair 落在结构外时只做平移。
         * <p>
         * Rotates/mirrors the chest pair coordinates; pairs outside the box are only translated.
         */
        private void transformPair(CompoundTag nbt, int[] source, int[] dest) {
            if (!nbt.contains("pairx") || !nbt.contains("pairz")) {
                return;
            }
            BlockVector3 origin = this.structure.getOrigin();
            int pairX = nbt.getInt("pairx") - origin.x;
            int pairZ = nbt.getInt("pairz") - origin.z;
            if (pairX >= 0 && pairX < this.sizeX && pairZ >= 0 && pairZ < this.sizeZ) {
                int[] pairDest = this.transform(pairX, 0, pairZ);
                nbt.putInt("pairx", this.options.position.x + pairDest[0]);
                nbt.putInt("pairz", this.options.position.z + pairDest[2]);
            } else {
                nbt.putInt("pairx", this.options.position.x + pairX + dest[0] - source[0]);
                nbt.putInt("pairz", this.options.position.z + pairZ + dest[2] - source[1]);
            }
        }

        void placeEntities() {
            if (!this.options.includeEntities) {
                return;
            }
            for (CompoundTag source : this.structure.getEntities()) {
                this.placeEntity(source);
            }
        }

        private void placeEntity(CompoundTag source) {
            String identifier = entityIdentifier(source);
            ListTag<?> posTag = source.get("Pos") instanceof ListTag<?> pos ? pos : null;
            if (identifier.isEmpty() || posTag == null || posTag.size() < 3) {
                return;
            }
            float[] local = this.localPosition(posTag);
            if (local == null) {
                return;
            }
            float[] position = this.transformEntity(local[0], local[1], local[2]);
            double[] world = {
                    this.options.position.x + position[0],
                    this.options.position.y + position[1],
                    this.options.position.z + position[2]
            };
            CompoundTag nbt = this.prepareEntityNbt(source, world);
            FullChunk chunk = this.level.getChunk((int) Math.floor(world[0]) >> 4, (int) Math.floor(world[2]) >> 4);
            Entity entity = Entity.createEntity(identifier, chunk, nbt);
            if (entity != null) {
                entity.spawnToAll();
            }
        }

        private static String entityIdentifier(CompoundTag source) {
            String identifier = source.getString("identifier");
            return identifier.isEmpty() ? source.getString("id") : identifier;
        }

        /**
         * 实体坐标转结构内相对坐标；越界（模板缺少 origin）时返回 {@code null}。
         * <p>
         * Entity position relative to the structure origin; {@code null} when it falls outside
         * the box (a template without a trustworthy origin).
         */
        private float[] localPosition(ListTag<?> posTag) {
            BlockVector3 origin = this.structure.getOrigin();
            float localX = floatValue(posTag, 0) - origin.x;
            float localY = floatValue(posTag, 1) - origin.y;
            float localZ = floatValue(posTag, 2) - origin.z;
            if (!Float.isFinite(localX) || !Float.isFinite(localY) || !Float.isFinite(localZ)) {
                return null;
            }
            if (localX < 0 || localX > this.sizeX || localY < 0 || localY > this.sizeY
                    || localZ < 0 || localZ > this.sizeZ) {
                return null;
            }
            return new float[]{localX, localY, localZ};
        }

        private CompoundTag prepareEntityNbt(CompoundTag source, double[] world) {
            CompoundTag nbt = source.copy();
            nbt.remove("uuid");
            nbt.putList("Pos", new ListTag<DoubleTag>()
                    .add(new DoubleTag(world[0]))
                    .add(new DoubleTag(world[1]))
                    .add(new DoubleTag(world[2])))
                    .putList("Motion", new ListTag<DoubleTag>()
                            .add(new DoubleTag(0))
                            .add(new DoubleTag(0))
                            .add(new DoubleTag(0)));
            if (!(nbt.get("Rotation") instanceof ListTag<?> rotationTag) || rotationTag.size() < 2) {
                nbt.putList("Rotation", new ListTag<FloatTag>()
                        .add(new FloatTag(0))
                        .add(new FloatTag(0)));
            }
            this.adjustRotation(nbt);
            return nbt;
        }

        /**
         * 与 JE 一致：先镜像 yaw，再加顺时针旋转角；pitch 不变。
         * <p>
         * Matches JE: mirror the yaw first, then add the clockwise rotation; pitch is unchanged.
         */
        private void adjustRotation(CompoundTag nbt) {
            if (!(nbt.get("Rotation") instanceof ListTag<?> rotationTag) || rotationTag.size() < 2) {
                return;
            }
            float yaw = floatValue(rotationTag, 0);
            float pitch = floatValue(rotationTag, 1);
            switch (this.options.mirror) {
                case 1 -> yaw = -yaw;
                case 2 -> yaw = 180f - yaw;
                case 3 -> yaw = 180f + yaw;
                default -> {
                }
            }
            yaw = (yaw + this.options.rotation * 90f) % 360f;
            nbt.putList("Rotation", new ListTag<FloatTag>()
                    .add(new FloatTag(yaw))
                    .add(new FloatTag(pitch)));
        }

        /**
         * 源格坐标 → 目标格坐标：先镜像后顺时针旋转（与原版一致）。
         * <p>
         * Source cell to destination cell: mirror first, then clockwise rotation (vanilla order).
         */
        private int[] transform(int x, int y, int z) {
            int mx = (this.options.mirror & 1) != 0 ? this.sizeX - 1 - x : x;
            int mz = (this.options.mirror & 2) != 0 ? this.sizeZ - 1 - z : z;
            return switch (this.options.rotation) {
                case 1 -> new int[]{this.sizeZ - 1 - mz, y, mx};
                case 2 -> new int[]{this.sizeX - 1 - mx, y, this.sizeZ - 1 - mz};
                case 3 -> new int[]{mz, y, this.sizeX - 1 - mx};
                default -> new int[]{mx, y, mz};
            };
        }

        private float[] transformEntity(float x, float y, float z) {
            float mx = (this.options.mirror & 1) != 0 ? this.sizeX - x : x;
            float mz = (this.options.mirror & 2) != 0 ? this.sizeZ - z : z;
            return switch (this.options.rotation) {
                case 1 -> new float[]{this.sizeZ - mz, y, mx};
                case 2 -> new float[]{this.sizeX - mx, y, this.sizeZ - mz};
                case 3 -> new float[]{mz, y, this.sizeX - mx};
                default -> new float[]{mx, y, mz};
            };
        }
    }

    private static float floatValue(ListTag<?> values, int index) {
        Tag tag = values.get(index);
        return tag instanceof NumberTag<?> number ? number.getData().floatValue() : 0f;
    }

    /**
     * 原版 StructureTemplate 的完整度筛选（MT19937 变体）。
     * <p>
     * Vanilla StructureTemplate integrity selection (a MT19937 variant).
     */
    private static boolean selected(float integrity, int seed) {
        if (Float.isNaN(integrity) || integrity <= 0f) {
            return false;
        }
        if (integrity >= 100f) {
            return true;
        }
        int state = seed;
        int first = 0;
        for (int i = 1; i <= 397; i++) {
            state = 1812433253 * (state ^ (state >>> 30)) + i;
            if (i == 1) {
                first = state;
            }
        }
        int y = (seed & 0x80000000) | (first & 0x7fffffff);
        int random = state ^ (y >>> 1) ^ ((y & 1) == 0 ? 0 : 0x9908b0df);
        random ^= random >>> 11;
        random ^= (random << 7) & 0x9d2c5680;
        random ^= (random << 15) & 0xefc60000;
        random ^= random >>> 18;
        float percentage = (float) ((random & 0xffffffffL) * 0x1.0p-32) * 100f;
        return integrity > percentage;
    }
}
