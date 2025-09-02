package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.network.protocol.types.biome.*;
import cn.nukkit.utils.Utils;
import com.google.common.io.ByteStreams;
import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import lombok.ToString;
import org.cloudburstmc.protocol.common.util.SequencedHashSet;

import java.awt.*;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.zip.Deflater;

@ToString()
public class BiomeDefinitionListPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.BIOME_DEFINITION_LIST_PACKET;

    private static final DataPacket CACHED_PACKET_361;
    private static final BatchPacket CACHED_PACKET_419;
    private static final BatchPacket CACHED_PACKET_486;
    private static final BatchPacket CACHED_PACKET_527;
    private static final BatchPacket CACHED_PACKET_544;
    private static final BatchPacket CACHED_PACKET_567;
    private static final BatchPacket CACHED_PACKET_786;
    private static final BatchPacket CACHED_PACKET_800;
    private static final BatchPacket CACHED_PACKET;

    private static final byte[] TAG_361;
    private static final byte[] TAG_419;
    private static final byte[] TAG_486;
    private static final byte[] TAG_527;
    private static final byte[] TAG_544;
    private static final byte[] TAG_567;
    private static final byte[] TAG_786;

    private LinkedHashMap<String, BiomeDefinitionData> biomeDefinitions;

    static {
        try {
            TAG_361 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_361.dat"));
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.protocol = ProtocolInfo.v1_12_0;
            pk.gameVersion = GameVersion.V1_12_0;
            pk.tryEncode();
            CACHED_PACKET_361 = pk; //.compress(Deflater.BEST_COMPRESSION); 压缩会导致1.16.40无法进入服务器
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 361", e);
        }
        try {
            TAG_419 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_419.dat"));
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.protocol = ProtocolInfo.v1_16_100;
            pk.gameVersion = GameVersion.V1_16_100;
            pk.tryEncode();
            CACHED_PACKET_419 = pk.compress(Deflater.BEST_COMPRESSION);
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 419", e);
        }
        try {
            TAG_486 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_486.dat"));
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.protocol = ProtocolInfo.v1_18_10;
            pk.gameVersion = GameVersion.V1_18_10;
            pk.tryEncode();
            CACHED_PACKET_486 = pk.compress(Deflater.BEST_COMPRESSION);
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 486", e);
        }
        try {
            TAG_527 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_527.dat"));
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.protocol = ProtocolInfo.v1_19_0;
            pk.gameVersion = GameVersion.V1_19_0;
            pk.tryEncode();
            CACHED_PACKET_527 = pk.compress(Deflater.BEST_COMPRESSION);
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 527", e);
        }
        try {
            TAG_544 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_554.dat"));
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.protocol = ProtocolInfo.v1_19_20;
            pk.gameVersion = GameVersion.V1_19_20;
            pk.tryEncode();
            CACHED_PACKET_544 = pk.compress(Deflater.BEST_COMPRESSION);
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 554", e);
        }
        try {
            TAG_567 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_567.dat"));
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.protocol = ProtocolInfo.v1_19_60;
            pk.gameVersion = GameVersion.V1_19_60;
            pk.tryEncode();
            CACHED_PACKET_567 = pk.compress(Deflater.BEST_COMPRESSION);
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 554", e);
        }
        try {
            TAG_786 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_786.dat"));
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.protocol = ProtocolInfo.v1_21_70;
            pk.gameVersion = GameVersion.V1_21_70;
            pk.tryEncode();
            CACHED_PACKET_786 = pk.compress(Deflater.BEST_COMPRESSION);
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 786", e);
        }

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Color.class, new ColorTypeAdapter())
                .registerTypeAdapter(Block.class, new BlockSerializer()) //避免GSON错误的扫描所有涉及方块的其他类
                .create();
        try {
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.biomeDefinitions = gson.fromJson(
                    Utils.loadJsonResource("stripped_biome_definitions_800.json"),
                    new TypeToken<LinkedHashMap<String, BiomeDefinitionData>>() {}.getType()
            );
            pk.protocol = ProtocolInfo.v1_21_80;
            pk.gameVersion = GameVersion.V1_21_80;
            pk.tryEncode();
            CACHED_PACKET_800 = pk.compress(Deflater.BEST_COMPRESSION);
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 800", e);
        }
        try {
            BiomeDefinitionListPacket pk = new BiomeDefinitionListPacket();
            pk.biomeDefinitions = gson.fromJson(
                    Utils.loadJsonResource("biome/stripped_biome_definitions_827.json"),
                    new TypeToken<LinkedHashMap<String, BiomeDefinitionData>>() {}.getType()
            );
            pk.protocol = ProtocolInfo.v1_21_100;
            pk.gameVersion = GameVersion.V1_21_100;
            pk.tryEncode();
            CACHED_PACKET = pk.compress(Deflater.BEST_COMPRESSION);
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 827", e);
        }
    }

    @Deprecated
    public static DataPacket getCachedPacket(int protocol) {
        return getCachedPacket(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode));
    }

    public static DataPacket getCachedPacket(GameVersion gameVersion) {
        int protocol = gameVersion.getProtocol();
        if (protocol < ProtocolInfo.v1_12_0) {
            throw new UnsupportedOperationException("Unsupported protocol version: " + protocol);
        }

        if (protocol >= ProtocolInfo.v1_21_100) {
            return CACHED_PACKET;
        } else if (protocol >= ProtocolInfo.v1_21_80) {
            return CACHED_PACKET_800;
        } else if (protocol >= ProtocolInfo.v1_21_70_24) {
            return CACHED_PACKET_786;
        } else if (protocol >= ProtocolInfo.v1_19_60) {
            return CACHED_PACKET_567;
        } else if (protocol >= ProtocolInfo.v1_19_30_23) {
            return CACHED_PACKET_544;
        } else if (protocol >= ProtocolInfo.v1_19_0) {
            return CACHED_PACKET_527;
        } else if (protocol >= ProtocolInfo.v1_18_10) {
            return CACHED_PACKET_486;
        } else if (protocol >= ProtocolInfo.v1_16_100) {
            return CACHED_PACKET_419;
        } else {
            return CACHED_PACKET_361;
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_21_80) {
            if (this.biomeDefinitions == null) {
                throw new RuntimeException("biomeDefinitions == null, use getCachedPacket!");
            }

            SequencedHashSet<String> strings = new SequencedHashSet<>();

            this.putUnsignedVarInt(this.biomeDefinitions.size());
            for (Map.Entry<String, BiomeDefinitionData> entry : this.biomeDefinitions.entrySet()) {
                String name = entry.getKey();
                this.putLShort(strings.addAndGetIndex(name));
                this.putBiomeDefinitionData(entry.getValue(), strings);
            }

            this.putUnsignedVarInt(strings.size());
            for (String str : strings) {
                this.putString(str);
            }
        } else if (this.protocol >= ProtocolInfo.v1_21_70_24) {
            this.put(TAG_786);
        } else if (this.protocol >= ProtocolInfo.v1_19_30_23) {
            this.put(TAG_544);
        } else if (this.protocol >= ProtocolInfo.v1_19_0) {
            this.put(TAG_527);
        } else if (this.protocol >= ProtocolInfo.v1_18_10) {
            this.put(TAG_486);
        } else if (this.protocol >= ProtocolInfo.v1_16_100) {
            this.put(TAG_419);
        } else {
            this.put(TAG_361);
        }
    }

    protected void putBiomeDefinitionData(BiomeDefinitionData definition, SequencedHashSet<String> strings) {
        if (protocol >= GameVersion.V1_21_100.getProtocol()) {
            if (definition.getId() == null) {
                this.putLShort(-1); // Vanilla biomes don't contain ID field
            } else {
                this.putLShort(strings.addAndGetIndex(definition.getId()));
            }
        } else {
            this.putOptional(Objects::nonNull, definition.getId(), id -> this.putLShort(strings.addAndGetIndex(id)));
        }
        this.putLFloat(definition.getTemperature());
        this.putLFloat(definition.getDownfall());
        if (protocol >= GameVersion.V1_21_110.getProtocol()) {
            this.putLFloat(definition.getFoliageSnow());
        } else {
            this.putLFloat(definition.getRedSporeDensity());
            this.putLFloat(definition.getBlueSporeDensity());
            this.putLFloat(definition.getAshDensity());
            this.putLFloat(definition.getWhiteAshDensity());
        }
        this.putLFloat(definition.getDepth());
        this.putLFloat(definition.getScale());
        this.putLInt(definition.getMapWaterColor().getRGB());
        this.putBoolean(definition.isRain());
        this.putOptionalNull(definition.getTags(), tags -> {
            this.putUnsignedVarInt(tags.size());
            for (String tag : tags) {
                this.putLShort(strings.addAndGetIndex(tag));
            }
        });
        this.putOptionalNull(definition.getChunkGenData(), definitionChunkGen -> this.putBiomeDefinitionChunkGenData(definitionChunkGen, strings));
    }

    protected void putBiomeDefinitionChunkGenData(BiomeDefinitionChunkGenData definitionChunkGen, SequencedHashSet<String> strings) {
        this.putOptionalNull(definitionChunkGen.getClimate(), this::putClimate);
        this.putOptionalNull(definitionChunkGen.getConsolidatedFeatures(), (consolidatedFeatures) -> this.putConsolidatedFeatures(consolidatedFeatures, strings));
        this.putOptionalNull(definitionChunkGen.getMountainParams(), this::putMountainParamsData);
        this.putOptionalNull(definitionChunkGen.getSurfaceMaterialAdjustment(),
                (surfaceMaterialAdjustment) -> this.putSurfaceMaterialAdjustment(surfaceMaterialAdjustment, strings));
        this.putOptionalNull(definitionChunkGen.getSurfaceMaterial(), this::putSurfaceMaterial);
        this.putBoolean(definitionChunkGen.isHasSwampSurface());
        this.putBoolean(definitionChunkGen.isHasFrozenOceanSurface());
        this.putBoolean(definitionChunkGen.isHasTheEndSurface());
        this.putOptionalNull(definitionChunkGen.getMesaSurface(), this::putMesaSurface);
        this.putOptionalNull(definitionChunkGen.getCappedSurface(), this::putCappedSurface);
        this.putOptionalNull(definitionChunkGen.getOverworldGenRules(),
                (overworldGenRules) -> this.putOverworldGenRules(overworldGenRules, strings));
        this.putOptionalNull(definitionChunkGen.getMultinoiseGenRules(), this::putMultinoiseGenRules);
        this.putOptionalNull(definitionChunkGen.getLegacyWorldGenRules(),
                (legacyWorldGenRules) -> this.putLegacyWorldGenRules(legacyWorldGenRules, strings));
    }

    protected void putClimate(BiomeClimateData climate) {
        this.putLFloat(climate.getTemperature());
        this.putLFloat(climate.getDownfall());
        if (protocol <= GameVersion.V1_21_100.getProtocol()) {
            this.putLFloat(climate.getRedSporeDensity());
            this.putLFloat(climate.getBlueSporeDensity());
            this.putLFloat(climate.getAshDensity());
            this.putLFloat(climate.getWhiteAshDensity());
        }
        this.putLFloat(climate.getSnowAccumulationMin());
        this.putLFloat(climate.getSnowAccumulationMax());
    }

    protected void putConsolidatedFeatures(List<BiomeConsolidatedFeatureData> consolidatedFeatures, SequencedHashSet<String> strings) {
        this.putArray(consolidatedFeatures, consolidatedFeature -> this.putConsolidatedFeature(consolidatedFeature, strings));
    }

    protected void putConsolidatedFeature(BiomeConsolidatedFeatureData consolidatedFeature, SequencedHashSet<String> strings) {
        this.putScatterParam(consolidatedFeature.getScatter(), strings);
        this.putLShort(strings.addAndGetIndex(consolidatedFeature.getFeature()));
        this.putLShort(strings.addAndGetIndex(consolidatedFeature.getIdentifier()));
        this.putLShort(strings.addAndGetIndex(consolidatedFeature.getPass()));
        this.putBoolean(consolidatedFeature.isInternalUse());
    }

    protected void putScatterParam(BiomeScatterParamData scatterParam, SequencedHashSet<String> strings) {
        this.putArray(scatterParam.getCoordinates(), (coordinate) -> this.putCoordinate(coordinate, strings));
        this.putVarInt(scatterParam.getEvalOrder().ordinal());
        this.putVarInt(scatterParam.getChancePercentType() == null ? -1 : scatterParam.getChancePercentType().ordinal());
        this.putLShort(strings.addAndGetIndex(scatterParam.getChancePercent()));
        this.putLInt(scatterParam.getChanceNumerator());
        this.putLInt(scatterParam.getChangeDenominator());
        this.putVarInt(scatterParam.getIterationsType() == null ? -1 : scatterParam.getIterationsType().ordinal());
        this.putLShort(strings.addAndGetIndex(scatterParam.getIterations()));
    }

    protected void putCoordinate(BiomeCoordinateData coordinate, SequencedHashSet<String> strings) {
        this.putExpressionOp(coordinate.getMinValueType());
        this.putLShort(strings.addAndGetIndex(coordinate.getMinValue()));
        this.putExpressionOp(coordinate.getMaxValueType());
        this.putLShort(strings.addAndGetIndex(coordinate.getMaxValue()));
        this.putLInt((int) coordinate.getGridOffset());
        this.putLInt((int) coordinate.getGridStepSize());
        this.putVarInt(coordinate.getDistribution().ordinal());
    }

    protected void putMountainParamsData(BiomeMountainParamsData mountainParams) {
        this.putBlockNetId(mountainParams.getSteepBlock());
        this.putBoolean(mountainParams.isNorthSlopes());
        this.putBoolean(mountainParams.isSouthSlopes());
        this.putBoolean(mountainParams.isWestSlopes());
        this.putBoolean(mountainParams.isEastSlopes());
        this.putBoolean(mountainParams.isTopSlideEnabled());
    }

    protected void putSurfaceMaterialAdjustment(BiomeSurfaceMaterialAdjustmentData surfaceMaterialAdjustment, SequencedHashSet<String> strings) {
        this.putArray(surfaceMaterialAdjustment.getBiomeElements(), (biomeElement) -> this.putBiomeElement(biomeElement, strings));
    }

    protected void putBiomeElement(BiomeElementData biomeElement, SequencedHashSet<String> strings) {
        this.putLFloat(biomeElement.getNoiseFrequencyScale());
        this.putLFloat(biomeElement.getNoiseLowerBound());
        this.putLFloat(biomeElement.getNoiseUpperBound());
        this.putExpressionOp(biomeElement.getHeightMinType());
        this.putLShort(strings.addAndGetIndex(biomeElement.getHeightMin()));
        this.putExpressionOp(biomeElement.getHeightMaxType());
        this.putLShort(strings.addAndGetIndex(biomeElement.getHeightMax()));
        this.putSurfaceMaterial(biomeElement.getAdjustedMaterials());
    }

    protected void putSurfaceMaterial(BiomeSurfaceMaterialData surfaceMaterial) {
        this.putBlockNetId(surfaceMaterial.getTopBlock());
        this.putBlockNetId(surfaceMaterial.getMidBlock());
        this.putBlockNetId(surfaceMaterial.getSeaFloorBlock());
        this.putBlockNetId(surfaceMaterial.getFoundationBlock());
        this.putBlockNetId(surfaceMaterial.getSeaBlock());
        this.putLInt(surfaceMaterial.getSeaFloorDepth());
    }

    protected void putMesaSurface(BiomeMesaSurfaceData mesaSurface) {
        this.putBlockNetId(mesaSurface.getClayMaterial());
        this.putBlockNetId(mesaSurface.getHardClayMaterial());
        this.putBoolean(mesaSurface.isBrycePillars());
        this.putBoolean(mesaSurface.isHasForest());
    }

    protected void putCappedSurface(BiomeCappedSurfaceData cappedSurface) {
        this.putArray(cappedSurface.getFloorBlocks(), this::putBlockNetId);
        this.putArray(cappedSurface.getCeilingBlocks(), this::putBlockNetId);
        this.putOptionalNull(cappedSurface.getSeaBlock(), this::putBlockNetId);
        this.putOptionalNull(cappedSurface.getFoundationBlock(), this::putBlockNetId);
        this.putOptionalNull(cappedSurface.getBeachBlock(), this::putBlockNetId);
    }

    protected void putOverworldGenRules(BiomeOverworldGenRulesData overworldGenRules, SequencedHashSet<String> strings) {
        Consumer<BiomeWeightedData> writeWeight = (data) -> this.putWeight(data, strings);
        this.putArray(overworldGenRules.getHillsTransformations(), writeWeight);
        this.putArray(overworldGenRules.getMutateTransformations(), writeWeight);
        this.putArray(overworldGenRules.getRiverTransformations(), writeWeight);
        this.putArray(overworldGenRules.getShoreTransformations(), writeWeight);
        Consumer<BiomeConditionalTransformationData> writeConditionalTransformation = (data) -> this.putConditionalTransformation(data, strings);
        this.putArray(overworldGenRules.getPreHillsEdgeTransformations(), writeConditionalTransformation);
        this.putArray(overworldGenRules.getPostShoreTransformations(), writeConditionalTransformation);
        this.putArray(overworldGenRules.getClimateTransformations(), this::putWeightedTemperature);
    }

    protected void putWeight(BiomeWeightedData weightedData, SequencedHashSet<String> strings) {
        this.putLShort(strings.addAndGetIndex(weightedData.getBiome()));
        this.putLInt(weightedData.getWeight());
    }

    protected void putConditionalTransformation(BiomeConditionalTransformationData conditionalTransformation, SequencedHashSet<String> strings) {
        this.putArray(conditionalTransformation.getWeightedBiomes(), (data) -> putWeight(data, strings));
        this.putLShort(strings.addAndGetIndex(conditionalTransformation.getConditionJson()));
        this.putLInt((int) conditionalTransformation.getMinPassingNeighbors());
    }

    protected void putWeightedTemperature(BiomeWeightedTemperatureData weightedTemperature) {
        this.putVarInt(weightedTemperature.getTemperature().ordinal());
        this.putLInt((int) weightedTemperature.getWeight());
    }

    protected void putMultinoiseGenRules(BiomeMultinoiseGenRulesData multinoiseGenRules) {
        this.putLFloat(multinoiseGenRules.getTemperature());
        this.putLFloat(multinoiseGenRules.getHumidity());
        this.putLFloat(multinoiseGenRules.getAltitude());
        this.putLFloat(multinoiseGenRules.getWeirdness());
        this.putLFloat(multinoiseGenRules.getWeight());
    }

    protected void putLegacyWorldGenRules(BiomeLegacyWorldGenRulesData legacyWorldGenRules, SequencedHashSet<String> strings) {
        this.putArray(legacyWorldGenRules.getLegacyPreHills(), (data) -> this.putConditionalTransformation(data, strings));
    }

    protected void putExpressionOp(ExpressionOp expressionOp) {
        if (expressionOp == null) {
            this.putVarInt(-1);
            return;
        }
        this.putVarInt(expressionOp.ordinal());
    }

    protected void putBlockNetId(Block block) {
        this.putLInt(GlobalBlockPalette.getOrCreateRuntimeId(this.gameVersion, block.getId(), block.getDamage()));
    }

    protected static class ColorTypeAdapter extends TypeAdapter<Color> {

        @Override
        public void write(JsonWriter out, Color color) {
        }

        @Override
        public Color read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            int r = 0, g = 0, b = 0, a = 255;
            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "r": r = in.nextInt(); break;
                    case "g": g = in.nextInt(); break;
                    case "b": b = in.nextInt(); break;
                    case "a": a = in.nextInt(); break;
                    default: in.skipValue(); break;
                }
            }
            in.endObject();
            return new Color(r, g, b, a);
        }
    }

    protected static class BlockSerializer implements JsonSerializer<Block>, JsonDeserializer<Block> {

        @Override
        public JsonElement serialize(Block src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", src.getId());
            obj.addProperty("meta", src.getDamage());
            return obj;
        }

        @Override
        public Block deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            int id = obj.get("id").getAsInt();
            int meta = obj.get("meta").getAsInt();
            return Block.get(id, meta);
        }
    }
}
