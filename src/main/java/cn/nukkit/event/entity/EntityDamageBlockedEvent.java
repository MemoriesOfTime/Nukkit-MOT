package cn.nukkit.event.entity;

import cn.nukkit.entity.Entity;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

public class EntityDamageBlockedEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final EntityDamageEvent damage;
    private final boolean knockBackAttacker;
    private final boolean animation;
    public EntityDamageBlockedEvent(Entity entity, EntityDamageEvent damage, boolean knockBack, boolean animation) {
        this.entity = entity;
        this.damage = damage;
        this.knockBackAttacker = knockBack;
        this.animation = animation;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public EntityDamageEvent.DamageCause getCause() {
        return damage.getCause();
    }

    public Entity getAttacker() {
        return damage.getEntity();
    }

    public EntityDamageEvent getDamage() {
        return damage;
    }

    public boolean getKnockBackAttacker() {
        return knockBackAttacker;
    }

    public boolean getAnimation() {
        return animation;
    }
}
