package cn.nukkit.level.generator.structure;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockFence;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockPlanks;
import cn.nukkit.block.BlockSandstone;
import cn.nukkit.block.BlockWood;
import cn.nukkit.block.BlockWood2;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.block.state.BlockState;
import cn.nukkit.level.generator.block.state.FacingDirection;
import cn.nukkit.level.generator.block.state.TorchFacingDirection;
import cn.nukkit.level.generator.block.state.WeirdoDirection;
import cn.nukkit.level.generator.loot.VillageBlacksmithChest;
import cn.nukkit.level.generator.loot.VillageTwoRoomHouseChest;
import cn.nukkit.level.generator.math.BoundingBox;
import cn.nukkit.level.generator.math.Mth;
import cn.nukkit.level.generator.populator.overworld.PopulatorVillage;
import cn.nukkit.level.generator.task.BlockActorSpawnTask;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.DyeColor;
import com.google.common.collect.Lists;

import java.util.List;

public final class VillagePieces {
    private static final BlockState PLANKS = new BlockState(Block.PLANKS, BlockPlanks.OAK);
    private static final BlockState SPRUCE_PLANKS = new BlockState(Block.PLANKS, BlockPlanks.SPRUCE);
    private static final BlockState ACACIA_PLANKS = new BlockState(Block.PLANKS, BlockPlanks.ACACIA);
    private static final BlockState OAK_FENCE = new BlockState(Block.FENCE, BlockFence.FENCE_OAK);
    private static final BlockState SPRUCE_FENCE = new BlockState(Block.FENCE, BlockFence.FENCE_SPRUCE);
    private static final BlockState ACACIA_FENCE = new BlockState(Block.FENCE, BlockFence.FENCE_ACACIA);
    private static final BlockState OAK_DOOR = new BlockState(Block.WOODEN_DOOR_BLOCK);
    private static final BlockState SPRUCE_DOOR = new BlockState(Block.SPRUCE_DOOR_BLOCK);
    private static final BlockState ACACIA_DOOR = new BlockState(Block.ACACIA_DOOR_BLOCK);
    private static final BlockState LOG = new BlockState(Block.LOG);
    private static final BlockState ACACIA_LOG__Y = new BlockState(Block.LOG2, BlockWood2.ACACIA);
    private static final BlockState COBBLESTONE = new BlockState(Block.COBBLESTONE);
    private static final BlockState SANDSTONE = new BlockState(Block.SANDSTONE);
    private static final BlockState SMOOTH_SANDSTONE = new BlockState(Block.SANDSTONE, BlockSandstone.SMOOTH);
    private static final BlockState COBBLESTONE_STAIRS__N = new BlockState(Block.COBBLESTONE_STAIRS, WeirdoDirection.NORTH);
    private static final BlockState OAK_STAIRS__N = new BlockState(Block.WOOD_STAIRS, WeirdoDirection.NORTH);
    private static final BlockState OAK_STAIRS__S = new BlockState(Block.WOOD_STAIRS, WeirdoDirection.SOUTH);
    private static final BlockState COBBLESTONE_STAIRS__E = new BlockState(Block.COBBLESTONE_STAIRS, WeirdoDirection.EAST);
    private static final BlockState OAK_STAIRS__E = new BlockState(Block.WOOD_STAIRS, WeirdoDirection.EAST);
    private static final BlockState COBBLESTONE_STAIRS__W = new BlockState(Block.COBBLESTONE_STAIRS, WeirdoDirection.WEST);
    private static final BlockState OAK_STAIRS__W = new BlockState(Block.WOOD_STAIRS, WeirdoDirection.WEST);
    private static final BlockState GRASS = new BlockState(Block.GRASS);
    private static final BlockState GRASS_PATH = new BlockState(Block.GRASS_PATH);
    private static final BlockState DIRT = new BlockState(Block.DIRT);
    private static final BlockState FARMLAND = new BlockState(Block.FARMLAND);
    private static final BlockState GRAVEL = new BlockState(Block.GRAVEL);
    private static final BlockState FLOWING_WATER = new BlockState(Block.WATER);
    private static final BlockState WATER = new BlockState(Block.STILL_WATER);
    private static final BlockState FLOWING_LAVA = new BlockState(Block.LAVA);
    private static final BlockState IRON_BARS = new BlockState(Block.IRON_BARS);
    private static final BlockState FURNACE = new BlockState(Block.FURNACE, FacingDirection.SOUTH);
    private static final BlockState CRAFTING_TABLE = new BlockState(Block.CRAFTING_TABLE);
    private static final BlockState BOOKSHELF = new BlockState(Block.BOOKSHELF);
    private static final BlockState LADDER__S = new BlockState(Block.LADDER, FacingDirection.SOUTH);
    private static final BlockState LADDER__W = new BlockState(Block.LADDER, FacingDirection.WEST);
    private static final BlockState GLASS_PANE = new BlockState(Block.GLASS_PANE);
    private static final BlockState STONE_SLAB = new BlockState(Block.STONE_SLAB);
    private static final BlockState DOUBLE_STONE_SLAB = new BlockState(Block.DOUBLE_STONE_SLAB);
    private static final BlockState WHEAT = new BlockState(Block.WHEAT_BLOCK);
    private static final BlockState CARROTS = new BlockState(Block.CARROT_BLOCK);
    private static final BlockState POTATOES = new BlockState(Block.POTATO_BLOCK);
    private static final BlockState BEETROOTS = new BlockState(Block.BEETROOT_BLOCK);
    private static final BlockState BLACK_WOOL = new BlockState(Block.WOOL, DyeColor.BLACK.getWoolData());
    private static final BlockState BROWN_CARPET = new BlockState(Block.CARPET, DyeColor.BROWN.getWoolData()); //BE

    public static List<PieceWeight> getStructureVillageWeightedPieceList(final NukkitRandom random, final int size) {
        final List<PieceWeight> weights = Lists.newArrayList();
        weights.add(new PieceWeight(SimpleHouse.class, 4, Mth.nextInt(random, 2 + size, 4 + (size << 1))));
        weights.add(new PieceWeight(SmallTemple.class, 20, Mth.nextInt(random, size, 1 + size)));
        weights.add(new PieceWeight(BookHouse.class, 20, Mth.nextInt(random, size, 2 + size)));
        weights.add(new PieceWeight(SmallHut.class, 3, Mth.nextInt(random, 2 + size, 5 + size * 3)));
        weights.add(new PieceWeight(PigHouse.class, 15, Mth.nextInt(random, size, 2 + size)));
        weights.add(new PieceWeight(DoubleFarmland.class, 3, Mth.nextInt(random, 1 + size, 4 + size)));
        weights.add(new PieceWeight(Farmland.class, 3, Mth.nextInt(random, 2 + size, 4 + (size << 1))));
        weights.add(new PieceWeight(Smithy.class, 15, Mth.nextInt(random, 0, 1 + size)));
        weights.add(new PieceWeight(TwoRoomHouse.class, 8, Mth.nextInt(random, size, 3 + (size << 1))));
        weights.removeIf(pieceWeight -> pieceWeight.maxPlaceCount == 0);
        return weights;
    }

    private static int updatePieceWeight(final List<PieceWeight> weights) {
        boolean success = false;
        int total = 0;

        for (final PieceWeight weight : weights) {
            if (weight.maxPlaceCount > 0 && weight.placeCount < weight.maxPlaceCount) {
                success = true;
            }

            total += weight.weight;
        }

        return success ? total : -1;
    }

    //\\ VillagePiece::findAndCreatePieceFactory(StartPiece *,PieceWeight &,std::vector<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>,std::allocator<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>>> &,Random &,int,int,int,int,int)
    private static VillagePiece findAndCreatePieceFactory(final StartPiece start, final PieceWeight weight, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int componentType) {
        final Class<? extends VillagePiece> pieceClass = weight.pieceClass;
        if (pieceClass == SimpleHouse.class) {
            return SimpleHouse.createPiece(start, pieces, random, x, y, z, orientation, componentType);
        }
        if (pieceClass == SmallTemple.class) {
            return SmallTemple.createPiece(start, pieces, random, x, y, z, orientation, componentType);
        }
        if (pieceClass == BookHouse.class) {
            return BookHouse.createPiece(start, pieces, random, x, y, z, orientation, componentType);
        }
        if (pieceClass == SmallHut.class) {
            return SmallHut.createPiece(start, pieces, random, x, y, z, orientation, componentType);
        }
        if (pieceClass == PigHouse.class) {
            return PigHouse.createPiece(start, pieces, random, x, y, z, orientation, componentType);
        }
        if (pieceClass == DoubleFarmland.class) {
            return DoubleFarmland.createPiece(start, pieces, random, x, y, z, orientation, componentType);
        }
        if (pieceClass == Farmland.class) {
            return Farmland.createPiece(start, pieces, random, x, y, z, orientation, componentType);
        }
        if (pieceClass == Smithy.class) {
            return Smithy.createPiece(start, pieces, random, x, y, z, orientation, componentType);
        }
        if (pieceClass == TwoRoomHouse.class) {
            return TwoRoomHouse.createPiece(start, pieces, random, x, y, z, orientation, componentType);
        }
        return null;
    }

    //\\ VillagePiece::generateAndAddPiece(StartPiece *,std::vector<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>,std::allocator<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>>> &,Random &,int,int,int,int,int)
    private static VillagePiece generateAndAddPiece(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
        final int total = updatePieceWeight(start.availablePieces);
        if (total > 0) {
            for (int i = 0; i < 5; ++i) {
                int target = random.nextBoundedInt(total);

                for (final PieceWeight weight : start.availablePieces) {
                    target -= weight.weight;

                    if (target < 0) {
                        if (!weight.doPlace(genDepth) || weight == start.previousPiece && start.availablePieces.size() > 1) {
                            break;
                        }

                        final VillagePiece piece = findAndCreatePieceFactory(start, weight, pieces, random, x, y, z, orientation, genDepth);
                        if (piece != null) {
                            ++weight.placeCount;
                            start.previousPiece = weight;

                            if (!weight.isValid()) {
                                start.availablePieces.remove(weight);
                            }

                            return piece;
                        }
                    }
                }
            }

            final BoundingBox boundingBox = LightPost.findPieceBox(start, pieces, random, x, y, z, orientation);
            if (boundingBox != null) {
                return new LightPost(start, genDepth, random, boundingBox, orientation);
            }
        }
        return null;
    }

    //\\ VillagePiece::generatePieceFromSmallDoor(StartPiece *,std::vector<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>,std::allocator<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>>> &,Random &,int,int,int,int,int)
    private static StructurePiece generatePieceFromSmallDoor(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
        if (genDepth <= 50 && Math.abs(x - start.getBoundingBox().x0) <= 112 && Math.abs(z - start.getBoundingBox().z0) <= 112) {
            final StructurePiece piece = generateAndAddPiece(start, pieces, random, x, y, z, orientation, genDepth + 1);
            if (piece != null) {
                pieces.add(piece);
                start.pendingHouses.add(piece);
                return piece;
            }
        }
        return null;
    }

