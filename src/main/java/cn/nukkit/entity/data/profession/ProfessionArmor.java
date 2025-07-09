package cn.nukkit.entity.data.profession;

import cn.nukkit.block.BlockID;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.RecipeBuildUtils;

import java.util.Random;

public class ProfessionArmor extends Profession {

    public ProfessionArmor() {
        super(8, BlockID.BLAST_FURNACE, "entity.villager.armor");
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
                Enchantment.ID_THORNS,
                Enchantment.ID_PROTECTION_ALL,
                Enchantment.ID_PROTECTION_EXPLOSION,
                Enchantment.ID_PROTECTION_PROJECTILE,
                Enchantment.ID_PROTECTION_FIRE,
                Enchantment.ID_VANISHING_CURSE
        };

        // Создаем зачарованные предметы
        Item mithrilLeggings = createEnchantedItem("fireshaldrpg:mithril_platelegs", enchantments, random);
        Item mithrilChestplate = createEnchantedItem("fireshaldrpg:mithril_platebody", enchantments, random);
        Item mithrilHelmet = createEnchantedItem("fireshaldrpg:mithril_helm", enchantments, random);
        Item mithrilBoots = createEnchantedItem("fireshaldrpg:mithril_boots", enchantments, random);

        // Добавляем рецепты
        recipes.add(RecipeBuildUtils.of(createItem("minecraft:coal", 15), createItem("minecraft:emerald", 1))
                        .setMaxUses(16)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(2)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 4), createItem("fireshaldrpg:iron_boots", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(1)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 7), createItem("fireshaldrpg:iron_platelegs", 1))
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 5), createItem("fireshaldrpg:iron_helm", 1))
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 9), createItem("fireshaldrpg:iron_platebody", 1))
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(0)
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
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 3), createItem("fireshaldrpg:steel_platelegs", 1))
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(2)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 1), createItem("fireshaldrpg:steel_boots", 1))
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(2)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(Item.get(Item.BUCKET), createItem("minecraft:emerald", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(3)
                        .setTraderExp(20)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:diamond", 1), createItem("minecraft:emerald", 1))
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(3)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 4), createItem("fireshaldrpg:steel_platebody", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(3)
                        .setTraderExp(10)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 1), createItem("fireshaldrpg:steel_helm", 1))
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(3)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 5), createItem("minecraft:shield", 1))
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(3)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 19 + random.nextInt(15)), mithrilLeggings)
                        .setMaxUses(3)
                        .setRewardExp((byte) 1)
                        .setTier(4)
                        .setTraderExp(15)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 13 + random.nextInt(15)), mithrilBoots)
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(4)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 21 + random.nextInt(15)), mithrilChestplate)
                        .setMaxUses(3)
                        .setRewardExp((byte) 1)
                        .setTier(5)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 13 + random.nextInt(15)), mithrilHelmet)
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(5)
                        .setTraderExp(0)
                        .build());

        return recipes;
    }
}