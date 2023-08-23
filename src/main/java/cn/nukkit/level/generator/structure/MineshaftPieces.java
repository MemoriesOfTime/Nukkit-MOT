package cn.nukkit.level.generator.structure;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockFence;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockPlanks;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityCaveSpider;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.block.state.BlockState;
import cn.nukkit.level.generator.block.state.RailDirection;
import cn.nukkit.level.generator.block.state.TorchFacingDirection;
import cn.nukkit.level.generator.loot.MineshaftChest;
import cn.nukkit.level.generator.math.BoundingBox;
import cn.nukkit.level.generator.populator.overworld.PopulatorMineshaft;
import cn.nukkit.level.generator.task.ActorSpawnTask;
import cn.nukkit.level.generator.task.BlockActorSpawnTask;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import com.google.common.collect.Lists;

import java.util.List;

public final class MineshaftPieces {
    private static final BlockState OAK_PLANKS = new BlockState(Block.PLANKS, BlockPlanks.OAK);
    private static final BlockState DARK_OAK_PLANKS = new BlockState(Block.PLANKS, BlockPlanks.DARK_OAK);
    private static final BlockState OAK_FENCE = new BlockState(Block.FENCE, BlockFence.FENCE_OAK);
    private static final BlockState DARK_OAK_FENCE = new BlockState(Block.FENCE, BlockFence.FENCE_DARK_OAK);
    private static final BlockState COBWEB = new BlockState(Block.COBWEB);
    private static final BlockState DIRT = new BlockState(Block.DIRT);
    private static final BlockState SPAWNER = new BlockState(Block.MONSTER_SPAWNER);
    private static final BlockState TORCH__N = new BlockState(Block.TORCH, TorchFacingDirection.NORTH);
    private static final BlockState TORCH__S = new BlockState(Block.TORCH, TorchFacingDirection.SOUTH);
    private static final BlockState RAIL__NS = new BlockState(Block.RAIL, RailDirection.NORTH_SOUTH);
    private static final BlockState RAIL__EW = new BlockState(Block.RAIL, RailDirection.EAST_WEST);

    //\\ MineshaftPiece::createRandomShaftPiece(MineshaftData &,std::vector<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>,std::allocator<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>>> &,Random &,int,int,int,int,int)
    private static MineshaftPiece createRandomShaftPiece(final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth, final PopulatorMineshaft.Type type) {
        final int chance = random.nextBoundedInt(100);
        if (chance >= 80) {
            final BoundingBox boundingBox = MineshaftCrossing.findCrossing(pieces, random, x, y, z, orientation);
            if (boundingBox != null) {
                return new MineshaftCrossing(genDepth, boundingBox, orientation, type);
            }
        } else if (chance >= 70) {
            final BoundingBox boundingBox = MineshaftStairs.findStairs(pieces, random, x, y, z, orientation);
            if (boundingBox != null) {
                return new MineshaftStairs(genDepth, boundingBox, orientation, type);
            }
        } else {
            final BoundingBox boundingBox = MineshaftCorridor.findCorridorSize(pieces, random, x, y, z, orientation);
            if (boundingBox != null) {
                return new MineshaftCorridor(genDepth, random, boundingBox, orientation, type);
            }
        }
        return null;
    }

    //\\ MineshaftPiece::generateAndAddPiece(StructurePiece *,std::vector<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>,std::allocator<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>>> &,Random &,int,int,int,int,int)
    private static MineshaftPiece generateAndAddPiece(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
        if (genDepth <= 8 && Math.abs(x - piece.getBoundingBox().x0) <= 80 && Math.abs(z - piece.getBoundingBox().z0) <= 80) {
            final MineshaftPiece result = createRandomShaftPiece(pieces, random, x, y, z, orientation, genDepth + 1, ((MineshaftPiece) piece).type);
            if (result != null) {
                pieces.add(result);
                result.addChildren(piece, pieces, random);
            }
            return result;
        }
        return null;
    }

    abstract static class MineshaftPiece extends StructurePiece {
        protected PopulatorMineshaft.Type type;

        public MineshaftPiece(final int genDepth, final PopulatorMineshaft.Type type) {
            super(genDepth);
            this.type = type;
        }

        protected BlockState getPlanksBlock() {
            return type == PopulatorMineshaft.Type.MESA ? DARK_OAK_PLANKS : OAK_PLANKS;
        }

        protected BlockState getFenceBlock() {
            return type == PopulatorMineshaft.Type.MESA ? DARK_OAK_FENCE : OAK_FENCE;
        }

