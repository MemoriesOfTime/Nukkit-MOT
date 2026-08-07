package cn.nukkit.item.customitem;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemArmor;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.enchantment.EnchantmentType;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemCategory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归测试：自定义盔甲和工具的服务端属性必须从 {@link CustomItemDefinition} 的 NBT 正确读取，
 * 而不是返回 {@link Item} 基类的错误默认值。
 * <p>
 * Regression tests: custom armor and tool server-side properties must be read from the
 * {@link CustomItemDefinition} NBT instead of the wrong {@link Item} base defaults.
 */
class CustomItemPropertyTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    private CustomArmor helmet;
    private CustomTool pickaxe;
    private CustomTool sword;

    @BeforeEach
    void setUp() {
        helmet = new CustomArmor("test:helmet", "Test Helmet");
        pickaxe = new CustomTool("test:pickaxe", "Test Pickaxe");
        sword = new CustomTool("test:sword", "Test Sword");
    }

    // ===== 自定义盔甲 =====

    @Test
    void customHelmetIsHelmetAndEquippable() {
        assertTrue(helmet.isHelmet());
        assertFalse(helmet.isChestplate());
        assertFalse(helmet.isLeggings());
        assertFalse(helmet.isBoots());
        assertTrue(helmet.isArmor());
        assertTrue(helmet.canBePutInHelmetSlot());
    }

    @Test
    void customHelmetHasConfiguredArmorPoints() {
        assertEquals(5, helmet.getArmorPoints());
    }

    @Test
    void customHelmetHasConfiguredToughness() {
        assertEquals(2, helmet.getToughness());
    }

    @Test
    void customHelmetHasConfiguredTier() {
        assertEquals(ItemArmor.TIER_IRON, helmet.getTier());
    }

    @Test
    void customHelmetEnchantAbilityNonZero() {
        // ItemArmor.getEnchantAbility() 分派于 getTier()，tier=IRON(3) -> 9
        assertEquals(9, helmet.getEnchantAbility());
    }

    @Test
    void customArmorBuilderWritesWearableSlotToNbt() {
        CompoundTag nbt = helmet.getDefinition().getNbt();
        assertEquals("slot.armor.head", nbt.getCompound("components").getCompound("minecraft:wearable").getString("slot"));
        assertEquals("armor_head", nbt.getCompound("components").getCompound("item_properties").getString("enchantable_slot"));
    }

    @Test
    void enchantArmorHeadAcceptsCustomHelmet() {
        assertTrue(EnchantmentType.ARMOR_HEAD.canEnchantItem(helmet));
    }

    // ===== 自定义工具 =====

    @Test
    void customPickaxeIsPickaxe() {
        assertTrue(pickaxe.isPickaxe());
        assertFalse(pickaxe.isAxe());
        assertFalse(pickaxe.isSword());
    }

    @Test
    void customPickaxeHasConfiguredAttackDamage() {
        assertEquals(7, pickaxe.getAttackDamage());
    }

    @Test
    void customPickaxeHasConfiguredMaxDurability() {
        assertEquals(1561, pickaxe.getMaxDurability());
    }

    @Test
    void customPickaxeHasConfiguredTier() {
        assertEquals(ItemTool.TIER_IRON, pickaxe.getTier());
    }

    @Test
    void customPickaxeEnchantAbilityNonZero() {
        // ItemTool.getEnchantAbility() 分派于 getTier()，TIER_IRON(5) -> 14
        assertEquals(14, pickaxe.getEnchantAbility());
    }

    @Test
    void customSwordIsSword() {
        assertTrue(sword.isSword());
        assertFalse(sword.isPickaxe());
    }

    @Test
    void enchantDiggerAcceptsCustomPickaxe() {
        assertTrue(EnchantmentType.DIGGER.canEnchantItem(pickaxe));
    }

    @Test
    void enchantSwordAcceptsCustomSword() {
        assertTrue(EnchantmentType.SWORD.canEnchantItem(sword));
    }

    @Test
    void customToolBuilderWritesAttackDamageToNbt() {
        CompoundTag nbt = pickaxe.getDefinition().getNbt();
        assertEquals(7, nbt.getCompound("components").getCompound("item_properties").getInt("damage"));
    }

    @Test
    void customToolBuilderWritesDurabilityToNbt() {
        CompoundTag nbt = pickaxe.getDefinition().getNbt();
        assertEquals(1561, nbt.getCompound("components").getCompound("minecraft:durability").getInt("max_durability"));
    }

    @Test
    void customToolBuilderWritesPickaxeTag() {
        CompoundTag nbt = pickaxe.getDefinition().getNbt();
        assertTrue(nbt.getCompound("components").contains("item_tags"));
        var tags = nbt.getCompound("components").getList("item_tags").getAll();
        boolean found = false;
        for (var tag : tags) {
            if ("minecraft:is_pickaxe".equals(tag.parseValue())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "minecraft:is_pickaxe tag should be written");
    }

    @Test
    void customArmorHasConfiguredMaxDurability() {
        assertEquals(165, helmet.getMaxDurability());
    }

    // ===== 最小配置（防递归）测试 =====

    @Test
    void minimalArmorDoesNotRecurseAndReturnsDefaults() {
        MinimalArmor chest = new MinimalArmor("test:min_armor", "Min Armor");
        //getDefinition() 内部调 build()，若递归会 StackOverflowError
        assertDoesNotThrow(chest::getDefinition);
        //未设置的属性返回安全默认值
        assertTrue(chest.isChestplate());
        assertEquals(0, chest.getArmorPoints());
        assertEquals(0, chest.getToughness());
        assertEquals(0, chest.getTier());
        //maxDurability 未设 → 默认 DURABILITY_DEFAULT(56)，避免首次受击即摧毁护甲。
        assertEquals(CustomItemDefinition.ArmorBuilder.DURABILITY_DEFAULT, chest.getMaxDurability());
    }

    @Test
    void minimalToolDoesNotRecurseAndReturnsDefaults() {
        MinimalTool axe = new MinimalTool("test:min_tool", "Min Tool");
        //getDefinition() 内部调 build()，若递归会 StackOverflowError
        assertDoesNotThrow(axe::getDefinition);
        //未设置的属性返回安全默认值
        assertTrue(axe.isAxe());
        //attackDamage 未设 → 默认 1（Item 基类默认）
        assertEquals(1, axe.getAttackDamage());
        //tier 未设 → 默认 0
        assertEquals(0, axe.getTier());
        //maxDurability 未设 → 默认 WOODEN(60)
        assertEquals(ItemTool.DURABILITY_WOODEN, axe.getMaxDurability());
    }

    @Test
    void speedWithoutToolTypeDoesNotRecurse() {
        //speed() 不再调 item.isPickaxe() 等，不会递归
        MinimalTool axe = new MinimalTool("test:min_tool2", "Min Tool 2");
        assertDoesNotThrow(axe::getDefinition);
        assertTrue(axe.isAxe());
    }

    // ===== digger 写回 + 逐方块速度回归测试（方案 B：getSpeedFor 按 blockId 查 destroy_speeds）=====

    @Test
    void customPickaxeBreakTimeMatchesVanilla() {
        //自定义铁镐挖石头须与原版一致（base=1.5*1.5=2.25, bonus=6 → 0.375）
        Block stone = Block.get(Block.STONE);
        double customTime = stone.calculateBreakTimeNotInAir(pickaxe, null);
        double vanillaTime = stone.calculateBreakTimeNotInAir(Item.get(Item.IRON_PICKAXE), null);
        assertEquals(vanillaTime, customTime, 0.001, "custom iron pickaxe must match vanilla");
        assertEquals(0.375, customTime, 0.001);
    }

    @Test
    void customHoeLeavesBreakTimeMatchesVanilla() {
        //原版锄头挖树叶本就慢（BlockLeaves.getToolType=HOE，correctTool0 line850 要求 ==SHEARS 故 false）。自定义锄头虽因 correctTool 扩展 correctTool=true，但 bonus 仍取 tier=1，保持一致。
        CustomHoe hoe = new CustomHoe("test:hoe_leaves", "Test Hoe Leaves");
        Block leaves = Block.get(Block.LEAVES);
        double customTime = leaves.calculateBreakTimeNotInAir(hoe, null);
        double vanillaTime = leaves.calculateBreakTimeNotInAir(Item.get(Item.IRON_HOE), null);
        assertEquals(vanillaTime, customTime, 0.001, "custom hoe leaves must match vanilla hoe");
    }

    @Test
    void customSwordCobwebBreakTimeMatchesVanilla() {
        //BlockCobweb.getToolType=SWORD，correctTool0 line854 true → bonus=15（base=6 → 0.4）
        Block cobweb = Block.get(Block.COBWEB);
        double customTime = cobweb.calculateBreakTimeNotInAir(sword, null);
        double vanillaTime = cobweb.calculateBreakTimeNotInAir(Item.get(Item.IRON_SWORD), null);
        assertEquals(vanillaTime, customTime, 0.001, "custom sword cobweb must match vanilla sword");
        assertEquals(0.4, customTime, 0.001);
    }

    @Test
    void clonedToolSpeedCacheConsistent() {
        Integer original = pickaxe.getSpeedFor(Block.get(Block.STONE));
        ItemCustomTool cloned = pickaxe.clone();
        assertEquals(original, cloned.getSpeedFor(Block.get(Block.STONE)));
        assertNotNull(cloned.getSpeedFor(Block.get(Block.STONE)));
    }

    @Test
    void customSwordWritesDiggerComponent() {
        CompoundTag nbt = sword.getDefinition().getNbt();
        assertTrue(nbt.getCompound("components").contains("minecraft:digger"),
                "SWORD should have minecraft:digger even without blockTags");
        var speeds = nbt.getCompound("components")
                .getCompound("minecraft:digger")
                .getList("destroy_speeds", CompoundTag.class).getAll();
        assertFalse(speeds.isEmpty(), "destroy_speeds must not be empty for sword");

        //SWORD 的 toolBlocks 应含 web 和 bamboo
        boolean hasWeb = false, hasBamboo = false;
        for (var entry : speeds) {
            String name = entry.getCompound("block").getString("name");
            if ("minecraft:web".equals(name)) hasWeb = true;
            if ("minecraft:bamboo".equals(name)) hasBamboo = true;
        }
        assertTrue(hasWeb, "sword digger should include minecraft:web");
        assertTrue(hasBamboo, "sword digger should include minecraft:bamboo");
    }

    @Test
    void customSwordCobwebUsesVanillaSpeed() {
        //cobweb 回归修复：剑挖蜘蛛网须用原版 15，而非 tier 默认值（此前 getSpeed()[0] 误判为 6）
        Block cobweb = Block.get(Block.COBWEB);
        assertEquals(15, sword.getSpeedFor(cobweb),
                "sword dig speed for cobweb must be the vanilla value 15");
    }

    @Test
    void customSwordNonListedBlockReturnsNull() {
        //石头不在 sword digger 列表 → null（Block 回退 tier 查表）
        assertNull(sword.getSpeedFor(Block.get(Block.STONE)),
                "sword getSpeedFor must be null for non-digger blocks");
    }

    @Test
    void customPickaxeToolBlockSpeedMatchesTier() {
        //PICKAXE toolBlocks 方块 speed = tier 查表值（IRON=6）
        Integer speed = pickaxe.getSpeedFor(Block.get(Block.STONE));
        assertNotNull(speed);
        assertEquals(6, speed, "iron pickaxe dig speed for stone must be 6");
    }

    @Test
    void addExtraBlockSpeedHonoredPerBlock() {
        //方案 B 核心：addExtraBlock 逐方块自定义速度，而非取 destroy_speeds[0]
        ExtraBlockTool tool = new ExtraBlockTool("test:extra_block", "Extra Block Tool");
        Integer speed = tool.getSpeedFor(Block.get(Block.STONE));
        assertNotNull(speed);
        assertEquals(5, speed, "addExtraBlock speed must apply to the specific block");
    }

    @Test
    void addExtraBlockEnablesCorrectToolBreakSpeed() {
        //correctTool 扩展：ExtraBlockTool 非 toolType 但 digger 含 stone → correctTool=true → bonus=5
        //（base=1.5*5=7.5 / 5 = 1.5；无扩展则 7.5）
        ExtraBlockTool tool = new ExtraBlockTool("test:extra_block", "Extra Block Tool");
        Block stone = Block.get(Block.STONE);
        double breakTime = stone.calculateBreakTimeNotInAir(tool, null);
        assertEquals(1.5, breakTime, 0.01,
                "digger-listed block must use digger speed (correctTool extension)");
    }

    @Test
    void goldTierPickaxeSpeedMatchesVanilla() {
        //tier 修正：GOLD 须为 12（此前误算 3）
        var gold = new CustomTierTool("test:gold_pickaxe", "Gold Pickaxe", ItemTool.TIER_GOLD, ToolType.PICKAXE);
        assertEquals(12, gold.getSpeedFor(Block.get(Block.STONE)));
    }

    @Test
    void diamondTierPickaxeSpeedMatchesVanilla() {
        //tier 修正：DIAMOND 须为 8（此前误算 7）
        var diamond = new CustomTierTool("test:diamond_pickaxe", "Diamond Pickaxe", ItemTool.TIER_DIAMOND, ToolType.PICKAXE);
        assertEquals(8, diamond.getSpeedFor(Block.get(Block.STONE)));
    }

    @Test
    void netheriteTierPickaxeSpeedMatchesVanilla() {
        //tier 修正：NETHERITE 须为 9（此前误算 1）
        var netherite = new CustomTierTool("test:netherite_pickaxe", "Netherite Pickaxe", ItemTool.TIER_NETHERITE, ToolType.PICKAXE);
        assertEquals(9, netherite.getSpeedFor(Block.get(Block.STONE)));
    }

    @Test
    void customHoeWritesDiggerComponent() {
        CustomHoe hoe = new CustomHoe("test:hoe", "Test Hoe");
        CompoundTag nbt = hoe.getDefinition().getNbt();
        assertTrue(nbt.getCompound("components").contains("minecraft:digger"),
                "HOE should have minecraft:digger even without blockTags");
        var speeds = nbt.getCompound("components")
                .getCompound("minecraft:digger")
                .getList("destroy_speeds", CompoundTag.class).getAll();
        assertFalse(speeds.isEmpty(), "destroy_speeds must not be empty for hoe");

        //HOE 的 toolBlocks 应含 leaves
        boolean hasLeaves = false;
        for (var entry : speeds) {
            if ("minecraft:leaves".equals(entry.getCompound("block").getString("name"))) {
                hasLeaves = true;
                break;
            }
        }
        assertTrue(hasLeaves, "hoe digger should include minecraft:leaves");
        //hoe leaves 走 tier 默认值（tier=0 → 1）；原版锄头挖树叶本就慢，保持一致
        assertEquals(1, hoe.getSpeedFor(Block.get(Block.LEAVES)));
    }

    @Test
    void addExtraBlockOnlyWritesDiggerWithoutToolType() {
        //不设 toolType 仅调 addExtraBlock：digger 仍应写回
        ExtraBlockTool tool = new ExtraBlockTool("test:extra_block", "Extra Block Tool");
        CompoundTag nbt = tool.getDefinition().getNbt();
        assertTrue(nbt.getCompound("components").contains("minecraft:digger"),
                "addExtraBlock without addExtraBlockTags should still write minecraft:digger");
        var speeds = nbt.getCompound("components")
                .getCompound("minecraft:digger")
                .getList("destroy_speeds", CompoundTag.class).getAll();
        assertEquals(1, speeds.size());
        assertEquals("minecraft:stone", speeds.get(0).getCompound("block").getString("name"));
        assertEquals(5, speeds.get(0).getInt("speed"));
    }

    @Test
    void shearsWithoutBlocksWritesNoDigger() {
        //SHEARS 无 type 方块也无 blockTags：digger 不应被写入。
        ShearsTool shears = new ShearsTool("test:shears", "Test Shears");
        CompoundTag nbt = shears.getDefinition().getNbt();
        assertFalse(nbt.getCompound("components").contains("minecraft:digger"),
                "shears with no blocks should not write minecraft:digger");
    }

    // ===== 旧式覆写契约回归测试 =====
    //旧插件不调用 builder setter，仅覆写 Item 方法（isHelmet/getArmorPoints/...、isPickaxe/getTier/...）声明属性；
    //build() 须写入这些覆写值且不因 getDefinitionNbt() 递归抛 StackOverflowError。

    @Test
    void legacyOverrideArmorWritesOverrideValuesAndIsEquippable() {
        LegacyOverrideArmor chest = new LegacyOverrideArmor("test:legacy_armor", "Legacy Armor");
        //build() 不得递归
        assertDoesNotThrow(chest::getDefinition);
        CompoundTag nbt = chest.getDefinition().getNbt();
        assertEquals(7, nbt.getCompound("components").getCompound("minecraft:wearable").getInt("protection"));
        assertEquals(3, nbt.getCompound("components").getCompound("minecraft:wearable").getInt("toughness"));
        assertEquals(ItemArmor.TIER_DIAMOND, nbt.getCompound("components").getCompound("item_properties").getInt("tier"));
        assertEquals(363, nbt.getCompound("components").getCompound("minecraft:durability").getInt("max_durability"));
        //slot 由 isChestplate() 覆写推断
        assertEquals("slot.armor.chest", nbt.getCompound("components").getCompound("minecraft:wearable").getString("slot"));
        assertEquals("armor_torso", nbt.getCompound("components").getCompound("item_properties").getString("enchantable_slot"));
        //服务端读回
        assertTrue(chest.isChestplate());
        assertFalse(chest.isHelmet());
        assertEquals(7, chest.getArmorPoints());
        assertEquals(3, chest.getToughness());
        assertEquals(ItemArmor.TIER_DIAMOND, chest.getTier());
        assertEquals(363, chest.getMaxDurability());
        //附魔能力分派于 tier，DIAMOND(6) -> 10
        assertEquals(10, chest.getEnchantAbility());
    }

    @Test
    void legacyOverrideToolWritesOverrideValuesAndToolType() {
        LegacyOverrideTool pick = new LegacyOverrideTool("test:legacy_tool", "Legacy Tool");
        //build() 不得递归
        assertDoesNotThrow(pick::getDefinition);
        CompoundTag nbt = pick.getDefinition().getNbt();
        assertEquals(9, nbt.getCompound("components").getCompound("item_properties").getInt("damage"));
        assertEquals(ItemTool.TIER_DIAMOND, nbt.getCompound("components").getCompound("item_properties").getInt("tier"));
        assertEquals(1234, nbt.getCompound("components").getCompound("minecraft:durability").getInt("max_durability"));
        //isPickaxe() 覆写 → toolType 回退 → 写入 item_tag / enchantable_slot / 可挖掘方块
        assertTrue(nbt.getCompound("components").contains("item_tags"));
        boolean found = false;
        for (var tag : nbt.getCompound("components").getList("item_tags").getAll()) {
            if ("minecraft:is_pickaxe".equals(tag.parseValue())) { found = true; break; }
        }
        assertTrue(found, "isPickaxe() override should cause minecraft:is_pickaxe tag to be written");
        assertEquals("pickaxe", nbt.getCompound("components").getCompound("item_properties").getString("enchantable_slot"));
        assertTrue(nbt.getCompound("components").contains("minecraft:digger"),
                "isPickaxe() override should cause digger blocks to be written");
        //服务端读回
        assertTrue(pick.isPickaxe());
        assertEquals(9, pick.getAttackDamage());
        assertEquals(ItemTool.TIER_DIAMOND, pick.getTier());
        assertEquals(1234, pick.getMaxDurability());
    }

    // ===== 测试用自定义物品 =====

    private static final class CustomArmor extends ItemCustomArmor {
        CustomArmor(String id, String name) {
            super(id, name);
        }

        @Override
        public CustomItemDefinition getDefinition() {
            return CustomItemDefinition
                    .armorBuilder(this, CreativeItemCategory.EQUIPMENT)
                    .slot(ArmorSlot.HEAD)
                    .armorPoints(5)
                    .toughness(2)
                    .tier(ItemArmor.TIER_IRON)
                    .maxDurability(165)
                    .build();
        }
    }

    private static final class CustomTool extends ItemCustomTool {
        CustomTool(String id, String name) {
            super(id, name);
        }

        @Override
        public CustomItemDefinition getDefinition() {
            var builder = CustomItemDefinition
                    .toolBuilder(this, CreativeItemCategory.EQUIPMENT)
                    .attackDamage(7)
                    .maxDurability(1561)
                    .tier(ItemTool.TIER_IRON);
            if (getNamespaceId().contains("pickaxe")) {
                builder.toolType(ToolType.PICKAXE);
            } else if (getNamespaceId().contains("sword")) {
                builder.toolType(ToolType.SWORD);
            }
            return builder.build();
        }
    }

    /**
     * 最小配置的自定义盔甲：只设 slot，不设 armorPoints/toughness/tier/maxDurability。
     * 用于验证 build() 不会递归，且未设置属性返回安全默认值。
     */
    private static final class MinimalArmor extends ItemCustomArmor {
        MinimalArmor(String id, String name) {
            super(id, name);
        }

        @Override
        public CustomItemDefinition getDefinition() {
            return CustomItemDefinition
                    .armorBuilder(this, CreativeItemCategory.EQUIPMENT)
                    .slot(ArmorSlot.CHEST)
                    .build();
        }
    }

    /**
     * 最小配置的自定义工具：只设 toolType，不设 attackDamage/maxDurability/tier，且调 speed() 不设 toolType 路径。
     * 用于验证 build() 不会递归（StackOverflow），且未设置属性返回安全默认值。
     */
    private static final class MinimalTool extends ItemCustomTool {
        MinimalTool(String id, String name) {
            super(id, name);
        }

        @Override
        public CustomItemDefinition getDefinition() {
            //只设 toolType + speed，不设 attackDamage/maxDurability/tier
            return CustomItemDefinition
                    .toolBuilder(this, CreativeItemCategory.EQUIPMENT)
                    .toolType(ToolType.AXE)
                    .speed(6)
                    .build();
        }
    }

    private static final class CustomHoe extends ItemCustomTool {
        CustomHoe(String id, String name) {
            super(id, name);
        }

        @Override
        public CustomItemDefinition getDefinition() {
            return CustomItemDefinition
                    .toolBuilder(this, CreativeItemCategory.EQUIPMENT)
                    .toolType(ToolType.HOE)
                    .build();
        }
    }

    private static final class ExtraBlockTool extends ItemCustomTool {
        ExtraBlockTool(String id, String name) {
            super(id, name);
        }

        @Override
        public CustomItemDefinition getDefinition() {
            return CustomItemDefinition
                    .toolBuilder(this, CreativeItemCategory.EQUIPMENT)
                    .addExtraBlock("minecraft:stone", 5)
                    .build();
        }
    }

    private static final class ShearsTool extends ItemCustomTool {
        ShearsTool(String id, String name) {
            super(id, name);
        }

        @Override
        public CustomItemDefinition getDefinition() {
            return CustomItemDefinition
                    .toolBuilder(this, CreativeItemCategory.EQUIPMENT)
                    .toolType(ToolType.SHEARS)
                    .build();
        }
    }

    /** 可配置 tier + toolType 的自定义工具，验证不同 tier 的 toolBlocks speed 是否与原版 tier 查表一致。 */
    private static final class CustomTierTool extends ItemCustomTool {
        private final int tier;
        private final ToolType toolType;

        CustomTierTool(String id, String name, int tier, ToolType toolType) {
            super(id, name);
            this.tier = tier;
            this.toolType = toolType;
        }

        @Override
        public CustomItemDefinition getDefinition() {
            return CustomItemDefinition
                    .toolBuilder(this, CreativeItemCategory.EQUIPMENT)
                    .toolType(toolType)
                    .tier(tier)
                    .build();
        }
    }

    /** 旧式覆写契约盔甲：不调用 builder setter，仅覆写 Item/ItemArmor 方法，build() 须从中读取写入 NBT。 */
    private static final class LegacyOverrideArmor extends ItemCustomArmor {
        LegacyOverrideArmor(String id, String name) {
            super(id, name);
        }

        @Override
        public CustomItemDefinition getDefinition() {
            return CustomItemDefinition
                    .armorBuilder(this, CreativeItemCategory.EQUIPMENT)
                    .build();
        }

        @Override
        public boolean isChestplate() {
            return true;
        }

        @Override
        public int getArmorPoints() {
            return 7;
        }

        @Override
        public int getToughness() {
            return 3;
        }

        @Override
        public int getTier() {
            return ItemArmor.TIER_DIAMOND;
        }

        @Override
        public int getMaxDurability() {
            return 363;
        }
    }

    /** 旧式覆写契约工具：不设 toolType/attackDamage/...，仅覆写 Item 方法，isPickaxe() 须触发工具类型回退。 */
    private static final class LegacyOverrideTool extends ItemCustomTool {
        LegacyOverrideTool(String id, String name) {
            super(id, name);
        }

        @Override
        public CustomItemDefinition getDefinition() {
            return CustomItemDefinition
                    .toolBuilder(this, CreativeItemCategory.EQUIPMENT)
                    .build();
        }

        @Override
        public boolean isPickaxe() {
            return true;
        }

        @Override
        public int getAttackDamage() {
            return 9;
        }

        @Override
        public int getTier() {
            return ItemTool.TIER_DIAMOND;
        }

        @Override
        public int getMaxDurability() {
            return 1234;
        }
    }
}
