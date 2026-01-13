package cn.nukkit.level;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockTNT;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityShulkerBox;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityExplosive;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.event.block.BlockExplodeEvent;
import cn.nukkit.event.block.BlockUpdateEvent;
import cn.nukkit.event.entity.*;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.particle.HugeExplodeSeedParticle;
import cn.nukkit.level.util.ExplosionSource;
import cn.nukkit.math.*;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Hash;
import cn.nukkit.utils.Utils;
import it.unimi.dsi.fastutil.longs.LongArraySet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Explosion that can't break stone (for fireballs)
 */
public class WeakExplosion extends Explosion {

    private final Level level;
    private final Position source;
    private final double size;
    private final ExplosionSource sourceObject;
    private boolean doesDamage = true;
    private List<Block> affectedBlocks = new ArrayList<>();
    private Set<Block> fireIgnitions;
    private double fireSpawnChance;

    public WeakExplosion(Position center, double size, Entity sourceObject) {
        this(center, size, new ExplosionSource.EntitySource(sourceObject));
    }

    public WeakExplosion(Position center, double size, Block sourceObject) {
        this(center, size, new ExplosionSource.BlockSource(sourceObject));
    }

    protected WeakExplosion(Position center, double size, ExplosionSource sourceObject) {
        super(center, size, sourceObject);
        this.level = center.getLevel();
        this.source = center;
        this.size = Math.max(size, 0);
        this.sourceObject = sourceObject;
    }

