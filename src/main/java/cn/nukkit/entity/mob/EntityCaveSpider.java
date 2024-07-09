package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityArthropod;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EntityCaveSpider extends EntityWalkingMob implements EntityArthropod {

    public static final int NETWORK_ID = 40;

    public EntityCaveSpider(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.7f;
    }

    @Override
    public float getHeight() {
        return 0.5f;
    }

    @Override
    public double getSpeed() {
        return 1.3;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(12);

        super.initEntity();

        this.setDamage(new int[] { 0, 2, 3, 3 });
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.attackDelay > 23 && this.distanceSquared(player) < 1.32) {
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

                EntityDamageByEntityEvent ev = new EntityDamageByEntityEvent(this, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage);

                if (player.attack(ev) && !ev.isCancelled() && this.server.getDifficulty() > 0) {
                    player.addEffect(Effect.getEffect(Effect.POISON).setDuration(this.server.getDifficulty() > 1 ? 300 : 140), EntityPotionEffectEvent.Cause.ATTACK);
                }
            }
        }
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        drops.add(Item.get(Item.STRING, 0, Utils.rand(0, 2)));
        drops.add(Item.get(Item.SPIDER_EYE, 0, (Utils.rand(0, 2) == 0 ? 1 : 0)));

        return drops.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public int getKillExperience() {
        return 5;
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.getNameTag() : "Cave Spider";
    }

    @Override
    public boolean canBeAffected(int effectId) {
        if (effectId == Effect.POISON) {
            return false;
        }
        return super.canBeAffected(effectId);
    }
}
