package cn.nukkit.entity.projectile;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.CreatureSpawnEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerTeleportEvent.TeleportCause;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.utils.Utils;

public class EntityEnderPearl extends EntityProjectile {

    public static final int NETWORK_ID = 87;

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.25f;
    }

    @Override
    public float getLength() {
        return 0.25f;
    }

    @Override
    public float getHeight() {
        return 0.25f;
    }

    @Override
    protected float getGravity() {
        return 0.03f;
    }

    @Override
    protected float getDrag() {
        return 0.01f;
    }

    public EntityEnderPearl(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntityEnderPearl(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        super(chunk, nbt, shootingEntity);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (this.isCollided && this.shootingEntity instanceof Player) {
            Block[] blocks = getCollisionHelper().getCollisionBlocks();

            boolean portal = false;
            for (Block collided : blocks) {
                if (collided.getId() == Block.NETHER_PORTAL) {
                    portal = true;
                }
            }

            this.close();

            if (!portal) {
                teleport();

                if (Server.getInstance().mobsFromBlocks) {
                    if (Utils.rand(1, 20) == 5) {
                        Position spawnPos = add(0.5, 1, 0.5);

                        CreatureSpawnEvent ev = new CreatureSpawnEvent(NETWORK_ID, spawnPos, CreatureSpawnEvent.SpawnReason.ENDER_PEARL, this.shootingEntity);
                        level.getServer().getPluginManager().callEvent(ev);

                        if (ev.isCancelled()) {
                            return false;
                        }

                        Entity entity = Entity.createEntity("Endermite", spawnPos);
                        if (entity != null) {
                            entity.spawnToAll();
                        }
                    }
                }
            }

            return false;
        }

        if (this.age > 1200 || this.isCollided) {
            this.close();
        }

        return super.onUpdate(currentTick);
    }
    
    @Override
    public void onCollideWithEntity(Entity entity) {
        if (this.shootingEntity instanceof Player) {
            teleport();
        }

        super.onCollideWithEntity(entity);
    }

    private void teleport() {
        if (!this.level.equals(this.shootingEntity.getLevel())) {
            return;
        }

        this.level.addLevelEvent(this.shootingEntity.add(0.5, 0.5, 0.5), LevelEventPacket.EVENT_SOUND_ENDERMAN_TELEPORT);

        this.shootingEntity.teleport(new Vector3(NukkitMath.floorDouble(this.x) + 0.5, this.y, NukkitMath.floorDouble(this.z) + 0.5), TeleportCause.ENDER_PEARL);

        int gamemode = ((Player) this.shootingEntity).getGamemode();
        if (gamemode == 0 || gamemode == 2) {
            this.shootingEntity.attack(new EntityDamageByEntityEvent(this, shootingEntity, EntityDamageEvent.DamageCause.FALL, 5f, 0f));
        }

        this.level.addLevelEvent(this, LevelEventPacket.EVENT_PARTICLE_ENDERMAN_TELEPORT);
        this.level.addLevelEvent(this.shootingEntity.add(0.5, 0.5, 0.5), LevelEventPacket.EVENT_SOUND_ENDERMAN_TELEPORT);
    }
}
