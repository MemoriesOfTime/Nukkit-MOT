package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.block.AnvilDamageEvent;
import cn.nukkit.event.block.AnvilDamageEvent.DamageCause;
import cn.nukkit.event.inventory.RepairItemEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Sound;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftRecipeOptionalAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseSlot;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Handles anvil rename/merge/repair and cartography combine operations via the
 * Server Authoritative "optional" craft recipe action. Replicates Vanilla anvil
 * cost accounting from the legacy RepairItemTransaction.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
@Log4j2
public class CraftRecipeOptionalProcessor implements ItemStackRequestActionProcessor<CraftRecipeOptionalAction> {

    public static final String ANVIL_FILTER_INDEX_KEY = "anvilFilterIndex";

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.CRAFT_RECIPE_OPTIONAL;
    }

    @Override
    public ActionResponse handle(CraftRecipeOptionalAction action, Player player, ItemStackRequestContext context) {
        Optional<Inventory> topWindow = player.getTopWindow();
        if (topWindow.isEmpty()) {
            return context.error();
        }
        Inventory inventory = topWindow.get();
        context.put(ANVIL_FILTER_INDEX_KEY, action.getFilteredStringIndex());

        ItemStackRequest request = context.getItemStackRequest();
        String filterString = null;
        String[] filterStrings = request.getFilterStrings();
        if (filterStrings != null && filterStrings.length > 0) {
            int index = action.getFilteredStringIndex();
            if (index >= 0 && index < filterStrings.length) {
                String candidate = filterStrings[index];
                if (candidate != null && !candidate.isBlank()) {
                    if (candidate.length() > 64) {
                        return context.error();
                    }
                    filterString = candidate;
                }
            }
        }

        Item result;
        int levelCost = 0;
        List<Item> expectedConsumes = new ArrayList<>(2);
        if (inventory instanceof AnvilInventory anvilInventory) {
            AnvilResult pair = updateAnvilResult(player, anvilInventory, filterString);
            if (pair == null || pair.result.isNull()) {
                return context.error();
            }
            result = pair.result;
            levelCost = pair.levelCost;
            CraftRecipeActionProcessor.addExpectedConsumeItem(expectedConsumes, anvilInventory.getInputSlot(), 1);
            CraftRecipeActionProcessor.addExpectedConsumeItem(expectedConsumes, anvilInventory.getMaterialSlot(), pair.materialCost);

            // Mirror legacy RepairItemTransaction: fire RepairItemEvent before any
            // state mutation so plugins can veto the anvil operation or override
            // the xp cost via event.setCost(). The event.getCost() result feeds
            // both exp deduction and the cost-check gate below.
            Item inputSnapshot = anvilInventory.getInputSlot().clone();
            Item materialSnapshot = anvilInventory.getMaterialSlot().clone();
            RepairItemEvent repairEvent = new RepairItemEvent(
                    anvilInventory, inputSnapshot, result.clone(), materialSnapshot, levelCost, player);
            Server.getInstance().getPluginManager().callEvent(repairEvent);
            if (repairEvent.isCancelled()) {
                return context.error();
            }
            int finalCost = repairEvent.getCost();
            if (!player.isCreative() && finalCost > 0) {
                if (player.getExperienceLevel() < finalCost) {
                    return context.error();
                }
            }
            final int commitCost = finalCost;
            context.onCommit(() -> {
                if (!player.isCreative() && commitCost > 0) {
                    player.setExperience(player.getExperience(), player.getExperienceLevel() - commitCost);
                }
                applyAnvilDamage(player, anvilInventory);
            });
        } else if (inventory instanceof CartographyTableInventory cartographyInventory) {
            result = updateCartographyTableResult(cartographyInventory, filterString);
            if (result == null || result.isNull()) {
                return context.error();
            }
            CraftRecipeActionProcessor.addExpectedConsumeItem(expectedConsumes, cartographyInventory.getInput(), 1);
            CraftRecipeActionProcessor.addExpectedConsumeItem(expectedConsumes, cartographyInventory.getAdditional(), 1);
        } else {
            return context.error();
        }

        if (!CraftRecipeActionProcessor.validateExpectedConsumePlan(player, expectedConsumes, context)) {
            return context.error();
        }

        result.autoAssignStackNetworkId();
        player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, result, false);

        ItemStackResponseSlot responseSlot = new ItemStackResponseSlot(
                0, 0, result.getCount(), result.getStackNetId(),
                result.hasCustomName() ? result.getCustomName() : "",
                result.getDamage(), ""
        );
        return context.success(List.of(new ItemStackResponseContainer(
                ContainerSlotType.CREATED_OUTPUT,
                List.of(responseSlot),
                new FullContainerName(ContainerSlotType.CREATED_OUTPUT, null)
        )));
    }

    private record AnvilResult(Item result, int levelCost, int materialCost) {}

    /**
     * Apply the vanilla 12% chance that an anvil loses one durability level on
     * use. At level 3 the block is destroyed outright. AnvilDamageEvent is fired
     * so plugins can override or veto the durability change.
     * Mirrors {@code RepairItemTransaction.execute()} from the legacy path.
     */
    private static void applyAnvilDamage(Player player, AnvilInventory anvilInventory) {
        FakeBlockMenu holder = anvilInventory.getHolder();
        if (holder == null) {
            return;
        }
        Block block = player.getLevel().getBlock(holder.getFloorX(), holder.getFloorY(), holder.getFloorZ());
        if (block.getId() != Block.ANVIL) {
            return;
        }

        int oldDamage = block.getDamage() >= 8 ? 2 : block.getDamage() >= 4 ? 1 : 0;
        int newDamage = !player.isCreative() && ThreadLocalRandom.current().nextInt(100) < 12
                ? oldDamage + 1
                : oldDamage;

        AnvilDamageEvent event = new AnvilDamageEvent(block, oldDamage, newDamage, DamageCause.USE, player);
        event.setCancelled(oldDamage == newDamage);
        Server.getInstance().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            player.getLevel().addLevelEvent(block, LevelEventPacket.EVENT_SOUND_ANVIL_USE);
            return;
        }

        int finalDamage = event.getNewDamage();
        if (finalDamage > 2) {
            player.getLevel().setBlock(block, Block.get(Block.AIR), true);
            player.getLevel().addLevelEvent(block, LevelEventPacket.EVENT_SOUND_ANVIL_BREAK);
        } else {
            if (finalDamage < 0) {
                finalDamage = 0;
            }
            if (finalDamage != oldDamage) {
                block.setDamage((finalDamage << 2) | (block.getDamage() & 0x3));
                player.getLevel().setBlock(block, block, true);
            }
            player.getLevel().addLevelEvent(block, LevelEventPacket.EVENT_SOUND_ANVIL_USE);
        }
    }

    private AnvilResult updateAnvilResult(Player player, AnvilInventory inventory, String filterString) {
        Item target = inventory.getInputSlot();
        Item sacrifice = inventory.getMaterialSlot();
        if (target.isNull() && sacrifice.isNull()) {
            return null;
        }

        int extraCost = 0;
        int costHelper = 0;
        int repairMaterial = getRepairMaterial(target);
        Item result = target.clone();
        int materialCost = 0;

        Set<Enchantment> enchantments = new LinkedHashSet<>(Arrays.asList(target.getEnchantments()));
        if (!sacrifice.isNull()) {
            boolean enchantedBook = sacrifice.getId() == Item.ENCHANTED_BOOK && sacrifice.getEnchantments().length > 0;
            int repair;
            int repair2;
            int repair3;
            if (result.getMaxDurability() != -1 && sacrifice.getId() == repairMaterial) {
                // Anvil - repair via material
                repair = Math.min(result.getDamage(), result.getMaxDurability() / 4);
                if (repair <= 0) {
                    return null;
                }
                for (repair2 = 0; repair > 0 && repair2 < sacrifice.getCount(); ++repair2) {
                    repair3 = result.getDamage() - repair;
                    result.setDamage(repair3);
                    ++extraCost;
                    repair = Math.min(result.getDamage(), result.getMaxDurability() / 4);
                }
                materialCost = repair2;
            } else {
                if (!enchantedBook && (result.getId() != sacrifice.getId() || result.getMaxDurability() == -1)) {
                    player.getLevel().addSound(player, Sound.RANDOM_ANVIL_USE, 1f, 1f);
                    return null;
                }
                materialCost = 1;

                if (result.getMaxDurability() != -1 && !enchantedBook) {
                    // Anvil - combine durability from same-type item
                    repair = target.getMaxDurability() - target.getDamage();
                    repair2 = sacrifice.getMaxDurability() - sacrifice.getDamage();
                    repair3 = repair2 + result.getMaxDurability() * 12 / 100;
                    int totalRepair = repair + repair3;
                    int finalDamage = result.getMaxDurability() - totalRepair + 1;
                    if (finalDamage < 0) {
                        finalDamage = 0;
                    }
                    if (finalDamage < result.getDamage()) {
                        result.setDamage(finalDamage);
                        extraCost += 2;
                    }
                }

                Enchantment[] sacrificeEnchantments = sacrifice.getEnchantments();
                boolean compatibleFlag = false;
                boolean incompatibleFlag = false;
                Iterator<Enchantment> it = Arrays.stream(sacrificeEnchantments).iterator();

                iter:
                while (true) {
                    Enchantment sacrificeEnch;
                    do {
                        if (!it.hasNext()) {
                            if (incompatibleFlag && !compatibleFlag) {
                                return null;
                            }
                            break iter;
                        }
                        sacrificeEnch = it.next();
                    } while (sacrificeEnch == null);

                    Enchantment resultEnch = result.getEnchantment(sacrificeEnch.id);
                    int targetLevel = resultEnch != null ? resultEnch.getLevel() : 0;
                    int resultLevel = sacrificeEnch.getLevel();
                    resultLevel = targetLevel == resultLevel ? resultLevel + 1 : Math.max(resultLevel, targetLevel);
                    boolean compatible = sacrificeEnch.canEnchant(target);
                    if (player.isCreative() || target.getId() == Item.ENCHANTED_BOOK) {
                        compatible = true;
                    }

                    Iterator<Enchantment> targetIt = Stream.of(target.getEnchantments()).iterator();
                    while (targetIt.hasNext()) {
                        Enchantment targetEnch = targetIt.next();
                        if (targetEnch.id != sacrificeEnch.id
                                && (!sacrificeEnch.isCompatibleWith(targetEnch)
                                || !targetEnch.isCompatibleWith(sacrificeEnch))) {
                            compatible = false;
                            ++extraCost;
                        }
                    }

                    if (!compatible) {
                        incompatibleFlag = true;
                    } else {
                        compatibleFlag = true;
                        if (resultLevel > sacrificeEnch.getMaxLevel()) {
                            resultLevel = sacrificeEnch.getMaxLevel();
                        }
                        Enchantment used = Enchantment.getEnchantment(sacrificeEnch.getId()).setLevel(resultLevel);
                        enchantments.add(used);
                        int rarity;
                        int weight = sacrificeEnch.getRarity().getWeight();
                        if (weight >= 10) {
                            rarity = 1;
                        } else if (weight >= 5) {
                            rarity = 2;
                        } else if (weight >= 2) {
                            rarity = 4;
                        } else {
                            rarity = 8;
                        }
                        if (enchantedBook) {
                            rarity = Math.max(1, rarity / 2);
                        }
                        extraCost += rarity * Math.max(0, resultLevel - targetLevel);
                        if (target.getCount() > 1) {
                            extraCost = 40;
                        }
                    }
                }
            }
        }

        // Anvil - rename
        if (filterString == null || filterString.isEmpty()) {
            player.getLevel().addSound(player, Sound.RANDOM_ANVIL_USE, 1f, 1f);
            if (target.hasCustomName()) {
                costHelper = 1;
                extraCost += costHelper;
                result.clearCustomName();
            }
        } else {
            if (filterString.length() > 50) {
                return null;
            }
            costHelper = 1;
            extraCost += costHelper;
            result.setCustomName(filterString);
        }

        int levelCost = getRepairCost(result) + (sacrifice.isNull() ? 0 : getRepairCost(sacrifice));
        levelCost += extraCost;
        if (extraCost <= 0) {
            return new AnvilResult(Item.get(Item.AIR), levelCost, materialCost);
        }

        if (costHelper == extraCost && costHelper > 0 && levelCost >= 40) {
            levelCost = 39;
        }
        if (levelCost >= 40 && !player.isCreative()) {
            return new AnvilResult(Item.get(Item.AIR), levelCost, materialCost);
        }

        int repairCost = getRepairCost(result);
        if (!sacrifice.isNull() && repairCost < getRepairCost(sacrifice)) {
            repairCost = getRepairCost(sacrifice);
        }
        if (costHelper != extraCost || costHelper == 0) {
            repairCost = repairCost * 2 + 1;
        }
        CompoundTag namedTag = result.hasCompoundTag() ? result.getNamedTag() : new CompoundTag();
        namedTag.putInt("RepairCost", repairCost);
        namedTag.remove("ench");
        result.setNamedTag(namedTag);
        if (!enchantments.isEmpty()) {
            result.addEnchantment(enchantments.toArray(Enchantment.EMPTY_ARRAY));
        }
        return new AnvilResult(result, levelCost, materialCost);
    }

    private Item updateCartographyTableResult(CartographyTableInventory inventory, String filterString) {
        Item input = inventory.getInput();
        Item additional = inventory.getAdditional();
        if (input.isNull() && additional.isNull()) {
            return null;
        }

        Item result = null;
        int inputId = input.getId();
        int addId = additional.getId();

        // PAPER alone → EMPTY_MAP
        if (inputId == Item.PAPER && additional.isNull()) {
            result = Item.get(Item.EMPTY_MAP);
        }
        // Blank/filled map alone → clone (acts as a reset/copy slot)
        if (result == null && (inputId == Item.EMPTY_MAP || inputId == Item.MAP) && additional.isNull()) {
            result = input.clone();
        }
        // + COMPASS → locator map (damage = 2)
        if (result == null && (inputId == Item.EMPTY_MAP || inputId == Item.MAP || inputId == Item.PAPER)
                && addId == Item.COMPASS) {
            Item base = inputId == Item.PAPER ? Item.get(Item.EMPTY_MAP) : input.clone();
            base.setDamage(2);
            result = base;
        }
        // + GLASS_PANE → lock map (damage = 6)
        if (result == null && inputId == Item.MAP && addId == Item.GLASS_PANE) {
            Item base = input.clone();
            base.setDamage(6);
            result = base;
        }
        // + EMPTY_MAP → copy
        if (result == null && inputId == Item.MAP && addId == Item.EMPTY_MAP) {
            Item base = input.clone();
            base.setCount(2);
            result = base;
        }
        // + PAPER → scale up (map scaling logic requires ItemFilledMap; fall back to damage bump)
        if (result == null && inputId == Item.MAP && addId == Item.PAPER) {
            Item base = input.clone();
            int scale = Math.min(4, base.getDamage() + 1);
            base.setDamage(scale);
            result = base;
        }

        if (result == null) {
            return null;
        }
        if (filterString != null && !filterString.isEmpty()) {
            if (filterString.length() > 50) {
                return null;
            }
            result.setCustomName(filterString);
        } else {
            result.clearCustomName();
        }
        return result;
    }

    private static int getRepairCost(Item item) {
        return item.hasCompoundTag() && Objects.requireNonNull(item.getNamedTag()).contains("RepairCost")
                ? item.getNamedTag().getInt("RepairCost")
                : 0;
    }

    private static int getRepairMaterial(Item target) {
        return switch (target.getId()) {
            case Item.WOODEN_SWORD, Item.WOODEN_PICKAXE, Item.WOODEN_SHOVEL, Item.WOODEN_AXE, Item.WOODEN_HOE ->
                    Item.PLANKS;
            case Item.IRON_SWORD, Item.IRON_PICKAXE, Item.IRON_SHOVEL, Item.IRON_AXE, Item.IRON_HOE,
                 Item.IRON_HELMET, Item.IRON_CHESTPLATE, Item.IRON_LEGGINGS, Item.IRON_BOOTS,
                 Item.CHAIN_HELMET, Item.CHAIN_CHESTPLATE, Item.CHAIN_LEGGINGS, Item.CHAIN_BOOTS ->
                    Item.IRON_INGOT;
            case Item.GOLD_SWORD, Item.GOLD_PICKAXE, Item.GOLD_SHOVEL, Item.GOLD_AXE, Item.GOLD_HOE,
                 Item.GOLD_HELMET, Item.GOLD_CHESTPLATE, Item.GOLD_LEGGINGS, Item.GOLD_BOOTS ->
                    Item.GOLD_INGOT;
            case Item.DIAMOND_SWORD, Item.DIAMOND_PICKAXE, Item.DIAMOND_SHOVEL, Item.DIAMOND_AXE, Item.DIAMOND_HOE,
                 Item.DIAMOND_HELMET, Item.DIAMOND_CHESTPLATE, Item.DIAMOND_LEGGINGS, Item.DIAMOND_BOOTS ->
                    Item.DIAMOND;
            case Item.LEATHER_CAP, Item.LEATHER_TUNIC, Item.LEATHER_PANTS, Item.LEATHER_BOOTS ->
                    Item.LEATHER;
            case Item.STONE_SWORD, Item.STONE_PICKAXE, Item.STONE_SHOVEL, Item.STONE_AXE, Item.STONE_HOE ->
                    Item.COBBLESTONE;
            case Item.NETHERITE_SWORD, Item.NETHERITE_PICKAXE, Item.NETHERITE_SHOVEL, Item.NETHERITE_AXE, Item.NETHERITE_HOE,
                 Item.NETHERITE_HELMET, Item.NETHERITE_CHESTPLATE, Item.NETHERITE_LEGGINGS, Item.NETHERITE_BOOTS ->
                    Item.NETHERITE_INGOT;
            case Item.ELYTRA -> Item.PHANTOM_MEMBRANE;
            default -> Item.AIR;
        };
    }
}
