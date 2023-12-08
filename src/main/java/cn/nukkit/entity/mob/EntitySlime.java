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

public class EntitySlime extends EntityJumpingMob {

    public static final int NETWORK_ID = 37;

    public static final int SIZE_SMALL = 1;
    public static final int SIZE_MEDIUM = 2;
    public static final int SIZE_BIG = 4;

    protected int size = SIZE_BIG;

    public EntitySlime(FullChunk chunk, CompoundTag nbt) {
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
        super.initEntity();

        if (this.namedTag.contains("Size")) {
            this.size = this.namedTag.getInt("Size");
        } else {
            this.size = Utils.rand(1, 3);
            if (this.size == 3) {
                this.size = 4;
            }
        }

        this.setScale(0.51f + size * 0.51f);

        if (size == SIZE_BIG) {
            this.setMaxHealth(16);
        } else if (size == SIZE_MEDIUM) {
            this.setMaxHealth(4);
        } else if (size == SIZE_SMALL) {
            this.setMaxHealth(1);
        }

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

        this.namedTag.putInt("Size", this.size);
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
    public Item[] getDrops() {
        if (this.size == SIZE_BIG) {
            CreatureSpawnEvent ev = new CreatureSpawnEvent(NETWORK_ID, this, CreatureSpawnEvent.SpawnReason.SLIME_SPLIT);
            level.getServer().getPluginManager().callEvent(ev);

            if (ev.isCancelled()) {
                return Item.EMPTY_ARRAY;
            }

            EntitySlime entity = (EntitySlime) Entity.createEntity("Slime", this);

            if (entity != null) {
                entity.size = SIZE_MEDIUM;
                entity.setScale(0.51f + entity.size * 0.51f);
                entity.spawnToAll();
            }

            return Item.EMPTY_ARRAY;
        } else if (this.size == SIZE_MEDIUM) {
            CreatureSpawnEvent ev = new CreatureSpawnEvent(NETWORK_ID, this, CreatureSpawnEvent.SpawnReason.SLIME_SPLIT);
            level.getServer().getPluginManager().callEvent(ev);

            if (ev.isCancelled()) {
                return Item.EMPTY_ARRAY;
            }

            EntitySlime entity = (EntitySlime) Entity.createEntity("Slime", this);

            if (entity != null) {
                entity.size = SIZE_SMALL;
                entity.setScale(0.51f + entity.size * 0.51f);
                entity.spawnToAll();
            }

            return Item.EMPTY_ARRAY;
        } else {
            return new Item[]{Item.get(Item.SLIMEBALL, 0, Utils.rand(0, 2))};
        }
    }

    @Override
    public int getKillExperience() {
        return switch (this.size) {
            case SIZE_BIG -> 4;
            case SIZE_MEDIUM -> 2;
            case SIZE_SMALL -> 1;
            default -> 0;
        };
    }

    public int getSlimeSize() {
        return this.size;
    }
}