        //\\ MineshaftPiece::_isSupportingBox(int,int,BlockSource *,int,int)
        protected boolean isSupportingBox(final ChunkManager level, final BoundingBox boundingBox, final int x0, final int x1, final int y, final int z) {
            for (int x = x0; x <= x1; ++x) {
                if (getBlock(level, x, y + 1, z, boundingBox).getId() == BlockID.AIR) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class MineshaftRoom extends MineshaftPiece {
        private final List<BoundingBox> childEntranceBoxes = Lists.newLinkedList();

        //\\ MineshaftStart::MineshaftStart(BiomeSource &,Random &,ChunkPos const &,short)
        public MineshaftRoom(final int genDepth, final NukkitRandom random, final int x, final int z, final PopulatorMineshaft.Type type) {
            super(genDepth, type);
            this.type = type;
            boundingBox = new BoundingBox(x, 40, z, x + 7 + random.nextBoundedInt(6), 44 + random.nextBoundedInt(6), z + 7 + random.nextBoundedInt(6));
        }

        @Override //\\ MineshaftRoom::getType() // 1297306189i64;
        public String getType() {
            return "MSRoom";
        }

        @Override
        //\\ MineshaftRoom::addChildren(StructurePiece *,std::vector<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>,std::allocator<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>>> &,Random &)
        public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
            final int genDepth = getGenDepth();
            int yOffset = boundingBox.getYSpan() - 3 - 1;
            if (yOffset <= 0) {
                yOffset = 1;
            }

            for (int x = 0; x < boundingBox.getXSpan(); x += 4) {
                x += random.nextBoundedInt(boundingBox.getXSpan());
                if (x + 3 > boundingBox.getXSpan()) {
                    break;
                }

                final MineshaftPiece next = generateAndAddPiece(piece, pieces, random, boundingBox.x0 + x, boundingBox.y0 + random.nextBoundedInt(yOffset) + 1, boundingBox.z0 - 1, BlockFace.NORTH, genDepth);
                if (next != null) {
                    final BoundingBox boundingBox = next.getBoundingBox();
                    childEntranceBoxes.add(new BoundingBox(boundingBox.x0, boundingBox.y0, this.boundingBox.z0, boundingBox.x1, boundingBox.y1, this.boundingBox.z0 + 1));
                }
            }

            for (int x = 0; x < boundingBox.getXSpan(); x += 4) {
                x += random.nextBoundedInt(boundingBox.getXSpan());
                if (x + 3 > boundingBox.getXSpan()) {
                    break;
                }

                final MineshaftPiece next = generateAndAddPiece(piece, pieces, random, boundingBox.x0 + x, boundingBox.y0 + random.nextBoundedInt(yOffset) + 1, boundingBox.z1 + 1, BlockFace.SOUTH, genDepth);
                if (next != null) {
                    final BoundingBox boundingBox = next.getBoundingBox();
                    childEntranceBoxes.add(new BoundingBox(boundingBox.x0, boundingBox.y0, this.boundingBox.z1 - 1, boundingBox.x1, boundingBox.y1, this.boundingBox.z1));
                }
            }

            for (int z = 0; z < boundingBox.getZSpan(); z += 4) {
                z += random.nextBoundedInt(boundingBox.getZSpan());
                if (z + 3 > boundingBox.getZSpan()) {
                    break;
                }

                final MineshaftPiece next = generateAndAddPiece(piece, pieces, random, boundingBox.x0 - 1, boundingBox.y0 + random.nextBoundedInt(yOffset) + 1, boundingBox.z0 + z, BlockFace.WEST, genDepth);
                if (next != null) {
                    final BoundingBox boundingBox = next.getBoundingBox();
                    childEntranceBoxes.add(new BoundingBox(this.boundingBox.x0, boundingBox.y0, boundingBox.z0, this.boundingBox.x0 + 1, boundingBox.y1, boundingBox.z1));
                }
            }

            for (int z = 0; z < boundingBox.getZSpan(); z += 4) {
                z += random.nextBoundedInt(boundingBox.getZSpan());
                if (z + 3 > boundingBox.getZSpan()) {
                    break;
                }

                final MineshaftPiece next = generateAndAddPiece(piece, pieces, random, boundingBox.x1 + 1, boundingBox.y0 + random.nextBoundedInt(yOffset) + 1, boundingBox.z0 + z, BlockFace.EAST, genDepth);
                if (next != null) {
                    final BoundingBox boundingBox = next.getBoundingBox();
                    childEntranceBoxes.add(new BoundingBox(this.boundingBox.x1 - 1, boundingBox.y0, boundingBox.z0, this.boundingBox.x1, boundingBox.y1, boundingBox.z1));
                }
            }
        }

        @Override //\\ MineshaftRoom::postProcess(BlockSource *,Random &,BoundingBox const &)
        public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
            if (edgesLiquid(level, boundingBox)) {
                return false;
            }

            generateBox(level, boundingBox, this.boundingBox.x0, this.boundingBox.y0, this.boundingBox.z0, this.boundingBox.x1, this.boundingBox.y0, this.boundingBox.z1, DIRT, BlockState.AIR, true);
            generateBox(level, boundingBox, this.boundingBox.x0, this.boundingBox.y0 + 1, this.boundingBox.z0, this.boundingBox.x1, Math.min(this.boundingBox.y0 + 3, this.boundingBox.y1), this.boundingBox.z1, BlockState.AIR, BlockState.AIR, false);

            for (final BoundingBox childEntranceBox : childEntranceBoxes) {
                generateBox(level, boundingBox, childEntranceBox.x0, childEntranceBox.y1 - 2, childEntranceBox.z0, childEntranceBox.x1, childEntranceBox.y1, childEntranceBox.z1, BlockState.AIR, BlockState.AIR, false);
            }

            generateUpperHalfSphere(level, boundingBox, this.boundingBox.x0, this.boundingBox.y0 + 4, this.boundingBox.z0, this.boundingBox.x1, this.boundingBox.y1, this.boundingBox.z1, BlockState.AIR, false);
            return true;
        }

        public void move(final int x, final int y, final int z) {
            super.move(x, y, z);
            for (final BoundingBox childEntranceBox : childEntranceBoxes) {
                childEntranceBox.move(x, y, z);
            }
        }
    }

