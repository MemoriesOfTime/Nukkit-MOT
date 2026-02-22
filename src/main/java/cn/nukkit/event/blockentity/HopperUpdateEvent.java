package cn.nukkit.event.blockentity;

import cn.nukkit.blockentity.BlockEntityHopper;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.Event;
import cn.nukkit.event.HandlerList;
import lombok.Getter;
import lombok.Setter;

/**
 * 漏斗方块实体每 tick 更新时触发的事件。
 * <p>
 * 该事件在漏斗 {@link BlockEntityHopper#onUpdate()} 的最开始触发，
 * 早于冷却递减和物品搜索。取消此事件将阻止漏斗的整个更新周期，
 * 包括冷却递减和物品转移。
 * <p>
 * 插件可通过 {@link #setTransferCooldown(int)} 修改漏斗的转移冷却时间，
 * 修改后的值将在本次更新中生效。
 *
 * <p>
 * Called every tick when a hopper block entity updates.
 * <p>
 * This event is fired at the very beginning of {@link BlockEntityHopper#onUpdate()},
 * before the cooldown decrement and item searching. Cancelling this event will
 * prevent the entire update cycle, including cooldown decrement and item transfer.
 * <p>
 * Plugins can modify the transfer cooldown via {@link #setTransferCooldown(int)},
 * and the modified value will take effect in the current update.
 */
@Getter
public class HopperUpdateEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    /**
     * 触发此事件的漏斗方块实体
     * <p>
     * The hopper block entity that triggered this event.
     */
    private final BlockEntityHopper blockEntity;

    /**
     * 漏斗的转移冷却时间（单位: tick）。
     * 插件可修改此值来加速或减速漏斗的物品转移频率。
     * 默认冷却为 8 tick，设置为更小的值将加快转移速度。
     * <p>
     * The transfer cooldown of the hopper (in ticks).
     * Plugins can modify this value to speed up or slow down item transfer frequency.
     * The default cooldown is 8 ticks; setting a smaller value will increase transfer speed.
     */
    @Setter
    private int transferCooldown;

    public HopperUpdateEvent(BlockEntityHopper blockEntity) {
        this.blockEntity = blockEntity;
        this.transferCooldown = blockEntity.transferCooldown;
    }
}
