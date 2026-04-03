package cn.nukkit.network.process.processor.v113;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.Server;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ContainerClosePacket;
import cn.nukkit.network.protocol.CraftingEventPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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

        // 处理输入物品
        for (int i = 0; i < pk.input.length; i++) {
            Item inputItem = pk.input[i];
            if (inputItem.getDamage() == -1 || inputItem.getDamage() == 0xffff) {
                inputItem.setDamage(null);
            }
            if (i < 9 && inputItem.getId() > 0) {
                inputItem.setCount(1);
            }
        }

        boolean canCraft = true;

        if (pk.input.length == 0) {
            // 没有输入物品，尝试根据输出查找配方
            Collection<Recipe> recipes = player.getServer().getCraftingManager().getRecipes();
            recipe = null;

            for (Recipe rec : recipes) {
                if (rec instanceof CraftingRecipe && rec.getResult().equals(pk.output[0])) {
                    CraftingRecipe craftRecipe = (CraftingRecipe) rec;
                    ArrayList<Item> ingredients = getRecipeIngredients(craftRecipe);
                    Map<String, Item> serialized = serializeIngredients(ingredients);

                    boolean found = true;
                    for (Item ingredient : serialized.values()) {
                        if (!player.getInventory().contains(ingredient)) {
                            found = false;
                            break;
                        }
                    }

                    if (found) {
                        recipe = craftRecipe;
                        CraftItemEvent craftItemEvent = new CraftItemEvent(player, serialized.values().toArray(new Item[0]), recipe);
                        player.getServer().getPluginManager().callEvent(craftItemEvent);

                        if (craftItemEvent.isCancelled()) {
                            player.getInventory().sendContents(player);
                            return;
                        }

                        for (Item ingredient : serialized.values()) {
                            player.getInventory().removeItem(ingredient);
                        }
                        player.getInventory().addItem(recipe.getResult());
                        break;
                    }
                }
            }

            if (recipe == null) {
                Server.getInstance().getLogger().debug("Unmatched recipe " + pk.id + " from player " + player.getName());
                player.getInventory().sendContents(player);
            }
        } else {
            // 有输入物品，验证配方
            ArrayList<Item> ingredients = getRecipeIngredients(recipe);
            Map<String, Item> serialized = serializeIngredients(ingredients);

            for (Item ingredient : serialized.values()) {
                if (!player.getInventory().contains(ingredient)) {
                    canCraft = false;
                    break;
                }
            }

            if (!canCraft) {
                Server.getInstance().getLogger().debug("Not enough ingredients for recipe " + pk.id + " from player " + player.getName());
                player.getInventory().sendContents(player);
                return;
            }

            CraftItemEvent craftItemEvent = new CraftItemEvent(player, serialized.values().toArray(new Item[0]), recipe);
            player.getServer().getPluginManager().callEvent(craftItemEvent);

            if (craftItemEvent.isCancelled()) {
                player.getInventory().sendContents(player);
                return;
            }

            for (Item ingredient : serialized.values()) {
                player.getInventory().removeItem(ingredient);
            }
            player.getInventory().addItem(recipe.getResult());
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

    private ArrayList<Item> getRecipeIngredients(CraftingRecipe recipe) {
        ArrayList<Item> ingredients = new ArrayList<>();
        if (recipe instanceof ShapedRecipe) {
            Map<Integer, Map<Integer, Item>> ingredientMap = ((ShapedRecipe) recipe).getIngredientMap();
            for (Map<Integer, Item> map : ingredientMap.values()) {
                for (Item ingredient : map.values()) {
                    if (ingredient != null && ingredient.getId() != Item.AIR) {
                        ingredients.add(ingredient);
                    }
                }
            }
        } else if (recipe instanceof ShapelessRecipe) {
            for (Item ingredient : ((ShapelessRecipe) recipe).getIngredientList()) {
                if (ingredient != null && ingredient.getId() != Item.AIR) {
                    ingredients.add(ingredient);
                }
            }
        }
        return ingredients;
    }

    private Map<String, Item> serializeIngredients(ArrayList<Item> ingredients) {
        Map<String, Item> serialized = new HashMap<>();
        for (Item ingredient : ingredients) {
            String hash = ingredient.getId() + ":" + ingredient.getDamage();
            Item r = serialized.get(hash);
            if (r != null) {
                r.setCount(r.getCount() + ingredient.getCount());
            } else {
                serialized.put(hash, ingredient.clone());
            }
        }
        return serialized;
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
