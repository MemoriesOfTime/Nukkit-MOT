package cn.nukkit.network.process.processor.v113;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
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
 * 1.1.x 合成包处理器：通过包内快照校验，在玩家背包结算。
 * 1.1.x crafting events are validated against the packet snapshot and settled on the player inventory.
 *
 * @author LT_Name
 */
public class CraftingEventProcessor_v113 extends DataPacketProcessor<CraftingEventPacket> {

    public static final CraftingEventProcessor_v113 INSTANCE = new CraftingEventProcessor_v113();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull CraftingEventPacket pk) {
        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive()) {
            logRejection(player, pk, "inactive_player");
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
                    logRejection(player, pk, "missing_anvil");
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
            logRejection(player, pk, "unknown_window");
            player.getInventory().sendContents(player);
            ContainerClosePacket containerClosePacket = new ContainerClosePacket();
            containerClosePacket.windowId = (byte) pk.windowId;
            player.dataPacket(containerClosePacket);
            return;
        }

        if (recipe == null) {
            logRejection(player, pk, "unknown_recipe");
            player.getInventory().sendContents(player);
            return;
        }

        if (!CraftingEvent_v113Adapter.execute(player, recipe, pk.input, pk.output)) {
            String reason = pk.input == null ? "missing_input"
                    : pk.input.length == 0 ? "empty_input" : "validation_failed";
            logRejection(player, pk, reason);
            // 1.1.x 没有服务端合成格，失败时只能重发背包让客户端回滚本地状态。
            // No server-side crafting grid exists on 1.1.x, so the inventory itself is the resync target.
            player.getInventory().sendContents(player);
        }
    }

    private static void logRejection(Player player, CraftingEventPacket pk, String reason) {
        player.getServer().getLogger().debug("Invalid crafting event " + pk.id + " from player " + player.getName()
                + " reason=" + reason + " protocol=" + player.protocol);
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
    public boolean isSupported(GameVersion gameVersion) {
        return gameVersion.getProtocol() < ProtocolInfo.v1_2_0;
    }
}
