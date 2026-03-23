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

    public Item getResult() {
        Item eq = getEquipment();
        Item iq = getIngredient();

        // 当 equipment 为空时，使用 ingredient 作为输入
        if (eq.isNull()) {
            eq = iq;
            iq = Item.get(Item.AIR);
        }

        if (eq.isNull()) {
            return Item.get(Item.AIR);
        }

        // 附魔书处理
        if (eq.getId() == Item.ENCHANTED_BOOK) {
            return getEnchantedBookResult(eq);
        }

        if (!(eq instanceof ItemDurable)) {
            return Item.get(Item.AIR);
        }

        // 两个输入物品必须相同
        if (!iq.isNull() && eq.getId() != iq.getId()) {
            return Item.get(Item.AIR);
        }

        // 移除非诅咒附魔，保留诅咒附魔
        Item result = eq.clone();
        removeNonCurseEnchantments(result);

        CompoundTag tag = result.getNamedTag();
        if (tag == null) tag = new CompoundTag();
        tag.putInt("RepairCost", 0);
        tag.remove("custom_ench");
        result.setNamedTag(tag);

        // 合并耐久度
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

        // 没有非诅咒附魔可移除时，砂轮无效果
        if (!hasNonCurse) {
            return Item.get(Item.AIR);
        }

        // 有诅咒附魔时保留为附魔书，只移除非诅咒附魔
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

        // 无诅咒附魔，转为普通书
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
     * 计算砂轮移除附魔产生的经验值
     */
    public int calculateExperience() {
        Item eq = getEquipment();
        Item iq = getIngredient();

        if (eq.isNull()) {
            eq = iq;
            iq = Item.get(Item.AIR);
        }

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
}