    //\\ VillagePiece::generateAndAddRoadPiece(StartPiece *,std::vector<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>,std::allocator<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>>> &,Random &,int,int,int,int,int)
    private static void generateAndAddRoadPiece(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
        if (genDepth <= 3 + start.size && Math.abs(x - start.getBoundingBox().x0) <= 112 && Math.abs(z - start.getBoundingBox().z0) <= 112) {
            final BoundingBox boundingBox = StraightRoad.findPieceBox(start, pieces, random, x, y, z, orientation);
            if (boundingBox != null && boundingBox.y0 > 10) {
                final StructurePiece piece = new StraightRoad(start, genDepth, random, boundingBox, orientation);
                pieces.add(piece);
                start.pendingRoads.add(piece);
            }
        }
    }

    public static class PieceWeight {

        public final int weight;
        public final Class<? extends VillagePiece> pieceClass;
        public final int maxPlaceCount;
        public int placeCount;

        public PieceWeight(final Class<? extends VillagePiece> pieceClass, final int weight, final int maxPlaceCount) {
            this.pieceClass = pieceClass;
            this.weight = weight;
            this.maxPlaceCount = maxPlaceCount;
        }

        public boolean doPlace(final int genDepth) {
            return maxPlaceCount == 0 || placeCount < maxPlaceCount;
        }

        public boolean isValid() {
            return maxPlaceCount == 0 || placeCount < maxPlaceCount;
        }
    }

    abstract static class VillagePiece extends StructurePiece {
        protected int horizPos = -1;
        protected PopulatorVillage.Type type;
        protected boolean isZombieVillage;
        protected int yOffset;

        protected VillagePiece(final StartPiece start, final int genDepth) {
            super(genDepth);

            if (start != null) {
                type = start.type;
                isZombieVillage = start.isZombieVillage;
                yOffset = start.yOffset;
            } else {
                type = PopulatorVillage.Type.PLAINS;
            }
        }

        protected static boolean isOkBox(final BoundingBox boundingBox) {
            return boundingBox != null && boundingBox.y0 > 10;
        }

        protected StructurePiece generateChildLeft(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int yOffset, final int horizontalOffset) {
            final BlockFace orientation = getOrientation();
            if (orientation != null) {
                return switch (orientation) {
                    case WEST, EAST -> generatePieceFromSmallDoor(start, pieces, random, boundingBox.x0 + horizontalOffset, boundingBox.y0 + yOffset, boundingBox.z0 - 1, BlockFace.NORTH, getGenDepth());
                    default -> generatePieceFromSmallDoor(start, pieces, random, boundingBox.x0 - 1, boundingBox.y0 + yOffset, boundingBox.z0 + horizontalOffset, BlockFace.WEST, getGenDepth());
                };
            }
            return null;
        }

        protected StructurePiece generateChildRight(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int yOffset, final int horizontalOffset) {
            final BlockFace orientation = getOrientation();
            if (orientation != null) {
                return switch (orientation) {
                    case WEST, EAST -> generatePieceFromSmallDoor(start, pieces, random, boundingBox.x0 + horizontalOffset, boundingBox.y0 + yOffset, boundingBox.z1 + 1, BlockFace.SOUTH, getGenDepth());
                    default -> generatePieceFromSmallDoor(start, pieces, random, boundingBox.x1 + 1, boundingBox.y0 + yOffset, boundingBox.z0 + horizontalOffset, BlockFace.EAST, getGenDepth());
                };
            }
            return null;
        }

        //\\ VillagePiece::getAverageGroundHeight(BlockSource *,BoundingBox const &)
        protected int getAverageGroundHeight(final ChunkManager level, final BoundingBox boundingBox) {
            int sum = 0;
            int count = 0;
            final BlockVector3 vec = new BlockVector3();

            for (int x = this.boundingBox.x0; x <= this.boundingBox.x1; ++x) {
                for (int z = this.boundingBox.z0; z <= this.boundingBox.z1; ++z) {
                    vec.setComponents(x, 64 + yOffset, z);

                    if (boundingBox.isInside(vec)) {
                        final BaseFullChunk chunk = level.getChunk(x >> 4, z >> 4);
                        if (chunk == null) {
                            sum += 63 + 1 - 1 + yOffset;
                        } else {
                            final int cx = x & 0xf;
                            final int cz = z & 0xf;
                            int y = chunk.getHighestBlockAt(cx, cz);
                            int id = chunk.getBlockId(cx, y, cz);
                            while (Block.transparent[id] && y > 63 + 1 - 1 + yOffset) {
                                id = chunk.getBlockId(cx, --y, cz);
                            }
                            sum += Math.max(y, 63 + 1 - 1 + yOffset);
                        }
                        ++count;
                    }
                }
            }

            if (count == 0) {
                return -1;
            }
            return sum / count;
        }

        //\\ VillagePiece::spawnVillagers(BlockSource *,BoundingBox const &,int,int,int,int)
        protected void spawnVillagers(final ChunkManager level, final BoundingBox boundingBox, final int x, final int y, final int z, final int maxVillagerCount) {
            // REMOVED
            /*if (villagerCount < maxVillagerCount) {
                for (int count = villagerCount; count < maxVillagerCount; ++count) {
                    final int worldX = getWorldX(x + count, z);
                    final int worldY = getWorldY(y);
                    final int worldZ = getWorldZ(x + count, z);

                    if (!boundingBox.isInside(new BlockVector3(worldX, worldY, worldZ))) {
                        break;
                    }

                    ++villagerCount;

                    final BaseFullChunk chunk = level.getChunk(worldX >> 4, worldZ >> 4);
                    if (chunk != null) {
                        final CompoundTag nbt = Entity.getDefaultNBT(new Vector3(worldX + .5, worldY, worldZ + .5));

                        if (isZombieVillage) {
                            nbt.putString("id", "ZombieVillager")
                                .putInt("Profession", getVillagerProfession(count, EntityVillager.PROFESSION_FARMER));
                        } else {
                            nbt.putString("id", "Villager")
                                .putInt("Profession", getVillagerProfession(count, ThreadLocalRandom.current().nextInt(6)));
                        }

                        Server.getInstance().getScheduler().scheduleTask(new ActorSpawnTask(chunk.getProvider().getLevel(), nbt));
                    }
                }
            }*/
        }

        protected int getVillagerProfession(final int villagerCount, final int profession) {
            return profession;
        }

        protected BlockState getSpecificBlock(final BlockState block) {
            switch (type) {
                case DESERT -> {
                    switch (block.getId()) {
                        case BlockID.LOG, BlockID.LOG2, BlockID.COBBLESTONE, BlockID.GRAVEL -> {
                            return SANDSTONE;
                        }
                        case BlockID.PLANKS -> {
                            return SMOOTH_SANDSTONE;
                        }
                        case BlockID.WOOD_STAIRS, BlockID.COBBLESTONE_STAIRS -> {
                            return new BlockState(Block.SANDSTONE_STAIRS, block.getMeta());
                        }
                    }
                }
                case TAIGA, COLD -> {
                    switch (block.getId()) {
                        case BlockID.LOG, BlockID.LOG2 -> {
                            return switch (block.getMeta() | 0b11) {
                                case 0b111 -> new BlockState(Block.LOG, BlockWood.SPRUCE | 0b101);
                                case 0b1011 -> new BlockState(Block.LOG, BlockWood.SPRUCE | 0b1001);
                                default -> new BlockState(Block.LOG, BlockWood.SPRUCE);
                            };
                        }
                        case BlockID.PLANKS -> {
                            return SPRUCE_PLANKS;
                        }
                        case BlockID.WOOD_STAIRS -> {
                            return new BlockState(Block.SPRUCE_WOOD_STAIRS, block.getMeta());
                        }
                        case BlockID.FENCE -> {
                            return SPRUCE_FENCE;
                        }
                    }
                }
                case SAVANNA -> {
                    switch (block.getId()) {
                        case BlockID.LOG, BlockID.LOG2 -> {
                            return switch (block.getMeta() | 0b11) {
                                case 0b111 -> new BlockState(Block.LOG2, BlockWood2.ACACIA | 0b101);
                                case 0b1011 -> new BlockState(Block.LOG2, BlockWood2.ACACIA | 0b1001);
                                default -> new BlockState(Block.LOG2, BlockWood2.ACACIA);
                            };
                        }
                        case BlockID.PLANKS -> {
                            return ACACIA_PLANKS;
                        }
                        case BlockID.WOOD_STAIRS -> {
                            return new BlockState(Block.ACACIA_WOODEN_STAIRS, block.getMeta());
                        }
                        case BlockID.COBBLESTONE -> {
                            return ACACIA_LOG__Y;
                        }
                        case BlockID.FENCE -> {
                            return ACACIA_FENCE;
                        }
                    }
                }
            }
            return block;
        }

        protected BlockState getDoorBlock() {
            return switch (type) {
                case SAVANNA -> ACACIA_DOOR;
                case TAIGA, COLD -> SPRUCE_DOOR;
                default -> OAK_DOOR;
            };
        }

        protected void placeDoor(final ChunkManager level, final BoundingBox boundingBox, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation) {
            if (!isZombieVillage) {
                generateDoor(level, boundingBox, random, x, y, z, BlockFace.NORTH, getDoorBlock());
            }
        }

        protected void placeTorch(final ChunkManager level, final BlockFace orientation, final int x, final int y, final int z, final BoundingBox boundingBox) {
            if (!isZombieVillage) {
                switch (orientation) {
                    case SOUTH -> placeBlock(level, new BlockState(Block.TORCH, TorchFacingDirection.SOUTH), x, y, z, boundingBox);
                    case EAST -> placeBlock(level, new BlockState(Block.TORCH, TorchFacingDirection.EAST), x, y, z, boundingBox);
                    case WEST -> placeBlock(level, new BlockState(Block.TORCH, TorchFacingDirection.WEST), x, y, z, boundingBox);
                    default -> placeBlock(level, new BlockState(Block.TORCH, TorchFacingDirection.NORTH), x, y, z, boundingBox);
                }
            }
        }

        @Override
        protected void fillColumnDown(final ChunkManager level, final BlockState block, final int x, final int y, final int z, final BoundingBox boundingBox) {
            super.fillColumnDown(level, getSpecificBlock(block), x, y, z, boundingBox);
        }

        protected void setType(final PopulatorVillage.Type type) {
            this.type = type;
        }
    }

    public abstract static class Road extends VillagePiece {
        protected Road(final StartPiece start, final int genDepth) {
            super(start, genDepth);
        }
    }

    public static class StartPiece extends Well {
        public final List<PieceWeight> availablePieces;
        public final List<StructurePiece> pendingHouses = Lists.newArrayList();
        public final List<StructurePiece> pendingRoads = Lists.newArrayList();
        public final ChunkManager world;
        public final int size;
        public PieceWeight previousPiece;

        //\\ VillageStart::VillageStart(BiomeSource *,Random &,int,int,int)
        public StartPiece(final ChunkManager level, final int genDepth, final NukkitRandom random, final int x, final int z, final List<PieceWeight> availablePieces, final int size) {
            super(null, 0, random, x, z);
            world = level;
            this.availablePieces = availablePieces;
            this.size = size;

            final BaseFullChunk chunk = level.getChunk(x >> 4, z >> 4);
            if (chunk != null) {
                final int biome = chunk.getBiomeId(x & 0xf, z & 0xf);

                if (biome == EnumBiome.DESERT.id) {
                    setType(PopulatorVillage.Type.DESERT);
                } else if (biome == EnumBiome.SAVANNA.id) {
                    setType(PopulatorVillage.Type.SAVANNA);
                } else if (biome == EnumBiome.TAIGA.id) {
                    setType(PopulatorVillage.Type.TAIGA);
                }
            }

            isZombieVillage = random.nextBoundedInt(50) == 0;

            yOffset = 2;
        }

