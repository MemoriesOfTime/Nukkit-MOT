package cn.nukkit.event.blockentity;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.Event;
import cn.nukkit.event.HandlerList;
import lombok.Getter;

@Getter
public class BlockEntityHopperUpdateEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }


    private final BlockEntity blockEntity;

    public BlockEntityHopperUpdateEvent(BlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }


}
