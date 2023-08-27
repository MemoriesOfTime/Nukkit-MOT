package cn.nukkit.item.enchantment;

import cn.nukkit.block.BlockID;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.network.protocol.PlayerEnchantOptionsPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class EnchantmentHelper {
    private static final int MAX_BOOKSHELF_COUNT = 15;

    public static List<PlayerEnchantOptionsPacket.EnchantOptionData> getEnchantOptions(Position tablePos, Item input, int seed) {
        if (input == null || input.hasEnchantments()) {
            return Collections.emptyList();
        }

        NukkitRandom random = new NukkitRandom(seed);

        int bookshelfCount = countBookshelves(tablePos);
        int baseRequiredLevel = random.nextRange(1, 8) + (bookshelfCount >> 1) + random.nextRange(0, bookshelfCount);

        return List.of(
            createEnchantOption(random, input, (int) Math.floor(Math.max(baseRequiredLevel / 3D, 1))),
            createEnchantOption(random, input, (int) Math.floor(baseRequiredLevel * 2D / 3 + 1)),
            createEnchantOption(random, input, Math.max(baseRequiredLevel, bookshelfCount * 2))
        );
    }

    private static int countBookshelves(Position tablePos) {
        int bookshelfCount = 0;
        Level world = tablePos.getLevel();

        for (int x = -2; x <= 2; x++) {
            outer:
            for (int z = -2; z <= 2; z++) {
                // We only check blocks at a distance of 2 blocks from the enchanting table
                if (Math.abs(x) != 2 && Math.abs(z) != 2) {
                    continue;
                }

                // Ensure the space between the bookshelf stack at this X/Z and the enchanting table is empty
                for (int y = 0; y <= 1; y++) {
                    // Calculate the coordinates of the space between the bookshelf and the enchanting table
                    if (world.getBlock(
                        tablePos.add(Math.max(Math.min(x, 1), -1),y, Math.max(Math.min(z, 1), -1))
                    ).getId() != BlockID.AIR) {
                        continue outer;
                    }
                }

                // Finally, check the number of bookshelves at the current position
                for (int y = 0; y <= 1; y++) {
                    if (world.getBlock(tablePos.add(x, y, z)).getId() == BlockID.BOOKSHELF) {
                        bookshelfCount++;
                        if (bookshelfCount == MAX_BOOKSHELF_COUNT) {
                            return bookshelfCount;
                        }
                    }
                }
            }
        }

        return bookshelfCount;
    }

    private static PlayerEnchantOptionsPacket.EnchantOptionData createEnchantOption(NukkitRandom random, Item inputItem, int requiredXpLevel) {
        int enchantingPower = requiredXpLevel;

        int enchantability = inputItem.getEnchantAbility();
        enchantingPower = enchantingPower + random.nextRange(0, enchantability >> 2) + random.nextRange(0, enchantability >> 2) + 1;

        // Random bonus for enchanting power between 0.85 and 1.15
        double bonus = 1 + (random.nextFloat() + random.nextFloat() - 1) * 0.15;
        enchantingPower = (int) Math.round(enchantingPower * bonus);

        List<Enchantment> resultEnchantments = new ArrayList<>();
        List<Enchantment> availableEnchantments = getAvailableEnchantments(enchantingPower, inputItem);
        Enchantment lastEnchantment = getRandomWeightedEnchantment(random, availableEnchantments);

        if (lastEnchantment != null) {
            resultEnchantments.add(lastEnchantment);

            // With probability (power + 1) / 50, continue adding enchantments
            while (random.nextFloat() <= (enchantingPower + 1) / 50D) {
                availableEnchantments = availableEnchantments.stream()
                    .filter(e -> e.getId() != lastEnchantment.getId() && e.isCompatibleWith(lastEnchantment))
                    .collect(Collectors.toList());
                Enchantment nextEnchantment = getRandomWeightedEnchantment(random, availableEnchantments);

                if (nextEnchantment == null) break;

                resultEnchantments.add(nextEnchantment);
                enchantingPower >>= 1;
            }
        }

        return new PlayerEnchantOptionsPacket.EnchantOptionData(requiredXpLevel, getRandomOptionName(random), resultEnchantments);
    }

    private static List<Enchantment> getAvailableEnchantments(int enchantingPower, Item item) {
        List<Enchantment> list = new ArrayList<>();
        for (Enchantment enchantment : getPrimaryEnchantmentsForItem(item)) {
            for (int lvl = enchantment.getMaxLevel(); lvl > 0; lvl--) {
                if (enchantingPower >= enchantment.getMinEnchantingPower(lvl) &&
                    enchantingPower <= enchantment.getMaxEnchantingPower(lvl)
                ) {
                    list.add(enchantment.setLevel(lvl));
                    break;
                }
            }
        }
        return list;
    }

    private static Enchantment getRandomWeightedEnchantment(NukkitRandom random, List<Enchantment> enchantments) {
        if (enchantments.size() == 0) {
            return null;
        }

        int totalWeight = 0;
        for (Enchantment enchantment : enchantments) {
            totalWeight += enchantment.getRarity().getWeight();
        }

        Enchantment result = null;
        int randomWeight = random.nextRange(1, totalWeight);

        for (Enchantment enchantment : enchantments) {
            randomWeight -= enchantment.getRarity().getWeight();
            if (randomWeight <= 0) {
                result = enchantment;
                break;
            }
        }

        return result;
    }

    private static String getRandomOptionName(NukkitRandom random) {
        StringBuilder name = new StringBuilder();
        for (int i = random.nextBoundedInt(15 - 5 + 1) + 5; i > 0; i--) {
            name.append((char) (random.nextBoundedInt('z' - 'a' + 1) + 'a'));
        }
        return name.toString();
    }

    private static List<Enchantment> getPrimaryEnchantmentsForItem(Item item) {
        final List<Enchantment> list = new ArrayList<>();

        for (final Enchantment ench : Enchantment.getEnchantments()) {
            if (ench.canEnchant(item)) {
                list.add(ench);
            }
        }

        return list;
    }
}
