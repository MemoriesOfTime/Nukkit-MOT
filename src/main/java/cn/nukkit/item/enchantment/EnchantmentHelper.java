package cn.nukkit.item.enchantment;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.PlayerEnchantOptionsPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Minimal helper that produces the three enchantment options displayed in the
 * Bedrock enchanting table UI. Used by the Server Authoritative Inventory flow:
 * the generated options are assigned synthetic recipe ids and echoed back via
 * {@link cn.nukkit.inventory.request.CraftRecipeActionProcessor} when the
 * player picks one.
 * <p>
 * This is a simplified implementation focused on keeping the SA path functional;
 * full parity with vanilla's bookshelf-weighted RNG is staged for a follow-up
 * pass.
 */
public final class EnchantmentHelper {

    private static final String[] OPTION_NAMES = {
            "ancient", "arcane", "bane", "breath", "cold", "conjuring",
            "craft", "enchant", "free", "greater", "lesser", "light",
            "magic", "marvel", "mirror", "mystery", "primal", "rune",
            "secret", "shadow", "spell", "temporal"
    };

    private EnchantmentHelper() {
    }

    public static List<PlayerEnchantOptionsPacket.EnchantOptionData> generateOptions(Item input, long seed) {
        if (input == null || input.isNull() || input.hasEnchantments()) {
            return Collections.emptyList();
        }
        Random random = new Random(seed);
        int[] requiredLevels = {
                Math.max(1, random.nextInt(8) + 1),
                Math.max(1, random.nextInt(15) + 5),
                Math.max(1, random.nextInt(30) + 10)
        };
        List<PlayerEnchantOptionsPacket.EnchantOptionData> options = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            options.add(createOption(random, input, requiredLevels[i], i));
        }
        return options;
    }

    private static PlayerEnchantOptionsPacket.EnchantOptionData createOption(Random random, Item input, int requiredLevel, int slot) {
        int enchantability = Math.max(1, input.getEnchantAbility());
        int power = requiredLevel + random.nextInt(enchantability >> 1 > 0 ? (enchantability >> 1) + 1 : 2);
        List<PlayerEnchantOptionsPacket.EnchantData> chosen = new ArrayList<>();
        List<Enchantment> applicable = filterApplicable(input, power);
        if (!applicable.isEmpty()) {
            Enchantment first = applicable.get(random.nextInt(applicable.size()));
            chosen.add(new PlayerEnchantOptionsPacket.EnchantData(first.getId(), first.getLevel()));

            int remainingPower = power / 2;
            while (remainingPower > 0 && !applicable.isEmpty() && random.nextInt(50) <= remainingPower) {
                Enchantment pick = applicable.get(random.nextInt(applicable.size()));
                boolean compatible = true;
                for (PlayerEnchantOptionsPacket.EnchantData picked : chosen) {
                    if (picked.getType() == pick.getId()) {
                        compatible = false;
                        break;
                    }
                    Enchantment other = Enchantment.getEnchantment(picked.getType());
                    if (other != null && !other.isCompatibleWith(pick)) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    chosen.add(new PlayerEnchantOptionsPacket.EnchantData(pick.getId(), pick.getLevel()));
                }
                remainingPower /= 2;
            }
        }

        String name = OPTION_NAMES[random.nextInt(OPTION_NAMES.length)];
        int netId = 0;
        PlayerEnchantOptionsPacket.EnchantOptionData option = new PlayerEnchantOptionsPacket.EnchantOptionData(
                requiredLevel, slot, chosen,
                Collections.emptyList(), Collections.emptyList(),
                name, netId
        );
        return option;
    }

    private static List<Enchantment> filterApplicable(Item input, int power) {
        List<Enchantment> result = new ArrayList<>();
        for (Enchantment enchantment : Enchantment.getEnchantments()) {
            if (enchantment == null || !enchantment.canEnchant(input)) {
                continue;
            }
            for (int lvl = enchantment.getMaxLevel(); lvl > 0; lvl--) {
                if (power >= enchantment.getMinEnchantAbility(lvl) && power <= enchantment.getMaxEnchantAbility(lvl)) {
                    result.add(enchantment.clone().setLevel(lvl));
                    break;
                }
            }
        }
        return result;
    }
}
