package cn.nukkit.event.entity;

import cn.nukkit.entity.Entity;
import cn.nukkit.event.HandlerList;
import cn.nukkit.potion.Effect;

/**
 * @deprecated use {@link EntityPotionEffectEvent} instead
 */
@Deprecated
public class EntityEffectUpdateEvent extends EntityEvent {
    private static final HandlerList handlers = new HandlerList();

    private Effect oldEffect;
    private Effect newEffect;

    public EntityEffectUpdateEvent(Entity entity, Effect oldEffect, Effect newEffect) {
        this.entity = entity;
        this.oldEffect = oldEffect;
        this.newEffect = newEffect;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public Effect getOldEffect() {
        return this.oldEffect;
    }

    public Effect getNewEffect() {
        return this.newEffect;
    }
}