        @Override
        public String getType() {
            return "ViStart";
        }
    }

    public static class Well extends VillagePiece {
        public Well(final StartPiece start, final int genDepth, final NukkitRandom random, final int x, final int z) {
            super(start, genDepth);
            setOrientation(BlockFace.Plane.HORIZONTAL.random(random));
            boundingBox = new BoundingBox(x, 64, z, x + 6 - 1, 78, z + 6 - 1);
        }

        @Override
        public String getType() {
            return "ViW";
        }

        @Override
        public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
            generateAndAddRoadPiece((StartPiece) piece, pieces, random, boundingBox.x0 - 1, boundingBox.y1 - 4, boundingBox.z0 + 1, BlockFace.WEST, getGenDepth());
            generateAndAddRoadPiece((StartPiece) piece, pieces, random, boundingBox.x1 + 1, boundingBox.y1 - 4, boundingBox.z0 + 1, BlockFace.EAST, getGenDepth());
            generateAndAddRoadPiece((StartPiece) piece, pieces, random, boundingBox.x0 + 1, boundingBox.y1 - 4, boundingBox.z0 - 1, BlockFace.NORTH, getGenDepth());
            generateAndAddRoadPiece((StartPiece) piece, pieces, random, boundingBox.x0 + 1, boundingBox.y1 - 4, boundingBox.z1 + 1, BlockFace.SOUTH, getGenDepth());
        }

        @Override
        public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
            if (horizPos < 0) {
                horizPos = getAverageGroundHeight(level, boundingBox);

                if (horizPos < 0) {
                    return true;
                }

                this.boundingBox.move(0, horizPos - this.boundingBox.y1 + 3, 0);
            }

            final BlockState cobble = getSpecificBlock(COBBLESTONE);
            final BlockState fence = getSpecificBlock(OAK_FENCE);

            generateBox(level, boundingBox, 1, 0, 1, 4, 12, 4, cobble, FLOWING_WATER, false);
            placeBlock(level, BlockState.AIR, 2, 12, 2, boundingBox);
            placeBlock(level, BlockState.AIR, 3, 12, 2, boundingBox);
            placeBlock(level, BlockState.AIR, 2, 12, 3, boundingBox);
            placeBlock(level, BlockState.AIR, 3, 12, 3, boundingBox);
            placeBlock(level, fence, 1, 13, 1, boundingBox);
            placeBlock(level, fence, 1, 14, 1, boundingBox);
            placeBlock(level, fence, 4, 13, 1, boundingBox);
            placeBlock(level, fence, 4, 14, 1, boundingBox);
            placeBlock(level, fence, 1, 13, 4, boundingBox);
            placeBlock(level, fence, 1, 14, 4, boundingBox);
            placeBlock(level, fence, 4, 13, 4, boundingBox);
            placeBlock(level, fence, 4, 14, 4, boundingBox);
            generateBox(level, boundingBox, 1, 15, 1, 4, 15, 4, cobble, cobble, false);

            for (int x = 0; x <= 5; ++x) {
                for (int z = 0; z <= 5; ++z) {
                    if (x == 0 || x == 5 || z == 0 || z == 5) {
                        placeBlock(level, cobble, x, 11, z, boundingBox);
                        fillAirColumnUp(level, x, 12, z, boundingBox);
                    }
                }
            }

