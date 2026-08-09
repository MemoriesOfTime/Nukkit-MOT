package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftCreativeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;

/**
 * Handles creative-mode item creation. The client sends this when it wants to
 * take a stack out of the creative catalog; the server materialises the item
 * into CREATED_OUTPUT for a subsequent TAKE action to move elsewhere.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
public class CraftCreativeActionProcessor implements ItemStackRequestActionProcessor<CraftCreativeAction> {

    public static final String CRAFT_CREATIVE_KEY = "craft_creative_key";

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.CRAFT_CREATIVE;
    }

    @Override
    public ActionResponse handle(CraftCreativeAction action, Player player, ItemStackRequestContext context) {
        if (!player.isCreative()) {
            return context.error();
        }

        // creativeItemNetworkId is 1-based; the catalog is indexed from 0. Use the
        // player's actual game version so the right catalog is queried for NetEase
        // and older clients.
        Item item = Item.getCreativeItem(player.getGameVersion(), action.getCreativeItemNetworkId() - 1);
        if (item == null || item.isNull()) {
            return context.error();
        }

        item = item.clone();
        // 创造背包拿物品时,客户端总是按最大堆叠数请求整堆(maxStackSize),随后的 PLACE/DROP
        // action 也会带这个数量。如果这里按 numberOfRequestedCrafts 写入更小的数量
        // (该字段在部分客户端版本下未正确填充或含义不一致),服务端 CREATED_OUTPUT 的 count
        // 就会小于客户端 PLACE 的 count,导致 doTransfer 的 "count < need" 校验失败 ->
        // 请求被判 error -> 回滚清空光标("光标短暂持有后被清")。
        // 因此对齐 Allay/PNX:创造产物始终使用 maxStackSize。
        item.setCount(item.getMaxStackSize());
        item.autoAssignStackNetworkId();

        player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, item, false);
        context.put(CRAFT_CREATIVE_KEY, true);
        return null;
    }
}
