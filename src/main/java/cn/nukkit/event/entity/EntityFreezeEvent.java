package cn.nukkit.event.entity;

import cn.nukkit.entity.Entity;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

public class EntityFreezeEvent extends EntityEvent implements Cancellable {
    private final Entity entity;

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    public EntityFreezeEvent(Entity human) {
        this.entity = human;
    }
}