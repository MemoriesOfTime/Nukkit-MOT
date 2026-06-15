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
}
