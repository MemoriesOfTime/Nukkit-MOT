package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Created on 2015/11/22 by xtypr.
 * Package cn.nukkit.block in project Nukkit .
 */
public class BlockBeetroot extends BlockCrops {

    public BlockBeetroot() {
        this(0);
    }

    public BlockBeetroot(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BEETROOT_BLOCK;
    }

    @Override
    public String getName() {
        return "Beetroot Block";
    }

    @Override
    public Item toItem() {
        return Item.get(Item.BEETROOT_SEEDS);
    }

    @Override
    public String getIdentifier() {
        return "minecraft:beetroot";
    }

    @Override
    public Item[] getDrops(Item item) {
        if (this.getPropertyValue(GROWTH) >= 7) {
            return new Item[]{
                    Item.get(Item.BEETROOT, 0, 1),
                    Item.get(Item.BEETROOT_SEEDS, 0, applySeedFortune(item))
            };
        } else {
            return new Item[]{
                    Item.get(Item.BEETROOT_SEEDS, 0, 1)
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
