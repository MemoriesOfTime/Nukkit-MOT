package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemCarrot;
import cn.nukkit.item.enchantment.Enchantment;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Nukkit Project Team
 */
public class BlockCarrot extends BlockCrops {

    public BlockCarrot(int meta) {
        super(meta);
    }

    public BlockCarrot() {
        this(0);
    }

    @Override
    public String getName() {
        return "Carrot Block";
    }

    @Override
    public int getId() {
        return CARROT_BLOCK;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:carrot";
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
            return new Item[]{
                    new ItemCarrot(0, count)
            };
        }
        return new Item[]{
                new ItemCarrot()
        };
    }

    @Override
    public Item toItem() {
        return new ItemCarrot();
    }
}
