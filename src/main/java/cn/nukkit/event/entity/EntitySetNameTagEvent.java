package cn.nukkit.event.entity;

import cn.nukkit.entity.Entity;
import cn.nukkit.event.HandlerList;

public class EntitySetNameTagEvent extends EntityEvent{
    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    // 可为null，表示清除nameTag
    private String nameTag;

    public EntitySetNameTagEvent(Entity entity, String nameTag) {
        this.entity = entity;
        this.nameTag = nameTag;
    }

    public String getNameTag() {
        return nameTag;
    }
}
