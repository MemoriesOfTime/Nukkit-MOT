package cn.nukkit.event.blockentity;

import cn.nukkit.block.BlockHopper;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.Event;
import cn.nukkit.event.HandlerList;
import cn.nukkit.math.AxisAlignedBB;
import lombok.Getter;
import lombok.Setter;

/**
 * 漏斗开始搜索可拉取或推送的物品时触发的事件。
 * <p>
 * 该事件在转移冷却结束后、漏斗开始搜索物品前触发。
 * 取消此事件将阻止漏斗在本次 tick 中搜索和转移物品，但不会影响冷却计时器的递减。
 * <p>
 * 插件可以通过 {@link #setCancelPull(boolean)} 和 {@link #setCancelPush(boolean)}
 * 分别控制拉取和推送阶段，而不必完全取消事件。
 * {@link #setCancelled(boolean)} 仍然有效，等同于同时取消拉取和推送（向后兼容）。
 *
 * <p>
 * Called when a hopper begins searching for items to pull or push.
 * <p>
 * This event is fired after the transfer cooldown has expired and before
 * the hopper starts looking for items. Cancelling this event will prevent
 * the hopper from searching for items in this tick, but will not affect
 * the cooldown timer.
 * <p>
 * Plugins can independently cancel the pull or push phase via
 * {@link #setCancelPull(boolean)} and {@link #setCancelPush(boolean)}.
 * {@link #setCancelled(boolean)} still works and is equivalent to cancelling
 * both phases (backwards compatible).
 */
@Getter
public class HopperSearchItemEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    /**
     * 触发此事件的漏斗实例
     * <p>
     * The hopper instance that triggered this event.
     */
    private final BlockHopper.IHopper hopper;

    /**
     * 该漏斗是否为矿车漏斗
     * <p>
     * Whether this hopper is a minecart hopper.
     */
    private final boolean minecart;

    /**
     * 是否取消拉取阶段（从上方容器或地面拾取物品）。
     * 优先级高于 {@link #isCancelled()}，即 pull 阶段实际判断为 {@code isCancelled() || isCancelPull()}。
     * <p>
     * Whether to cancel the pull phase (pulling from container above or picking up items from ground).
     * Takes priority over {@link #isCancelled()}: the pull phase checks {@code isCancelled() || isCancelPull()}.
     */
    @Setter
    private boolean cancelPull;

    /**
     * 是否取消推送阶段（向下方容器推送物品）。
     * 优先级高于 {@link #isCancelled()}，即 push 阶段实际判断为 {@code isCancelled() || isCancelPush()}。
     * <p>
     * Whether to cancel the push phase (pushing items into container below).
     * Takes priority over {@link #isCancelled()}: the push phase checks {@code isCancelled() || isCancelPush()}.
     */
    @Setter
    private boolean cancelPush;

    /**
     * 地面物品拾取范围。仅在地面拾取时使用，不影响从容器拉取。
     * <p>
     * The pickup area for collecting items from the ground.
     * Only used during ground item pickup, does not affect pulling from containers.
     */
    @Setter
    private AxisAlignedBB pickupArea;

    public HopperSearchItemEvent(BlockHopper.IHopper hopper, boolean minecart, AxisAlignedBB pickupArea) {
        this.hopper = hopper;
        this.minecart = minecart;
        this.pickupArea = pickupArea;
    }
}
