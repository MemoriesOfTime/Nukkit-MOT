package cn.nukkit.level.generator.object.ore;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.math.MathHelper;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.NukkitRandom;

import static cn.nukkit.block.BlockID.STONE;

public class OreType {
    public static final OreType[] EMPTY_ARRAY = new OreType[0];

    public final int fullId;
    public final int blockId;
    public final int blockData;
    public final int clusterCount;
    public final int clusterSize;
    public final int maxHeight;
    public final int minHeight;
    public final int replaceBlockId;

    public OreType(final Block material, final int clusterCount, final int clusterSize, final int minHeight, final int maxHeight) {
        this(material, clusterCount, clusterSize, minHeight, maxHeight, STONE);
    }

    public OreType(final Block material, final int clusterCount, final int clusterSize, final int minHeight, final int maxHeight, final int replaceBlockId) {
        fullId = material.getFullId();
        blockId = material.getId();
        blockData = material.getDamage();
        this.clusterCount = clusterCount;
        this.clusterSize = clusterSize;
        this.maxHeight = maxHeight;
        this.minHeight = minHeight;
        this.replaceBlockId = replaceBlockId;
    }

    public boolean spawn(final ChunkManager level, final NukkitRandom rand, final int replaceId, final int x, final int y, final int z) {
        final float piScaled = rand.nextFloat() * (float) Math.PI;
        final double scaleMaxX = (float) (x + 8) + MathHelper.sin(piScaled) * (float) clusterSize / 8.0F;
        final double scaleMinX = (float) (x + 8) - MathHelper.sin(piScaled) * (float) clusterSize / 8.0F;
        final double scaleMaxZ = (float) (z + 8) + MathHelper.cos(piScaled) * (float) clusterSize / 8.0F;
        final double scaleMinZ = (float) (z + 8) - MathHelper.cos(piScaled) * (float) clusterSize / 8.0F;
        final double scaleMaxY = y + rand.nextBoundedInt(3) - 2;
        final double scaleMinY = y + rand.nextBoundedInt(3) - 2;

        for (int i = 0; i < clusterSize; ++i) {
            final float sizeIncr = (float) i / (float) clusterSize;
            final double scaleX = scaleMaxX + (scaleMinX - scaleMaxX) * (double) sizeIncr;
            final double scaleY = scaleMaxY + (scaleMinY - scaleMaxY) * (double) sizeIncr;
            final double scaleZ = scaleMaxZ + (scaleMinZ - scaleMaxZ) * (double) sizeIncr;
            final double randSizeOffset = rand.nextDouble() * (double) clusterSize / 16.0D;
            final double randVec1 = (double) (MathHelper.sin((float) Math.PI * sizeIncr) + 1.0F) * randSizeOffset + 1.0D;
            final double randVec2 = (double) (MathHelper.sin((float) Math.PI * sizeIncr) + 1.0F) * randSizeOffset + 1.0D;
            final int minX = NukkitMath.floorDouble(scaleX - randVec1 / 2.0D);
            final int minY = Math.max(NukkitMath.floorDouble(scaleY - randVec2 / 2.0D), level.getMinBlockY());
            final int minZ = NukkitMath.floorDouble(scaleZ - randVec1 / 2.0D);
            final int maxX = NukkitMath.floorDouble(scaleX + randVec1 / 2.0D);
            final int maxY = Math.min(NukkitMath.floorDouble(scaleY + randVec2 / 2.0D), level.getMaxBlockY());
            final int maxZ = NukkitMath.floorDouble(scaleZ + randVec1 / 2.0D);

            for (int xSeg = minX; xSeg <= maxX; ++xSeg) {
                final double xVal = ((double) xSeg + 0.5D - scaleX) / (randVec1 / 2.0D);

                if (xVal * xVal < 1.0D) {
                    for (int ySeg = minY; ySeg <= maxY; ++ySeg) {
                        final double yVal = ((double) ySeg + 0.5D - scaleY) / (randVec2 / 2.0D);

                        if (xVal * xVal + yVal * yVal < 1.0D) {
                            for (int zSeg = minZ; zSeg <= maxZ; ++zSeg) {
                                final double zVal = ((double) zSeg + 0.5D - scaleZ) / (randVec1 / 2.0D);

                                if (xVal * xVal + yVal * yVal + zVal * zVal < 1.0D) {
                                    if (level.getBlockIdAt(xSeg, ySeg, zSeg) == replaceBlockId) {
                                        level.setBlockAt(xSeg, ySeg, zSeg, blockId, blockData);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return true;
    }
}
