package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerTeleportEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Sound;
import cn.nukkit.level.biome.Biome;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

import java.util.HashMap;

public class EntityEnderman extends EntityWalkingMob {

    public static final int NETWORK_ID = 38;

    private int angry = 0;

    private boolean teleported;

    public EntityEnderman(FullChunk chunk, CompoundTag nbt) {
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
        return 2.9f;
    }

    @Override
    public double getSpeed() {
        return this.isAngry() ? 1.6 : 1.21;
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(40);

        super.initEntity();

        this.setDamage(new int[]{0, 4, 7, 10});
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.attackDelay > 23 && this.distanceSquared(player) < 1) {
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
        }
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        super.attack(ev);

        if (!ev.isCancelled()) {
            if (ev.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                if (!isAngry()) {
                    setAngry(2400);
                }
            }

            if (ev.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
                if (!isAngry()) {
                    setAngry(2400);
                }
                ev.setCancelled(true);
                this.teleport();
                return false;
            } else if (!this.teleported && Utils.rand(1, 10) == 1) {
                this.teleport();
            }
        }
        return true;
    }

    @Override
    public Item[] getDrops() {
        return new Item[]{Item.get(Item.ENDER_PEARL, 0, Utils.rand(0, 1))};
    }

    @Override
    public int getKillExperience() {
        return 5;
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (this.closed) {
            return false;
        }

        if (this.getServer().getDifficulty() == 0) {
            this.close();
            return true;
        }

        this.teleported = false;

        if (this.angry > 0) {
            if (this.angry == 1) {
                this.setAngry(0);
            } else {
                this.angry--;
            }
        }

        int b = level.getBlockIdAt(chunk, NukkitMath.floorDouble(this.x), (int) this.y, NukkitMath.floorDouble(this.z));
        CompoundTag biomeDefinitions;
        if (b == BlockID.WATER || b == BlockID.STILL_WATER
                || (this.level.isRaining() && Utils.rand() && this.level.canBlockSeeSky(this)
                && ((biomeDefinitions = Biome.getBiomeDefinitions(this.level.getBiomeId(this.getFloorX(), this.getFloorZ()))) == null
                || biomeDefinitions.getBoolean("rain")))
        ) {
            this.attack(new EntityDamageEvent(this, EntityDamageEvent.DamageCause.DROWNING, 1));
            this.setAngry(0);
            this.teleport();
        } else if (Utils.rand(0, 500) == 20) {
            this.setAngry(0);
            this.teleport();
        }

        return super.entityBaseTick(tickDiff);
    }

    public void teleport() {
        Location to = this.getSafeTpLocation();
        if (to != null) {
            this.level.addSound(this, Sound.MOB_ENDERMEN_PORTAL);
            if (this.teleport(to, PlayerTeleportEvent.TeleportCause.UNKNOWN)) {
                this.level.addSound(this, Sound.MOB_ENDERMEN_PORTAL);
                this.teleported = true;
            }
        }
    }

    private Location getSafeTpLocation() {
        double dx = this.x + Utils.rand(-16, 16);
        double dz = this.z + Utils.rand(-16, 16);
        Vector3 pos = new Vector3(Math.floor(dx), (int) Math.floor(this.y + 0.1) + 16, Math.floor(dz));
        FullChunk chunk = this.level.getChunk((int) pos.x >> 4, (int) pos.z >> 4, false);
        int x = (int) pos.x & 0x0f;
        int z = (int) pos.z & 0x0f;
        int previousY1 = -1;
        int previousY2 = -1;
        if (chunk != null && chunk.isGenerated()) {
            for (int y = Math.min(255, (int) pos.y); y >= 0; y--) {
                if (previousY1 > -1 && previousY2 > -1) {
                    if (Block.solid[chunk.getBlockId(x, y, z)] && chunk.getBlockId(x, previousY1, z) == 0 && chunk.getBlockId(x, previousY2, z) == 0) {
                        return new Location(pos.x + 0.5, previousY1 + 0.1, pos.z + 0.5, this.level);
                    }
                }
                previousY2 = previousY1;
                previousY1 = y;
            }
        }
        return null;
    }

    @Override
    public boolean canDespawn() {
        if (this.getLevel().getDimension() == Level.DIMENSION_THE_END) {
            return false;
        }

        return super.canDespawn();
    }

    public boolean isAngry() {
        return this.angry > 0;
    }

    public void setAngry(int val) {
        if (this.angry != val) {
            this.angry = val;
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_ANGRY, val > 0);
        }
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (!isAngry()) return false;
        if (creature instanceof Player player) {
            return !player.closed && player.spawned && player.isAlive() && (player.isSurvival() || player.isAdventure()) && distance <= 1024;
        }
        return creature.isAlive() && !creature.closed && distance <= 1024;
    }

    public void stareToAngry() {
        if (!isAngry()) {
            setAngry(2400);
        }
    }
}