    public static class MineshaftCorridor extends MineshaftPiece {
        private final boolean hasRails;
        private final boolean spiderCorridor;
        private final int numSections;
        private boolean hasPlacedSpider;

        public MineshaftCorridor(final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation, final PopulatorMineshaft.Type type) {
            super(genDepth, type);
            setOrientation(orientation);
            this.boundingBox = boundingBox;
            hasRails = random.nextBoundedInt(3) == 0;
            spiderCorridor = !hasRails && random.nextBoundedInt(23) == 0;
            if (getOrientation() == null || getOrientation().getAxis() == BlockFace.Axis.Z) {
                numSections = boundingBox.getZSpan() / 5;
            } else {
                numSections = boundingBox.getXSpan() / 5;
            }
        }

        //\\ MineshaftCorridor::findCorridorSize(std::vector<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>,std::allocator<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>>> &,Random &,int,int,int,int)
        public static BoundingBox findCorridorSize(final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation) {
            final BoundingBox boundingBox = new BoundingBox(x, y, z, x, y + 3 - 1, z);

            int count = random.nextBoundedInt(3) + 2;
            for (int i; count > 0; --count) {
                i = count * 5;

                switch (orientation) {
                    case SOUTH -> {
                        boundingBox.x1 = x + 3 - 1;
                        boundingBox.z1 = z + i - 1;
                    }
                    case WEST -> {
                        boundingBox.x0 = x - (i - 1);
                        boundingBox.z1 = z + 3 - 1;
                    }
                    case EAST -> {
                        boundingBox.x1 = x + i - 1;
                        boundingBox.z1 = z + 3 - 1;
                    }
                    default -> {
                        boundingBox.x1 = x + 3 - 1;
                        boundingBox.z0 = z - (i - 1);
                    }
                }

                if (findCollisionPiece(pieces, boundingBox) == null) {
                    break;
                }
            }

            return count > 0 ? boundingBox : null;
        }

        @Override //\\ MineshaftCorridor::getType() // 1297302351i64;
        public String getType() {
            return "MSCorridor";
        }

