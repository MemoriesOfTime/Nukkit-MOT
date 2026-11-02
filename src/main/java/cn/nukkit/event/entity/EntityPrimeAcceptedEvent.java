package cn.nukkit.event.entity;

import cn.nukkit.block.Block;
import cn.nukkit.entity.item.EntityPrimedTNT;
import cn.nukkit.event.HandlerList;

/** Notification after the exact TNT source and its actor have been accepted.
 * Unlike EntitySpawnEvent this observes AIR at the source. It cannot refuse or refund
 * the committed source; listeners may attach source metadata before world notifications.
 */
public final class EntityPrimeAcceptedEvent extends EntityEvent {
    private static final HandlerList handlers = new HandlerList();
    private final Block sourceBlock;
    public static HandlerList getHandlers() { return handlers; }
    public EntityPrimeAcceptedEvent(EntityPrimedTNT entity, Block sourceBlock) {
        this.entity = entity;
        this.sourceBlock = sourceBlock.clone();
    }
    @Override public EntityPrimedTNT getEntity() { return (EntityPrimedTNT) entity; }
    public Block getSourceBlock() { return sourceBlock.clone(); }
}
