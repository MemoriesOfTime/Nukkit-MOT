package cn.nukkit.level.biome;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class Biome implements BlockID {

    public static final int MAX_BIOMES = 256;
    public static final Biome[] biomes = new Biome[MAX_BIOMES];
    public static final List<Biome> unorderedBiomes = new ObjectArrayList<>();
    private static final Int2ObjectMap<String> runtimeId2Identifier = new Int2ObjectOpenHashMap<>();
    private static CompoundTag biomeDefinitions;

    private final ArrayList<Populator> populators = new ArrayList<>();
    private int id;
    private float baseHeight = 0.1f;
    private float heightVariation = 0.3f;

    static {
        try (InputStream stream = Biome.class.getClassLoader().getResourceAsStream("biome_id_map.json")) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
            for (String identifier : json.keySet()) {
                int biomeId = json.get(identifier).getAsInt();
                runtimeId2Identifier.put(biomeId, identifier);
            }
        } catch (NullPointerException | IOException e) {
            throw new AssertionError("Unable to load biome mapping from biome_id_map.json", e);
        }

        //TODO Multiversion
        try (InputStream stream = Biome.class.getClassLoader().getResourceAsStream("biome_definitions_554.dat")) {
            if (stream == null) {
                throw new AssertionError("Unable to locate block biome_definitions_554");
            }
            biomeDefinitions = (CompoundTag) NBTIO.readTag(new BufferedInputStream(stream), ByteOrder.BIG_ENDIAN, true);
        } catch (IOException e) {
            throw new AssertionError("Unable to locate block biome_definitions_554", e);
        }
    }

    public static String getBiomeNameFromId(int protocol, int biomeId) {
        return runtimeId2Identifier.get(biomeId);
    }

    public static int getBiomeIdOrCorrect(int protocol, int biomeId) {
        if (runtimeId2Identifier.get(biomeId) == null) {
            return EnumBiome.OCEAN.id;
        }
        return biomeId;
    }

    public static CompoundTag getBiomeDefinitions(int biomeId) {
        return getBiomeDefinitions(getBiomeNameFromId(ProtocolInfo.CURRENT_PROTOCOL, biomeId));
    }

    public static CompoundTag getBiomeDefinitions(String biomeName) {
        return biomeDefinitions.getCompound(biomeName);
    }

    protected static void register(int id, Biome biome) {
        biome.setId(id);
        biomes[id] = biome;
        unorderedBiomes.add(biome);
    }

    public static Biome getBiome(int id) {
        Biome biome = biomes[id];
        return biome != null ? biome : EnumBiome.OCEAN.biome;
    }

    /**
     * Get Biome by name.
     *
     * @param name Name of biome. Name could contain symbol "_" instead of space
     * @return Biome. Null - when biome was not found
     */
    public static Biome getBiome(String name) {
        for (Biome biome : biomes) {
            if (biome != null) {
                if (biome.getName().equalsIgnoreCase(name.replace("_", " "))) return biome;
            }
        }
        return null;
    }

    public void clearPopulators() {
        this.populators.clear();
    }

    public void addPopulator(Populator populator) {
        this.populators.add(populator);
    }

    public void populateChunk(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random) {
        FullChunk chunk = level.getChunk(chunkX, chunkZ);
        for (Populator populator : populators) {
            populator.populate(level, chunkX, chunkZ, random, chunk);
        }
    }

    public ArrayList<Populator> getPopulators() {
        return populators;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public abstract String getName();

    public void setBaseHeight(float baseHeight) {
        this.baseHeight = baseHeight;
    }

    public void setHeightVariation(float heightVariation)   {
        this.heightVariation = heightVariation;
    }

    public float getBaseHeight() {
        return baseHeight;
    }

    public float getHeightVariation() {
        return heightVariation;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return hashCode() == obj.hashCode();
    }

    /**
     * Whether or not water should freeze into ice on generation
     *
     * @return overhang
     */
    public boolean isFreezing() {
        return false;
    }

    /**
     * Whether or not overhangs should generate in this biome (places where solid blocks generate over air)
     *
     * This should probably be used with a custom max elevation or things can look stupid
     *
     * @return overhang
     */
    public boolean doesOverhang()   {
        return false;
    }

    /**
     * How much offset should be added to the min/max heights at this position
     *
     * @param x x
     * @param z z
     * @return height offset
     */
    public int getHeightOffset(int x, int z)    {
        return 0;
    }

    public boolean canRain() {
        return true;
    }
}
