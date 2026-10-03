package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.ItemPotato;
import cn.nukkit.item.enchantment.Enchantment;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by Pub4Game on 15.01.2016.
 */
public class BlockPotato extends BlockCrops {

    public BlockPotato(int meta) {
        super(meta);
    }

    public BlockPotato() {
        this(0);
    }

    @Override
    public String getName() {
        return "Potato Block";
    }

    @Override
    public int getId() {
        return POTATO_BLOCK;
    }

    @Override
    public Item toItem() {
        return new ItemPotato();
    }

    @Override
    public String getIdentifier() {
        return "minecraft:potato";
    }

    @Override
    public Item[] getDrops(Item item) {
        if (this.getPropertyValue(GROWTH) >= 7) {
            int fortuneLevel = Math.max(0, item.getEnchantmentLevel(Enchantment.ID_FORTUNE_DIGGING));
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int count = random.nextInt(1, fortuneLevel + 2);
            for (int i = 0; i < 3; i++) {
                if (random.nextInt(15) < 8) {
                    count++;
                }
            }
            if (random.nextDouble() < 0.02) {
                return new Item[]{
                        Item.get(ItemID.POTATO, 0, count),
                        Item.get(ItemID.POISONOUS_POTATO, 0, 1)
                };
            } else {
                return new Item[]{
                        Item.get(ItemID.POTATO, 0, count)
                };
            }
        } else {
            return new Item[]{
                    Item.get(ItemID.POTATO)
            };
        }
    }
}
