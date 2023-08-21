package cn.nukkit.level.generator;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.Level;
import cn.nukkit.level.biome.Biome;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.biome.impl.nether.NetherBiome;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.noise.nukkit.OpenSimplex2S;
import cn.nukkit.level.generator.noise.nukkit.f.SimplexF;
import cn.nukkit.level.generator.object.ore.OreType;
import cn.nukkit.level.generator.populator.impl.PopulatorGroundFire;
import cn.nukkit.level.generator.populator.impl.PopulatorLava;
import cn.nukkit.level.generator.populator.impl.PopulatorOre;
import cn.nukkit.level.generator.populator.nether.PopulatorGlowStone;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Nether extends Generator {
    private static final double BIOME_AMPLIFICATION = 512;
    private final List<Populator> populators = new ArrayList<>();
    private final double lavaHeight = 32;
    private final SimplexF[] noiseGen = new SimplexF[3];
    private ChunkManager level;
    private NukkitRandom nukkitRandom;
    private OpenSimplex2S biomeGen;
    private long localSeed1;
    private long localSeed2;

    public Nether() {
        this(Collections.emptyMap());
    }

    public Nether(final Map<String, Object> options) {
    }

    @Override
    public int getId() {
        return Generator.TYPE_NETHER;
    }

    @Override
    public int getDimension() {
        return Level.DIMENSION_NETHER;
    }

    @Override
    public void init(final ChunkManager level, final NukkitRandom random) {
        this.level = level;
        nukkitRandom = random;
        nukkitRandom.setSeed(this.level.getSeed());

        for (int i = 0; i < noiseGen.length; i++) {
            noiseGen[i] = new SimplexF(nukkitRandom, 4, 1 / 4f, 1 / 64f);
        }

        biomeGen = new OpenSimplex2S(random.getSeed());

        nukkitRandom.setSeed(this.level.getSeed());
        localSeed1 = Utils.random.nextLong();
        localSeed2 = Utils.random.nextLong();

        final PopulatorOre ores = new PopulatorOre(BlockID.NETHERRACK, new OreType[]{
            new OreType(Block.get(BlockID.QUARTZ_ORE), 20, 16, 0, 128),
            new OreType(Block.get(BlockID.SOUL_SAND), 5, 64, 0, 128),
            new OreType(Block.get(BlockID.GRAVEL), 5, 64, 0, 128),
            new OreType(Block.get(BlockID.LAVA), 1, 16, 0, (int) lavaHeight),
        });
        populators.add(ores);

        final PopulatorGroundFire groundFire = new PopulatorGroundFire();
        groundFire.setBaseAmount(1);
        groundFire.setRandomAmount(1);
        populators.add(groundFire);

        final PopulatorLava lava = new PopulatorLava();
        lava.setBaseAmount(1);
        lava.setRandomAmount(2);
        populators.add(lava);
        populators.add(new PopulatorGlowStone());
        final PopulatorOre ore = new PopulatorOre(BlockID.NETHERRACK, new OreType[]{
            new OreType(Block.get(BlockID.QUARTZ_ORE), 20, 16, 0, 128, BlockID.NETHERRACK),
            new OreType(Block.get(BlockID.SOUL_SAND), 1, 64, 30, 35, BlockID.NETHERRACK),
            new OreType(Block.get(BlockID.LAVA), 32, 1, 0, 32, BlockID.NETHERRACK),
            new OreType(Block.get(BlockID.MAGMA), 32, 16, 26, 37, BlockID.NETHERRACK),
            new OreType(Block.get(BlockID.NETHER_GOLD_ORE), 5, 16, 10, 117, BlockID.NETHERRACK),
            new OreType(Block.get(BlockID.ANCIENT_DEBRIS), 2, 2, 8, 119, BlockID.NETHERRACK),
            new OreType(Block.get(BlockID.ANCIENT_DEBRIS), 1, 3, 8, 22, BlockID.NETHERRACK),
        });
        populators.add(ore);
    }

    @Override
    public void generateChunk(final int chunkX, final int chunkZ) {
        final int baseX = chunkX << 4;
        final int baseZ = chunkZ << 4;
        nukkitRandom.setSeed(chunkX * localSeed1 ^ chunkZ * localSeed2 ^ level.getSeed());

        final BaseFullChunk chunk = level.getChunk(chunkX, chunkZ);

        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                final NetherBiome biome = (NetherBiome) pickBiome(baseX + x, baseZ + z).biome;
                chunk.setBiomeId(x, z, biome.getId());

                chunk.setBlockId(x, 0, z, BlockID.BEDROCK);
                for (int i = 0; i < nukkitRandom.nextBoundedInt(6); i++) {
                    chunk.setBlockId(x, 126 - i, z, biome.getMiddleBlock());
                }
                for (int y = 126; y < 127; ++y) {
                    chunk.setBlockId(x, y, z, biome.getMiddleBlock());
                }
                chunk.setBlockId(x, 127, z, BlockID.BEDROCK);
                for (int y = 1; y < 127; ++y) {
                    if (getNoise(baseX | x, y, baseZ | z) > 0) {
                        chunk.setBlockId(x, y, z, biome.getMiddleBlock());
                    } else if (y <= lavaHeight) {
                        chunk.setBlockId(x, y, z, BlockID.LAVA);
                        chunk.setBlockLight(x, y + 1, z, 15);
                    }
                }
                for (int y = 1; y < 127; ++y) {
                    if (getNoise(baseX | x, y, baseZ | z) > 0) {
                        if (chunk.getBlockId(x, y + 1, z) == 0) chunk.setBlockId(x, y, z, biome.getCoverBlock());
                    }
                }
            }
        }
    }

    @Override
    public void populateChunk(final int chunkX, final int chunkZ) {
        final BaseFullChunk chunk = level.getChunk(chunkX, chunkZ);
        nukkitRandom.setSeed(0xdeadbeef ^ (long) chunkX << 8 ^ chunkZ ^ level.getSeed());

        final Biome biome = EnumBiome.getBiome(chunk.getBiomeId(7, 7));

        for (final Populator populator : populators) {
            populator.populate(level, chunkX, chunkZ, nukkitRandom, chunk);
        }

        biome.populateChunk(level, chunkX, chunkZ, nukkitRandom);
    }

    @Override
    public Map<String, Object> getSettings() {
        return new HashMap<>();
    }

    @Override
    public String getName() {
        return "nether";
    }

    @Override
    public Vector3 getSpawn() {
        return new Vector3(0, 64, 0);
    }

    @Override
    public ChunkManager getChunkManager() {
        return level;
    }

    public float getNoise(final int x, final int y, final int z) {
        float val = 0f;
        for (int i = 0; i < noiseGen.length; i++) {
            val += noiseGen[i].noise3D(x >> i, y, z >> i, true);
        }
        return val;
    }

    public EnumBiome pickBiome(final int x, final int z) {
        final double value = biomeGen.noise2(x / BIOME_AMPLIFICATION, z / BIOME_AMPLIFICATION);
        final double secondaryValue = biomeGen.noise3_XZBeforeY(x / (BIOME_AMPLIFICATION * 2d), 0, z / (BIOME_AMPLIFICATION * 2d));
        if (value >= 1 / 3f) {
            return secondaryValue >= 0 ? EnumBiome.WARPED_FOREST : EnumBiome.CRIMSON_FOREST;
        }
        if (value >= -1 / 3f) {
            return EnumBiome.HELL;
        }
        return secondaryValue >= 0 ? EnumBiome.BASALT_DELTAS : EnumBiome.SOUL_SAND_VALLEY;
    }
}