            return true;
        }
    }

    public static class SimpleHouse extends VillagePiece {

        private final boolean hasTerrace;

        public SimpleHouse(final StartPiece start, final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
            super(start, genDepth);
            setOrientation(orientation);
            this.boundingBox = boundingBox;
            hasTerrace = random.nextBoolean();
        }

        public static SimpleHouse createPiece(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
            final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, 0, 0, 0, 5, 6, 5, orientation);
            return StructurePiece.findCollisionPiece(pieces, boundingBox) != null ? null : new SimpleHouse(start, genDepth, random, boundingBox, orientation);
        }

        @Override
        public String getType() {
            return "ViSH";
        }

        @Override
        public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
            if (horizPos < 0) {
                horizPos = getAverageGroundHeight(level, boundingBox);

                if (horizPos < 0) {
                    return true;
                }

                this.boundingBox.move(0, horizPos - this.boundingBox.y1 + 6 - 1, 0);
            }

            final BlockState cobble = getSpecificBlock(COBBLESTONE);
            final BlockState planks = getSpecificBlock(PLANKS);
            final BlockState stairsN = getSpecificBlock(COBBLESTONE_STAIRS__N);
            final BlockState log = getSpecificBlock(LOG);
            final BlockState fence = getSpecificBlock(OAK_FENCE);

            generateBox(level, boundingBox, 0, 0, 0, 4, 0, 4, cobble, cobble, false);
            generateBox(level, boundingBox, 0, 4, 0, 4, 4, 4, log, log, false);
            generateBox(level, boundingBox, 1, 4, 1, 3, 4, 3, planks, planks, false);
            placeBlock(level, cobble, 0, 1, 0, boundingBox);
            placeBlock(level, cobble, 0, 2, 0, boundingBox);
            placeBlock(level, cobble, 0, 3, 0, boundingBox);
            placeBlock(level, cobble, 4, 1, 0, boundingBox);
            placeBlock(level, cobble, 4, 2, 0, boundingBox);
            placeBlock(level, cobble, 4, 3, 0, boundingBox);
            placeBlock(level, cobble, 0, 1, 4, boundingBox);
            placeBlock(level, cobble, 0, 2, 4, boundingBox);
            placeBlock(level, cobble, 0, 3, 4, boundingBox);
            placeBlock(level, cobble, 4, 1, 4, boundingBox);
            placeBlock(level, cobble, 4, 2, 4, boundingBox);
            placeBlock(level, cobble, 4, 3, 4, boundingBox);
            generateBox(level, boundingBox, 0, 1, 1, 0, 3, 3, planks, planks, false);
            generateBox(level, boundingBox, 4, 1, 1, 4, 3, 3, planks, planks, false);
            generateBox(level, boundingBox, 1, 1, 4, 3, 3, 4, planks, planks, false);
            placeBlock(level, GLASS_PANE, 0, 2, 2, boundingBox);
            placeBlock(level, GLASS_PANE, 2, 2, 4, boundingBox);
            placeBlock(level, GLASS_PANE, 4, 2, 2, boundingBox);
            placeBlock(level, planks, 1, 1, 0, boundingBox);
            placeBlock(level, planks, 1, 2, 0, boundingBox);
            placeBlock(level, planks, 1, 3, 0, boundingBox);
            placeBlock(level, planks, 2, 3, 0, boundingBox);
            placeBlock(level, planks, 3, 3, 0, boundingBox);
            placeBlock(level, planks, 3, 2, 0, boundingBox);
            placeBlock(level, planks, 3, 1, 0, boundingBox);

            if (getBlock(level, 2, 0, -1, boundingBox).equals(BlockState.AIR) && !getBlock(level, 2, -1, -1, boundingBox).equals(BlockState.AIR)) {
                placeBlock(level, stairsN, 2, 0, -1, boundingBox);

                if (getBlock(level, 2, -1, -1, boundingBox).getId() == BlockID.GRASS_PATH) {
                    placeBlock(level, GRASS, 2, -1, -1, boundingBox);
                }
            }

            generateBox(level, boundingBox, 1, 1, 1, 3, 3, 3, BlockState.AIR, BlockState.AIR, false);

            if (hasTerrace) {
                placeBlock(level, fence, 0, 5, 0, boundingBox);
                placeBlock(level, fence, 1, 5, 0, boundingBox);
                placeBlock(level, fence, 2, 5, 0, boundingBox);
                placeBlock(level, fence, 3, 5, 0, boundingBox);
                placeBlock(level, fence, 4, 5, 0, boundingBox);
                placeBlock(level, fence, 0, 5, 4, boundingBox);
                placeBlock(level, fence, 1, 5, 4, boundingBox);
                placeBlock(level, fence, 2, 5, 4, boundingBox);
                placeBlock(level, fence, 3, 5, 4, boundingBox);
                placeBlock(level, fence, 4, 5, 4, boundingBox);
                placeBlock(level, fence, 4, 5, 1, boundingBox);
                placeBlock(level, fence, 4, 5, 2, boundingBox);
                placeBlock(level, fence, 4, 5, 3, boundingBox);
                placeBlock(level, fence, 0, 5, 1, boundingBox);
                placeBlock(level, fence, 0, 5, 2, boundingBox);
                placeBlock(level, fence, 0, 5, 3, boundingBox);
            }

            if (hasTerrace) {
                final BlockState ladderS = LADDER__S;
                placeBlock(level, ladderS, 3, 1, 3, boundingBox);
                placeBlock(level, ladderS, 3, 2, 3, boundingBox);
                placeBlock(level, ladderS, 3, 3, 3, boundingBox);
                placeBlock(level, ladderS, 3, 4, 3, boundingBox);
            }

            placeTorch(level, BlockFace.NORTH, 2, 3, 1, boundingBox);

            for (int x = 0; x < 5; ++x) {
                for (int z = 0; z < 5; ++z) {
                    fillAirColumnUp(level, x, 6, z, boundingBox);
                    fillColumnDown(level, cobble, x, -1, z, boundingBox);
                }
            }

            spawnVillagers(level, boundingBox, 1, 1, 2, 1);
            return true;
        }
    }

    public static class SmallTemple extends VillagePiece {
        public SmallTemple(final StartPiece start, final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
            super(start, genDepth);
            setOrientation(orientation);
            this.boundingBox = boundingBox;
        }

        public static SmallTemple createPiece(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
            final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, 0, 0, 0, 5, 12, 9, orientation);
            return isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ? new SmallTemple(start, genDepth, random, boundingBox, orientation) : null;
        }

        @Override
        public String getType() {
            return "ViST";
        }

        @Override
        public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
            if (horizPos < 0) {
                horizPos = getAverageGroundHeight(level, boundingBox);

                if (horizPos < 0) {
                    return true;
                }

                this.boundingBox.move(0, horizPos - this.boundingBox.y1 + 12 - 1, 0);
            }

            final BlockState cobble = COBBLESTONE;
            final BlockState stairsN = getSpecificBlock(COBBLESTONE_STAIRS__N);
            final BlockState stairsW = getSpecificBlock(COBBLESTONE_STAIRS__W);
            final BlockState stairsE = getSpecificBlock(COBBLESTONE_STAIRS__E);

            generateBox(level, boundingBox, 1, 1, 1, 3, 3, 7, BlockState.AIR, BlockState.AIR, false);
            generateBox(level, boundingBox, 1, 5, 1, 3, 9, 3, BlockState.AIR, BlockState.AIR, false);
            generateBox(level, boundingBox, 1, 0, 0, 3, 0, 8, cobble, cobble, false);
            generateBox(level, boundingBox, 1, 1, 0, 3, 10, 0, cobble, cobble, false);
            generateBox(level, boundingBox, 0, 1, 1, 0, 10, 3, cobble, cobble, false);
            generateBox(level, boundingBox, 4, 1, 1, 4, 10, 3, cobble, cobble, false);
            generateBox(level, boundingBox, 0, 0, 4, 0, 4, 7, cobble, cobble, false);
            generateBox(level, boundingBox, 4, 0, 4, 4, 4, 7, cobble, cobble, false);
            generateBox(level, boundingBox, 1, 1, 8, 3, 4, 8, cobble, cobble, false);
            generateBox(level, boundingBox, 1, 5, 4, 3, 10, 4, cobble, cobble, false);
            generateBox(level, boundingBox, 1, 5, 5, 3, 5, 7, cobble, cobble, false);
            generateBox(level, boundingBox, 0, 9, 0, 4, 9, 4, cobble, cobble, false);
            generateBox(level, boundingBox, 0, 4, 0, 4, 4, 4, cobble, cobble, false);
            placeBlock(level, cobble, 0, 11, 2, boundingBox);
            placeBlock(level, cobble, 4, 11, 2, boundingBox);
            placeBlock(level, cobble, 2, 11, 0, boundingBox);
            placeBlock(level, cobble, 2, 11, 4, boundingBox);
            placeBlock(level, cobble, 1, 1, 6, boundingBox);
            placeBlock(level, cobble, 1, 1, 7, boundingBox);
            placeBlock(level, cobble, 2, 1, 7, boundingBox);
            placeBlock(level, cobble, 3, 1, 6, boundingBox);
            placeBlock(level, cobble, 3, 1, 7, boundingBox);
            placeBlock(level, stairsN, 1, 1, 5, boundingBox);
            placeBlock(level, stairsN, 2, 1, 6, boundingBox);
            placeBlock(level, stairsN, 3, 1, 5, boundingBox);
            placeBlock(level, stairsW, 1, 2, 7, boundingBox);
            placeBlock(level, stairsE, 3, 2, 7, boundingBox);
            placeBlock(level, GLASS_PANE, 0, 2, 2, boundingBox);
            placeBlock(level, GLASS_PANE, 0, 3, 2, boundingBox);
            placeBlock(level, GLASS_PANE, 4, 2, 2, boundingBox);
            placeBlock(level, GLASS_PANE, 4, 3, 2, boundingBox);
            placeBlock(level, GLASS_PANE, 0, 6, 2, boundingBox);
            placeBlock(level, GLASS_PANE, 0, 7, 2, boundingBox);
            placeBlock(level, GLASS_PANE, 4, 6, 2, boundingBox);
            placeBlock(level, GLASS_PANE, 4, 7, 2, boundingBox);
            placeBlock(level, GLASS_PANE, 2, 6, 0, boundingBox);
            placeBlock(level, GLASS_PANE, 2, 7, 0, boundingBox);
            placeBlock(level, GLASS_PANE, 2, 6, 4, boundingBox);
            placeBlock(level, GLASS_PANE, 2, 7, 4, boundingBox);
            placeBlock(level, GLASS_PANE, 0, 3, 6, boundingBox);
            placeBlock(level, GLASS_PANE, 4, 3, 6, boundingBox);
            placeBlock(level, GLASS_PANE, 2, 3, 8, boundingBox);
            placeTorch(level, BlockFace.SOUTH, 2, 4, 7, boundingBox);
            placeTorch(level, BlockFace.EAST, 1, 4, 6, boundingBox);
            placeTorch(level, BlockFace.WEST, 3, 4, 6, boundingBox);
            placeTorch(level, BlockFace.NORTH, 2, 4, 5, boundingBox);

            for (int y = 1; y <= 9; ++y) {
                placeBlock(level, LADDER__W, 3, y, 3, boundingBox);
            }

            placeBlock(level, BlockState.AIR, 2, 1, 0, boundingBox);
            placeBlock(level, BlockState.AIR, 2, 2, 0, boundingBox);
            placeDoor(level, boundingBox, random, 2, 1, 0, BlockFace.NORTH);

            if (getBlock(level, 2, 0, -1, boundingBox).equals(BlockState.AIR) && !getBlock(level, 2, -1, -1, boundingBox).equals(BlockState.AIR)) {
                placeBlock(level, stairsN, 2, 0, -1, boundingBox);

                if (getBlock(level, 2, -1, -1, boundingBox).getId() == BlockID.GRASS_PATH) {
                    placeBlock(level, GRASS, 2, -1, -1, boundingBox);
                }
            }

            for (int x = 0; x < 5; ++x) {
                for (int z = 0; z < 9; ++z) {
                    fillAirColumnUp(level, x, 12, z, boundingBox);
                    fillColumnDown(level, cobble, x, -1, z, boundingBox);
                }
            }

            spawnVillagers(level, boundingBox, 2, 1, 2, 1);
            return true;
        }

        @Override
        protected int getVillagerProfession(final int villagerCount, final int profession) {
            return EntityVillager.PROFESSION_PRIEST;
        }
    }

    public static class BookHouse extends VillagePiece {
        public BookHouse(final StartPiece start, final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
            super(start, genDepth);
            setOrientation(orientation);
            this.boundingBox = boundingBox;
        }

        public static BookHouse createPiece(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
            final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, 0, 0, 0, 9, 9, 6, orientation);
            return isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ? new BookHouse(start, genDepth, random, boundingBox, orientation) : null;
        }

        @Override
        public String getType() {
            return "ViBH";
        }

        @Override
        public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
            if (horizPos < 0) {
                horizPos = getAverageGroundHeight(level, boundingBox);

                if (horizPos < 0) {
                    return true;
                }

                this.boundingBox.move(0, horizPos - this.boundingBox.y1 + 9 - 1, 0);
            }

            final BlockState cobble = getSpecificBlock(COBBLESTONE);
            final BlockState stairsN = getSpecificBlock(OAK_STAIRS__N);
            final BlockState stairsS = getSpecificBlock(OAK_STAIRS__S);
            final BlockState stairsE = getSpecificBlock(OAK_STAIRS__E);
            final BlockState planks = getSpecificBlock(PLANKS);
            final BlockState cobbleStairsN = getSpecificBlock(COBBLESTONE_STAIRS__N);
            final BlockState fence = getSpecificBlock(OAK_FENCE);

            generateBox(level, boundingBox, 1, 1, 1, 7, 5, 4, BlockState.AIR, BlockState.AIR, false);
            generateBox(level, boundingBox, 0, 0, 0, 8, 0, 5, cobble, cobble, false);
            generateBox(level, boundingBox, 0, 5, 0, 8, 5, 5, cobble, cobble, false);
            generateBox(level, boundingBox, 0, 6, 1, 8, 6, 4, cobble, cobble, false);
            generateBox(level, boundingBox, 0, 7, 2, 8, 7, 3, cobble, cobble, false);

            for (int i = -1; i <= 2; ++i) {
                for (int x = 0; x <= 8; ++x) {
                    placeBlock(level, stairsN, x, 6 + i, i, boundingBox);
                    placeBlock(level, stairsS, x, 6 + i, 5 - i, boundingBox);
                }
            }

            generateBox(level, boundingBox, 0, 1, 0, 0, 1, 5, cobble, cobble, false);
            generateBox(level, boundingBox, 1, 1, 5, 8, 1, 5, cobble, cobble, false);
            generateBox(level, boundingBox, 8, 1, 0, 8, 1, 4, cobble, cobble, false);
            generateBox(level, boundingBox, 2, 1, 0, 7, 1, 0, cobble, cobble, false);
            generateBox(level, boundingBox, 0, 2, 0, 0, 4, 0, cobble, cobble, false);
            generateBox(level, boundingBox, 0, 2, 5, 0, 4, 5, cobble, cobble, false);
            generateBox(level, boundingBox, 8, 2, 5, 8, 4, 5, cobble, cobble, false);
            generateBox(level, boundingBox, 8, 2, 0, 8, 4, 0, cobble, cobble, false);
            generateBox(level, boundingBox, 0, 2, 1, 0, 4, 4, planks, planks, false);
            generateBox(level, boundingBox, 1, 2, 5, 7, 4, 5, planks, planks, false);
            generateBox(level, boundingBox, 8, 2, 1, 8, 4, 4, planks, planks, false);
            generateBox(level, boundingBox, 1, 2, 0, 7, 4, 0, planks, planks, false);
            placeBlock(level, GLASS_PANE, 4, 2, 0, boundingBox);
            placeBlock(level, GLASS_PANE, 5, 2, 0, boundingBox);
            placeBlock(level, GLASS_PANE, 6, 2, 0, boundingBox);
            placeBlock(level, GLASS_PANE, 4, 3, 0, boundingBox);
            placeBlock(level, GLASS_PANE, 5, 3, 0, boundingBox);
            placeBlock(level, GLASS_PANE, 6, 3, 0, boundingBox);
            placeBlock(level, GLASS_PANE, 0, 2, 2, boundingBox);
            placeBlock(level, GLASS_PANE, 0, 2, 3, boundingBox);
            placeBlock(level, GLASS_PANE, 0, 3, 2, boundingBox);
            placeBlock(level, GLASS_PANE, 0, 3, 3, boundingBox);
            placeBlock(level, GLASS_PANE, 8, 2, 2, boundingBox);
            placeBlock(level, GLASS_PANE, 8, 2, 3, boundingBox);
            placeBlock(level, GLASS_PANE, 8, 3, 2, boundingBox);
            placeBlock(level, GLASS_PANE, 8, 3, 3, boundingBox);
            placeBlock(level, GLASS_PANE, 2, 2, 5, boundingBox);
            placeBlock(level, GLASS_PANE, 3, 2, 5, boundingBox);
            placeBlock(level, GLASS_PANE, 5, 2, 5, boundingBox);
            placeBlock(level, GLASS_PANE, 6, 2, 5, boundingBox);
            generateBox(level, boundingBox, 1, 4, 1, 7, 4, 1, planks, planks, false);
            generateBox(level, boundingBox, 1, 4, 4, 7, 4, 4, planks, planks, false);
            generateBox(level, boundingBox, 1, 3, 4, 7, 3, 4, BOOKSHELF, BOOKSHELF, false);
            placeBlock(level, planks, 7, 1, 4, boundingBox);
            placeBlock(level, stairsE, 7, 1, 3, boundingBox);
            placeBlock(level, stairsN, 6, 1, 4, boundingBox);
            placeBlock(level, stairsN, 5, 1, 4, boundingBox);
            placeBlock(level, stairsN, 4, 1, 4, boundingBox);
            placeBlock(level, stairsN, 3, 1, 4, boundingBox);
            placeBlock(level, fence, 6, 1, 3, boundingBox);
            placeBlock(level, BROWN_CARPET, 6, 2, 3, boundingBox);
            placeBlock(level, fence, 4, 1, 3, boundingBox);
            placeBlock(level, BROWN_CARPET, 4, 2, 3, boundingBox);
            placeBlock(level, CRAFTING_TABLE, 7, 1, 1, boundingBox);
            placeBlock(level, BlockState.AIR, 1, 1, 0, boundingBox);
            placeBlock(level, BlockState.AIR, 1, 2, 0, boundingBox);
            placeDoor(level, boundingBox, random, 1, 1, 0, BlockFace.NORTH);

            if (getBlock(level, 1, 0, -1, boundingBox).equals(BlockState.AIR) && !getBlock(level, 1, -1, -1, boundingBox).equals(BlockState.AIR)) {
                placeBlock(level, cobbleStairsN, 1, 0, -1, boundingBox);

                if (getBlock(level, 1, -1, -1, boundingBox).getId() == BlockID.GRASS_PATH) {
                    placeBlock(level, GRASS, 1, -1, -1, boundingBox);
                }
            }

            for (int x = 0; x < 9; ++x) {
                for (int z = 0; z < 6; ++z) {
                    fillAirColumnUp(level, x, 9, z, boundingBox);
                    fillColumnDown(level, cobble, x, -1, z, boundingBox);
                }
            }

            spawnVillagers(level, boundingBox, 2, 1, 2, 1);
            return true;
        }

        @Override
        protected int getVillagerProfession(final int villagerCount, final int profession) {
            return EntityVillager.PROFESSION_LIBRARIAN;
        }
    }

    public static class SmallHut extends VillagePiece {
        private final boolean hasCompoundRoof;
        private final int tablePos;

        public SmallHut(final StartPiece start, final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
            super(start, genDepth);
            setOrientation(orientation);
            this.boundingBox = boundingBox;
            hasCompoundRoof = random.nextBoolean();
            tablePos = random.nextBoundedInt(3);
        }

        //\\ SmallHut::createPiece(StartPiece *,std::vector<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>,std::allocator<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>>> &,Random &,int,int,int,int,int)
        public static SmallHut createPiece(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
            final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, 0, 0, 0, 4, 6, 5, orientation);
            return isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ? new SmallHut(start, genDepth, random, boundingBox, orientation) : null;
        }

        @Override
        public String getType() {
            return "ViSmH";
        }

        @Override
        public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
            if (horizPos < 0) {
                horizPos = getAverageGroundHeight(level, boundingBox);

                if (horizPos < 0) {
                    return true;
                }

                this.boundingBox.move(0, horizPos - this.boundingBox.y1 + 6 - 1, 0);
            }

            final BlockState cobble = getSpecificBlock(COBBLESTONE);
            final BlockState planks = getSpecificBlock(PLANKS);
            final BlockState stairsN = getSpecificBlock(COBBLESTONE_STAIRS__N);
            final BlockState log = getSpecificBlock(LOG);
            final BlockState fence = getSpecificBlock(OAK_FENCE);

            generateBox(level, boundingBox, 1, 1, 1, 3, 5, 4, BlockState.AIR, BlockState.AIR, false);
            generateBox(level, boundingBox, 0, 0, 0, 3, 0, 4, cobble, cobble, false);
            generateBox(level, boundingBox, 1, 0, 1, 2, 0, 3, DIRT, DIRT, false);

            if (hasCompoundRoof) {
                generateBox(level, boundingBox, 1, 4, 1, 2, 4, 3, log, log, false);
            } else {
                generateBox(level, boundingBox, 1, 5, 1, 2, 5, 3, log, log, false);
            }

            placeBlock(level, log, 1, 4, 0, boundingBox);
            placeBlock(level, log, 2, 4, 0, boundingBox);
            placeBlock(level, log, 1, 4, 4, boundingBox);
            placeBlock(level, log, 2, 4, 4, boundingBox);
            placeBlock(level, log, 0, 4, 1, boundingBox);
            placeBlock(level, log, 0, 4, 2, boundingBox);
            placeBlock(level, log, 0, 4, 3, boundingBox);
            placeBlock(level, log, 3, 4, 1, boundingBox);
            placeBlock(level, log, 3, 4, 2, boundingBox);
            placeBlock(level, log, 3, 4, 3, boundingBox);
            generateBox(level, boundingBox, 0, 1, 0, 0, 3, 0, log, log, false);
            generateBox(level, boundingBox, 3, 1, 0, 3, 3, 0, log, log, false);
            generateBox(level, boundingBox, 0, 1, 4, 0, 3, 4, log, log, false);
            generateBox(level, boundingBox, 3, 1, 4, 3, 3, 4, log, log, false);
            generateBox(level, boundingBox, 0, 1, 1, 0, 3, 3, planks, planks, false);
            generateBox(level, boundingBox, 3, 1, 1, 3, 3, 3, planks, planks, false);
            generateBox(level, boundingBox, 1, 1, 0, 2, 3, 0, planks, planks, false);
            generateBox(level, boundingBox, 1, 1, 4, 2, 3, 4, planks, planks, false);
            placeBlock(level, GLASS_PANE, 0, 2, 2, boundingBox);
            placeBlock(level, GLASS_PANE, 3, 2, 2, boundingBox);

            if (tablePos > 0) {
                placeBlock(level, fence, tablePos, 1, 3, boundingBox);
                placeBlock(level, BROWN_CARPET, tablePos, 2, 3, boundingBox);
            }

            placeBlock(level, BlockState.AIR, 1, 1, 0, boundingBox);
            placeBlock(level, BlockState.AIR, 1, 2, 0, boundingBox);
            placeDoor(level, boundingBox, random, 1, 1, 0, BlockFace.NORTH);

            if (getBlock(level, 1, 0, -1, boundingBox).equals(BlockState.AIR) && !getBlock(level, 1, -1, -1, boundingBox).equals(BlockState.AIR)) {
                placeBlock(level, stairsN, 1, 0, -1, boundingBox);

                if (getBlock(level, 1, -1, -1, boundingBox).getId() == BlockID.GRASS_PATH) {
                    placeBlock(level, GRASS, 1, -1, -1, boundingBox);
                }
            }

            for (int x = 0; x < 4; ++x) {
                for (int z = 0; z < 5; ++z) {
                    fillAirColumnUp(level, x, 6, z, boundingBox);
                    fillColumnDown(level, cobble, x, -1, z, boundingBox);
                }
            }

            spawnVillagers(level, boundingBox, 1, 1, 2, 1);
            return true;
        }
    }

    public static class PigHouse extends VillagePiece {
        public PigHouse(final StartPiece start, final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
            super(start, genDepth);
            setOrientation(orientation);
            this.boundingBox = boundingBox;
        }

        public static PigHouse createPiece(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
            final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, 0, 0, 0, 9, 7, 11, orientation);
            return isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ? new PigHouse(start, genDepth, random, boundingBox, orientation) : null;
        }

        @Override
        public String getType() {
        	return "ViPH";
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			if (horizPos < 0) {
				horizPos = getAverageGroundHeight(level, boundingBox);

				if (horizPos < 0) {
					return true;
				}

				this.boundingBox.move(0, horizPos - this.boundingBox.y1 + 7 - 1, 0);
			}

			final BlockState cobble = getSpecificBlock(COBBLESTONE);
			final BlockState stairsN = getSpecificBlock(OAK_STAIRS__N);
			final BlockState stairsS = getSpecificBlock(OAK_STAIRS__S);
			final BlockState stairsW = getSpecificBlock(OAK_STAIRS__W);
			final BlockState planks = getSpecificBlock(PLANKS);
			final BlockState log = getSpecificBlock(LOG);
			final BlockState fence = getSpecificBlock(OAK_FENCE);

			generateBox(level, boundingBox, 1, 1, 1, 7, 4, 4, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 2, 1, 6, 8, 4, 10, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 2, 0, 6, 8, 0, 10, DIRT, DIRT, false);
			placeBlock(level, cobble, 6, 0, 6, boundingBox);
			generateBox(level, boundingBox, 2, 1, 6, 2, 1, 10, fence, fence, false);
			generateBox(level, boundingBox, 8, 1, 6, 8, 1, 10, fence, fence, false);
			generateBox(level, boundingBox, 3, 1, 10, 7, 1, 10, fence, fence, false);
			generateBox(level, boundingBox, 1, 0, 1, 7, 0, 4, planks, planks, false);
			generateBox(level, boundingBox, 0, 0, 0, 0, 3, 5, cobble, cobble, false);
			generateBox(level, boundingBox, 8, 0, 0, 8, 3, 5, cobble, cobble, false);
			generateBox(level, boundingBox, 1, 0, 0, 7, 1, 0, cobble, cobble, false);
			generateBox(level, boundingBox, 1, 0, 5, 7, 1, 5, cobble, cobble, false);
			generateBox(level, boundingBox, 1, 2, 0, 7, 3, 0, planks, planks, false);
			generateBox(level, boundingBox, 1, 2, 5, 7, 3, 5, planks, planks, false);
			generateBox(level, boundingBox, 0, 4, 1, 8, 4, 1, planks, planks, false);
			generateBox(level, boundingBox, 0, 4, 4, 8, 4, 4, planks, planks, false);
			generateBox(level, boundingBox, 0, 5, 2, 8, 5, 3, planks, planks, false);
			placeBlock(level, planks, 0, 4, 2, boundingBox);
			placeBlock(level, planks, 0, 4, 3, boundingBox);
			placeBlock(level, planks, 8, 4, 2, boundingBox);
			placeBlock(level, planks, 8, 4, 3, boundingBox);

			for (int i = -1; i <= 2; ++i) {
				for (int x = 0; x <= 8; ++x) {
					placeBlock(level, stairsN, x, 4 + i, i, boundingBox);
					placeBlock(level, stairsS, x, 4 + i, 5 - i, boundingBox);
				}
			}

			placeBlock(level, log, 0, 2, 1, boundingBox);
			placeBlock(level, log, 0, 2, 4, boundingBox);
			placeBlock(level, log, 8, 2, 1, boundingBox);
			placeBlock(level, log, 8, 2, 4, boundingBox);
			placeBlock(level, GLASS_PANE, 0, 2, 2, boundingBox);
			placeBlock(level, GLASS_PANE, 0, 2, 3, boundingBox);
			placeBlock(level, GLASS_PANE, 8, 2, 2, boundingBox);
			placeBlock(level, GLASS_PANE, 8, 2, 3, boundingBox);
			placeBlock(level, GLASS_PANE, 2, 2, 5, boundingBox);
			placeBlock(level, GLASS_PANE, 3, 2, 5, boundingBox);
			placeBlock(level, GLASS_PANE, 5, 2, 0, boundingBox);
			placeBlock(level, GLASS_PANE, 6, 2, 5, boundingBox);
			placeBlock(level, fence, 2, 1, 3, boundingBox);
			placeBlock(level, BROWN_CARPET, 2, 2, 3, boundingBox);
			placeBlock(level, planks, 1, 1, 4, boundingBox);
			placeBlock(level, stairsN, 2, 1, 4, boundingBox);
			placeBlock(level, stairsW, 1, 1, 3, boundingBox);
			generateBox(level, boundingBox, 5, 0, 1, 7, 0, 3, DOUBLE_STONE_SLAB, DOUBLE_STONE_SLAB, false);
			placeBlock(level, DOUBLE_STONE_SLAB, 6, 1, 1, boundingBox);
			placeBlock(level, DOUBLE_STONE_SLAB, 6, 1, 2, boundingBox);
			placeBlock(level, BlockState.AIR, 2, 1, 0, boundingBox);
			placeBlock(level, BlockState.AIR, 2, 2, 0, boundingBox);
			placeTorch(level, BlockFace.NORTH, 2, 3, 1, boundingBox);
			placeDoor(level, boundingBox, random, 2, 1, 0, BlockFace.NORTH);

			if (getBlock(level, 2, 0, -1, boundingBox).equals(BlockState.AIR) && !getBlock(level, 2, -1, -1, boundingBox).equals(BlockState.AIR)) {
				placeBlock(level, stairsN, 2, 0, -1, boundingBox);

				if (getBlock(level, 2, -1, -1, boundingBox).getId() == BlockID.GRASS_PATH) {
					placeBlock(level, GRASS, 2, -1, -1, boundingBox);
				}
			}

			placeBlock(level, BlockState.AIR, 6, 1, 5, boundingBox);
			placeBlock(level, BlockState.AIR, 6, 2, 5, boundingBox);
			placeTorch(level, BlockFace.SOUTH, 6, 3, 4, boundingBox);
			placeDoor(level, boundingBox, random, 6, 1, 5, BlockFace.SOUTH);

			for (int x = 0; x < 9; ++x) {
				for (int z = 0; z < 5; ++z) {
					fillAirColumnUp(level, x, 7, z, boundingBox);
					fillColumnDown(level, cobble, x, -1, z, boundingBox);
				}
			}

			spawnVillagers(level, boundingBox, 4, 1, 2, 2);
			return true;
		}

		@Override //\\ PigHouse::getVillagerProfession(int)
		protected int getVillagerProfession(final int villagerCount, final int profession) {
			return villagerCount == 0 ? EntityVillager.PROFESSION_BUTCHER : super.getVillagerProfession(villagerCount, profession);
		}
	}

	public static class DoubleFarmland extends VillagePiece {
		private final BlockState cropA;
		private final BlockState cropB;
		private final BlockState cropC;
		private final BlockState cropD;

		public DoubleFarmland(final StartPiece start, final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
			super(start, genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
			cropA = Farmland.selectCrops(random);
			cropB = Farmland.selectCrops(random);
			cropC = Farmland.selectCrops(random);
			cropD = Farmland.selectCrops(random);
		}

		public static DoubleFarmland createPiece(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, 0, 0, 0, 13, 4, 9, orientation);
			return isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ? new DoubleFarmland(start, genDepth, random, boundingBox, orientation) : null;
		}

		@Override
		public String getType() {
			return "ViDF";
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			if (horizPos < 0) {
				horizPos = getAverageGroundHeight(level, boundingBox);

				if (horizPos < 0) {
					return true;
				}

				this.boundingBox.move(0, horizPos - this.boundingBox.y1 + 4 - 1, 0);
			}

			final BlockState log = getSpecificBlock(LOG);

			generateBox(level, boundingBox, 0, 1, 0, 12, 4, 8, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 1, 0, 1, 2, 0, 7, FARMLAND, FARMLAND, false);
			generateBox(level, boundingBox, 4, 0, 1, 5, 0, 7, FARMLAND, FARMLAND, false);
			generateBox(level, boundingBox, 7, 0, 1, 8, 0, 7, FARMLAND, FARMLAND, false);
			generateBox(level, boundingBox, 10, 0, 1, 11, 0, 7, FARMLAND, FARMLAND, false);
			generateBox(level, boundingBox, 0, 0, 0, 0, 0, 8, log, log, false);
			generateBox(level, boundingBox, 6, 0, 0, 6, 0, 8, log, log, false);
			generateBox(level, boundingBox, 12, 0, 0, 12, 0, 8, log, log, false);
			generateBox(level, boundingBox, 1, 0, 0, 11, 0, 0, log, log, false);
			generateBox(level, boundingBox, 1, 0, 8, 11, 0, 8, log, log, false);
			generateBox(level, boundingBox, 3, 0, 1, 3, 0, 7, WATER, WATER, false);
			generateBox(level, boundingBox, 9, 0, 1, 9, 0, 7, WATER, WATER, false);

			if (type != PopulatorVillage.Type.COLD) { //BE
				for (int z = 1; z <= 7; ++z) {
					final int maxAgeA = 7;
					final int minAgeA = maxAgeA / 3;
					placeBlock(level, new BlockState(cropA.getId(), Mth.nextInt(random, minAgeA, maxAgeA)), 1, 1, z, boundingBox);
					placeBlock(level, new BlockState(cropA.getId(), Mth.nextInt(random, minAgeA, maxAgeA)), 2, 1, z, boundingBox);
					final int maxAgeB = 7;
					final int minAgeB = maxAgeB / 3;
					placeBlock(level, new BlockState(cropB.getId(), Mth.nextInt(random, minAgeB, maxAgeB)), 4, 1, z, boundingBox);
					placeBlock(level, new BlockState(cropB.getId(), Mth.nextInt(random, minAgeB, maxAgeB)), 5, 1, z, boundingBox);
					final int maxAgeC = 7;
					final int minAgeC = maxAgeC / 3;
					placeBlock(level, new BlockState(cropC.getId(), Mth.nextInt(random, minAgeC, maxAgeC)), 7, 1, z, boundingBox);
					placeBlock(level, new BlockState(cropC.getId(), Mth.nextInt(random, minAgeC, maxAgeC)), 8, 1, z, boundingBox);
					final int maxAgeD = 7;
					final int minAgeD = maxAgeD / 3;
					placeBlock(level, new BlockState(cropD.getId(), Mth.nextInt(random, minAgeD, maxAgeD)), 10, 1, z, boundingBox);
					placeBlock(level, new BlockState(cropD.getId(), Mth.nextInt(random, minAgeD, maxAgeD)), 11, 1, z, boundingBox);
				}
			}

			for (int x = 0; x < 13; ++x) {
				for (int z = 0; z < 9; ++z) {
					fillAirColumnUp(level, x, 4, z, boundingBox);
					fillColumnDown(level, DIRT, x, -1, z, boundingBox);
				}
			}

			return true;
		}
	}

	public static class Farmland extends VillagePiece {
		private final BlockState cropA;
		private final BlockState cropB;

		public Farmland(final StartPiece start, final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
			super(start, genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
			cropA = selectCrops(random);
			cropB = selectCrops(random);
		}

		//\\ Farmland::selectCrops(Random &,StartPiece &)
		protected static BlockState selectCrops(final NukkitRandom random) {
			return switch (random.nextBoundedInt(10)) {
				case 0, 1 -> CARROTS;
				case 2, 3 -> POTATOES;
				case 4 -> BEETROOTS;
				default -> WHEAT;
			};
		}

		public static Farmland createPiece(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, 0, 0, 0, 7, 4, 9, orientation);
			return isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ? new Farmland(start, genDepth, random, boundingBox, orientation) : null;
		}

		@Override
		public String getType() {
			return "ViF";
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			if (horizPos < 0) {
				horizPos = getAverageGroundHeight(level, boundingBox);

				if (horizPos < 0) {
					return true;
				}

				this.boundingBox.move(0, horizPos - this.boundingBox.y1 + 4 - 1, 0);
			}

			final BlockState log = getSpecificBlock(LOG);

			generateBox(level, boundingBox, 0, 1, 0, 6, 4, 8, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 1, 0, 1, 2, 0, 7, FARMLAND, FARMLAND, false);
			generateBox(level, boundingBox, 4, 0, 1, 5, 0, 7, FARMLAND, FARMLAND, false);
			generateBox(level, boundingBox, 0, 0, 0, 0, 0, 8, log, log, false);
			generateBox(level, boundingBox, 6, 0, 0, 6, 0, 8, log, log, false);
			generateBox(level, boundingBox, 1, 0, 0, 5, 0, 0, log, log, false);
			generateBox(level, boundingBox, 1, 0, 8, 5, 0, 8, log, log, false);
			generateBox(level, boundingBox, 3, 0, 1, 3, 0, 7, WATER, WATER, false);

			if (type != PopulatorVillage.Type.COLD) { //BE
				for (int z = 1; z <= 7; ++z) {
					final int maxAgeA = 7;
					final int minAgeA = maxAgeA / 3;
					placeBlock(level, new BlockState(cropA.getId(), Mth.nextInt(random, minAgeA, maxAgeA)), 1, 1, z, boundingBox);
					placeBlock(level, new BlockState(cropA.getId(), Mth.nextInt(random, minAgeA, maxAgeA)), 2, 1, z, boundingBox);
					final int maxAgeB = 7;
					final int minAgeB = maxAgeB / 3;
					placeBlock(level, new BlockState(cropB.getId(), Mth.nextInt(random, minAgeB, maxAgeB)), 4, 1, z, boundingBox);
					placeBlock(level, new BlockState(cropB.getId(), Mth.nextInt(random, minAgeB, maxAgeB)), 5, 1, z, boundingBox);
				}
			}

			for (int x = 0; x < 7; ++x) {
				for (int z = 0; z < 9; ++z) {
					fillAirColumnUp(level, x, 4, z, boundingBox);
					fillColumnDown(level, DIRT, x, -1, z, boundingBox);
				}
			}

			return true;
		}
	}

	public static class Smithy extends VillagePiece {
		private boolean hasPlacedChest;

		public Smithy(final StartPiece start, final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
			super(start, genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
		}

		public static Smithy createPiece(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, 0, 0, 0, 10, 6, 7, orientation);
			return isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ? new Smithy(start, genDepth, random, boundingBox, orientation) : null;
		}

		@Override
		public String getType() {
			return "ViS";
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			if (horizPos < 0) {
				horizPos = getAverageGroundHeight(level, boundingBox);

				if (horizPos < 0) {
					return true;
				}

				this.boundingBox.move(0, horizPos - this.boundingBox.y1 + 6 - 1, 0);
			}

			final BlockState cobble = COBBLESTONE;
			final BlockState stairsN = getSpecificBlock(OAK_STAIRS__N);
			final BlockState stairsW = getSpecificBlock(OAK_STAIRS__W);
			final BlockState planks = getSpecificBlock(PLANKS);
			final BlockState cobbleStairsN = getSpecificBlock(COBBLESTONE_STAIRS__N);
			final BlockState log = getSpecificBlock(LOG);
			final BlockState fence = getSpecificBlock(OAK_FENCE);

			generateBox(level, boundingBox, 0, 1, 0, 9, 4, 6, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 0, 0, 0, 9, 0, 6, cobble, cobble, false);
			generateBox(level, boundingBox, 0, 4, 0, 9, 4, 6, cobble, cobble, false);
			generateBox(level, boundingBox, 0, 5, 0, 9, 5, 6, STONE_SLAB, STONE_SLAB, false);
			generateBox(level, boundingBox, 1, 5, 1, 8, 5, 5, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 1, 1, 0, 2, 3, 0, planks, planks, false);
			generateBox(level, boundingBox, 0, 1, 0, 0, 4, 0, log, log, false);
			generateBox(level, boundingBox, 3, 1, 0, 3, 4, 0, log, log, false);
			generateBox(level, boundingBox, 0, 1, 6, 0, 4, 6, log, log, false);
			placeBlock(level, planks, 3, 3, 1, boundingBox);
			generateBox(level, boundingBox, 3, 1, 2, 3, 3, 2, planks, planks, false);
			generateBox(level, boundingBox, 4, 1, 3, 5, 3, 3, planks, planks, false);
			generateBox(level, boundingBox, 0, 1, 1, 0, 3, 5, planks, planks, false);
			generateBox(level, boundingBox, 1, 1, 6, 5, 3, 6, planks, planks, false);
			generateBox(level, boundingBox, 5, 1, 0, 5, 3, 0, fence, fence, false);
			generateBox(level, boundingBox, 9, 1, 0, 9, 3, 0, fence, fence, false);
			generateBox(level, boundingBox, 6, 1, 4, 9, 4, 6, cobble, cobble, false);
			placeBlock(level, FLOWING_LAVA, 7, 1, 5, boundingBox);
			placeBlock(level, FLOWING_LAVA, 8, 1, 5, boundingBox);
			placeBlock(level, IRON_BARS, 9, 2, 5, boundingBox);
			placeBlock(level, IRON_BARS, 9, 2, 4, boundingBox);
			generateBox(level, boundingBox, 7, 2, 4, 8, 2, 5, BlockState.AIR, BlockState.AIR, false);
			placeBlock(level, cobble, 6, 1, 3, boundingBox);

			placeBlock(level, FURNACE, 6, 2, 3, boundingBox);
			placeBlock(level, FURNACE, 6, 3, 3, boundingBox);

			BlockVector3 vec = new BlockVector3(getWorldX(6, 3), getWorldY(2), getWorldZ(6, 3));
			if (boundingBox.isInside(vec)) {
				final BaseFullChunk chunk = level.getChunk(vec.x >> 4, vec.z >> 4);
				if (chunk != null) {
					Server.getInstance().getScheduler().scheduleTask(new BlockActorSpawnTask(chunk.getProvider().getLevel(),
						BlockEntity.getDefaultCompound(vec.asVector3(), BlockEntity.FURNACE)));
				}
			}
			vec = vec.up();
			if (boundingBox.isInside(vec)) {
				final BaseFullChunk chunk = level.getChunk(vec.x >> 4, vec.z >> 4);
				if (chunk != null) {
					Server.getInstance().getScheduler().scheduleTask(new BlockActorSpawnTask(chunk.getProvider().getLevel(),
						BlockEntity.getDefaultCompound(vec.asVector3(), BlockEntity.FURNACE)));
				}
			}

			placeBlock(level, DOUBLE_STONE_SLAB, 8, 1, 1, boundingBox);
			placeBlock(level, GLASS_PANE, 0, 2, 2, boundingBox);
			placeBlock(level, GLASS_PANE, 0, 2, 4, boundingBox);
			placeBlock(level, GLASS_PANE, 2, 2, 6, boundingBox);
			placeBlock(level, GLASS_PANE, 4, 2, 6, boundingBox);
			placeBlock(level, fence, 2, 1, 4, boundingBox);
			placeBlock(level, BROWN_CARPET, 2, 2, 4, boundingBox);
			placeBlock(level, planks, 1, 1, 5, boundingBox);
			placeBlock(level, stairsN, 2, 1, 5, boundingBox);
			placeBlock(level, stairsW, 1, 1, 4, boundingBox);

			if (!hasPlacedChest && boundingBox.isInside(new BlockVector3(getWorldX(5, 5), getWorldY(1), getWorldZ(5, 5)))) {
				hasPlacedChest = true;

				//\\ StructureHelpers::createChest(v7, v6, (int *)v4, v5, 5, 1, 5, v197, (__int64)&v204);
				final BlockFace orientation = getOrientation();
				placeBlock(level, new BlockState(Block.CHEST, (orientation == null ? BlockFace.NORTH : orientation).getOpposite().getIndex()), 5, 1, 5, boundingBox);

				vec = new BlockVector3(getWorldX(5, 5), getWorldY(1), getWorldZ(5, 5));
				if (boundingBox.isInside(vec)) {
					final BaseFullChunk chunk = level.getChunk(vec.x >> 4, vec.z >> 4);
					if (chunk != null) {
						final CompoundTag nbt = BlockEntity.getDefaultCompound(vec.asVector3(), BlockEntity.CHEST);
						final ListTag<CompoundTag> itemList = new ListTag<>("Items");
						VillageBlacksmithChest.get().create(itemList, random);
						nbt.putList(itemList);
						Server.getInstance().getScheduler().scheduleTask(new BlockActorSpawnTask(chunk.getProvider().getLevel(), nbt));
					}
				}
			}

			for (int x = 6; x <= 8; ++x) {
				if (getBlock(level, x, 0, -1, boundingBox).equals(BlockState.AIR) && !getBlock(level, x, -1, -1, boundingBox).equals(BlockState.AIR)) {
					placeBlock(level, cobbleStairsN, x, 0, -1, boundingBox);

					if (getBlock(level, x, -1, -1, boundingBox).getId() == BlockID.GRASS_PATH) {
						placeBlock(level, GRASS, x, -1, -1, boundingBox);
					}
				}
			}

			for (int x = 0; x < 10; ++x) {
				for (int z = 0; z < 7; ++z) {
					fillAirColumnUp(level, x, 6, z, boundingBox);
					fillColumnDown(level, cobble, x, -1, z, boundingBox);
				}
			}

			spawnVillagers(level, boundingBox, 7, 1, 1, 1);
			return true;
		}

		@Override
		protected int getVillagerProfession(final int villagerCount, final int profession) {
			return EntityVillager.PROFESSION_BLACKSMITH;
		}
	}

	public static class TwoRoomHouse extends VillagePiece {
		public TwoRoomHouse(final StartPiece start, final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
			super(start, genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
		}

		public static TwoRoomHouse createPiece(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation, final int genDepth) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, 0, 0, 0, 9, 7, 12, orientation);
			return isOkBox(boundingBox) && StructurePiece.findCollisionPiece(pieces, boundingBox) == null ? new TwoRoomHouse(start, genDepth, random, boundingBox, orientation) : null;
		}

		@Override
		public String getType() {
			return "ViTRH";
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			if (horizPos < 0) {
				horizPos = getAverageGroundHeight(level, boundingBox);

				if (horizPos < 0) {
					return true;
				}

				this.boundingBox.move(0, horizPos - this.boundingBox.y1 + 7 - 1, 0);
			}

			final BlockState cobble = getSpecificBlock(COBBLESTONE);
			final BlockState stairsN = getSpecificBlock(OAK_STAIRS__N);
			final BlockState stairsS = getSpecificBlock(OAK_STAIRS__S);
			final BlockState stairsE = getSpecificBlock(OAK_STAIRS__E);
			final BlockState stairsW = getSpecificBlock(OAK_STAIRS__W);
			final BlockState planks = getSpecificBlock(PLANKS);
			final BlockState log = getSpecificBlock(LOG);

			generateBox(level, boundingBox, 1, 1, 1, 7, 4, 4, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 2, 1, 6, 8, 4, 10, BlockState.AIR, BlockState.AIR, false);
			generateBox(level, boundingBox, 2, 0, 5, 8, 0, 10, planks, planks, false);
			generateBox(level, boundingBox, 1, 0, 1, 7, 0, 4, planks, planks, false);
			generateBox(level, boundingBox, 0, 0, 0, 0, 3, 5, cobble, cobble, false);
			generateBox(level, boundingBox, 8, 0, 0, 8, 3, 10, cobble, cobble, false);
			generateBox(level, boundingBox, 1, 0, 0, 7, 2, 0, cobble, cobble, false);
			generateBox(level, boundingBox, 1, 0, 5, 2, 1, 5, cobble, cobble, false);
			generateBox(level, boundingBox, 2, 0, 6, 2, 3, 10, cobble, cobble, false);
			generateBox(level, boundingBox, 3, 0, 10, 7, 3, 10, cobble, cobble, false);
			generateBox(level, boundingBox, 1, 2, 0, 7, 3, 0, planks, planks, false);
			generateBox(level, boundingBox, 1, 2, 5, 2, 3, 5, planks, planks, false);
			generateBox(level, boundingBox, 0, 4, 1, 8, 4, 1, planks, planks, false);
			generateBox(level, boundingBox, 0, 4, 4, 3, 4, 4, planks, planks, false);
			generateBox(level, boundingBox, 0, 5, 2, 8, 5, 3, planks, planks, false);
			placeBlock(level, planks, 0, 4, 2, boundingBox);
			placeBlock(level, planks, 0, 4, 3, boundingBox);
			placeBlock(level, planks, 8, 4, 2, boundingBox);
			placeBlock(level, planks, 8, 4, 3, boundingBox);
			placeBlock(level, planks, 8, 4, 4, boundingBox);

			for (int i = -1; i <= 2; ++i) {
				for (int x = 0; x <= 8; ++x) {
					placeBlock(level, stairsN, x, 4 + i, i, boundingBox);

					if ((i > -1 || x <= 1) && (i > 0 || x <= 3) && (i > 1 || x != 5)) {
						placeBlock(level, stairsS, x, 4 + i, 5 - i, boundingBox);
					}
				}
			}

			generateBox(level, boundingBox, 3, 4, 5, 3, 4, 10, planks, planks, false);
			generateBox(level, boundingBox, 7, 4, 2, 7, 4, 10, planks, planks, false);
			generateBox(level, boundingBox, 4, 5, 4, 4, 5, 10, planks, planks, false);
			generateBox(level, boundingBox, 6, 5, 4, 6, 5, 10, planks, planks, false);
			generateBox(level, boundingBox, 5, 6, 3, 5, 6, 10, planks, planks, false);

			for (int i = 4; i >= 1; --i) {
				placeBlock(level, planks, i, 2 + i, 7 - i, boundingBox);

				for (int z = 8 - i; z <= 10; ++z) {
					placeBlock(level, stairsE, i, 2 + i, z, boundingBox);
				}
			}

			placeBlock(level, planks, 6, 6, 3, boundingBox);
			placeBlock(level, planks, 7, 5, 4, boundingBox);
			placeBlock(level, stairsW, 6, 6, 4, boundingBox);

			for (int i = 6; i <= 8; ++i) {
				for (int z = 5; z <= 10; ++z) {
					placeBlock(level, stairsW, i, 12 - i, z, boundingBox);
				}
			}

			placeBlock(level, log, 0, 2, 1, boundingBox);
			placeBlock(level, log, 0, 2, 4, boundingBox);
			placeBlock(level, GLASS_PANE, 0, 2, 2, boundingBox);
			placeBlock(level, GLASS_PANE, 0, 2, 3, boundingBox);
			placeBlock(level, log, 4, 2, 0, boundingBox);
			placeBlock(level, GLASS_PANE, 5, 2, 0, boundingBox);
			placeBlock(level, log, 6, 2, 0, boundingBox);
			placeBlock(level, log, 8, 2, 1, boundingBox);
			placeBlock(level, GLASS_PANE, 8, 2, 2, boundingBox);
			placeBlock(level, GLASS_PANE, 8, 2, 3, boundingBox);
			placeBlock(level, log, 8, 2, 4, boundingBox);
			placeBlock(level, planks, 8, 2, 5, boundingBox);
			placeBlock(level, log, 8, 2, 6, boundingBox);
			placeBlock(level, GLASS_PANE, 8, 2, 7, boundingBox);
			placeBlock(level, GLASS_PANE, 8, 2, 8, boundingBox);
			placeBlock(level, log, 8, 2, 9, boundingBox);
			placeBlock(level, log, 2, 2, 6, boundingBox);
			placeBlock(level, GLASS_PANE, 2, 2, 7, boundingBox);
			placeBlock(level, GLASS_PANE, 2, 2, 8, boundingBox);
			placeBlock(level, log, 2, 2, 9, boundingBox);
			placeBlock(level, log, 4, 4, 10, boundingBox);
			placeBlock(level, GLASS_PANE, 5, 4, 10, boundingBox);
			placeBlock(level, log, 6, 4, 10, boundingBox);
			placeBlock(level, planks, 5, 5, 10, boundingBox);
			placeBlock(level, BlockState.AIR, 2, 1, 0, boundingBox);
			placeBlock(level, BlockState.AIR, 2, 2, 0, boundingBox);
			placeTorch(level, BlockFace.NORTH, 2, 3, 1, boundingBox);
			placeDoor(level, boundingBox, random, 2, 1, 0, BlockFace.NORTH);
			generateBox(level, boundingBox, 1, 0, -1, 3, 2, -1, BlockState.AIR, BlockState.AIR, false);

			if (getBlock(level, 2, 0, -1, boundingBox).equals(BlockState.AIR) && !getBlock(level, 2, -1, -1, boundingBox).equals(BlockState.AIR)) {
				placeBlock(level, stairsN, 2, 0, -1, boundingBox);

				if (getBlock(level, 2, -1, -1, boundingBox).getId() == BlockID.GRASS_PATH) {
					placeBlock(level, GRASS, 2, -1, -1, boundingBox);
				}
			}

			for (int x = 0; x < 9; ++x) {
				for (int z = 0; z < 5; ++z) {
					fillAirColumnUp(level, x, 7, z, boundingBox);
					fillColumnDown(level, cobble, x, -1, z, boundingBox);
				}
			}

			for (int x = 2; x < 9; ++x) {
				for (int z = 5; z < 11; ++z) {
					fillAirColumnUp(level, x, 7, z, boundingBox);
					fillColumnDown(level, cobble, x, -1, z, boundingBox);
				}
			}

			if (type == PopulatorVillage.Type.COLD) { //BE
				//\\ StructureHelpers::createChest(v7, v6, 5, 1, 9, v279, (unsigned __int64)&Dst, v284, v285, v288, v289);
				final BlockFace orientation = getOrientation();
				placeBlock(level, new BlockState(Block.CHEST, (orientation == null ? BlockFace.NORTH : orientation).getOpposite().getIndex()), 5, 1, 9, boundingBox);

				final BlockVector3 vec = new BlockVector3(getWorldX(5, 9), getWorldY(1), getWorldZ(5, 9));
				if (boundingBox.isInside(vec)) {
					final BaseFullChunk chunk = level.getChunk(vec.x >> 4, vec.z >> 4);
					if (chunk != null) {
						final CompoundTag nbt = BlockEntity.getDefaultCompound(vec.asVector3(), BlockEntity.CHEST);
						final ListTag<CompoundTag> itemList = new ListTag<>("Items");
						VillageTwoRoomHouseChest.get().create(itemList, random);
						nbt.putList(itemList);
						Server.getInstance().getScheduler().scheduleTask(new BlockActorSpawnTask(chunk.getProvider().getLevel(), nbt));
					}
				}
			}

			spawnVillagers(level, boundingBox, 4, 1, 2, 2);
			return true;
		}
	}

	public static class LightPost extends VillagePiece {
		public LightPost(final StartPiece start, final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
			super(start, genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
		}

		public static BoundingBox findPieceBox(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation) {
			final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, 0, 0, 0, 3, 4, 2, orientation);
			return StructurePiece.findCollisionPiece(pieces, boundingBox) != null ? null : boundingBox;
		}

		@Override
		public String getType() {
			return "ViL";
		}

		@Override
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			if (horizPos < 0) {
				horizPos = getAverageGroundHeight(level, boundingBox);

				if (horizPos < 0) {
					return true;
				}

				this.boundingBox.move(0, horizPos - this.boundingBox.y1 + 4 - 1, 0);
			}

			final BlockState fence = getSpecificBlock(OAK_FENCE);
			generateBox(level, boundingBox, 0, 0, 0, 2, 3, 1, BlockState.AIR, BlockState.AIR, false);
			placeBlock(level, fence, 1, 0, 0, boundingBox);
			placeBlock(level, fence, 1, 1, 0, boundingBox);
			placeBlock(level, fence, 1, 2, 0, boundingBox);
			placeBlock(level, BLACK_WOOL, 1, 3, 0, boundingBox);
			placeTorch(level, BlockFace.EAST, 2, 3, 0, boundingBox);
			placeTorch(level, BlockFace.NORTH, 1, 3, 1, boundingBox);
			placeTorch(level, BlockFace.WEST, 0, 3, 0, boundingBox);
			placeTorch(level, BlockFace.SOUTH, 1, 3, -1, boundingBox);
			return true;
		}
	}

	public static class StraightRoad extends Road {
		private final int length;

		public StraightRoad(final StartPiece start, final int genDepth, final NukkitRandom random, final BoundingBox boundingBox, final BlockFace orientation) {
			super(start, genDepth);
			setOrientation(orientation);
			this.boundingBox = boundingBox;
			length = Math.max(boundingBox.getXSpan(), boundingBox.getZSpan());
		}

		//\\ StraightRoad::findPieceBox(StartPiece *,std::vector<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>,std::allocator<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>>> &,Random &,int,int,int,int)
		public static BoundingBox findPieceBox(final StartPiece start, final List<StructurePiece> pieces, final NukkitRandom random, final int x, final int y, final int z, final BlockFace orientation) {
			for (int i = 7 * Mth.nextInt(random, 3, 5); i >= 7; i -= 7) {
				final BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, 0, 0, 0, 3, 3, i, orientation);

				if (StructurePiece.findCollisionPiece(pieces, boundingBox) == null) {
					return boundingBox;
				}
			}

			return null;
		}

		@Override
		public String getType() {
			return "ViSR";
		}

		@Override
		//\\ StraightRoad::addChildren(StructurePiece *,std::vector<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>,std::allocator<std::unique_ptr<StructurePiece,std::default_delete<StructurePiece>>>> &,Random &)
		public void addChildren(final StructurePiece piece, final List<StructurePiece> pieces, final NukkitRandom random) {
			boolean success = false;

			for (int offset = random.nextBoundedInt(5); offset < length - 8; offset += 2 + random.nextBoundedInt(5)) {
				final StructurePiece result = generateChildLeft((StartPiece) piece, pieces, random, 0, offset);
				if (result != null) {
					offset += Math.max(result.getBoundingBox().getXSpan(), result.getBoundingBox().getZSpan());
					success = true;
				}
			}

			for (int offset = random.nextBoundedInt(5); offset < length - 8; offset += 2 + random.nextBoundedInt(5)) {
				final StructurePiece result = generateChildRight((StartPiece) piece, pieces, random, 0, offset);
				if (result != null) {
					offset += Math.max(result.getBoundingBox().getXSpan(), result.getBoundingBox().getZSpan());
					success = true;
				}
			}

			final BlockFace orientation = getOrientation();

			if (success && random.nextBoundedInt(3) > 0 && orientation != null) {
				switch (orientation) {
					case SOUTH -> generateAndAddRoadPiece((StartPiece) piece, pieces, random, boundingBox.x0 - 1, boundingBox.y0, boundingBox.z1 - 2, BlockFace.WEST, getGenDepth());
					case WEST -> generateAndAddRoadPiece((StartPiece) piece, pieces, random, boundingBox.x0, boundingBox.y0, boundingBox.z0 - 1, BlockFace.NORTH, getGenDepth());
					case EAST -> generateAndAddRoadPiece((StartPiece) piece, pieces, random, boundingBox.x1 - 2, boundingBox.y0, boundingBox.z0 - 1, BlockFace.NORTH, getGenDepth());
					default -> generateAndAddRoadPiece((StartPiece) piece, pieces, random, boundingBox.x0 - 1, boundingBox.y0, boundingBox.z0, BlockFace.WEST, getGenDepth());
				}
			}

			if (success && random.nextBoundedInt(3) > 0 && orientation != null) {
				switch (orientation) {
					case SOUTH -> generateAndAddRoadPiece((StartPiece) piece, pieces, random, boundingBox.x1 + 1, boundingBox.y0, boundingBox.z1 - 2, BlockFace.EAST, getGenDepth());
					case WEST -> generateAndAddRoadPiece((StartPiece) piece, pieces, random, boundingBox.x0, boundingBox.y0, boundingBox.z1 + 1, BlockFace.SOUTH, getGenDepth());
					case EAST -> generateAndAddRoadPiece((StartPiece) piece, pieces, random, boundingBox.x1 - 2, boundingBox.y0, boundingBox.z1 + 1, BlockFace.SOUTH, getGenDepth());
					default -> generateAndAddRoadPiece((StartPiece) piece, pieces, random, boundingBox.x1 + 1, boundingBox.y0, boundingBox.z0, BlockFace.EAST, getGenDepth());
				}
			}
		}

		@Override //\\ StraightRoad::postProcess(BlockSource *,Random &,BoundingBox const &)
		public boolean postProcess(final ChunkManager level, final NukkitRandom random, final BoundingBox boundingBox, final int chunkX, final int chunkZ) {
			final BlockState path = getSpecificBlock(GRASS_PATH);
			final BlockState planks = getSpecificBlock(PLANKS);
			final BlockState gravel = getSpecificBlock(GRAVEL);
			final BlockState cobble = getSpecificBlock(COBBLESTONE);

			for (int x = this.boundingBox.x0; x <= this.boundingBox.x1; ++x) {
				for (int z = this.boundingBox.z0; z <= this.boundingBox.z1; ++z) {
					final BlockVector3 vec = new BlockVector3(x, 64 + yOffset, z);

					if (boundingBox.isInside(vec)) {
						final BaseFullChunk chunk = level.getChunk(chunkX, chunkZ);
						if (chunk == null) {
							vec.y = 63 - 1 + yOffset;
						} else {
							final int cx = x & 0xf;
							final int cz = z & 0xf;
							int y = chunk.getHighestBlockAt(cx, cz);
							int id = chunk.getBlockId(cx, y, cz);
							while (Block.transparent[id] && y > 63 - 1 + yOffset) {
								id = chunk.getBlockId(cx, --y, cz);
							}
							vec.y = y;
						}

						if (vec.y < 63 + yOffset) {
							vec.y = 63 - 1 + yOffset;
						}

						while (vec.y >= 63 - 1 + yOffset) {
							final int block = level.getBlockIdAt(vec.x, vec.y, vec.z);

							if (block == BlockID.GRASS && level.getBlockIdAt(vec.x, vec.y + 1, vec.z) == BlockID.AIR) {
								level.setBlockAt(vec.x, vec.y, vec.z, path.getId(), path.getMeta());
								break;
							}
							if (block == BlockID.WATER || block == BlockID.STILL_WATER || block == BlockID.LAVA || block == BlockID.STILL_LAVA) {
								level.setBlockAt(vec.x, vec.y, vec.z, planks.getId(), planks.getMeta());
								break;
							}
							if (block == BlockID.SAND || block == BlockID.SANDSTONE || block == BlockID.RED_SANDSTONE) {
								level.setBlockAt(vec.x, vec.y, vec.z, gravel.getId(), gravel.getMeta());
								level.setBlockAt(vec.x, vec.y - 1, vec.z, cobble.getId(), cobble.getMeta());
								break;
							}

							--vec.y;
						}
					}
				}
			}

			return true;
		}
	}
}