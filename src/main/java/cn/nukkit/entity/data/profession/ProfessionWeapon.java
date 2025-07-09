package cn.nukkit.entity.data.profession;

import cn.nukkit.block.BlockID;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.RecipeBuildUtils;

import java.util.Random;

public class ProfessionWeapon extends Profession {

    public ProfessionWeapon() {
        super(9, BlockID.GRINDSTONE, "entity.villager.weapon");
    }

    // Вспомогательный метод для создания предмета по namespace ID
    private Item createItem(String namespaceId, int count) {
        Item item = Item.fromString(namespaceId);
        if (item != null) {
            item.setCount(count);
        }
        return item;
    }

    // Вспомогательный метод для создания зачарованного предмета
    private Item createEnchantedItem(String namespaceId, int[] possibleEnchantments, Random random) {
        Item item = Item.fromString(namespaceId);
        if (item != null) {
            int enchantId = possibleEnchantments[random.nextInt(possibleEnchantments.length)];
            Enchantment enchantment = Enchantment.getEnchantment(enchantId);
            enchantment.setLevel(1 + random.nextInt(enchantment.getMaxLevel()));
            item.addEnchantment(enchantment);
        }
        return item;
    }

    @Override
    public ListTag<Tag> buildTrades(int seed) {
        ListTag<Tag> recipes = new ListTag<>("Recipes");
        Random random = new Random(seed);

        int[] enchantments = new int[]{
                Enchantment.ID_DURABILITY,
                Enchantment.ID_DAMAGE_ALL,
                Enchantment.ID_VANISHING_CURSE,
                Enchantment.ID_DAMAGE_SMITE,
                Enchantment.ID_DAMAGE_ARTHROPODS,
                Enchantment.ID_LOOTING,
                Enchantment.ID_FIRE_ASPECT
        };

        // Создаем зачарованные кастомные мечи
        Item ironSword = createEnchantedItem("fireshaldrpg:iron_sword", enchantments, random);
        Item steelSword = createEnchantedItem("fireshaldrpg:steel_sword", enchantments, random);
        Item mithrilSword = createEnchantedItem("fireshaldrpg:mithril_sword", enchantments, random);

        // Создаем зачарованные топоры
        Item diamondAxe = createEnchantedItem("minecraft:diamond_axe", enchantments, random);

        recipes.add(RecipeBuildUtils.of(createItem("minecraft:coal", 15), createItem("minecraft:emerald", 1))
                        .setMaxUses(16)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(2)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 3), createItem("fireshaldrpg:iron_axe", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(1)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 7 + random.nextInt(15)), ironSword)
                        .setMaxUses(3)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(1)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:iron_ingot", 4), createItem("minecraft:emerald", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(2)
                        .setTraderExp(10)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 36), createItem("minecraft:bell", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(2)
                        .setTraderExp(5)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 5), steelSword)
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(2)
                        .setTraderExp(5)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:flint", 24), createItem("minecraft:emerald", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(3)
                        .setTraderExp(20)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:diamond", 1), createItem("minecraft:emerald", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(4)
                        .setTraderExp(30)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 17 + random.nextInt(15)), diamondAxe)
                        .setMaxUses(3)
                        .setRewardExp((byte) 1)
                        .setTier(4)
                        .setTraderExp(15)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 25 + random.nextInt(15)), mithrilSword)
                        .setMaxUses(3)
                        .setRewardExp((byte) 1)
                        .setTier(5)
                        .setTraderExp(0)
                        .build());
        return recipes;
    }
}