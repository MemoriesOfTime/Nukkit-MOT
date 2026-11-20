package cn.nukkit.inventory.transaction;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Action-merging behaviour of {@link InventoryTransaction}: actions on distinct slots stay
 * separate, actions on the same slot are chained, and the caller's items are never mutated.
 * <p>
 * 动作合并行为：不同槽位保持独立、同槽位串联、调用方物品不被修改。
 * 这里只断言对外可观察的结果，不统计 {@link Item#clone()} 的调用次数。
 */
class InventoryTransactionActionMergeTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    private static Player creativePlayer() {
        Player player = mock(Player.class);
        when(player.isCreative()).thenReturn(true);
        return player;
    }

    private static SlotChangeAction action(PlayerInventory inventory, int slot, Item from, Item to) {
        return new SlotChangeAction(inventory, slot, from, to);
    }

    private static int slotOf(InventoryAction action) {
        return ((SlotChangeAction) action).getSlot();
    }

    @Test
    void distinctSlotsKeepBothActions() {
        Player player = creativePlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        Item stoneA = Item.get(Item.STONE), dirtA = Item.get(Item.DIRT);
        Item stoneB = Item.get(Item.STONE), dirtB = Item.get(Item.DIRT);

        InventoryTransaction transaction = new InventoryTransaction(player, List.of(
                action(inventory, 0, stoneA, dirtA),
                action(inventory, 1, stoneB, dirtB)));

        assertEquals(2, transaction.getActionList().size(), "不同槽位的动作不得被合并");
        assertEquals(List.of(0, 1),
                transaction.getActionList().stream().map(InventoryTransactionActionMergeTest::slotOf).toList());
        assertEquals(1, stoneA.getCount(), "调用方传入的物品不得被修改");
        assertEquals(1, dirtA.getCount());
        assertEquals(1, stoneB.getCount());
        assertEquals(1, dirtB.getCount());
        assertEquals(Item.STONE, stoneA.getId());
        assertEquals(Item.DIRT, dirtA.getId());
    }

    @Test
    void sameSlotChainsIntoOneActionWithoutMutatingInputs() {
        Player player = creativePlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        Item stone = Item.get(Item.STONE), dirt = Item.get(Item.DIRT), sand = Item.get(Item.SAND);

        InventoryTransaction transaction = new InventoryTransaction(player, List.of(
                action(inventory, 0, stone, dirt),
                action(inventory, 0, dirt, sand)));

        assertEquals(1, transaction.getActionList().size(), "同槽位的连续动作应串联为一条");
        assertTrue(transaction.getActionList().get(0).getSourceItem().equalsExact(stone));
        assertTrue(transaction.getActionList().get(0).getTargetItem().equalsExact(sand));
        assertEquals(1, stone.getCount());
        assertEquals(1, dirt.getCount());
        assertEquals(1, sand.getCount());
    }

    /**
     * {@code TRIM_PATTERN} 是插件子类可能引用的受保护成员，这里以编译期引用的方式守护它：
     * 字段被删除会直接编译失败，而不是在运行期抛 {@code NoSuchFieldException}。
     * <p>
     * Guards the protected {@code TRIM_PATTERN} surface through a compile-time reference and
     * asserts the regex semantics that smithing-template detection depends on.
     */
    @Test
    void trimPatternMatchesOnlySmithingTemplates() {
        InventoryTransaction transaction = new InventoryTransaction(null, List.of(), false);
        Pattern pattern = transaction.TRIM_PATTERN;

        assertTrue(pattern.matcher("minecraft:coast_smithing_template").matches());
        assertTrue(pattern.matcher("minecraft:netherite_upgrade_smithing_template").matches());
        assertFalse(pattern.matcher("minecraft:stone").matches());
        assertFalse(pattern.matcher("coast_smithing_template").matches(), "必须要求 minecraft: 命名空间");
        assertFalse(pattern.matcher("minecraft:coast_smithing_template_extra").matches());
    }
}
