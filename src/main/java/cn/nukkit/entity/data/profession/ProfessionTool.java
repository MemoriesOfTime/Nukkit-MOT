package cn.nukkit.entity.data.profession;

import cn.nukkit.block.BlockID;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.RecipeBuildUtils;

import java.util.Random;

public class ProfessionTool extends Profession {

    public ProfessionTool() {
        super(10, BlockID.SMITHING_TABLE, "entity.villager.tool");
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

        int[] ench = new int[]{
                Enchantment.ID_DURABILITY,
                Enchantment.ID_EFFICIENCY,
                Enchantment.ID_FORTUNE_DIGGING,
                Enchantment.ID_SILK_TOUCH
        };

        // Железные инструменты
        Item ironAxe = createEnchantedItem("fireshaldrpg:iron_axe", ench, random);
        Item ironShovel = createEnchantedItem("fireshaldrpg:iron_shovel", ench, random);
        Item ironPickaxe = createEnchantedItem("fireshaldrpg:iron_pickaxe", ench, random);
        Item ironHoe = createEnchantedItem("fireshaldrpg:iron_hoe", ench, random);

        // Стальные инструменты
        Item steelAxe = createEnchantedItem("fireshaldrpg:steel_axe", ench, random);
        Item steelShovel = createEnchantedItem("fireshaldrpg:steel_shovel", ench, random);
        Item steelPickaxe = createEnchantedItem("fireshaldrpg:steel_pickaxe", ench, random);
        Item steelHoe = createEnchantedItem("fireshaldrpg:steel_hoe", ench, random);

        // Мифриловые инструменты
        Item mithrilAxe = createEnchantedItem("fireshaldrpg:mithril_axe", ench, random);
        Item mithrilShovel = createEnchantedItem("fireshaldrpg:mithril_shovel", ench, random);
        Item mithrilPickaxe = createEnchantedItem("fireshaldrpg:mithril_pickaxe", ench, random);

        recipes.add(RecipeBuildUtils.of(createItem("minecraft:coal", 15), createItem("minecraft:emerald", 1))
                        .setMaxUses(16)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(2)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 1), createItem("fireshaldrpg:bronze_axe", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(1)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 1), createItem("fireshaldrpg:bronze_shovel", 1))
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 1), createItem("fireshaldrpg:bronze_pickaxe", 1))
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 1), createItem("fireshaldrpg:bronze_hoe", 1))
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
                .add(RecipeBuildUtils.of(createItem("minecraft:flint", 24), createItem("minecraft:emerald", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(3)
                        .setTraderExp(20)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 6 + random.nextInt(15)), steelAxe)
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(3)
                        .setTraderExp(10)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 7 + random.nextInt(15)), steelShovel)
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(3)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 8 + random.nextInt(15)), steelPickaxe)
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(3)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 4), steelHoe)
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(3)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:diamond", 1), createItem("minecraft:emerald", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(4)
                        .setTraderExp(30)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 17 + random.nextInt(15)), mithrilAxe)
                        .setMaxUses(3)
                        .setRewardExp((byte) 1)
                        .setTier(4)
                        .setTraderExp(15)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 10 + random.nextInt(15)), mithrilShovel)
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(4)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 18 + random.nextInt(15)), mithrilPickaxe)
                        .setMaxUses(3)
                        .setRewardExp((byte) 1)
                        .setTier(5)
                        .setTraderExp(0)
                        .build());
        return recipes;
    }
}