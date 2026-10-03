package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.item.Item;

import java.util.Collections;
import java.util.List;

/**
 * 合成事件的只读数据快照，供 ItemStackRequest 路径填充
 * {@link cn.nukkit.event.inventory.CraftItemEvent#getTransaction()}，使其恒非空。
 * 不携带可执行动作链——真实库存变更由 ItemStackRequest 处理器驱动。
 * <p>
 * Read-only snapshot used by the server-authoritative ItemStackRequest crafting path so
 * {@code CraftItemEvent.getTransaction()} is never null. It carries no executable action
 * chain; real mutations are driven by the ItemStackRequest processors.
 */
public class ItemStackRequestCraftingTransaction extends CraftingTransaction {

    /**
     * {@code primaryOutput} 为 {@code null} 表示输出待定（如 MultiRecipe，其输出稍后
     * 由 CraftResultsDeprecatedAction 计算）。
     */
    public ItemStackRequestCraftingTransaction(Player source, List<Item> inputs, Item primaryOutput, Recipe recipe) {
        super(source, Collections.emptyList());
        for (Item input : inputs) {
            if (input != null && !input.isNull()) {
                setInput(input);
            }
        }
        if (primaryOutput != null && !primaryOutput.isNull()) {
            setPrimaryOutput(primaryOutput);
        }
        setTransactionRecipe(recipe);
    }

    /**
     * 同物合并不按 {@code gridSize} 封顶：本快照的输入来自配料描述符（auto-craft 可超过
     * 当前打开网格的容量），遗留的 {@code gridSize*gridSize} 上限会误拒合法的配方书自动合成。
     * <p>
     * 仅在构造期调用：{@code super(...)} 已返回，基类 {@code this.inputs} 已初始化。
     */
    @Override
    public void setInput(Item item) {
        for (Item existing : this.inputs) {
            if (existing.equals(item, item.hasMeta(), item.hasCompoundTag())) {
                existing.setCount(existing.getCount() + item.getCount());
                return;
            }
        }
        this.inputs.add(item.clone());
    }

    /** 此对象为只读快照，无可执行内容。 */
    @Override
    public boolean canExecute() {
        throw new UnsupportedOperationException(
                "ItemStackRequestCraftingTransaction is a read-only snapshot; canExecute() is not supported");
    }

    /** @see #canExecute() */
    @Override
    public boolean execute() {
        throw new UnsupportedOperationException(
                "ItemStackRequestCraftingTransaction is a read-only snapshot; execute() is not supported");
    }
}
