package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntitySmite;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.entity.projectile.EntityThrownTrident;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityShootBowEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.MobEquipmentPacket;
import cn.nukkit.utils.Utils;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EntityDrowned extends EntityWalkingMob implements EntitySmite {

    public static final int NETWORK_ID = 110;

    protected Item tool;

    public EntityDrowned(FullChunk chunk, CompoundTag nbt) {
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
        return 1.95f;
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(20);

        super.initEntity();

        this.setDamage(new int[] { 0, 2, 3, 4 });

        if (this.namedTag.contains("Item")) {
            this.tool = NBTIO.getItemHelper(this.namedTag.getCompound("Item"));
        } else if (!this.namedTag.getBoolean("HandItemSet")) {
            this.setRandomTool();
        }
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.attackDelay > 23 && player.distanceSquared(this) <= 1) {
            this.attackDelay = 0;
            HashMap<EntityDamageEvent.DamageModifier, Float> damage = new HashMap<>();
            damage.put(EntityDamageEvent.DamageModifier.BASE, (float) this.getDamage());

            if (player instanceof Player) {
                float points = 0;
                for (Item i : ((Player) player).getInventory().getArmorContents()) {
                    points += this.getArmorPoints(i.getId());
                }

                damage.put(EntityDamageEvent.DamageModifier.ARMOR,
                        (float) (damage.getOrDefault(EntityDamageEvent.DamageModifier.ARMOR, 0f) - Math.floor(damage.getOrDefault(EntityDamageEvent.DamageModifier.BASE, 1f) * points * 0.04)));
            }
            player.attack(new EntityDamageByEntityEvent(this, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
            this.playAttack();
        } else if (tool != null && tool.getId() == Item.TRIDENT && this.attackDelay > 120 && Utils.rand(1, 32) < 4 && this.distanceSquared(player) <= 55) {
            this.attackDelay = 0;

            double f = 1.3;
            double yaw = this.yaw;
            double pitch = this.pitch;
            double yawR = FastMath.toRadians(yaw);
            double pitchR = FastMath.toRadians(pitch);
            Location pos = new Location(this.x - Math.sin(yawR) * Math.cos(pitchR) * 0.5, this.y + this.getHeight() - 0.18,
                    this.z + Math.cos(yawR) * Math.cos(pitchR) * 0.5, yaw, pitch, this.level);
            if (this.getLevel().getBlockIdAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ()) == Block.AIR) {
                Entity k = Entity.createEntity("ThrownTrident", pos, this);
                if (!(k instanceof EntityThrownTrident)) {
                    return;
                }

                EntityThrownTrident trident = (EntityThrownTrident) k;

                setProjectileMotion(trident, pitch, yawR, pitchR, f);

                EntityShootBowEvent ev = new EntityShootBowEvent(this, Item.get(Item.TRIDENT, 0, 1), trident, f);
                this.server.getPluginManager().callEvent(ev);

                EntityProjectile projectile = ev.getProjectile();
                if (ev.isCancelled()) {
                    projectile.close();
                } else {
                    ProjectileLaunchEvent launch = new ProjectileLaunchEvent(projectile);
                    this.server.getPluginManager().callEvent(launch);
                    if (launch.isCancelled()) {
                        projectile.close();
                    } else {
                        projectile.spawnToAll();
                        ((EntityThrownTrident) projectile).setPickupMode(EntityThrownTrident.PICKUP_NONE);
                        this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_ITEM_TRIDENT_THROW);
                    }
                }
            }
        }
    }
    
    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (getServer().getDifficulty() == 0) {
            this.close();
            return true;
        }

        boolean hasUpdate = super.entityBaseTick(tickDiff);

        if (!this.closed && level.shouldMobBurn(this)) {
            this.setOnFire(100);
        }

        return hasUpdate;
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        if (!this.isBaby()) {
            drops.add(Item.get(Item.ROTTEN_FLESH, 0, Utils.rand(0, 2)));

            if (Utils.rand(1, 100) <= 11) {
                drops.add(Item.get(Item.GOLD_INGOT, 0, 1));
            }

            if (tool != null && Utils.rand(1, 4) == 1) {
                drops.add(tool);
            } else if (tool == null && Utils.rand(1, 80) <= 3) {
                drops.add(Item.get(Item.TRIDENT, Utils.rand(230, 246), 1));
            }
        }

        return drops.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : 5;
    }

    private void setRandomTool() {
        switch (Utils.rand(1, 3)) {
            case 1:
                if (Utils.rand(1, 100) <= 15) {
                    this.tool = Item.get(Item.TRIDENT, Utils.rand(200, 246), 1);
                }
                return;
            case 2:
                if (Utils.rand(1, 100) == 1) {
                    this.tool = Item.get(Item.FISHING_ROD, Utils.rand(51, 61), 1);
                }
                return;
            case 3:
                if (Utils.rand(1, 100) <= 8) {
                    this.tool = Item.get(Item.NAUTILUS_SHELL, 0, 1);
                }
        }
    }

    @Override
    public void spawnTo(Player player) {
        super.spawnTo(player);

        if (this.tool != null) {
            MobEquipmentPacket pk = new MobEquipmentPacket();
            pk.eid = this.getId();
            pk.hotbarSlot = 0;
            pk.item = this.tool;
            player.dataPacket(pk);
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.namedTag.putBoolean("HandItemSet", true); // Can be air but don't try to set a new tool next time
        if (tool != null) {
            this.namedTag.put("Item", NBTIO.putItemHelper(tool));
        }
    }

    public Item getTool() {
        return this.tool;
    }
}
