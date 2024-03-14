package cn.nukkit.level;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockTNT;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityShulkerBox;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityExplosive;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.event.block.BlockUpdateEvent;
import cn.nukkit.event.entity.EntityDamageByBlockEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.particle.HugeExplodeSeedParticle;
import cn.nukkit.math.*;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Hash;
import cn.nukkit.utils.Utils;
import it.unimi.dsi.fastutil.longs.LongArraySet;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Angelic47
 * Nukkit Project
 */
public class Explosion {

    private static final int rays = 16;
    private final Level level;
    private final Position source;
    private final double size;

    private List<Block> affectedBlocks = new ArrayList<>();
    private static final double stepLen = 0.3d;

    private final Object what;
    private boolean doesDamage = true;

    public Explosion(Position center, double size, Entity what) {
        this(center, size, (Object) what);
    }

    public Explosion(Position center, double size, Block what) {
        this(center, size, (Object) what);
    }

    protected Explosion(Position center, double size, Object what) {
        this.level = center.getLevel();
        this.source = center;
        this.size = Math.max(size, 0);
        this.what = what;
    }

    /**
     * @return bool
     */
    public boolean explode() {
        if (explodeA()) {
            return explodeB();
        }
        return false;
    }

    /**
     * @return bool
     */
    public boolean explodeA() {
        if (what instanceof EntityExplosive && ((Entity) what).isInsideOfWater()) {
            this.doesDamage = false;
            return true;
        }
        if (this.size < 0.1) return false;
        if (!level.getServer().explosionBreakBlocks) return true;

        Vector3 vector = new Vector3(0, 0, 0);
        Vector3 vBlock = new Vector3(0, 0, 0);

        int mRays = 15;
        for (int i = 0; i < rays; ++i) {
            for (int j = 0; j < rays; ++j) {
                for (int k = 0; k < rays; ++k) {
                    if (i == 0 || i == mRays || j == 0 || j == mRays || k == 0 || k == mRays) {
                        vector.setComponents((double) i / (double) mRays * 2d - 1, (double) j / (double) mRays * 2d - 1, (double) k / (double) mRays * 2d - 1);
                        double len = vector.length();
                        vector.setComponents((vector.x / len) * stepLen, (vector.y / len) * stepLen, (vector.z / len) * stepLen);
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

                            if (block.getId() != 0 && block.getId() != 7) {
                                blastForce -= (block.getResistance() / 5 + 0.3d) * stepLen;
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

    public boolean explodeB() {
        LongArraySet updateBlocks = new LongArraySet();
        double yield = (1d / this.size) * 100d;

        if (this.what instanceof Entity) {
            EntityExplodeEvent ev = new EntityExplodeEvent((Entity) this.what, this.source, this.affectedBlocks, yield);
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
        Entity[] list = this.level.getNearbyEntities(explosionBB, this.what instanceof Entity ? (Entity) this.what : null);
        for (Entity entity : list) {
            double distance = entity.distance(this.source) / explosionSize;

            if (distance <= 1) {
                Vector3 motion = entity.subtract(this.source).normalize();
                float exposure = 1 - this.level.getBlockDensity(this.source, entity.boundingBox);
                double impact = (1 - distance) * exposure;

                int damage = this.doesDamage ? (int) (((impact * impact + impact) / 2) * 8 * explosionSize + 1) : 0;

                if (this.what instanceof Entity) {
                    entity.attack(new EntityDamageByEntityEvent((Entity) this.what, entity, DamageCause.ENTITY_EXPLOSION, damage));
                } else if (this.what instanceof Block) {
                    entity.attack(new EntityDamageByBlockEvent((Block) this.what, entity, DamageCause.BLOCK_EXPLOSION, damage));
                } else {
                    entity.attack(new EntityDamageEvent(entity, DamageCause.BLOCK_EXPLOSION, damage));
                }

                if (!(entity instanceof EntityItem || entity instanceof EntityXPOrb)) {
                    entity.setMotion(motion.multiply(impact));
                }
            }
        }

        ItemTool netheritePickaxe = (ItemTool) Item.get(Item.NETHERITE_PICKAXE);
        netheritePickaxe.setUnbreakable(true);
        BlockEntity container;
        for (Block block : this.affectedBlocks) {
            if (block.getId() == Block.TNT) {
                ((BlockTNT) block).prime(Utils.rand(10, 30), this.what instanceof Entity ? (Entity) this.what : null);
            } else if (block.getId() == Block.BED_BLOCK && (block.getDamage() & 0x08) == 0x08) {
                this.level.setBlockAt((int) block.x, (int) block.y, (int) block.z, Block.AIR);
                continue; // We don't want drops from both bed parts
            } else if ((container = block.getLevel().getBlockEntity(block)) instanceof InventoryHolder) {
                if (block.getLevel().getGameRules().getBoolean(GameRule.DO_TILE_DROPS)) {
                    if (container instanceof BlockEntityShulkerBox) {
                        this.level.dropItem(block.add(0.5, 0.5, 0.5), block.toItem());
                        ((InventoryHolder) container).getInventory().clearAll();
                    } else {
                        for (Item drop : ((InventoryHolder) container).getInventory().getContents().values()) {
                            this.level.dropItem(block.add(0.5, 0.5, 0.5), drop);
                        }
                        ((InventoryHolder) container).getInventory().clearAll();
                    }
                }
            } else if (Math.random() * 100 < yield) {
                for (Item drop : block.getDrops(netheritePickaxe)) {
                    this.level.dropItem(block.add(0.5, 0.5, 0.5), drop);
                }
            }

            this.level.setBlockAt((int) block.x, (int) block.y, (int) block.z, Block.AIR);

            Vector3 pos = new Vector3(block.x, block.y, block.z);

            for (BlockFace side : BlockFace.values()) {
                Vector3 sideBlock = pos.getSide(side);
                long index = Hash.hashBlock((int) sideBlock.x, (int) sideBlock.y, (int) sideBlock.z);
                if (!this.affectedBlocks.contains(sideBlock) && !updateBlocks.contains(index)) {
                    BlockUpdateEvent ev = new BlockUpdateEvent(this.level.getBlock(sideBlock));
                    this.level.getServer().getPluginManager().callEvent(ev);
                    if (!ev.isCancelled()) {
                        ev.getBlock().onUpdate(Level.BLOCK_UPDATE_NORMAL);
                    }
                    updateBlocks.add(index);
                    this.level.antiXrayOnBlockChange(null, block, 0);
                }
            }
        }

        this.level.addParticle(new HugeExplodeSeedParticle(this.source));
        this.level.addLevelSoundEvent(source, LevelSoundEventPacket.SOUND_EXPLODE);
        return true;
    }
}
