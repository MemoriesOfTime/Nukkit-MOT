package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityExpBottle;
import cn.nukkit.entity.projectile.EntityEnderPearl;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
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

    public ProjectileItem(int id, Integer meta, int count, String name) {
        super(id, meta, count, name);
    }

    abstract public String getProjectileEntityType();

    abstract public float getThrowForce();

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", player.x))
                        .add(new DoubleTag("", player.y + player.getEyeHeight()))
                        .add(new DoubleTag("", player.z)))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("", directionVector.x))
                        .add(new DoubleTag("", directionVector.y))
                        .add(new DoubleTag("", directionVector.z)))
                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("", (float) player.yaw))
                        .add(new FloatTag("", (float) player.pitch)));

        this.correctNBT(nbt);

        Entity projectile = Entity.createEntity(this.getProjectileEntityType(), player.getLevel().getChunk(player.getFloorX() >> 4, player.getFloorZ() >> 4), nbt, player);
        if (projectile != null) {
            if (projectile instanceof EntityEnderPearl) {
                if (player.getServer().getTick() - player.getLastEnderPearlThrowingTick() < 20) {
                    projectile.close();
                    return false;
                }
            }

            projectile.setMotion(projectile.getMotion().multiply(this.getThrowForce()));

            if (projectile instanceof EntityProjectile) {
                ProjectileLaunchEvent ev = new ProjectileLaunchEvent((EntityProjectile) projectile);

                player.getServer().getPluginManager().callEvent(ev);

                if (ev.isCancelled()) {
                    projectile.close();
                } else if (player.getGamemode() == Player.CREATIVE && projectile instanceof EntityExpBottle && !player.getServer().xpBottlesOnCreative) {
                    ev.setCancelled(true);
                    projectile.close();
                    player.sendMessage("\u00A7cXP bottles are disabled on creative");
                } else {
                    if (!player.isCreative()) {
                        this.count--;
                    }
                    if (projectile instanceof EntityEnderPearl) {
                        player.onThrowEnderPearl();
                    }
                    projectile.spawnToAll();
                    player.getLevel().addLevelSoundEvent(player, LevelSoundEventPacket.SOUND_BOW);
                }
            }
        }

        return true;
    }

    protected void correctNBT(CompoundTag nbt) {
    }
}
