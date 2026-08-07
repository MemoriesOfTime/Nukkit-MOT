package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDurable;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Position;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;

import java.util.concurrent.ThreadLocalRandom;

public class GrindstoneInventory extends FakeBlockUIComponent {

    private static final int EQUIPMENT = 0;
    private static final int INGREDIENT = 1;

    public static final int GRINDSTONE_EQUIPMENT_UI_SLOT = 16;
    public static final int GRINDSTONE_INGREDIENT_UI_SLOT = 17;

    public GrindstoneInventory(PlayerUIInventory playerUI, Position position) {
        super(playerUI, InventoryType.GRINDSTONE, 16, position);
    }

    /**
     * Get effective inputs: when equipment is empty, use ingredient as primary input.
     * @return [equipment, ingredient], both AIR if neither slot has items
     */
    private Item[] getEffectiveInputs() {
        Item eq = getEquipment();
        Item iq = getIngredient();
        if (eq.isNull()) {
            eq = iq;
            iq = Item.get(Item.AIR);
        }
        return new Item[]{eq, iq};
    }

    public Item getResult() {
        Item[] inputs = getEffectiveInputs();
        Item eq = inputs[0];
        Item iq = inputs[1];

        if (eq.isNull()) {
            return Item.get(Item.AIR);
        }

        // Enchanted book handling
        if (eq.getId() == Item.ENCHANTED_BOOK) {
            return getEnchantedBookResult(eq);
        }

        if (!(eq instanceof ItemDurable)) {
            return Item.get(Item.AIR);
        }

        // Both input items must be the same type
        if (!iq.isNull() && eq.getId() != iq.getId()) {
            return Item.get(Item.AIR);
        }

        // Remove non-curse enchantments, keep curse enchantments
        Item result = eq.clone();
        removeNonCurseEnchantments(result);

        CompoundTag tag = result.getNamedTag();
        if (tag == null) tag = new CompoundTag();
        tag.putInt("RepairCost", 0);
        tag.remove("custom_ench");
        result.setNamedTag(tag);

        // Merge durability
        if (!iq.isNull() && eq.getId() == iq.getId()) {
            // Output durability is the sum of the durabilities of the two input items
            // plus 5% of the maximum durability of the output item (rounded down)
            // capped so it does not exceed the maximum durability of the output item
            // https://minecraft.wiki/w/Grindstone#Repairing_and_disenchanting
            result.setDamage(Math.max(result.getMaxDurability() - ((result.getMaxDurability() - eq.getDamage()) + (iq.getMaxDurability() - iq.getDamage()) + NukkitMath.floorDouble(result.getMaxDurability() * 0.05)) + 1, 0));
        }

        return result;
    }

    private Item getEnchantedBookResult(Item book) {
        Enchantment[] enchantments = book.getEnchantments();
        boolean hasNonCurse = false;
        boolean hasCurse = false;
        for (Enchantment enchantment : enchantments) {
            if (enchantment.isCurse()) {
                hasCurse = true;
            } else {
                hasNonCurse = true;
            }
        }

        // No non-curse enchantments to remove, grindstone has no effect
        if (!hasNonCurse) {
            return Item.get(Item.AIR);
        }

        // Has curse enchantments: keep as enchanted book, only remove non-curse
        if (hasCurse) {
            Item result = book.clone();
            removeNonCurseEnchantments(result);
            CompoundTag tag = result.getNamedTag();
            if (tag != null) {
                tag.putInt("RepairCost", 0);
                tag.remove("custom_ench");
                result.setNamedTag(tag);
            }
            return result;
        }

        // No curse enchantments, convert to normal book
        return Item.get(Item.BOOK, 0, book.getCount());
    }

    private void removeNonCurseEnchantments(Item item) {
        if (!item.hasEnchantments()) {
            return;
        }
        CompoundTag tag = item.getNamedTag();
        if (tag == null) return;

        Enchantment[] enchantments = item.getEnchantments();
        ListTag<CompoundTag> curseEnch = new ListTag<>("ench");
        for (Enchantment enchantment : enchantments) {
            if (enchantment.isCurse()) {
                curseEnch.add(new CompoundTag()
                        .putShort("id", enchantment.getId())
                        .putShort("lvl", enchantment.getLevel()));
            }
        }

        if (curseEnch.size() > 0) {
            tag.putList(curseEnch);
        } else {
            tag.remove("ench");
        }
        item.setNamedTag(tag);
    }

    /**
     * Calculate experience dropped when removing enchantments via grindstone.
     */
    public int calculateExperience() {
        Item[] inputs = getEffectiveInputs();
        Item eq = inputs[0];
        Item iq = inputs[1];

        if (eq.isNull()) {
            return 0;
        }

        int totalCost = calculateEnchantmentCost(eq) + calculateEnchantmentCost(iq);
        if (totalCost <= 0) {
            return 0;
        }

        return ThreadLocalRandom.current().nextInt(
                NukkitMath.ceilDouble((double) totalCost / 2),
                totalCost + 1
        );
    }

    private int calculateEnchantmentCost(Item item) {
        if (item.isNull() || !item.hasEnchantments()) {
            return 0;
        }
        int cost = 0;
        for (Enchantment enchantment : item.getEnchantments()) {
            if (!enchantment.isCurse()) {
                cost += enchantment.getMinEnchantAbility(enchantment.getLevel());
            }
        }
        return cost;
    }

    public Item getEquipment() {
        return getItem(EQUIPMENT);
    }

    public void setEquipment(Item equipment) {
        setItem(EQUIPMENT, equipment);
    }

    public Item getIngredient() {
        return getItem(INGREDIENT);
    }

    public void setIngredient(Item ingredient) {
        setItem(INGREDIENT, ingredient);
    }

    @Override
    public void onOpen(Player who) {
        super.onOpen(who);
        who.craftingType = Player.GRINDSTONE_WINDOW_ID;
    }

    @Override
    public void onClose(Player who) {
        super.onClose(who);
        who.craftingType = Player.CRAFTING_SMALL;

        Item[] drops = who.getInventory().addItem(this.getItem(EQUIPMENT), this.getItem(INGREDIENT));
        for (Item drop : drops) {
            if (!who.dropItem(drop)) {
                this.getHolder().getLevel().dropItem(this.getHolder().add(0.5, 0.5, 0.5), drop);
            }
        }

        this.clear(EQUIPMENT);
        this.clear(INGREDIENT);
        who.resetCraftingGridType();
    }
}
