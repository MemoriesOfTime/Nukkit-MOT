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

/**
 * 生成附魔台界面展示的三档附魔选项（基岩版 SAI 流程使用）。
 * <p>
 * Generates the three enchantment options shown in the Bedrock enchanting table
 * UI. Used by the Server Authoritative Inventory flow: the generated options are
 * assigned synthetic recipe ids and echoed back via
 * {@link cn.nukkit.inventory.request.CraftRecipeActionProcessor} when the
 * player picks one.
 */
public final class EnchantmentHelper {

    /**
     * 原版附魔台有效书架数上限，超出此值后不再计入附魔强度。
     * <p>
     * Vanilla cap on bookshelves counted toward enchanting power.
     */
    public static final int MAX_BOOKSHELF_COUNT = 15;

    private static final String[] OPTION_NAMES = {
            "ancient", "arcane", "bane", "breath", "cold", "conjuring",
            "craft", "enchant", "free", "greater", "lesser", "light",
            "magic", "marvel", "mirror", "mystery", "primal", "rune",
            "secret", "shadow", "spell", "temporal"
    };

    private EnchantmentHelper() {
    }

    /**
     * 根据附魔台周围书架数量与原版算法生成三档附魔选项。
     * <p>
     * Generates the three enchantment options using the vanilla bookshelf-weighted
     * algorithm. The required xp level of every option is bounded by 30 regardless
     * of bookshelf count, matching Bedrock parity (see #808).
     *
     * @param tablePos 附魔台世界坐标，可为 null（视为 0 书架）
     *                 <p>enchanting table position; null is treated as 0 bookshelves.
     * @param input    待附魔物品
     *                 <p>item to enchant.
     * @param seed     随机种子
     *                 <p>random seed.
     */
    public static List<PlayerEnchantOptionsPacket.EnchantOptionData> generateOptions(Position tablePos, Item input, long seed) {
        if (input == null || input.isNull() || input.hasEnchantments()) {
            return Collections.emptyList();
        }
        NukkitRandom random = new NukkitRandom(seed);
        int bookshelfCount = countBookshelves(tablePos);
        int selected = random.nextRange(1, 8) + (bookshelfCount >> 1) + random.nextRange(0, bookshelfCount);
        int[] requiredLevels = {
                Math.max(selected / 3, 1),
                selected * 2 / 3 + 1,
                Math.max(selected, bookshelfCount * 2)
        };
        List<PlayerEnchantOptionsPacket.EnchantOptionData> options = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            options.add(createOption(random, input, requiredLevels[i], i));
        }
        return options;
    }

    private static PlayerEnchantOptionsPacket.EnchantOptionData createOption(NukkitRandom random, Item input, int requiredLevel, int slot) {
        int enchantability = Math.max(1, input.getEnchantAbility());
        int power = requiredLevel + random.nextBoundedInt(enchantability >> 1 > 0 ? (enchantability >> 1) + 1 : 2);
        List<PlayerEnchantOptionsPacket.EnchantData> chosen = new ArrayList<>();
        List<Enchantment> applicable = filterApplicable(input, power);
        if (!applicable.isEmpty()) {
            Enchantment first = applicable.get(random.nextBoundedInt(applicable.size()));
            chosen.add(new PlayerEnchantOptionsPacket.EnchantData(first.getId(), first.getLevel()));

            int remainingPower = power / 2;
            while (remainingPower > 0 && !applicable.isEmpty() && random.nextRange(0, 50) <= remainingPower) {
                Enchantment pick = applicable.get(random.nextBoundedInt(applicable.size()));
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

        String name = OPTION_NAMES[random.nextBoundedInt(OPTION_NAMES.length)];
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
            if (enchantment == null
                    || enchantment.isTreasure()
                    || enchantment.isCurse()
                    || !enchantment.canEnchant(input)) {
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

    /**
     * 统计附魔台周围有效书架数量（原版算法，上限 {@link #MAX_BOOKSHELF_COUNT}）。
     * <p>
     * Counts bookshelves around the enchanting table using the vanilla rule: only
     * positions exactly 2 blocks away on the X or Z axis qualify, and the air
     * column between the bookshelf stack and the table must be unobstructed.
     */
    private static int countBookshelves(Position tablePos) {
        if (tablePos == null) {
            return 0;
        }
        Level level = tablePos.getLevel();
        if (level == null) {
            return 0;
        }
        int tableX = tablePos.getFloorX();
        int tableY = tablePos.getFloorY();
        int tableZ = tablePos.getFloorZ();
        int bookshelfCount = 0;
        outer:
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                // 仅检查距附魔台 X 或 Z 距离恰为 2 的位置
                // <p>Only positions exactly 2 blocks away on X or Z qualify.
                if (Math.abs(x) != 2 && Math.abs(z) != 2) {
                    continue;
                }

                // 校验书架与附魔台之间的中间方块必须为空气（可透传方块）。
                // 中间坐标用整数除以 2（向零截断），与 JE EnchantingTableBlock.isValidBookShelf
                // 的 offset.getX()/2 语义一致：±2→±1，±1/0→0。
                // <p>The middle block between bookshelf and table must be air (transmitter).
                // Integer division by 2 (truncating toward zero) matches JE
                // EnchantingTableBlock.isValidBookShelf's offset.getX()/2: ±2→±1, ±1/0→0.
                int midX = x / 2;
                int midZ = z / 2;
                for (int y = 0; y <= 1; y++) {
                    if (level.getBlockIdAt(tableX + midX, tableY + y, tableZ + midZ) != BlockID.AIR) {
                        continue outer;
                    }
                }

                // 统计当前位置 0/1 高度上的书架
                // <p>Count bookshelves at y=0 and y=1 on this column.
                for (int y = 0; y <= 1; y++) {
                    if (level.getBlockIdAt(tableX + x, tableY + y, tableZ + z) == BlockID.BOOKSHELF) {
                        bookshelfCount++;
                        if (bookshelfCount >= MAX_BOOKSHELF_COUNT) {
                            return bookshelfCount;
                        }
                    }
                }
            }
        }
        return bookshelfCount;
    }
}
