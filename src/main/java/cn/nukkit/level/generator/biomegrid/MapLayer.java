package cn.nukkit.level.generator.biomegrid;

import cn.nukkit.level.Level;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.generator.util.Vanillaset;
import cn.nukkit.math.NukkitRandom;



public abstract class MapLayer {

    private final NukkitRandom random = new NukkitRandom();
    private final long seed;

    public MapLayer(long seed) {
        this.seed = seed;
    }

    /**
     * Creates the instances for the given map.
     * @param seed the world seed
     * @param environment the type of dimension
     * @param worldType the world generator
     * @return an array of all map layers this dimension needs
     */
    public static MapLayer[] initialize(long seed, int environment, int worldType) {
        if (environment == Level.DIMENSION_OVERWORLD && worldType == Generator.TYPE_FLAT) {
            return new MapLayer[]{new MapLayerBiomeConstant(seed, EnumBiome.PLAINS.id), null};
        } else if (environment == Level.DIMENSION_NETHER) {
            return new MapLayer[]{new MapLayerBiomeConstant(seed, EnumBiome.HELL.id), null};
        } else if (environment == Level.DIMENSION_THE_END) {
            return new MapLayer[]{new MapLayerBiomeConstant(seed, 9), null};//EnumBiome.THE_END.id
        }

        int zoom = 2;
        if (worldType == Vanillaset.TYPE_LARGE_BIOMES) {
            zoom = 4;
        }

        MapLayer layer = new MapLayerNoise(seed); // this is initial land spread layer
        layer = new MapLayerWhittaker(seed + 1, layer, MapLayerWhittaker.ClimateType.WARM_WET);
        layer = new MapLayerWhittaker(seed + 1, layer, MapLayerWhittaker.ClimateType.COLD_DRY);
        layer = new MapLayerWhittaker(seed + 2, layer, MapLayerWhittaker.ClimateType.LARGER_BIOMES);
        for (int i = 0; i < 2; i++) {
            layer = new MapLayerZoom(seed + 100 + i, layer, MapLayerZoom.ZoomType.BLURRY);
        }
        for (int i = 0; i < 2; i++) {
            layer = new MapLayerErosion(seed + 3 + i, layer);
        }
        layer = new MapLayerDeepOcean(seed + 4, layer);

        MapLayer layerMountains = new MapLayerBiomeVariation(seed + 200, layer);
        for (int i = 0; i < 2; i++) {
            layerMountains = new MapLayerZoom(seed + 200 + i, layerMountains);
        }

        layer = new MapLayerBiome(seed + 5, layer);
        for (int i = 0; i < 2; i++) {
            layer = new MapLayerZoom(seed + 200 + i, layer);
        }
        layer = new MapLayerBiomeEdge(seed + 200, layer);
        layer = new MapLayerBiomeVariation(seed + 200, layer, layerMountains);
        layer = new MapLayerRarePlains(seed + 201, layer);
        layer = new MapLayerZoom(seed + 300, layer);
        layer = new MapLayerErosion(seed + 6, layer);
        layer = new MapLayerZoom(seed + 400, layer);
        layer = new MapLayerBiomeEdgeThin(seed + 400, layer);
        layer = new MapLayerShore(seed + 7, layer);
        for (int i = 0; i < zoom; i++) {
            layer = new MapLayerZoom(seed + 500 + i, layer);
        }

        MapLayer layerRiver = layerMountains;
        layerRiver = new MapLayerZoom(seed + 300, layerRiver);
        layerRiver = new MapLayerZoom(seed + 400, layerRiver);
        for (int i = 0; i < zoom; i++) {
            layerRiver = new MapLayerZoom(seed + 500 + i, layerRiver);
        }
        layerRiver = new MapLayerRiver(seed + 10, layerRiver);
        layer = new MapLayerRiver(seed + 1000, layerRiver, layer);

        MapLayer layerLowerRes = layer;
        for (int i = 0; i < 2; i++) {
            layer = new MapLayerZoom(seed + 2000 + i, layer);
        }

        layer = new MapLayerSmooth(seed + 1001, layer);

        return new MapLayer[]{layer, layerLowerRes};
    }

    public void setCoordsSeed(int x, int z) {
        this.random.setSeed(this.seed);
        this.random.setSeed(x * this.random.nextInt() + z * this.random.nextInt() ^ this.seed);
    }

    public int nextInt(int max) {
        return this.random.nextBoundedInt(max);
    }

    public abstract int[] generateValues(int x, int z, int sizeX, int sizeZ);
}