        @Override
        public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
            final int genDepth = getGenDepth();
            final int target = random.nextBoundedInt(4);
            final BlockFace orientation = getOrientation();
            if (orientation != null) {
                switch (orientation) {
                    case SOUTH -> {
                        if (target <= 1) {
                            generateAndAddPiece(piece, pieces, random, boundingBox.x0, boundingBox.y0 - 1 + random.nextBoundedInt(3), boundingBox.z1 + 1, orientation, genDepth);
                        } else if (target == 2) {
                            generateAndAddPiece(piece, pieces, random, boundingBox.x0 - 1, boundingBox.y0 - 1 + random.nextBoundedInt(3), boundingBox.z1 - 3, BlockFace.WEST, genDepth);
                        } else {
                            generateAndAddPiece(piece, pieces, random, boundingBox.x1 + 1, boundingBox.y0 - 1 + random.nextBoundedInt(3), boundingBox.z1 - 3, BlockFace.EAST, genDepth);
                        }
                    }
                    case WEST -> {
                        if (target <= 1) {
                            generateAndAddPiece(piece, pieces, random, boundingBox.x0 - 1, boundingBox.y0 - 1 + random.nextBoundedInt(3), boundingBox.z0, orientation, genDepth);
                        } else if (target == 2) {
                            generateAndAddPiece(piece, pieces, random, boundingBox.x0, boundingBox.y0 - 1 + random.nextBoundedInt(3), boundingBox.z0 - 1, BlockFace.NORTH, genDepth);
                        } else {
                            generateAndAddPiece(piece, pieces, random, boundingBox.x0, boundingBox.y0 - 1 + random.nextBoundedInt(3), boundingBox.z1 + 1, BlockFace.SOUTH, genDepth);
                        }
                    }
                    case EAST -> {
                        if (target <= 1) {
                            generateAndAddPiece(piece, pieces, random, boundingBox.x1 + 1, boundingBox.y0 - 1 + random.nextBoundedInt(3), boundingBox.z0, orientation, genDepth);
                        } else if (target == 2) {
                            generateAndAddPiece(piece, pieces, random, boundingBox.x1 - 3, boundingBox.y0 - 1 + random.nextBoundedInt(3), boundingBox.z0 - 1, BlockFace.NORTH, genDepth);
                        } else {
                            generateAndAddPiece(piece, pieces, random, boundingBox.x1 - 3, boundingBox.y0 - 1 + random.nextBoundedInt(3), boundingBox.z1 + 1, BlockFace.SOUTH, genDepth);
                        }
                    }
                    default -> {
                        if (target <= 1) {
                            generateAndAddPiece(piece, pieces, random, boundingBox.x0, boundingBox.y0 - 1 + random.nextBoundedInt(3), boundingBox.z0 - 1, orientation, genDepth);
                        } else if (target == 2) {
                            generateAndAddPiece(piece, pieces, random, boundingBox.x0 - 1, boundingBox.y0 - 1 + random.nextBoundedInt(3), boundingBox.z0, BlockFace.WEST, genDepth);
                        } else {
                            generateAndAddPiece(piece, pieces, random, boundingBox.x1 + 1, boundingBox.y0 - 1 + random.nextBoundedInt(3), boundingBox.z0, BlockFace.EAST, genDepth);
                        }
                    }
                }
            }

            if (genDepth < 8) {
                if (orientation != BlockFace.NORTH && orientation != BlockFace.SOUTH) {
                    for (int x = boundingBox.x0 + 3; x + 3 <= boundingBox.x1; x += 5) {
                        final int type = random.nextBoundedInt(5);
                        if (type == 0) {
                            generateAndAddPiece(piece, pieces, random, x, boundingBox.y0, boundingBox.z0 - 1, BlockFace.NORTH, genDepth + 1);
                        } else if (type == 1) {
                            generateAndAddPiece(piece, pieces, random, x, boundingBox.y0, boundingBox.z1 + 1, BlockFace.SOUTH, genDepth + 1);
                        }
                    }
                } else {
                    for (int z = boundingBox.z0 + 3; z + 3 <= boundingBox.z1; z += 5) {
                        final int type = random.nextBoundedInt(5);
                        if (type == 0) {
                            generateAndAddPiece(piece, pieces, random, boundingBox.x0 - 1, boundingBox.y0, z, BlockFace.WEST, genDepth + 1);
                        } else if (type == 1) {
                            generateAndAddPiece(piece, pieces, random, boundingBox.x1 + 1, boundingBox.y0, z, BlockFace.EAST, genDepth + 1);
                        }
                    }
                }
            }
        }

        protected void createChest(final ChunkManager level, final BoundingBox boundingBox, final NukkitRandom random, final int x, final int y, final int z) {
            final BlockVector3 vec = new BlockVector3(getWorldX(x, z), getWorldY(y), getWorldZ(x, z));

            if (boundingBox.isInside(vec) && level.getBlockIdAt(vec.x, vec.y, vec.z) == BlockID.AIR && level.getBlockIdAt(vec.x, vec.y - 1, vec.z) != BlockID.AIR) {
                placeBlock(level, random.nextBoolean() ? RAIL__NS : RAIL__EW, x, y, z, boundingBox);

                //\\ MineshaftCorridor::postProcessMobsAt(BlockSource *,Random &,BoundingBox const &)
                final BaseFullChunk chunk = level.getChunk(vec.x >> 4, vec.z >> 4);
                if (chunk != null) {
                    final CompoundTag nbt = Entity.getDefaultNBT(vec.asVector3().add(.5, 0.5, .5))
                        .putString("id", "MinecartChest");

                    final ListTag<CompoundTag> itemList = new ListTag<>("Items");
                    MineshaftChest.get().create(itemList, random);
                    nbt.putList(itemList);

                    Server.getInstance().getScheduler().scheduleTask(new ActorSpawnTask(chunk.getProvider().getLevel(), nbt));
                }

            }

        }

