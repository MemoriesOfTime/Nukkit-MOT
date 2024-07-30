package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.CreatureSpawnEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

import java.util.HashMap;

public class EntityMagmaCube extends EntityJumpingMob {

    public static final int NETWORK_ID = 42;

    public static final int SIZE_SMALL = 1;
    public static final int SIZE_MEDIUM = 2;
    public static final int SIZE_BIG = 3;

    protected int size;

    public EntityMagmaCube(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.51f + size * 0.51f;
    }

    @Override
    public float getHeight() {
        return 0.51f + size * 0.51f;
    }

    @Override
    public float getLength() {
        return 0.51f + size * 0.51f;
    }

    @Override
    protected void initEntity() {
        if (this.namedTag.contains("Size")) {
            this.size = this.namedTag.getInt("Size");
        } else {
            this.size = Utils.rand(1, 3);
        }

        this.setScale(0.51f + size * 0.51f);

        if (size == SIZE_BIG) {
            this.setMaxHealth(16);
        } else if (size == SIZE_MEDIUM) {
            this.setMaxHealth(4);
        } else if (size == SIZE_SMALL) {
            this.setMaxHealth(1);
        }

        super.initEntity();

        this.fireProof = true;
        this.noFallDamage = true;

        this.setScale(0.51f + size * 0.51f);

        if (size == SIZE_BIG) {
            this.setDamage(new int[] { 0, 3, 4, 6 });
        } else if (size == SIZE_MEDIUM) {
            this.setDamage(new int[] { 0, 2, 2, 3 });
        } else {
            this.setDamage(Utils.getEmptyDamageArray());
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.namedTag.putInt("Size", this.getSlimeSize());
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
    public void kill() {
        if (this.closed || !this.isAlive() || this.chunk == null) {
            return;
        }

        super.kill();

        if (this.size == SIZE_BIG) {
            CreatureSpawnEvent ev = new CreatureSpawnEvent(NETWORK_ID, this, this.namedTag, CreatureSpawnEvent.SpawnReason.SLIME_SPLIT);
            level.getServer().getPluginManager().callEvent(ev);

            if (ev.isCancelled()) {
                return;
            }

            for (int i = 0; i < 2; i++) {
                EntityMagmaCube entity = (EntityMagmaCube) Entity.createEntity("MagmaCube", this.chunk, Entity.getDefaultNBT(this).putInt("Size", SIZE_MEDIUM));
                if (entity != null) {
                    entity.spawnToAll();
                }
            }
        } else if (this.size == SIZE_MEDIUM) {
            CreatureSpawnEvent ev = new CreatureSpawnEvent(NETWORK_ID, this, this.namedTag, CreatureSpawnEvent.SpawnReason.SLIME_SPLIT);
            level.getServer().getPluginManager().callEvent(ev);

            if (ev.isCancelled()) {
                return;
            }

            for (int i = 0; i < 2; i++) {
                EntityMagmaCube entity = (EntityMagmaCube) Entity.createEntity("MagmaCube", this.chunk, Entity.getDefaultNBT(this).putInt("Size", SIZE_SMALL));
                if (entity != null) {
                    entity.spawnToAll();
                }
            }
        }
    }

    @Override
    public Item[] getDrops() {
        if (this.size == SIZE_SMALL) {
            return new Item[]{Item.get(Item.MAGMA_CREAM, 0, Utils.rand(0, 1))};
        }

        return Item.EMPTY_ARRAY;
    }

    @Override
    public int getKillExperience() {
        if (this.size == SIZE_BIG) {
            return 4;
        }
        if (this.size == SIZE_MEDIUM) {
            return 2;
        }
        if (this.size == SIZE_SMALL) {
            return 1;
        }
        return 0;
    }

    public int getSlimeSize() {
        return this.size;
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.getNameTag() : "Magma Cube";
    }
}
