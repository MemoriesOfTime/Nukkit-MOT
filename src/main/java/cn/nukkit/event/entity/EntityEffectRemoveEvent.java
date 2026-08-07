package cn.nukkit.event.entity;

import cn.nukkit.entity.Entity;
import cn.nukkit.event.HandlerList;
import cn.nukkit.potion.Effect;

/**
 * @deprecated use {@link EntityPotionEffectEvent} instead
 */
@Deprecated
public class EntityEffectRemoveEvent extends EntityEvent {
    private static final HandlerList handlers = new HandlerList();

    private Effect removeEffect;

    public EntityEffectRemoveEvent(Entity entity, Effect effect) {
        this.entity = entity;
        this.removeEffect = effect;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public Effect getRemoveEffect() {
        return removeEffect;
    }

}