        @Override
        public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
            if (edgesLiquid(level, boundingBox)) {
                return false;
            }

            final int z1 = numSections * 5 - 1;
            generateBox(level, boundingBox, 0, 0, 0, 2, 1, z1, BlockState.AIR, BlockState.AIR, false);
            generateMaybeBox(level, boundingBox, random, 50, 0, 2, 0, 2, 2, z1, BlockState.AIR, BlockState.AIR, false, false);
            if (spiderCorridor) {
                generateMaybeBox(level, boundingBox, random, 60, 0, 0, 0, 2, 1, z1, COBWEB, BlockState.AIR, false, true);
            }

            for (int i = 0; i < numSections; ++i) {
                final int z = 2 + i * 5;

                placeSupport(level, boundingBox, 0, 0, z, 2, 2, random);
                placeCobWeb(level, boundingBox, random, 10, 0, 2, z - 1);
                placeCobWeb(level, boundingBox, random, 10, 2, 2, z - 1);
                placeCobWeb(level, boundingBox, random, 10, 0, 2, z + 1);
                placeCobWeb(level, boundingBox, random, 10, 2, 2, z + 1);
                placeCobWeb(level, boundingBox, random, 5, 0, 2, z - 2);
                placeCobWeb(level, boundingBox, random, 5, 2, 2, z - 2);
                placeCobWeb(level, boundingBox, random, 5, 0, 2, z + 2);
                placeCobWeb(level, boundingBox, random, 5, 2, 2, z + 2);

                if (random.nextBoundedInt(100) == 0) {
                    createChest(level, boundingBox, random, 2, 0, z - 1);
                }
                if (random.nextBoundedInt(100) == 0) {
                    createChest(level, boundingBox, random, 0, 0, z + 1);
                }

                if (spiderCorridor && !hasPlacedSpider) {
                    final int pz = z - 1 + random.nextBoundedInt(3);
                    final int worldX = getWorldX(1, pz);
                    final int worldZ = getWorldZ(1, pz);
                    final BlockVector3 vec = new BlockVector3(worldX, getWorldY(0), worldZ);

                    if (boundingBox.isInside(vec) && isInterior(level, 1, 0, pz, boundingBox)) {
                        hasPlacedSpider = true;
                        level.setBlockAt(vec.x, vec.y, vec.z, SPAWNER.getId(), SPAWNER.getMeta());

                        final BaseFullChunk chunk = level.getChunk(vec.x >> 4, vec.z >> 4);
                        if (chunk != null) {
                            Server.getInstance().getScheduler().scheduleTask(new BlockActorSpawnTask(chunk.getProvider().getLevel(),
                                BlockEntity.getDefaultCompound(vec.asVector3(), BlockEntity.MOB_SPAWNER)
                                    .putInt("EntityId", EntityCaveSpider.NETWORK_ID)));
                        }
                    }
                }
            }

            final BlockState planks = getPlanksBlock();
            for (int x = 0; x <= 2; ++x) {
                for (int z = 0; z <= z1; ++z) {
                    final BlockState block = getBlock(level, x, -1, z, boundingBox);
                    if (block.equals(BlockState.AIR) && isInterior(level, x, -1, z, boundingBox)) {
                        placeBlock(level, planks, x, -1, z, boundingBox);
                    }
                }
            }

            if (hasRails) {
                for (int z = 0; z <= z1; ++z) {
                    final BlockState block = getBlock(level, 1, -1, z, boundingBox);
                    final int id = level.getBlockIdAt(getWorldX(1, z), getWorldY(-1), getWorldZ(1, z));
                    if (!block.equals(BlockState.AIR) && Block.solid[id] && !Block.transparent[id]) {
                        maybeGenerateBlock(level, boundingBox, random, isInterior(level, 1, 0, z, boundingBox) ? 70 : 90, 1, 0, z, RAIL__NS);
                    }
                }
            }

            return true;
        }

