package cn.nukkit.event.block;

import cn.nukkit.block.Block;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.item.Item;

import java.util.Objects;

/**
 * 在方块来源派生的掉落生成前派发；可取消，自动链、嵌套批次与直接掉落还可整批替换。
 * <p>
 * 归因来源有三种：{@code useBreakOn} 的收尾批次、{@code Level.dropItemFromAutomaticBlock}
 * 的显式来源掉落、{@code Level.dropItemFromBlockInteraction} 的方块交互产物；普通
 * {@code Level.dropItem} 永不触发本事件。玩家发起的破坏链中，最外层事件只携带空的掉落批次
 * （主批次已由 {@code BlockBreakEvent} 暴露）且只做簿记：嵌套批次与直接掉落在它之前已经生成，
 * 取消收尾事件不会撤回它们；这些批次仍可取消并以 {@link Cause#PLAYER_BREAK} 标记，直接掉落
 * 派发时 {@code isOutermostBreak()} 必为 {@code false} 且变更方块为空。
 * {@link #getChangedBlocks()} 报告本链同步替换的全部主层方块快照。来源方块即便已被替换或
 * 移除也会保留，方块实体（容器、唱片机等）的内容不属于本事件。
 * <p>Called before a source-derived block drop is created; it is cancellable, and automatic,
 * nested and direct batches are replaceable as a whole. Attribution has exactly three entries: the
 * {@code useBreakOn} terminal batch, explicit {@code Level.dropItemFromAutomaticBlock} drops, and
 * {@code Level.dropItemFromBlockInteraction} output; plain {@code Level.dropItem} never fires this
 * event. In a player-started chain the outermost batch is empty (the primary batch belongs to
 * {@code BlockBreakEvent}) and purely informational: nested batches and direct drops are created
 * before it fires, so cancelling it cannot roll them back. Those batches stay cancellable under
 * {@link Cause#PLAYER_BREAK}, and a direct drop always fires with {@code isOutermostBreak()} false
 * and an empty changed-block list. {@code getChangedBlocks()} reports every main-layer cell the
 * chain replaced. Block-entity contents are excluded.
 */
public class AutomaticBlockDropsEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public enum Cause {
        /** 链中没有玩家来源：物理、活塞、实体等自动破坏。<p>No player started the chain. */
        AUTOMATIC_BREAK,
        /**
         * 玩家发起的破坏链；最外层批次为空，嵌套批次与直接掉落按玩家规则保留。
         * <p>A player-started chain whose nested and direct batches keep the player's drop rules.
         */
        PLAYER_BREAK,
        /** 方块交互产物，不完成破坏链也不报告变更方块。<p>Interaction output only. */
        BLOCK_INTERACTION
    }

    private Item[] drops;
    private final Block[] changedBlocks;
    private final boolean outermostBreak;
    private final Cause cause;

    public AutomaticBlockDropsEvent(
            Block block, Item[] drops, Block[] changedBlocks, boolean outermostBreak, Cause cause) {
        super(block);
        this.cause = Objects.requireNonNull(cause, "cause");
        if (cause == Cause.BLOCK_INTERACTION
                && (changedBlocks == null || changedBlocks.length != 0 || outermostBreak)) {
            throw new IllegalArgumentException("Interaction output cannot complete a block break");
        }
        this.drops = drops;
        this.changedBlocks = changedBlocks;
        this.outermostBreak = outermostBreak;
    }

    public Cause getCause() {
        return cause;
    }

    public Item[] getDrops() {
        return drops;
    }

    /**
     * 替换本批掉落；{@code useBreakOn} 以显式玩家派发的收尾批次不生效（其掉落由
     * {@code BlockBreakEvent} 决定）。
     * <p>Replaces this batch; ineffective for the closing batch a {@code useBreakOn} call fires
     * with an explicit player, whose drops belong to {@code BlockBreakEvent}.
     */
    public void setDrops(Item[] drops) {
        this.drops = drops;
    }

    /** 本链同步替换的全部主层方块快照。<p>Snapshots of every main-layer cell this chain replaced. */
    public Block[] getChangedBlocks() {
        return changedBlocks;
    }

    /**
     * 是否为本链的收尾事件；此事件只做簿记，取消它不会撤回已经生成的嵌套/直接掉落。
     * <p>Whether this event closes the outermost synchronous break chain; it is bookkeeping only,
     * so cancelling it cannot roll back nested or direct drops that already fired.
     */
    public boolean isOutermostBreak() {
        return outermostBreak;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
