package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.inventory.ShapelessRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemFirework;
import cn.nukkit.item.ItemID;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FireworkRecipe extends MultiRecipe {

    public FireworkRecipe(){
        super(UUID.fromString(TYPE_FIREWORKS));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        if (outputItem.getId() == ItemID.FIREWORKS) {
            boolean hasPaper = false;
            int powder = 0;
            for (Item input : inputs) {
                if (input.getId() == ItemID.GUNPOWDER) {
                    powder++;
                } else if (input.getId() == ItemID.PAPER) {
                    hasPaper = true;
                } else if (input.getId() != ItemID.FIREWORKSCHARGE) {
                    // Only paper, gunpowder and firework stars are allowed
                    return false;
                }
            }
            if (!hasPaper) {
                return false;
            }
            if (powder < 1 || powder > 3) {
                return false;
            }
            // 原版固定产出3个火箭，防止恶意客户端声明任意数量
            if (outputItem.getCount() != 3) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public Recipe toRecipe(Item outputItem, List<Item> inputs) {
        // 服务端根据输入材料构建正确的输出，不信任客户端 NBT
        int powder = 0;
        List<CompoundTag> explosions = new ArrayList<>();

        for (Item input : inputs) {
            if (input.getId() == ItemID.GUNPOWDER) {
                powder++;
            } else if (input.getId() == ItemID.FIREWORKSCHARGE && input.hasCompoundTag()) {
                // 烟花星的爆炸数据嵌套在 FireworksItem 子标签中
                CompoundTag starTag = input.getNamedTag();
                CompoundTag fireworksItem = starTag.contains("FireworksItem")
                        ? starTag.getCompound("FireworksItem") : starTag;
                CompoundTag explosion = new CompoundTag();
                if (fireworksItem.exist("FireworkColor")) {
                    explosion.putByteArray("FireworkColor", fireworksItem.getByteArray("FireworkColor"));
                }
                if (fireworksItem.exist("FireworkFade")) {
                    explosion.putByteArray("FireworkFade", fireworksItem.getByteArray("FireworkFade"));
                }
                explosion.putBoolean("FireworkFlicker", fireworksItem.getBoolean("FireworkFlicker"));
                explosion.putBoolean("FireworkTrail", fireworksItem.getBoolean("FireworkTrail"));
                explosion.putByte("FireworkType", fireworksItem.getByte("FireworkType"));
                explosions.add(explosion);
            }
        }

        ItemFirework firework = new ItemFirework(0, 3);
        firework.setFlight(powder);

        if (!explosions.isEmpty()) {
            CompoundTag tag = firework.getNamedTag();
            ListTag<CompoundTag> explosionList = tag.getCompound("Fireworks").getList("Explosions", CompoundTag.class);
            for (CompoundTag explosion : explosions) {
                explosionList.add(explosion);
            }
            firework.setNamedTag(tag);
        }

        return new ShapelessRecipe(firework, inputs);
    }
}