        //\\ MineshaftCorridor::_placeSupport(BlockSource *,BoundingBox const &,int,int,int,int,int,Random &)
        private void placeSupport(final ChunkManager level, final BoundingBox boundingBox, final int x0, final int y0, final int z, final int y1, final int x1, final NukkitRandom random) {
            if (isSupportingBox(level, boundingBox, x0, x1, y1, z)) {
                final BlockState fence = getFenceBlock();
                generateBox(level, boundingBox, x0, y0, z, x0, y1 - 1, z, fence, BlockState.AIR, false);
                generateBox(level, boundingBox, x1, y0, z, x1, y1 - 1, z, fence, BlockState.AIR, false);

                final BlockState planks = getPlanksBlock();
                if (random.nextBoundedInt(4) == 0) {
                    generateBox(level, boundingBox, x0, y1, z, x0, y1, z, planks, BlockState.AIR, false);
                    generateBox(level, boundingBox, x1, y1, z, x1, y1, z, planks, BlockState.AIR, false);
                } else {
                    generateBox(level, boundingBox, x0, y1, z, x1, y1, z, planks, BlockState.AIR, false);
                    maybeGenerateBlock(level, boundingBox, random, 5, x0 + 1, y1, z - 1, TORCH__S);
                    maybeGenerateBlock(level, boundingBox, random, 5, x0 + 1, y1, z + 1, TORCH__N);
                }
            }
        }

