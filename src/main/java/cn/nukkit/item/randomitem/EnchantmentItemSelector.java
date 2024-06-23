package cn.nukkit.item.randomitem;

import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author LT_Name
 */
public class EnchantmentItemSelector extends ConstantItemSelector {

    public EnchantmentItemSelector(int id, Selector parent) {
        this(id, 0, parent);
    }

    public EnchantmentItemSelector(int id, Selector parent, boolean randomDurability) {
        this(id, 0, parent, randomDurability);
    }

    public EnchantmentItemSelector(int id, Integer meta, Selector parent) {
        this(id, meta, 1, parent);
    }

    public EnchantmentItemSelector(int id, Integer meta, Selector parent, boolean randomDurability) {
        this(id, meta, 1, parent, randomDurability);
    }

    public EnchantmentItemSelector(int id, Integer meta, int count, Selector parent) {
        this(Item.get(id, meta, count), parent);
    }

    public EnchantmentItemSelector(int id, Integer meta, int count, Selector parent, boolean randomDurability) {
        this(Item.get(id, meta, count), parent, randomDurability);
    }

    public EnchantmentItemSelector(Item item, Selector parent) {
        super(item, parent, false);
    }

    public EnchantmentItemSelector(Item item, Selector parent, boolean randomDurability) {
        super(item, parent, randomDurability);
    }

    @Override
    public Object select() {
        Item result = item.clone();
        double random = ThreadLocalRandom.current().nextDouble();
        if (random < 0.1) {
            for (int i = 0; i < 3; i++) {
                endowRandomEnchantmentByItem(result);
            }
        } else if (random < 0.2) {
            for (int i = 0; i < 2; i++) {
                endowRandomEnchantmentByItem(result);
            }
        } else {
            endowRandomEnchantmentByItem(result);
        }
        if (this.randomDurability) {
            result.setDamage(result.getMaxDurability() - Utils.rand(0, result.getMaxDurability() - 1));
        }
        return result;
    }

    public void endowRandomEnchantmentByItem(Item randomItem) {
        Random random = ThreadLocalRandom.current();
        List<Enchantment> enchantments = getSupportEnchantments(randomItem);
        if (!enchantments.isEmpty()) {
            Enchantment enchantment = enchantments.get(random.nextInt(enchantments.size()));
            double randomDouble = random.nextDouble();
            if (randomDouble < 0.05) { //减少高等级附魔概率
                enchantment.setLevel(Utils.rand(Math.min(3, enchantment.getLevel()), enchantment.getMaxLevel()));
            } else {
                enchantment.setLevel(Utils.rand(1, Math.min(2, enchantment.getMaxLevel())));
            }
            randomItem.addEnchantment(enchantment);
        }
    }

    /**
     * 根据物品获得支持的附魔
     *
     * @param item 物品
     * @return 支持的附魔
     */
    public List<Enchantment> getSupportEnchantments(Item item) {
        ArrayList<Enchantment> enchantments = new ArrayList<>();
        for (Enchantment enchantment : Enchantment.getEnchantments()) {
            if (item.getId() == Item.ENCHANTED_BOOK || enchantment.canEnchant(item)) {
                enchantments.add(enchantment);
            }
        }
        return enchantments;
    }

}
