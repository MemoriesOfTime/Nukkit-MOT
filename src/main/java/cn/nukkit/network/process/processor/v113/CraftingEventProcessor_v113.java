package cn.nukkit.network.process.processor.v113;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.Server;
import cn.nukkit.inventory.AnvilInventory;
import cn.nukkit.inventory.CraftingRecipe;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.item.Item;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ContainerClosePacket;
import cn.nukkit.network.protocol.CraftingEventPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * v113 (1.1) 专用的 CraftingEventPacket 处理器。
 * 在v113协议中，合成事件通过CraftingEventPacket处理，而非高版本的InventoryTransactionPacket。
 *
 * @author LT_Name
 */
public class CraftingEventProcessor_v113 extends DataPacketProcessor<CraftingEventPacket> {

    public static final CraftingEventProcessor_v113 INSTANCE = new CraftingEventProcessor_v113();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull CraftingEventPacket pk) {
        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive()) {
            return;
        }

        CraftingRecipe recipe = getRecipe(player, pk.id);

        // 铁砧处理
        if (player.craftingType == Player.CRAFTING_ANVIL) {
            Inventory inv = playerHandle.getWindowIndex().containsKey(pk.windowId) ?
                    player.getWindowById(pk.windowId) : null;
            AnvilInventory anvilInventory = inv instanceof AnvilInventory ? (AnvilInventory) inv : null;

            if (anvilInventory == null) {
                for (Inventory window : playerHandle.getWindowIndex().values()) {
                    if (window instanceof AnvilInventory) {
                        anvilInventory = (AnvilInventory) window;
                        break;
                    }
                }

                if (anvilInventory == null) {
                    Server.getInstance().getLogger().debug("Couldn't find an anvil window for " + player.getName());
                    player.getInventory().sendContents(player);
                    return;
                }
            }

            if (recipe == null && pk.output != null && pk.output.length > 0) {
                // 物品重命名
                Item result = pk.output[0];
                Item sourceItem = anvilInventory.getItem(0);
                if (sourceItem != null && sourceItem.getId() != Item.AIR) {
                    // 简单的重命名逻辑
                    if (!sourceItem.hasCustomName() || !sourceItem.getCustomName().equals(result.getCustomName())) {
                        if (player.isSurvival() || player.isAdventure()) {
                            Item first = anvilInventory.getItem(0);
                            if (result.getCustomName() != null && !result.getCustomName().isEmpty()) {
                                first.setCustomName(result.getCustomName());
                            } else {
                                first.clearCustomName();
                            }
                            anvilInventory.setItem(2, first);
                        }
                    }
                }
            }
            return;
        }

        // 检查窗口
        if (!playerHandle.getWindowIndex().containsKey(pk.windowId)) {
            player.getInventory().sendContents(player);
            ContainerClosePacket containerClosePacket = new ContainerClosePacket();
            containerClosePacket.windowId = (byte) pk.windowId;
            player.dataPacket(containerClosePacket);
            return;
        }

        if (recipe == null) {
            player.getInventory().sendContents(player);
            return;
        }

        Item requestedOutput = pk.output != null && pk.output.length > 0 ? pk.output[0] : null;
        if (!CraftingEvent_v113Adapter.execute(player, recipe, requestedOutput)) {
            Server.getInstance().getLogger().debug("Invalid crafting event " + pk.id + " from player " + player.getName());
            player.getInventory().sendContents(player);
            if (player.getCraftingGrid() != null) {
                player.getCraftingGrid().sendContents(player);
            }
        }
    }

    private CraftingRecipe getRecipe(Player player, UUID id) {
        for (Recipe recipe : player.getServer().getCraftingManager().getRecipes()) {
            if (recipe instanceof CraftingRecipe) {
                CraftingRecipe craftRecipe = (CraftingRecipe) recipe;
                if (craftRecipe.getId() != null && craftRecipe.getId().equals(id)) {
                    return craftRecipe;
                }
            }
        }
        return null;
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(CraftingEventPacket.NETWORK_ID);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return CraftingEventPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol < ProtocolInfo.v1_2_0;
    }
}
