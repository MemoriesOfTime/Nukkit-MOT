package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityEnderEye;
import cn.nukkit.entity.projectile.EntityEnderPearl;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

/**
 * @author CreeperFace
 */
public abstract class ProjectileItem extends Item {

    private volatile boolean isThrowing = false;
    private final Object throwLock = new Object();

    public ProjectileItem(int id, Integer meta, int count, String name) {
        super(id, meta, count, name);
    }

    abstract public String getProjectileEntityType();

    abstract public float getThrowForce();

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        return performThrow(player, directionVector);
    }

    /**
     * 当对着方块右键使用投掷物品时调用
     * 复用 onClickAir 的投掷逻辑，使投掷物品可以对着方块使用
     */
    @Override
    public boolean onActivate(Level level, Player player, Block block, Block target, BlockFace face, double fx, double fy, double fz) {
        return performThrow(player, player.getDirectionVector());
    }

    private boolean performThrow(Player player, Vector3 directionVector) {
        synchronized (throwLock) {
            if (isThrowing) {
                return false;
            }
            isThrowing = true;
        }

        try {
            Vector3 motion;

            if (this instanceof ItemEnderEye) {
                if (player.getLevel().getDimension() != Level.DIMENSION_OVERWORLD) {
                    return false;
                }

                Vector3 vector = player
                        .subtract(player).normalize();
                vector.y = 0.55f;
                motion = vector.divide(this.getThrowForce());
            } else {
                motion = directionVector.multiply(this.getThrowForce());
            }

            CompoundTag nbt = new CompoundTag()
                    .putList(new ListTag<DoubleTag>("Pos")
                            .add(new DoubleTag("", player.x))
                            .add(new DoubleTag("", player.y + player.getEyeHeight()))
                            .add(new DoubleTag("", player.z)))
                    .putList(new ListTag<DoubleTag>("Motion")
                            .add(new DoubleTag("", motion.x))
                            .add(new DoubleTag("", motion.y))
                            .add(new DoubleTag("", motion.z)))
                    .putList(new ListTag<FloatTag>("Rotation")
                            .add(new FloatTag("", (float) player.yaw))
                            .add(new FloatTag("", (float) player.pitch)));

            this.correctNBT(nbt);

            Entity projectile = Entity.createEntity(this.getProjectileEntityType(),
                    player.getLevel().getChunk(player.getChunkX(), player.getChunkZ()), nbt, player);

            if (projectile instanceof EntityProjectile) {
                if (projectile instanceof EntityEnderPearl || projectile instanceof EntityEnderEye) {
                    if (player.getServer().getTick() - player.getLastEnderPearlThrowingTick() < 20) {
                        projectile.close();
                        return false;
                    }
                }

                ProjectileLaunchEvent ev = new ProjectileLaunchEvent((EntityProjectile) projectile);

                player.getServer().getPluginManager().callEvent(ev);

                if (ev.isCancelled()) {
                    projectile.close();
                    return false;
                } else {
                    if (!player.isCreative()) {
                        Item itemInHand = player.getInventory().getItemInHand();

                        if (itemInHand.getId() == this.getId() &&
                                itemInHand.getDamage() == this.getDamage()) {

                            if (itemInHand.getCount() > 1) {
                                itemInHand.setCount(itemInHand.getCount() - 1);
                                player.getInventory().setItemInHand(itemInHand);
                            } else {
                                player.getInventory().setItemInHand(Item.get(Item.AIR));
                            }

                            player.getInventory().sendContents(player);
                        }
                    }

                    synchronized (this) {
                        if (this.count > 0) {
                            this.count--;
                        }
                    }

                    if (projectile instanceof EntityEnderPearl || projectile instanceof EntityEnderEye) {
                        player.onThrowEnderPearl();
                    }
                    projectile.spawnToAll();
                    player.getLevel().addLevelSoundEvent(player, LevelSoundEventPacket.SOUND_BOW);
                    return true;
                }
            }
            return false;
        } finally {
            synchronized (throwLock) {
                isThrowing = false;
            }
        }
    }

    protected void correctNBT(CompoundTag nbt) {
    }
}