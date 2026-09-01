package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSeedsWheat;
import cn.nukkit.item.ItemWheat;
import cn.nukkit.item.enchantment.Enchantment;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Created on 2015/12/2 by xtypr.
 * Package cn.nukkit.block in project Nukkit .
 */
public class BlockWheat extends BlockCrops {

    public BlockWheat() {
        this(0);
    }

    public BlockWheat(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Wheat Block";
    }

    @Override
    public int getId() {
        return WHEAT_BLOCK;
    }

    @Override
    public Item toItem() {
        return new ItemSeedsWheat();
    }

    @Override
    public String getIdentifier() {
        return "minecraft:wheat";
    }

    @Override
    public Item[] getDrops(Item item) {
        if (this.getPropertyValue(GROWTH) >= 7) {
            int count = applySeedFortune(item);
            if (count > 0) {
                return new Item[]{
                        new ItemWheat(),
                        new ItemSeedsWheat(0, count)
                };
            }
            return new Item[]{new ItemWheat()};
        } else {
            return new Item[]{
                    new ItemSeedsWheat()
            };
        }
    }

    /**
     * Apply Bedrock binomial Fortune drop to crop seeds.
     * <p>
     * Performs {@code 3 + fortuneLevel} trials, each with an 8/15 success probability.
     * Returns the number of successful trials (no base seed on Bedrock).
     *
     * @param item the tool used to break the crop
     * @return the seed count produced by the binomial trials
     */
    private static int applySeedFortune(Item item) {
        int fortuneLevel = Math.max(0, item.getEnchantmentLevel(Enchantment.ID_FORTUNE_DIGGING));
        int attempts = 3 + fortuneLevel;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = 0;
        for (int i = 0; i < attempts; i++) {
            if (random.nextInt(15) < 8) { // 8/15 ≈ 0.5333 (Bedrock)
                count++;
            }
        }
        return count;
    }
}
