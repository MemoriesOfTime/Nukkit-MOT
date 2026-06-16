package cn.nukkit.item.customitem;

import cn.nukkit.MockServer;
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

    // ===== digger 组件写回回归测试 =====
    // 回归：toolType(SWORD/HOE) 无 blockTags，或仅调 addExtraBlock，build() 仍应写回
    // minecraft:digger，否则服务端 getSpeed() 返回 null、客户端收不到挖掘速度。

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
    void customSwordGetSpeedNotNull() {
        //CustomTool tier=IRON(5) → 默认 speed=6
        Integer s = sword.getSpeed();
        assertNotNull(s, "getSpeed() must not be null when digger is written");
        assertEquals(6, s);
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
        assertNotNull(hoe.getSpeed(), "hoe getSpeed() must not be null");
    }

    @Test
    void addExtraBlockOnlyWritesDiggerWithoutToolType() {
        //不设 toolType、仅调 addExtraBlock：此前 digger 不被写回。
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
}