        //\\ MineshaftCorridor::_placeCobWeb(BlockSource *,BoundingBox const &,Random &,float,int,int,int)
        private void placeCobWeb(final ChunkManager level, final BoundingBox boundingBox, final NukkitRandom random, final int probability, final int x, final int y, final int z) {
            if (isInterior(level, x, y, z, boundingBox)) {
                maybeGenerateBlock(level, boundingBox, random, probability, x, y, z, COBWEB);
            }
        }
    }

    public static class MineshaftCrossing extends MineshaftPiece {

        private final BlockFace direction;
        private final boolean isTwoFloored;

        public MineshaftCrossing(final int genDepth, final BoundingBox boundingBox, final BlockFace orientation, final PopulatorMineshaft.Type type) {
            super(genDepth, type);
            direction = orientation;
            this.boundingBox = boundingBox;
            isTwoFloored = boundingBox.getYSpan() > 3;
        }

        public static BoundingBox findCrossing(final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation) {
            final BoundingBox boundingBox = new BoundingBox(x, y, z, x, y + 3 - 1, z);
            if (random.nextBoundedInt(4) == 0) {
                boundingBox.y1 += 4;
            }

            switch (orientation) {
                case SOUTH -> {
                    boundingBox.x0 = x - 1;
                    boundingBox.x1 = x + 3;
                    boundingBox.z1 = z + 3 + 1;
                }
                case WEST -> {
                    boundingBox.x0 = x - 4;
                    boundingBox.z0 = z - 1;
                    boundingBox.z1 = z + 3;
                }
                case EAST -> {
                    boundingBox.x1 = x + 3 + 1;
                    boundingBox.z0 = z - 1;
                    boundingBox.z1 = z + 3;
                }
                default -> {
                    boundingBox.x0 = x - 1;
                    boundingBox.x1 = x + 3;
                    boundingBox.z0 = z - 4;
                }
            }

            return findCollisionPiece(pieces, boundingBox) == null ? boundingBox : null;
        }

        @Override //\\ MineshaftCrossing::getType() // 1297302354i64;
        public String getType() {
            return "MSCrossing";
        }

        @Override
        public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
            final int genDepth = getGenDepth();
            switch (direction) {
                case SOUTH -> {
                    generateAndAddPiece(piece, pieces, random, boundingBox.x0 + 1, boundingBox.y0, boundingBox.z1 + 1, BlockFace.SOUTH, genDepth);
                    generateAndAddPiece(piece, pieces, random, boundingBox.x0 - 1, boundingBox.y0, boundingBox.z0 + 1, BlockFace.WEST, genDepth);
                    generateAndAddPiece(piece, pieces, random, boundingBox.x1 + 1, boundingBox.y0, boundingBox.z0 + 1, BlockFace.EAST, genDepth);
                }
                case WEST -> {
                    generateAndAddPiece(piece, pieces, random, boundingBox.x0 + 1, boundingBox.y0, boundingBox.z0 - 1, BlockFace.NORTH, genDepth);
                    generateAndAddPiece(piece, pieces, random, boundingBox.x0 + 1, boundingBox.y0, boundingBox.z1 + 1, BlockFace.SOUTH, genDepth);
                    generateAndAddPiece(piece, pieces, random, boundingBox.x0 - 1, boundingBox.y0, boundingBox.z0 + 1, BlockFace.WEST, genDepth);
                }
                case EAST -> {
                    generateAndAddPiece(piece, pieces, random, boundingBox.x0 + 1, boundingBox.y0, boundingBox.z0 - 1, BlockFace.NORTH, genDepth);
                    generateAndAddPiece(piece, pieces, random, boundingBox.x0 + 1, boundingBox.y0, boundingBox.z1 + 1, BlockFace.SOUTH, genDepth);
                    generateAndAddPiece(piece, pieces, random, boundingBox.x1 + 1, boundingBox.y0, boundingBox.z0 + 1, BlockFace.EAST, genDepth);
                }
                default -> {
                    generateAndAddPiece(piece, pieces, random, boundingBox.x0 + 1, boundingBox.y0, boundingBox.z0 - 1, BlockFace.NORTH, genDepth);
                    generateAndAddPiece(piece, pieces, random, boundingBox.x0 - 1, boundingBox.y0, boundingBox.z0 + 1, BlockFace.WEST, genDepth);
                    generateAndAddPiece(piece, pieces, random, boundingBox.x1 + 1, boundingBox.y0, boundingBox.z0 + 1, BlockFace.EAST, genDepth);
                }
            }

            if (isTwoFloored) {
                if (random.nextBoolean()) {
                    generateAndAddPiece(piece, pieces, random, boundingBox.x0 + 1, boundingBox.y0 + 3 + 1, boundingBox.z0 - 1, BlockFace.NORTH, genDepth);
                }
                if (random.nextBoolean()) {
                    generateAndAddPiece(piece, pieces, random, boundingBox.x0 - 1, boundingBox.y0 + 3 + 1, boundingBox.z0 + 1, BlockFace.WEST, genDepth);
                }
                if (random.nextBoolean()) {
                    generateAndAddPiece(piece, pieces, random, boundingBox.x1 + 1, boundingBox.y0 + 3 + 1, boundingBox.z0 + 1, BlockFace.EAST, genDepth);
                }
                if (random.nextBoolean()) {
                    generateAndAddPiece(piece, pieces, random, boundingBox.x0 + 1, boundingBox.y0 + 3 + 1, boundingBox.z1 + 1, BlockFace.SOUTH, genDepth);
                }
            }
        }

        @Override
        public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
            if (edgesLiquid(level, boundingBox)) {
                return false;
            }

            if (isTwoFloored) {
                generateBox(level, boundingBox, this.boundingBox.x0 + 1, this.boundingBox.y0, this.boundingBox.z0, this.boundingBox.x1 - 1, this.boundingBox.y0 + 3 - 1, this.boundingBox.z1, BlockState.AIR, BlockState.AIR, false);
                generateBox(level, boundingBox, this.boundingBox.x0, this.boundingBox.y0, this.boundingBox.z0 + 1, this.boundingBox.x1, this.boundingBox.y0 + 3 - 1, this.boundingBox.z1 - 1, BlockState.AIR, BlockState.AIR, false);
                generateBox(level, boundingBox, this.boundingBox.x0 + 1, this.boundingBox.y1 - 2, this.boundingBox.z0, this.boundingBox.x1 - 1, this.boundingBox.y1, this.boundingBox.z1, BlockState.AIR, BlockState.AIR, false);
                generateBox(level, boundingBox, this.boundingBox.x0, this.boundingBox.y1 - 2, this.boundingBox.z0 + 1, this.boundingBox.x1, this.boundingBox.y1, this.boundingBox.z1 - 1, BlockState.AIR, BlockState.AIR, false);
                generateBox(level, boundingBox, this.boundingBox.x0 + 1, this.boundingBox.y0 + 3, this.boundingBox.z0 + 1, this.boundingBox.x1 - 1, this.boundingBox.y0 + 3, this.boundingBox.z1 - 1, BlockState.AIR, BlockState.AIR, false);
            } else {
                generateBox(level, boundingBox, this.boundingBox.x0 + 1, this.boundingBox.y0, this.boundingBox.z0, this.boundingBox.x1 - 1, this.boundingBox.y1, this.boundingBox.z1, BlockState.AIR, BlockState.AIR, false);
                generateBox(level, boundingBox, this.boundingBox.x0, this.boundingBox.y0, this.boundingBox.z0 + 1, this.boundingBox.x1, this.boundingBox.y1, this.boundingBox.z1 - 1, BlockState.AIR, BlockState.AIR, false);
            }

            placeSupportPillar(level, boundingBox, this.boundingBox.x0 + 1, this.boundingBox.y0, this.boundingBox.z0 + 1, this.boundingBox.y1);
            placeSupportPillar(level, boundingBox, this.boundingBox.x0 + 1, this.boundingBox.y0, this.boundingBox.z1 - 1, this.boundingBox.y1);
            placeSupportPillar(level, boundingBox, this.boundingBox.x1 - 1, this.boundingBox.y0, this.boundingBox.z0 + 1, this.boundingBox.y1);
            placeSupportPillar(level, boundingBox, this.boundingBox.x1 - 1, this.boundingBox.y0, this.boundingBox.z1 - 1, this.boundingBox.y1);

            final BlockState planks = getPlanksBlock();
            for (int x = this.boundingBox.x0; x <= this.boundingBox.x1; ++x) {
                for (int z = this.boundingBox.z0; z <= this.boundingBox.z1; ++z) {
                    if (getBlock(level, x, this.boundingBox.y0 - 1, z, boundingBox).equals(BlockState.AIR) && isInterior(level, x, this.boundingBox.y0 - 1, z, boundingBox)) {
                        placeBlock(level, planks, x, this.boundingBox.y0 - 1, z, boundingBox);
                    }
                }
            }

            return true;
        }

        //\\ MineshaftCrossing::_placeSupportPillar(BlockSource *,BoundingBox const &,int,int,int,int)
        private void placeSupportPillar(final ChunkManager level, final BoundingBox boundingBox, final int x, final int y0, final int z, final int y1) {
            if (!getBlock(level, x, y1 + 1, z, boundingBox).equals(BlockState.AIR)) {
                generateBox(level, boundingBox, x, y0, z, x, y1, z, getPlanksBlock(), BlockState.AIR, false);
            }
        }
    }

    public static class MineshaftStairs extends MineshaftPiece {

        public MineshaftStairs(final int genDepth, final BoundingBox boundingBox, final BlockFace orientation, final PopulatorMineshaft.Type type) {
            super(genDepth, type);
            setOrientation(orientation);
            this.boundingBox = boundingBox;
        }

        public static BoundingBox findStairs(final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation) {
            final BoundingBox boundingBox = new BoundingBox(x, y - 5, z, x, y + 3 - 1, z);
            switch (orientation) {
                case SOUTH -> {
                    boundingBox.x1 = x + 3 - 1;
                    boundingBox.z1 = z + 8;
                }
                case WEST -> {
                    boundingBox.x0 = x - 8;
                    boundingBox.z1 = z + 3 - 1;
                }
                case EAST -> {
                    boundingBox.x1 = x + 8;
                    boundingBox.z1 = z + 3 - 1;
                }
                default -> {
                    boundingBox.x1 = x + 3 - 1;
                    boundingBox.z0 = z - 8;
                }
            }

            return findCollisionPiece(pieces, boundingBox) == null ? boundingBox : null;
        }

        @Override//\\ MineshaftStairs::getType() // 1297306452i64;
        public String getType() {
            return "MSStairs";
        }

        @Override
        public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
            final int genDepth = getGenDepth();
            final BlockFace orientation = getOrientation();
            if (orientation != null) {
                switch (orientation) {
                    case SOUTH -> generateAndAddPiece(piece, pieces, random, boundingBox.x0, boundingBox.y0, boundingBox.z1 + 1, BlockFace.SOUTH, genDepth);
                    case WEST -> generateAndAddPiece(piece, pieces, random, boundingBox.x0 - 1, boundingBox.y0, boundingBox.z0, BlockFace.WEST, genDepth);
                    case EAST -> generateAndAddPiece(piece, pieces, random, boundingBox.x1 + 1, boundingBox.y0, boundingBox.z0, BlockFace.EAST, genDepth);
                    default -> generateAndAddPiece(piece, pieces, random, boundingBox.x0, boundingBox.y0, boundingBox.z0 - 1, BlockFace.NORTH, genDepth);
                }
            }
        }

        @Override
        public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
            if (edgesLiquid(level, boundingBox)) {
                return false;
            }

            generateBox(level, boundingBox, 0, 5, 0, 2, 7, 1, BlockState.AIR, BlockState.AIR, false);
            generateBox(level, boundingBox, 0, 0, 7, 2, 2, 8, BlockState.AIR, BlockState.AIR, false);

            for (int i = 0; i < 5; ++i) {
                generateBox(level, boundingBox, 0, 5 - i - (i < 4 ? 1 : 0), 2 + i, 2, 7 - i, 2 + i, BlockState.AIR, BlockState.AIR, false);
            }

            return true;
        }
    }
}
