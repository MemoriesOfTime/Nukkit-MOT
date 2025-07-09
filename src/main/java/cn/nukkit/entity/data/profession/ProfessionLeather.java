package cn.nukkit.entity.data.profession;

import cn.nukkit.block.BlockID;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemColorArmor;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.DyeColor;
import cn.nukkit.utils.RecipeBuildUtils;

public class ProfessionLeather extends Profession {

    public ProfessionLeather() {
        super(12, BlockID.CAULDRON_BLOCK, "entity.villager.leather");
    }

    // Вспомогательный метод для создания предмета по namespace ID
    private Item createItem(String namespaceId, int count) {
        Item item = Item.fromString(namespaceId);
        if (item != null) {
            item.setCount(count);
        }
        return item;
    }

    // Вспомогательный метод для создания окрашенной кожаной брони
    private Item createDyedLeatherArmor(String namespaceId, DyeColor color) {
        Item armor = Item.fromString(namespaceId);
        if (armor instanceof ItemColorArmor) {
            ((ItemColorArmor) armor).setColor(color);
        }
        return armor;
    }

    @Override
    public ListTag<Tag> buildTrades(int seed) {
        ListTag<Tag> recipes = new ListTag<>("Recipes");

        recipes.add(RecipeBuildUtils.of(createItem("minecraft:leather", 6), createItem("minecraft:emerald", 1))
                        .setMaxUses(16)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(2)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 7),
                                createDyedLeatherArmor("fireshaldrpg:leather_platebody", DyeColor.RED))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(1)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 3),
                                createDyedLeatherArmor("fireshaldrpg:leather_platelegs", DyeColor.RED))
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(1)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:flint", 26), createItem("minecraft:emerald", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(2)
                        .setTraderExp(10)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 5),
                                createDyedLeatherArmor("fireshaldrpg:leather_helm", DyeColor.RED))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(2)
                        .setTraderExp(5)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 4),
                                createDyedLeatherArmor("fireshaldrpg:leather_boots", DyeColor.RED))
                        .setMaxUses(99)
                        .setRewardExp((byte) 1)
                        .setTier(2)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:rabbit_hide", 9), createItem("minecraft:emerald", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(3)
                        .setTraderExp(20)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 7),
                                createDyedLeatherArmor("fireshaldrpg:leather_platebody", DyeColor.RED))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(3)
                        .setTraderExp(10)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:turtle_shell", 4), createItem("minecraft:emerald", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(4)
                        .setTraderExp(30)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 6), createItem("fireshaldrpg:leather_horse_armor", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(4)
                        .setTraderExp(15)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 6), createItem("fireshaldrpg:saddle", 1))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(5)
                        .setTraderExp(0)
                        .build())
                .add(RecipeBuildUtils.of(createItem("minecraft:emerald", 5),
                                createDyedLeatherArmor("fireshaldrpg:leather_helm", DyeColor.RED))
                        .setMaxUses(12)
                        .setRewardExp((byte) 1)
                        .setTier(5)
                        .setTraderExp(0)
                        .build());

        return recipes;
    }
}