    @Override
    public boolean explodeA() {
        if (this.sourceObject instanceof ExplosionSource.EntitySource entitySource) {
            Entity entity = entitySource.entity();
            if (entity instanceof EntityExplosive && entity.isInsideOfWater()) {
                this.doesDamage = false;
                return true;
            }
        }

        if (this.size < 0.1) return false;
        if (!level.getServer().explosionBreakBlocks) return true;

        Vector3 vector = new Vector3(0, 0, 0);
        Vector3 vBlock = new Vector3(0, 0, 0);
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                for (int k = 0; k < 16; ++k) {
                    if (i == 0 || i == 15 || j == 0 || j == 15 || k == 0 || k == 15) {
                        vector.setComponents((double) i / (double) 15 * 2d - 1, (double) j / (double) 15 * 2d - 1, (double) k / (double) 15 * 2d - 1);
                        double len = vector.length();
                        vector.setComponents((vector.x / len) * 0.3d, (vector.y / len) * 0.3d, (vector.z / len) * 0.3d);
                        double pointerX = this.source.x;
                        double pointerY = this.source.y;
                        double pointerZ = this.source.z;
                        for (double blastForce = this.size * (Utils.random.nextInt(700, 1301)) / 1000d; blastForce > 0; blastForce -= 0.22499999999999998) {
                            int x = (int) pointerX;
                            int y = (int) pointerY;
                            int z = (int) pointerZ;
                            vBlock.x = pointerX >= x ? x : x - 1;
                            vBlock.y = pointerY >= y ? y : y - 1;
                            vBlock.z = pointerZ >= z ? z : z - 1;
                            if (!level.isYInRange(vBlock.y)) {
                                break;
                            }
                            Block block = this.level.getBlock(vBlock);
                            if (block.getId() != 0 && block.getResistance() < 20) {
                                blastForce -= (block.getResistance() / 5 + 0.3d) * 0.3d;
                                if (blastForce > 0) {
                                    if (!this.affectedBlocks.contains(block)) {
                                        this.affectedBlocks.add(block);
                                    }
                                }
                            }
                            pointerX += vector.x;
                            pointerY += vector.y;
                            pointerZ += vector.z;
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean explodeB() {
        LongArraySet updateBlocks = new LongArraySet();
        double yield = (1d / this.size) * 100d;
        if (this.sourceObject instanceof ExplosionSource.EntitySource entitySource) {
            Entity exploder = entitySource.entity();
            EntityExplodeEvent ev = new EntityExplodeEvent(exploder, this.source, this.affectedBlocks, yield);
            this.level.getServer().getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                return false;
            } else {
                yield = ev.getYield();
                this.affectedBlocks = ev.getBlockList();
            }
        }

        double explosionSize = this.size * 2d;
        double minX = NukkitMath.floorDouble(this.source.x - explosionSize - 1);
        double maxX = NukkitMath.ceilDouble(this.source.x + explosionSize + 1);
        double minY = NukkitMath.floorDouble(this.source.y - explosionSize - 1);
        double maxY = NukkitMath.ceilDouble(this.source.y + explosionSize + 1);
        double minZ = NukkitMath.floorDouble(this.source.z - explosionSize - 1);
        double maxZ = NukkitMath.ceilDouble(this.source.z + explosionSize + 1);
        AxisAlignedBB explosionBB = new SimpleAxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);

        Entity[] list = this.level.getNearbyEntities(explosionBB,
                this.sourceObject instanceof ExplosionSource.EntitySource es ? es.entity() : null);

        for (Entity entity : list) {
            double distance = entity.distance(this.source) / explosionSize;
            if (distance <= 1) {
                Vector3 motion = entity.subtract(this.source).normalize();
                float blockDensity = 1 - level.getBlockDensity(this.source, entity.boundingBox);
                double impact = (1.0 - distance) * blockDensity;
                float damage = this.doesDamage ? (float) (((impact * impact + impact) / 2) * 5 * explosionSize + 1) : 0f;

                if (this.sourceObject instanceof ExplosionSource.EntitySource es) {
                    entity.attack(new EntityDamageByEntityEvent(es.entity(), entity, DamageCause.ENTITY_EXPLOSION, damage));
                } else if (this.sourceObject instanceof ExplosionSource.BlockSource bs) {
                    entity.attack(new EntityDamageByBlockEvent(bs.block(), entity, DamageCause.BLOCK_EXPLOSION, damage));
                } else {
                    entity.attack(new EntityDamageEvent(entity, DamageCause.BLOCK_EXPLOSION, damage));
                }

                if (!(entity instanceof EntityItem || entity instanceof EntityXPOrb)) {
                    entity.setMotion(motion.multiply(impact));
                }
            }
        }

        ItemBlock air = new ItemBlock(Block.get(BlockID.AIR));
        BlockEntity container;

        for (Block block : this.affectedBlocks) {
            if (block.getId() == Block.TNT) {
                Entity exploder = (this.sourceObject instanceof ExplosionSource.EntitySource es) ? es.entity() : null;
                ((BlockTNT) block).prime(Utils.rand(10, 30), exploder);
            } else if (block.getId() == Block.BED_BLOCK && (block.getDamage() & 0x08) == 0x08) {
                this.level.setBlockAt((int) block.x, (int) block.y, (int) block.z, Block.AIR);
                continue; // We don't want drops from both bed parts
            } else if ((container = block.getLevel().getBlockEntity(block)) instanceof InventoryHolder inventoryHolder) {
                if (block.getLevel().getGameRules().getBoolean(GameRule.DO_TILE_DROPS)) {
                    if (container instanceof BlockEntityShulkerBox) {
                        this.level.dropItem(block.add(0.5, 0.5, 0.5), block.toItem());
                        inventoryHolder.getInventory().clearAll();
                    } else {
                        for (Item drop : inventoryHolder.getInventory().getContents().values()) {
                            this.level.dropItem(block.add(0.5, 0.5, 0.5), drop);
                        }
                        inventoryHolder.getInventory().clearAll();
                    }
                }
            } else if (Math.random() * 100 < yield) {
                for (Item drop : block.getDrops(air)) {
                    this.level.dropItem(block.add(0.5, 0.5, 0.5), drop);
                }
            }

            this.level.setBlockAt((int) block.x, (int) block.y, (int) block.z, BlockID.AIR);

            Vector3 pos = new Vector3(block.x, block.y, block.z);

            for (BlockFace side : BlockFace.values()) {
                Vector3 sideBlock = pos.getSide(side);
                long index = Hash.hashBlock((int) sideBlock.x, (int) sideBlock.y, (int) sideBlock.z);
                if (!this.affectedBlocks.contains(sideBlock) && !updateBlocks.contains(index)) {
                    this.processBlockUpdate(sideBlock);
                    updateBlocks.add(index);
                    this.level.antiXrayOnBlockChange(null, block, 0);
                }
            }
        }
        this.level.addParticle(new HugeExplodeSeedParticle(this.source));
        this.level.addLevelSoundEvent(source, LevelSoundEventPacket.SOUND_EXPLODE);
        return true;
    }

    private void processBlockUpdate(Vector3 pos) {
        Block block = this.level.getBlock(pos);
        BlockUpdateEvent ev = new BlockUpdateEvent(block);
        this.level.getServer().getPluginManager().callEvent(ev);
        if (!ev.isCancelled()) {
            ev.getBlock().onUpdate(Level.BLOCK_UPDATE_NORMAL);
        }
    }
}