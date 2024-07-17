package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.EntityExplosive;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityExplosionPrimeEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSkull;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class EntityCreeper extends EntityWalkingMob implements EntityExplosive {

    public static final int NETWORK_ID = 33;

    private short bombTime = 0;
    private int explodeTimer;

    public EntityCreeper(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.6f;
    }

    @Override
    public float getHeight() {
        return 1.7f;
    }

    @Override
    public double getSpeed() {
        return 0.9;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(20);

        super.initEntity();

        if (this.namedTag.contains("powered")) {
            this.setPowered(this.namedTag.getBoolean("powered"));
        }
    }

    @Override
    public void explode() {
        if (this.closed) {
            return;
        }

        EntityExplosionPrimeEvent ev = new EntityExplosionPrimeEvent(this, this.isPowered() ? 6 : 3);
        this.server.getPluginManager().callEvent(ev);

        if (!ev.isCancelled()) {
            Explosion explosion = new Explosion(this, (float) ev.getForce(), this);

            if (ev.isBlockBreaking() && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)) {
                explosion.explodeA();
            }

            explosion.explodeB();
        }

        this.close();
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (this.server.getDifficulty() < 1) {
            this.close();
            return false;
        }

        if (!this.isAlive()) {
            if (++this.deadTicks >= 23) {
                this.close();
                return false;
            }
            return true;
        }

        if (this.explodeTimer > 0) {
            if (this.explodeTimer == 1) {
                this.explode();
                return false;
            }
            this.explodeTimer--;
        }

        int tickDiff = currentTick - this.lastUpdate;
        this.lastUpdate = currentTick;
        this.entityBaseTick(tickDiff);

        if (!this.isMovement()) {
            return true;
        }

        Vector3 target = this.updateMove(tickDiff);
        if (target != null) {
            double distance = target.distanceSquared(this);
            if (distance <= 16) { // 4 blocks
                if (target instanceof EntityCreature) {
                    if (this.explodeTimer <= 0) {
                        if (bombTime == 0) {
                            this.getLevel().addLevelEvent(this, LevelEventPacket.EVENT_SOUND_TNT);
                            this.setDataFlag(DATA_FLAGS, DATA_FLAG_IGNITED, true);
                        }
                        this.bombTime += tickDiff;
                        if (this.bombTime >= 30) {
                            this.explode();
                            return false;
                        }
                    }
                    if (distance <= 1) {
                        this.stayTime = 10;
                    }
                }
            } else {
                if (this.explodeTimer <= 0) {
                    this.setDataFlag(DATA_FLAGS, DATA_FLAG_IGNITED, false);
                    this.bombTime = 0;
                }
            }
        }
        return true;
    }

    @Override
    public void attackEntity(Entity player) {}

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        drops.add(Item.get(Item.GUNPOWDER, 0, Utils.rand(0, 2)));

        if (this.lastDamageCause instanceof EntityDamageByEntityEvent) {
            Entity killer = ((EntityDamageByEntityEvent) this.lastDamageCause).getDamager();

            if (killer instanceof EntitySkeleton || killer instanceof EntityStray) {
                drops.add(Item.get(Utils.rand(500, 511), 0, 1));
            }

            if (killer instanceof EntityCreeper) {
                if (((EntityCreeper) killer).isPowered()) {
                    drops.add(Item.get(Item.SKULL, ItemSkull.CREEPER_HEAD, 1));
                }
            }
        }

        return drops.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public int getKillExperience() {
        return 5;
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (item.getId() == Item.FLINT_AND_STEEL && this.explodeTimer <= 0) {
            level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_IGNITE);
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_IGNITED, true);
            this.getLevel().addLevelEvent(this, LevelEventPacket.EVENT_SOUND_TNT);
            this.stayTime = 31;
            this.explodeTimer = 31;
            return true;
        }

        return super.onInteract(player, item, clickedPos);
    }

    public boolean isPowered() {
        return this.getDataFlag(DATA_FLAGS, DATA_FLAG_POWERED);
    }

    public void setPowered(boolean charged) {
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_POWERED, charged);
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.namedTag.putBoolean("powered", this.isPowered());
    }

    @Override
    public void onStruckByLightning(Entity lightning) {
        if (this.attack(new EntityDamageByEntityEvent(lightning, this, EntityDamageEvent.DamageCause.LIGHTNING, 5))) {
            if (this.fireTicks < 160) {
                this.setOnFire(8);
            }
            this.setPowered(true);
        }
    }
}
