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
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Hash;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Explosion {

    private static final int RAYS = 16;
    private final double STEP_LEN = 0.3d;

    private final Level level;
    private final Position source;
    private final double size;
    private double fireSpawnChance;

    private Set<Block> affectedBlocks;
    private Set<Block> fireIgnitions;

    private final ExplosionSource target;
    private boolean doesDamage = true;

    public Explosion(Position center, double size, Entity target) {
        this(center, size, new ExplosionSource.EntitySource(target));
    }

    public Explosion(Position center, double size, Block target) {
        this(center, size, new ExplosionSource.BlockSource(target));
    }

    protected Explosion(Position center, double size, ExplosionSource target) {
        this.level = center.getLevel();
        this.source = center;
        this.size = Math.max(size, 0);
        this.target = target;
    }

    public double getFireSpawnChance() {
        return fireSpawnChance;
    }

    public void setFireSpawnChance(double fireSpawnChance) {
        this.fireSpawnChance = fireSpawnChance;
    }

    public boolean isIncendiary() {
        return fireSpawnChance > 0;
    }

    public void setIncendiary(boolean incendiary) {
        if (!incendiary) {
            fireSpawnChance = 0;
        } else if (fireSpawnChance <= 0) {
            fireSpawnChance = 1.0 / 3.0;
        }
    }

    /**
     * @return bool
     */
    public boolean explode() {
        return explodeA() && explodeB();
    }

    /**
     * Calculates which blocks will be destroyed by this explosion. If {@link #explodeB()} is called without calling this,
     * no blocks will be destroyed.
     *
     * @return {@code true} if success
     */
    public boolean explodeA() {
        if (target instanceof ExplosionSource.EntitySource entitySource) {
            Entity entity = entitySource.entity();
            if (entity instanceof EntityExplosive) {
                Block blockLayer0 = level.getBlock(entity.floor());
                Block blockLayer1 = level.getBlock(entity.floor(), 1);
                if (blockLayer0.getId() == BlockID.WATER || blockLayer0.getId() == BlockID.STILL_WATER ||
                        blockLayer1.getId() == BlockID.WATER || blockLayer1.getId() == BlockID.STILL_WATER) {
                    this.doesDamage = false;
                    return true;
                }
            }
        }

        if (this.size < 0.1) {
            return false;
        }

        if (affectedBlocks == null) {
            affectedBlocks = new LinkedHashSet<>();
        }

        boolean incendiary = fireSpawnChance > 0;
        if (incendiary && fireIgnitions == null) {
            fireIgnitions = new LinkedHashSet<>();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector3 vector = new Vector3(0, 0, 0);
        Vector3 vBlock = new Vector3(0, 0, 0);

        int mRays = RAYS - 1;
        for (int i = 0; i < RAYS; ++i) {
            for (int j = 0; j < RAYS; ++j) {
                for (int k = 0; k < RAYS; ++k) {
                    if (i == 0 || i == mRays || j == 0 || j == mRays || k == 0 || k == mRays) {
                        vector.setComponents((double) i / mRays * 2d - 1, (double) j / mRays * 2d - 1, (double) k / mRays * 2d - 1);
                        double len = vector.length();
                        vector.setComponents((vector.x / len) * STEP_LEN, (vector.y / len) * STEP_LEN, (vector.z / len) * STEP_LEN);
                        double pointerX = this.source.x;
                        double pointerY = this.source.y;
                        double pointerZ = this.source.z;

                        for (double blastForce = this.size * random.nextDouble(0.7, 1.3); blastForce > 0; blastForce -= STEP_LEN * 0.75d) {
                            int x = (int) pointerX;
                            int y = (int) pointerY;
                            int z = (int) pointerZ;
                            vBlock.setComponents(
                                    pointerX >= x ? x : x - 1,
                                    pointerY >= y ? y : y - 1,
                                    pointerZ >= z ? z : z - 1
                            );
                            if (!this.level.isYInRange((int) vBlock.y)) {
                                break;
                            }
                            Block block = this.level.getBlock(vBlock);
                            if (!block.isAir()) {
                                Block layer1 = block.getLevelBlockAtLayer(1);
                                double resistance = Math.max(block.getResistance(), layer1.getResistance());
                                blastForce -= (resistance / 5 + 0.3d) * STEP_LEN;
                                if (blastForce > 0) {
                                    if (this.affectedBlocks.add(block)) {
                                        if (incendiary && random.nextDouble() <= fireSpawnChance) {
                                            this.fireIgnitions.add(block);
                                        }
                                        if (!layer1.isAir()) {
                                            this.affectedBlocks.add(layer1);
                                        }
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

    /**
     * Executes the explosion's effects on the world. This includes destroying blocks (if any),
     * harming and knocking back entities, and creating sounds and particles.
     *
     * @return {@code false} if explosion was canceled, otherwise {@code true}
     */
    public boolean explodeB() {
        LongArraySet updateBlocks = new LongArraySet();
        Vector3 sourceVec = this.source.floor();
        double yield = (1d / this.size) * 100d;

        if (affectedBlocks == null) {
            affectedBlocks = new LinkedHashSet<>();
        }

        if (target instanceof ExplosionSource.EntitySource entitySource) {
            Entity exploder = entitySource.entity();
            List<Block> affectedBlocksList = new ArrayList<>(this.affectedBlocks);
            EntityExplodeEvent ev = new EntityExplodeEvent(exploder, this.source, affectedBlocksList, yield);
            ev.setIgnitions(fireIgnitions == null ? new LinkedHashSet<>(0) : fireIgnitions);
            this.level.getServer().getPluginManager().callEvent(ev);
            if (ev.isCancelled()) return false;
            yield = ev.getYield();
            affectedBlocks.clear();
            affectedBlocks.addAll(ev.getBlockList());
            fireIgnitions = ev.getIgnitions();
        } else if (target instanceof ExplosionSource.BlockSource blockSource) {
            BlockExplodeEvent ev = new BlockExplodeEvent(blockSource.block(), this.source, this.affectedBlocks,
                    fireIgnitions == null ? new LinkedHashSet<>(0) : fireIgnitions, yield, this.fireSpawnChance);
            this.level.getServer().getPluginManager().callEvent(ev);
            if (ev.isCancelled()) return false;
            yield = ev.getYield();
            affectedBlocks = ev.getAffectedBlocks();
            fireIgnitions = ev.getIgnitions();
        }

        double explosionSize = this.size * 2d;
        AxisAlignedBB explosionBB = new SimpleAxisAlignedBB(
                NukkitMath.floorDouble(this.source.x - explosionSize - 1),
                NukkitMath.floorDouble(this.source.y - explosionSize - 1),
                NukkitMath.floorDouble(this.source.z - explosionSize - 1),
                NukkitMath.ceilDouble(this.source.x + explosionSize + 1),
                NukkitMath.ceilDouble(this.source.y + explosionSize + 1),
                NukkitMath.ceilDouble(this.source.z + explosionSize + 1)
        );

        Entity[] nearbyEntities = this.level.getNearbyEntities(explosionBB,
                target instanceof ExplosionSource.EntitySource es ? es.entity() : null);

        for (Entity entity : nearbyEntities) {
            double distance = entity.distance(this.source) / explosionSize;
            if (distance > 1) continue;

            Vector3 motion = entity.subtract(this.source).normalize();
            float blockDensity = 1 - level.getBlockDensity(this.source, entity.boundingBox);
            double force = this.size * 2.0;
            double d = entity.distance(sourceVec) / force;
            double impact = (1.0 - d) * blockDensity;
            float damage = this.doesDamage ? (float) ((impact * impact + impact) / 2.0 * 7.0 * force + 1.0) : 0f;

            EntityDamageEvent damageEvent;
            if (target instanceof ExplosionSource.EntitySource es) {
                damageEvent = new EntityDamageByEntityEvent(es.entity(), entity, DamageCause.ENTITY_EXPLOSION, damage);
            } else if (target instanceof ExplosionSource.BlockSource bs) {
                damageEvent = new EntityDamageByBlockEvent(bs.block(), entity, DamageCause.BLOCK_EXPLOSION, damage);
            } else {
                damageEvent = new EntityDamageEvent(entity, DamageCause.BLOCK_EXPLOSION, damage);
            }
            entity.attack(damageEvent);

            if (!(entity instanceof EntityItem || entity instanceof EntityXPOrb)) {
                entity.motionX += motion.x * impact;
                entity.motionY += motion.y * impact;
                entity.motionZ += motion.z * impact;
            }
        }

        ItemBlock air = new ItemBlock(Block.get(BlockID.AIR));
        List<Vector3> smokePositions = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (Block originalBlock : new ArrayList<>(this.affectedBlocks)) {
            Block currentBlock = this.level.getBlock((int) originalBlock.x, (int) originalBlock.y, (int) originalBlock.z, originalBlock.layer);

            if (currentBlock.getId() != originalBlock.getId()) {
                continue;
            }

            if (currentBlock instanceof BlockTNT tnt) {
                Entity exploder = (target instanceof ExplosionSource.EntitySource es) ? es.entity() : null;
                tnt.prime(random.nextInt(10, 31), exploder);
            } else {
                BlockEntity container = currentBlock.getLevel().getBlockEntity(currentBlock);
                if (container instanceof InventoryHolder inventoryHolder) {
                    if (container instanceof BlockEntityShulkerBox) {
                        this.level.dropItem(currentBlock.add(0.5, 0.5, 0.5), currentBlock.toItem());
                        inventoryHolder.getInventory().clearAll();
                    } else {
                        for (Item drop : inventoryHolder.getInventory().getContents().values()) {
                            this.level.dropItem(currentBlock.add(0.5, 0.5, 0.5), drop);
                        }
                        inventoryHolder.getInventory().clearAll();
                    }
                } else if (random.nextDouble() * 100 < yield) {
                    for (Item drop : currentBlock.getDrops(air)) {
                        this.level.dropItem(currentBlock.add(0.5, 0.5, 0.5), drop);
                    }
                }
            }

            if (random.nextInt(8) == 0) {
                smokePositions.add(currentBlock);
            }

            this.level.setBlock(new Vector3((int) currentBlock.x, (int) currentBlock.y, (int) currentBlock.z), currentBlock.layer, Block.get(BlockID.AIR));

            if (currentBlock.layer == 0) {
                Vector3 pos = new Vector3(currentBlock.x, currentBlock.y, currentBlock.z);
                for (BlockFace side : BlockFace.values()) {
                    Vector3 sideBlock = pos.getSide(side);
                    long index = Hash.hashBlock((int) sideBlock.x, (int) sideBlock.y, (int) sideBlock.z);
                    if (!this.affectedBlocks.contains(sideBlock) && !updateBlocks.contains(index)) {
                        this.processBlockUpdate(sideBlock);
                        Block layer1 = this.level.getBlock(sideBlock, 1);
                        if (!layer1.isAir()) {
                            this.processBlockUpdate(layer1);
                        }
                        updateBlocks.add(index);
                    }
                }
            }
        }

        if (fireIgnitions != null) {
            for (Vector3 pos : fireIgnitions) {
                Block toIgnite = level.getBlock(pos);
                if (toIgnite.isAir() && toIgnite.down().isSolid(BlockFace.UP)) {
                    level.setBlock(toIgnite, Block.get(BlockID.FIRE));
                }
            }
        }

        int count = smokePositions.size();
        CompoundTag data = new CompoundTag(new Object2ObjectOpenHashMap<>(count, 0.999999f))
                .putFloat("originX", (float) this.source.x)
                .putFloat("originY", (float) this.source.y)
                .putFloat("originZ", (float) this.source.z)
                .putFloat("radius", (float) this.size)
                .putInt("size", count);
        for (int i = 0; i < count; i++) {
            Vector3 pos = smokePositions.get(i);
            String prefix = "pos" + i;
            data.putFloat(prefix + "x", (float) pos.x);
            data.putFloat(prefix + "y", (float) pos.y);
            data.putFloat(prefix + "z", (float) pos.z);
        }

        this.level.addParticle(new HugeExplodeSeedParticle(this.source));
        this.level.addSound(this.source, Sound.RANDOM_EXPLODE);

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