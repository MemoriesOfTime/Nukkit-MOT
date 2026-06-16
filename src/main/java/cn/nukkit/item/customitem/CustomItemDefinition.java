package cn.nukkit.item.customitem;

import cn.nukkit.GameVersion;
import cn.nukkit.inventory.ItemTag;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemArmor;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.item.customitem.data.DigProperty;
import cn.nukkit.item.customitem.data.ItemCreativeGroup;
import cn.nukkit.item.customitem.data.RenderOffsets;
import cn.nukkit.item.food.Food;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemCategory;
import cn.nukkit.utils.Identifier;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * CustomBlockDefinition用于获得发送给客户端的物品行为包数据。{@link CustomItemDefinition.SimpleBuilder}中提供的方法都是控制发送给客户端数据，如果需要控制服务端部分行为，请覆写{@link cn.nukkit.item.Item Item}中的方法。
 * <p>
 * CustomBlockDefinition is used to get the data of the item behavior_pack sent to the client. The methods provided in {@link CustomItemDefinition.SimpleBuilder} control the data sent to the client, if you need to control some of the server-side behavior, please override the methods in {@link cn.nukkit.item.Item Item}.
 */
@Log4j2
public class CustomItemDefinition {
    /**
     * 物品注册模式
     * <p>
     * Item registration mode
     */
    public enum ItemRegistrationMode {
        /**
         * Legacy模式 (version=0): 服务端只注册物品标识符，贴图和组件由客户端资源包提供
         * <p>
         * Legacy mode (version=0): Server only registers item identifier, textures and components are provided by client resource pack
         */
        LEGACY,

        /**
         * Component-based模式 (version=1): 服务端通过NBT完全定义物品的属性和行为
         * <p>
         * Component-based mode (version=1): Server fully defines item properties and behavior through NBT
         */
        COMPONENT_BASED
    }

    private static final ConcurrentHashMap<String, Integer> INTERNAL_ALLOCATION_ID_MAP = new ConcurrentHashMap<>();
    private static final AtomicInteger nextRuntimeId = new AtomicInteger(10000);

    private final String identifier;
    private final CompoundTag nbt; //649
    private final CompoundTag nbt465;
    private final CompoundTag nbt419;

    /**
     * 物品注册模式
     * <p>
     * Item registration mode
     */
    @Getter
    private final ItemRegistrationMode registrationMode;

    /**
     * Creative inventory page where the item is put into
     */
    @Getter
    private final CreativeItemCategory creativeCategory;

    /**
     * Group items in creative inventory
     */
    @Getter
    private final String creativeGroup;

    private CustomItemDefinition(String identifier, CompoundTag nbt) {
        this(identifier, nbt, ItemRegistrationMode.COMPONENT_BASED);
    }

    private CustomItemDefinition(String identifier, CompoundTag nbt, ItemRegistrationMode registrationMode) {
        this.identifier = identifier;
        this.nbt = nbt;
        this.registrationMode = registrationMode;

        if (registrationMode == ItemRegistrationMode.COMPONENT_BASED) {
            this.nbt465  = nbt.clone();
            CompoundTag components465 = this.nbt465.getCompound("components");
            components465
                    .getCompound("item_properties")
                    .getCompound("minecraft:icon")
                    .remove("textures")
                    .putString("texture", this.getTexture());
            if (this.nbt465.getCompound("item_properties").getInt("damage") > 0
                    && !components465.containsCompound("minecraft:weapon")) {
                components465.putCompound(new CompoundTag("minecraft:weapon"));
            }
            if (components465.contains("minecraft:wearable")) {
                CompoundTag wearable465 = components465.getCompound("minecraft:wearable");
                this.nbt465.putCompound("minecraft:armor", new CompoundTag()
                        .putInt("protection", wearable465.getInt("protection")));
                wearable465.remove("protection");
            }

            this.nbt419 = this.nbt465.clone();
            this.nbt419.getCompound("components").getCompound("item_properties").remove("minecraft:icon");
            this.nbt419.getCompound("components").putCompound("minecraft:icon", new CompoundTag().putString("texture", this.getTexture()));

            CompoundTag compound = this.nbt.getCompound("components").getCompound("item_properties");
            if (compound.containsInt("creative_category")) {
                this.creativeCategory = CreativeItemCategory.values()[compound.getInt("creative_category")];
            } else {
                this.creativeCategory = CreativeItemCategory.UNDEFINED;
            }
            this.creativeGroup = compound.getString("creative_group");
        } else {
            this.nbt465 = nbt;
            this.nbt419 = nbt;
            this.creativeCategory = CreativeItemCategory.UNDEFINED;
            this.creativeGroup = "";
        }
    }

    public String identifier() {
        return identifier;
    }

    /**
     * 获取物品注册版本号
     * <p>
     * Get item registration version number
     *
     * @return 0 for legacy mode, 1 for component-based mode
     */
    public int getVersion() {
        return registrationMode == ItemRegistrationMode.LEGACY ? 0 : 1;
    }

    /**
     * 是否为Component-based模式
     * <p>
     * Whether this is component-based mode
     *
     * @return true if component-based, false if legacy
     */
    public boolean isComponentBased() {
        return registrationMode == ItemRegistrationMode.COMPONENT_BASED;
    }

    public CompoundTag getNbt() {
        return getNbt(ProtocolInfo.CURRENT_PROTOCOL);
    }

    public CompoundTag getNbt(int protocol) {
        if (protocol >= ProtocolInfo.v1_20_60) {
            return this.nbt;
        } else if (protocol >= ProtocolInfo.v1_17_30) {
            return this.nbt465;
        }
        return this.nbt419;
    }

    /**
     * 自定义物品的定义构造器
     * <p>
     * Definition builder for custom simple item
     *
     * @param item             the item
     * @param creativeCategory the creative category
     * @return the custom item definition . simple builder
     */
    public static CustomItemDefinition.SimpleBuilder customBuilder(CustomItem item, CreativeItemCategory creativeCategory) {
        return new CustomItemDefinition.SimpleBuilder(item, creativeCategory);
    }

    /**
     * 简单物品的定义构造器
     * <p>
     * Definition builder for custom simple item
     *
     * @param item             the item
     * @param creativeCategory the creative category
     */
    public static CustomItemDefinition.SimpleBuilder simpleBuilder(ItemCustom item, CreativeItemCategory creativeCategory) {
        return new CustomItemDefinition.SimpleBuilder(item, creativeCategory);
    }

    /**
     * 自定义工具的定义构造器
     * <p>
     * Definition builder for custom tools
     *
     * @param item             the item
     * @param creativeCategory the creative category
     */
    public static CustomItemDefinition.ToolBuilder toolBuilder(ItemCustomTool item, CreativeItemCategory creativeCategory) {
        return new CustomItemDefinition.ToolBuilder(item, creativeCategory);
    }

    /**
     * 自定义盔甲的定义构造器
     * <p>
     * Definition builder for custom armor
     *
     * @param item             the item
     * @param creativeCategory the creative category
     */
    public static CustomItemDefinition.ArmorBuilder armorBuilder(ItemCustomArmor item, CreativeItemCategory creativeCategory) {
        return new CustomItemDefinition.ArmorBuilder(item, creativeCategory);
    }

    /**
     * 自定义食物(药水)的定义构造器
     * <p>
     * Definition builder for custom food or potion
     *
     * @param item             the item
     * @param creativeCategory the creative category
     */
    public static CustomItemDefinition.EdibleBuilder edibleBuilder(ItemCustomEdible item, CreativeItemCategory creativeCategory) {
        return new CustomItemDefinition.EdibleBuilder(item, creativeCategory);
    }

    /**
     * Legacy模式物品的定义构造器
     * <p>
     * Definition builder for legacy mode items
     *
     * @param item the item
     * @return the legacy item builder
     */
    public static CustomItemDefinition.LegacyItemBuilder legacyBuilder(CustomItem item) {
        return new CustomItemDefinition.LegacyItemBuilder(item);
    }

    /**
     * Legacy模式食物的定义构造器
     * <p>
     * Definition builder for legacy mode food items
     *
     * @param item the item
     * @return the legacy food builder
     */
    public static CustomItemDefinition.LegacyFoodBuilder legacyFoodBuilder(CustomItem item) {
        return new CustomItemDefinition.LegacyFoodBuilder(item);
    }

    @Nullable
    public String getDisplayName() {
        if (!this.nbt.getCompound("components").contains("minecraft:display_name")) return null;
        return this.nbt.getCompound("components").getCompound("minecraft:display_name").getString("value");
    }

    public String getTexture() {
        return this.nbt.getCompound("components")
                .getCompound("item_properties")
                .getCompound("minecraft:icon")
                .getCompound("textures")
                .getString("default");
    }

    public int getRuntimeId() {
        return CustomItemDefinition.INTERNAL_ALLOCATION_ID_MAP.get(identifier);
    }

    public static int getRuntimeId(String identifier) {
        return CustomItemDefinition.INTERNAL_ALLOCATION_ID_MAP.get(identifier);
    }

    public static class SimpleBuilder {
        protected final String identifier;
        protected final CompoundTag nbt = new CompoundTag()
                .putCompound("components", new CompoundTag()
                        .putCompound("item_properties", new CompoundTag()
                                .putCompound("minecraft:icon", new CompoundTag())));
        private final Item item;

        protected SimpleBuilder(CustomItem customItem, CreativeItemCategory creativeCategory) {
            this(customItem,  creativeCategory, "");
        }

        protected SimpleBuilder(CustomItem customItem, CreativeItemCategory creativeCategory, String creativeItemGroup) {
            this.item = (Item) customItem;
            this.identifier = customItem.getNamespaceId();
            //定义材质
            CompoundTag properties = this.nbt.getCompound("components")
                    .getCompound("item_properties");
            properties.getCompound("minecraft:icon")
                    .putCompound("textures", new CompoundTag().putString("default", customItem.getTextureName()));

            //定义显示名
            if (item.getName() != null && !item.getName().equals(Item.UNKNOWN_STR)) {
                this.nbt.getCompound("components")
                        .putCompound("minecraft:display_name", new CompoundTag().putString("value", item.getName()));
            }

            //定义最大堆叠数量
            properties.putInt("max_stack_size", item.getMaxStackSize());
            //定义在创造栏的分类
            if (creativeCategory != null) {
                properties.putInt("creative_category", creativeCategory.ordinal());
                if (creativeCategory != CreativeItemCategory.UNDEFINED && creativeItemGroup != null) {
                    properties.putString("creative_group", creativeItemGroup);
                }
            }
        }

        /**
         * 是否允许副手持有
         * <p>
         * Whether to allow the offHand to have
         */
        public SimpleBuilder allowOffHand(boolean allowOffHand) {
            this.nbt.getCompound("components")
                    .getCompound("item_properties")
                    .putBoolean("allow_off_hand", allowOffHand);
            return this;
        }

        /**
         * 控制第三人称手持物品的显示方式
         * <p>
         * Control how third-person handheld items are displayed
         */
        public SimpleBuilder handEquipped(boolean handEquipped) {
            this.nbt.getCompound("components")
                    .getCompound("item_properties")
                    .putBoolean("hand_equipped", handEquipped);
            return this;
        }

        /**
         * @param foil 自定义物品是否带有附魔光辉效果<br>whether or not the item has an enchanted light effect
         */
        public SimpleBuilder foil(boolean foil) {
            this.nbt.getCompound("components")
                    .getCompound("item_properties")
                    .putBoolean("foil", foil);
            return this;
        }

        /**
         * 控制自定义物品在创造栏的分组,例如所有的附魔书是一组
         * <p>
         * Control the grouping of custom items in the creation inventory, e.g. all enchantment books are grouped together
         *
         * @see <a href="https://wiki.bedrock.dev/documentation/creative-categories.html#list-of-creative-categories">bedrock wiki</a>
         */
        public SimpleBuilder creativeGroup(String creativeGroup) {
            if (creativeGroup.isBlank()) {
                log.warn("creativeGroup has an invalid value!");
                return this;
            }
            this.nbt.getCompound("components")
                    .getCompound("item_properties")
                    .putString("creative_group", creativeGroup);
            return this;
        }

        /**
         * 控制自定义物品在创造栏的分组,例如所有的附魔书是一组
         * <p>
         * Control the grouping of custom items in the creation inventory, e.g. all enchantment books are grouped together
         *
         * @see <a href="https://wiki.bedrock.dev/documentation/creative-categories.html#list-of-creative-categories">bedrock wiki</a>
         */
        @Deprecated
        public SimpleBuilder creativeGroup(ItemCreativeGroup creativeGroup) {
            this.nbt.getCompound("components")
                    .getCompound("item_properties")
                    .putString("creative_group", creativeGroup.getGroupName());
            return this;
        }

        /**
         * 控制自定义物品在不同视角下的渲染偏移
         * <p>
         * Control rendering offsets of custom items at different viewpoints
         */
        public SimpleBuilder renderOffsets(@NotNull RenderOffsets renderOffsets) {
            this.nbt.getCompound("components")
                    .putCompound("minecraft:render_offsets", renderOffsets.nbt);
            return this;
        }

        /**
         * 向自定义物品添加一个tag，通常用于合成等
         * <p>
         * Add a tag to a custom item, usually used for crafting, etc.
         * todo: 2022/12/13  检查是否真的在客户端起作用
         *
         * @param tags the tags
         * @return the simple builder
         */
        public SimpleBuilder tag(String... tags) {
            Arrays.stream(tags).forEach(Identifier::assertValid);
            ListTag<StringTag> list;
            if (this.nbt.getCompound("components").contains("item_tags")) {
                list = this.nbt.getCompound("components").getList("item_tags", StringTag.class);
            } else {
                list = new ListTag<>("item_tags");
                this.nbt.getCompound("components").putList(list);
            }
            for (var s : tags) {
                list.add(new StringTag("", s));
            }
            return this;
        }

        /**
         * 控制拿该物品的玩家是否可以在创造模式挖掘方块
         * <p>
         * Control whether the player with the item can dig the block in creation mode
         *
         * @param value the value
         * @return the simple builder
         */
        public SimpleBuilder canDestroyInCreative(boolean value) {
            this.nbt.getCompound("components")
                    .getCompound("item_properties")
                    .putBoolean("can_destroy_in_creative", value);
            return this;
        }

        /**
         * 对要发送给客户端的物品ComponentNBT进行自定义处理，这里包含了所有对自定义物品的定义。在符合条件的情况下，你可以任意修改。
         * <p>
         * Custom processing of the item to be sent to the client ComponentNBT, which contains all definitions for custom item. You can modify them as much as you want, under the right conditions.
         */
        public CustomItemDefinition customBuild(Consumer<CompoundTag> nbt) {
            var def = this.build();
            nbt.accept(def.nbt);
            return def;
        }

        public CustomItemDefinition build() {
            return calculateID();
        }

        protected CustomItemDefinition calculateID() {
            var result = new CustomItemDefinition(identifier, nbt);
            if (!INTERNAL_ALLOCATION_ID_MAP.containsKey(result.identifier())) {
                while (RuntimeItems.getMapping(GameVersion.getLastVersion()).getNamespacedIdByNetworkId(nextRuntimeId.incrementAndGet()) != null)
                    ;
                INTERNAL_ALLOCATION_ID_MAP.put(result.identifier(), nextRuntimeId.get());
                result.nbt.putString("name", result.identifier());
                result.nbt.putInt("id", nextRuntimeId.get());
            }
            return result;
        }

        /**
         * 添加一个可修理该物品的物品
         * <p>
         * Add an item that can repair the item
         *
         * @param repairItemNames the repair item names
         * @param molang          the molang
         * @return the simple builder
         */
        protected SimpleBuilder addRepairs(@NotNull List<String> repairItemNames, String molang) {
            if (molang.isBlank()) {
                log.warn("repairAmount has an invalid value!");
                return this;
            }

            if (this.nbt.getCompound("components").contains("minecraft:repairable")) {
                var repair_items = this.nbt
                        .getCompound("components")
                        .getCompound("minecraft:repairable")
                        .getList("repair_items", CompoundTag.class);

                var items = new ListTag<CompoundTag>("items");
                for (var name : repairItemNames) {
                    items.add(new CompoundTag().putString("name", name));
                }

                repair_items.add(new CompoundTag()
                        .putList(items)
                        .putCompound("repair_amount", new CompoundTag()
                                .putString("expression", molang)
                                .putInt("version", 1)));
            } else {
                var repair_items = new ListTag<CompoundTag>("repair_items");
                var items = new ListTag<CompoundTag>("items");
                for (var name : repairItemNames) {
                    items.add(new CompoundTag().putString("name", name));
                }
                repair_items.add(new CompoundTag()
                        .putList(items)
                        .putCompound("repair_amount", new CompoundTag()
                                .putString("expression", molang)
                                .putInt("version", 1)));
                this.nbt.getCompound("components")
                        .putCompound("minecraft:repairable", new CompoundTag()
                                .putList(repair_items));
            }
            return this;
        }
    }

    public static class ToolBuilder extends SimpleBuilder {
        private final ItemCustomTool item;
        private Integer speed = null;
        private final List<CompoundTag> blocks = new ArrayList<>();
        private final List<String> blockTags = new ArrayList<>();
        private final CompoundTag diggerRoot = new CompoundTag("minecraft:digger")
                .putBoolean("use_efficiency", true)
                .putList(new ListTag<>("destroy_speeds"));
        private @Nullable ToolType toolType = null;
        private @Nullable Integer attackDamage = null;
        private @Nullable Integer maxDurability = null;
        private @Nullable Integer tier = null;

        public static Map<Identifier, Map<String, DigProperty>> toolBlocks = new HashMap<>();

        static {
            var pickaxeBlocks = new Object2ObjectOpenHashMap<String, DigProperty>();
            var axeBlocks = new Object2ObjectOpenHashMap<String, DigProperty>();
            var shovelBlocks = new Object2ObjectOpenHashMap<String, DigProperty>();
            var hoeBlocks = new Object2ObjectOpenHashMap<String, DigProperty>();
            var swordBlocks = new Object2ObjectOpenHashMap<String, DigProperty>();
            for (var name : List.of("minecraft:ice", "minecraft:undyed_shulker_box", "minecraft:shulker_box", "minecraft:prismarine", "minecraft:stone_slab4", "minecraft:prismarine_bricks_stairs", "minecraft:prismarine_stairs", "minecraft:dark_prismarine_stairs", "minecraft:anvil", "minecraft:bone_block", "minecraft:iron_trapdoor", "minecraft:nether_brick_fence", "minecraft:crying_obsidian", "minecraft:magma", "minecraft:smoker", "minecraft:lit_smoker", "minecraft:hopper", "minecraft:redstone_block", "minecraft:mob_spawner", "minecraft:netherite_block", "minecraft:smooth_stone", "minecraft:diamond_block", "minecraft:lapis_block", "minecraft:emerald_block", "minecraft:enchanting_table", "minecraft:end_bricks", "minecraft:cracked_polished_blackstone_bricks", "minecraft:nether_brick", "minecraft:cracked_nether_bricks", "minecraft:purpur_block", "minecraft:purpur_stairs", "minecraft:end_brick_stairs", "minecraft:stone_slab", "minecraft:stone_slab2", "minecraft:stone_slab3", "minecraft:stone_brick_stairs", "minecraft:mossy_stone_brick_stairs", "minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_stairs", "minecraft:blackstone_wall", "minecraft:blackstone_wall", "minecraft:polished_blackstone_wall", "minecraft:sandstone", "minecraft:grindstone", "minecraft:smooth_stone", "minecraft:brewing_stand", "minecraft:chain", "minecraft:lantern", "minecraft:soul_lantern", "minecraft:ancient_debris", "minecraft:quartz_ore", "minecraft:netherrack", "minecraft:basalt", "minecraft:polished_basalt", "minecraft:stonebrick", "minecraft:warped_nylium", "minecraft:crimson_nylium", "minecraft:end_stone", "minecraft:ender_chest", "minecraft:quartz_block", "minecraft:quartz_stairs", "minecraft:quartz_bricks", "minecraft:quartz_stairs", "minecraft:nether_gold_ore", "minecraft:furnace", "minecraft:blast_furnace", "minecraft:lit_furnace", "minecraft:blast_furnace", "minecraft:blackstone", "minecraft:concrete", "minecraft:deepslate_copper_ore", "minecraft:deepslate_lapis_ore", "minecraft:chiseled_deepslate", "minecraft:cobbled_deepslate", "minecraft:cobbled_deepslate_double_slab", "minecraft:cobbled_deepslate_slab", "minecraft:cobbled_deepslate_stairs", "minecraft:cobbled_deepslate_wall", "minecraft:cracked_deepslate_bricks", "minecraft:cracked_deepslate_tiles", "minecraft:deepslate", "minecraft:deepslate_brick_double_slab", "minecraft:deepslate_brick_slab", "minecraft:deepslate_brick_stairs", "minecraft:deepslate_brick_wall", "minecraft:deepslate_bricks", "minecraft:deepslate_tile_double_slab", "minecraft:deepslate_tile_slab", "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_wall", "minecraft:deepslate_tiles", "minecraft:infested_deepslate", "minecraft:polished_deepslate", "minecraft:polished_deepslate_double_slab", "minecraft:polished_deepslate_slab", "minecraft:polished_deepslate_stairs", "minecraft:polished_deepslate_wall", "minecraft:calcite", "minecraft:amethyst_block", "minecraft:amethyst_cluster", "minecraft:budding_amethyst", "minecraft:raw_copper_block", "minecraft:raw_gold_block", "minecraft:raw_iron_block", "minecraft:copper_ore", "minecraft:copper_block", "minecraft:cut_copper", "minecraft:cut_copper_slab", "minecraft:cut_copper_stairs", "minecraft:double_cut_copper_slab", "minecraft:exposed_copper", "minecraft:exposed_cut_copper", "minecraft:exposed_cut_copper_slab", "minecraft:exposed_cut_copper_stairs", "minecraft:exposed_double_cut_copper_slab", "minecraft:oxidized_copper", "minecraft:oxidized_cut_copper", "minecraft:oxidized_cut_copper_slab", "minecraft:oxidized_cut_copper_stairs", "minecraft:oxidized_double_cut_copper_slab", "minecraft:weathered_copper", "minecraft:weathered_cut_copper", "minecraft:weathered_cut_copper_slab", "minecraft:weathered_cut_copper_stairs", "minecraft:weathered_double_cut_copper_slab", "minecraft:waxed_copper", "minecraft:waxed_cut_copper", "minecraft:waxed_cut_copper_slab", "minecraft:waxed_cut_copper_stairs", "minecraft:waxed_double_cut_copper_slab", "minecraft:waxed_exposed_copper", "minecraft:waxed_exposed_cut_copper", "minecraft:waxed_exposed_cut_copper_slab", "minecraft:waxed_exposed_cut_copper_stairs", "minecraft:waxed_exposed_double_cut_copper_slab", "minecraft:waxed_oxidized_copper", "minecraft:waxed_oxidized_cut_copper", "minecraft:waxed_oxidized_cut_copper_slab", "minecraft:waxed_oxidized_cut_copper_stairs", "minecraft:waxed_oxidized_double_cut_copper_slab", "minecraft:waxed_weathered_copper", "minecraft:waxed_weathered_cut_copper", "minecraft:waxed_weathered_cut_copper_slab", "minecraft:waxed_weathered_cut_copper_stairs", "minecraft:waxed_weathered_double_cut_copper_slab", "minecraft:dripstone_block", "minecraft:pointed_dripstone", "minecraft:lightning_rod", "minecraft:basalt", "minecraft:tuff", "minecraft:double_stone_slab", "minecraft:double_stone_slab2", "minecraft:double_stone_slab3", "minecraft:double_stone_slab4", "minecraft:blackstone_double_slab", "minecraft:polished_blackstone_brick_double_slab", "minecraft:polished_blackstone_double_slab", "minecraft:mossy_cobblestone_stairs", "minecraft:stonecutter", "minecraft:stonecutter_block", "minecraft:red_nether_brick", "minecraft:red_nether_brick_stairs", "minecraft:normal_stone_stairs", "minecraft:smooth_basalt", "minecraft:stone", "minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:dripstone_block", "minecraft:brick_block", "minecraft:stone_stairs", "minecraft:stone_block_slab", "minecraft:stone_block_slab2", "minecraft:stone_block_slab3", "minecraft:stone_block_slab4", "minecraft:cobblestone_wall", "minecraft:gold_block", "minecraft:iron_block", "minecraft:cauldron", "minecraft:iron_bars", "minecraft:obsidian", "minecraft:coal_ore", "minecraft:deepslate_coal_ore", "minecraft:deepslate_diamond_ore", "minecraft:deepslate_emerald_ore", "minecraft:deepslate_gold_ore", "minecraft:deepslate_iron_ore", "minecraft:deepslate_redstone_ore", "minecraft:lit_deepslate_redstone_ore", "minecraft:diamond_ore", "minecraft:emerald_ore", "minecraft:gold_ore", "minecraft:iron_ore", "minecraft:lapis_ore", "minecraft:redstone_ore", "minecraft:lit_redstone_ore", "minecraft:raw_iron_block", "minecraft:raw_gold_block", "minecraft:raw_copper_block", "minecraft:mud_brick_double_slab", "minecraft:mud_brick_slab", "minecraft:mud_brick_stairs", "minecraft:mud_brick_wall", "minecraft:mud_bricks", "minecraft:hardened_clay", "minecraft:stained_hardened_clay", "minecraft:polished_diorite_stairs", "minecraft:andesite_stairs", "minecraft:polished_andesite_stairs", "minecraft:granite_stairs", "minecraft:polished_granite_stairs", "minecraft:polished_blackstone", "minecraft:chiseled_polished_blackstone", "minecraft:polished_blackstone_brick_stairs", "minecraft:blackstone_stairs", "minecraft:polished_blackstone_brick_wall", "minecraft:gilded_blackstone", "minecraft:coal_block",
                    /* 带釉陶瓦 */ "minecraft:white_glazed_terracotta", "minecraft:orange_glazed_terracotta", "minecraft:magenta_glazed_terracotta", "minecraft:light_blue_glazed_terracotta", "minecraft:yellow_glazed_terracotta", "minecraft:lime_glazed_terracotta", "minecraft:pink_glazed_terracotta", "minecraft:gray_glazed_terracotta", "minecraft:silver_glazed_terracotta", "minecraft:cyan_glazed_terracotta", "minecraft:purple_glazed_terracotta", "minecraft:blue_glazed_terracotta", "minecraft:brown_glazed_terracotta", "minecraft:green_glazed_terracotta", "minecraft:red_glazed_terracotta", "minecraft:black_glazed_terracotta",
                    /* 珊瑚块  */"minecraft:coral_block", "minecraft:tube_coral_block", "minecraft:brain_coral_block", "minecraft:brain_coral_block", "minecraft:brain_coral_block", "minecraft:brain_coral_block", "minecraft:brain_coral_block", "minecraft:dead_brain_coral_block", "minecraft:dead_brain_coral_block", "minecraft:dead_brain_coral_block", "minecraft:dead_brain_coral_block")) {
                pickaxeBlocks.put(name, new DigProperty());
            }
            toolBlocks.put(ItemTag.IS_PICKAXE, pickaxeBlocks);

            for (var name : List.of("minecraft:chest", "minecraft:bookshelf", "minecraft:melon_block", "minecraft:warped_stem", "minecraft:crimson_stem", "minecraft:warped_stem", "minecraft:crimson_stem", "minecraft:crafting_table", "minecraft:crimson_planks", "minecraft:warped_planks", "minecraft:warped_stairs", "minecraft:warped_trapdoor", "minecraft:crimson_stairs", "minecraft:crimson_trapdoor", "minecraft:crimson_door", "minecraft:crimson_double_slab", "minecraft:warped_door", "minecraft:warped_double_slab", "minecraft:crafting_table", "minecraft:composter", "minecraft:cartography_table", "minecraft:lectern", "minecraft:stripped_crimson_stem", "minecraft:stripped_warped_stem", "minecraft:trapdoor", "minecraft:spruce_trapdoor", "minecraft:birch_trapdoor", "minecraft:jungle_trapdoor", "minecraft:acacia_trapdoor", "minecraft:dark_oak_trapdoor", "minecraft:wooden_door", "minecraft:spruce_door", "minecraft:birch_door", "minecraft:jungle_door", "minecraft:acacia_door", "minecraft:dark_oak_door", "minecraft:fence", "minecraft:fence_gate", "minecraft:spruce_fence_gate", "minecraft:birch_fence_gate", "minecraft:jungle_fence_gate", "minecraft:acacia_fence_gate", "minecraft:dark_oak_fence_gate", "minecraft:log", "minecraft:log2", "minecraft:wood", "minecraft:planks", "minecraft:wooden_slab", "minecraft:double_wooden_slab", "minecraft:oak_stairs", "minecraft:spruce_stairs", "minecraft:birch_stairs", "minecraft:jungle_stairs", "minecraft:acacia_stairs", "minecraft:dark_oak_stairs", "minecraft:wall_sign", "minecraft:spruce_wall_sign", "minecraft:birch_wall_sign", "minecraft:jungle_wall_sign", "minecraft:acacia_wall_sign", "minecraft:darkoak_wall_sign", "minecraft:wooden_pressure_plate", "minecraft:spruce_pressure_plate", "minecraft:birch_pressure_plate", "minecraft:jungle_pressure_plate", "minecraft:acacia_pressure_plate", "minecraft:dark_oak_pressure_plate", "minecraft:smithing_table", "minecraft:fletching_table", "minecraft:barrel", "minecraft:beehive", "minecraft:bee_nest", "minecraft:ladder", "minecraft:pumpkin", "minecraft:carved_pumpkin", "minecraft:lit_pumpkin", "minecraft:mangrove_door", "minecraft:mangrove_double_slab", "minecraft:mangrove_fence", "minecraft:mangrove_fence_gate", "minecraft:mangrove_log", "minecraft:mangrove_planks", "minecraft:mangrove_pressure_plate", "minecraft:mangrove_slab", "minecraft:mangrove_stairs", "minecraft:mangrove_wall_sign", "minecraft:mangrove_wood", "minecraft:wooden_button", "minecraft:spruce_button", "minecraft:birch_button", "minecraft:jungle_button", "minecraft:acacia_button", "minecraft:dark_oak_button", "minecraft:mangrove_button", "minecraft:stripped_oak_wood", "minecraft:stripped_spruce_wood", "minecraft:stripped_birch_wood", "minecraft:stripped_jungle_wood", "minecraft:stripped_acacia_wood", "minecraft:stripped_dark_oak_wood", "minecraft:stripped_mangrove_wood", "minecraft:stripped_oak_log", "minecraft:stripped_spruce_log", "minecraft:stripped_birch_log", "minecraft:stripped_jungle_log", "minecraft:stripped_acacia_log", "minecraft:stripped_dark_oak_log", "minecraft:stripped_mangrove_log", "minecraft:standing_sign", "minecraft:spruce_standing_sign", "minecraft:birch_standing_sign", "minecraft:jungle_standing_sign", "minecraft:acacia_standing_sign", "minecraft:darkoak_standing_sign", "minecraft:mangrove_standing_sign", "minecraft:mangrove_trapdoor", "minecraft:warped_standing_sign", "minecraft:warped_wall_sign", "minecraft:crimson_standing_sign", "minecraft:crimson_wall_sign", "minecraft:mangrove_roots")) {
                axeBlocks.put(name, new DigProperty());
            }
            toolBlocks.put(ItemTag.IS_AXE, axeBlocks);

            for (var name : List.of("minecraft:soul_sand", "minecraft:soul_soil", "minecraft:dirt_with_roots", "minecraft:mycelium", "minecraft:podzol", "minecraft:dirt", "minecraft:farmland", "minecraft:sand", "minecraft:gravel", "minecraft:grass", "minecraft:grass_path", "minecraft:snow", "minecraft:mud", "minecraft:packed_mud", "minecraft:clay")) {
                shovelBlocks.put(name, new DigProperty());
            }
            toolBlocks.put(ItemTag.IS_SHOVEL, shovelBlocks);

            for (var name : List.of("minecraft:nether_wart_block", "minecraft:hay_block", "minecraft:target", "minecraft:shroomlight", "minecraft:leaves", "minecraft:leaves2", "minecraft:azalea_leaves_flowered", "minecraft:azalea_leaves", "minecraft:warped_wart_block")) {
                hoeBlocks.put(name, new DigProperty());
            }
            toolBlocks.put(ItemTag.IS_HOE, hoeBlocks);

            //web 须显式写入原版 cobweb 速度 15，否则被 tier 默认值覆盖致回归；bamboo 瞬间破坏，不经 toolBreakTimeBonus0。
            swordBlocks.put("minecraft:web", new DigProperty(new CompoundTag(), 15));
            swordBlocks.put("minecraft:bamboo", new DigProperty());
            toolBlocks.put(ItemTag.IS_SWORD, swordBlocks);
        }

        private ToolBuilder(ItemCustomTool item, CreativeItemCategory creativeCategory) {
            super(item, creativeCategory);
            this.item = item;
            this.nbt.getCompound("components")
                    .getCompound("item_properties")
                    .putFloat("mining_speed", 1f)
                    .putBoolean("can_destroy_in_creative", true);
        }

        public ToolBuilder addRepairItemName(@NonNull String repairItemName, String molang) {
            super.addRepairs(List.of(repairItemName), molang);
            return this;
        }

        public ToolBuilder addRepairItemName(@NonNull String repairItemName, int repairAmount) {
            super.addRepairs(List.of(repairItemName), String.valueOf(repairAmount));
            return this;
        }

        public ToolBuilder addRepairItems(@NotNull List<Item> repairItems, String molang) {
            super.addRepairs(repairItems.stream().map(Item::getNamespaceId).toList(), molang);
            return this;
        }

        public ToolBuilder addRepairItems(@NotNull List<Item> repairItems, int repairAmount) {
            super.addRepairs(repairItems.stream().map(Item::getNamespaceId).toList(), String.valueOf(repairAmount));
            return this;
        }

        /**
         * 指定工具类型。决定可挖掘方块、{@code enchantable_slot}、{@code item_tags}，
         * 并使服务端的 {@code isPickaxe()/isAxe()/...} 返回 {@code true}。
         * <p>
         * Specifies the tool type. Determines mineable blocks, {@code enchantable_slot},
         * {@code item_tags}, and makes server-side {@code isPickaxe()/isAxe()/...} return {@code true}.
         */
        public ToolBuilder toolType(@NotNull ToolType toolType) {
            this.toolType = toolType;
            return this;
        }

        /**
         * 设置攻击伤害。服务端的 {@link Item#getAttackDamage()} 会读取此值。
         * 未设置时默认为 1。
         * <p>
         * Sets the attack damage. Server-side {@link Item#getAttackDamage()} reads this value.
         * Defaults to 1 when unset.
         */
        public ToolBuilder attackDamage(int attackDamage) {
            this.attackDamage = attackDamage;
            return this;
        }

        /**
         * 设置最大耐久。服务端的 {@link Item#getMaxDurability()} 会读取此值。
         * 未设置时默认为 {@link ItemTool#DURABILITY_WOODEN}。
         * <p>
         * Sets the max durability. Server-side {@link Item#getMaxDurability()} reads this value.
         * Defaults to {@link ItemTool#DURABILITY_WOODEN} when unset.
         */
        public ToolBuilder maxDurability(int maxDurability) {
            this.maxDurability = maxDurability;
            return this;
        }

        /**
         * 设置工具层级（tier）。影响 {@code getEnchantAbility()} 及默认挖掘速度。
         * 未设置时默认为 0（无附魔能力）。
         * <p>
         * Sets the tool tier. Affects {@code getEnchantAbility()} and the default mining speed.
         * Defaults to 0 (no enchantability) when unset.
         */
        public ToolBuilder tier(int tier) {
            this.tier = tier;
            return this;
        }

        /**
         * 控制采集类工具的挖掘速度
         *
         * @param speed 挖掘速度
         */
        public ToolBuilder speed(int speed) {
            if (speed < 0) {
                log.warn("speed has an invalid value!");
                return this;
            }
            //不调用 item.isPickaxe() 等实例方法，因为它们会通过 getDefinitionNbt() 递归。
            //直接接受 speed 值；若 toolType 未设置，build() 也不会应用工具类型方块挖掘速度。
            this.speed = speed;
            return this;
        }

        /**
         * 给工具添加可挖掘的方块，及挖掘它的速度
         * <p>
         * Add a diggable block to the tool and define dig speed
         *
         * @param blockName the block name
         * @param speed     挖掘速度
         * @return the tool builder
         */
        public ToolBuilder addExtraBlock(@NotNull String blockName, int speed) {
            if (speed < 0) {
                log.warn("speed has an invalid value!");
                return this;
            }
            this.blocks.add(new CompoundTag()
                    .putCompound("block", new CompoundTag()
                            .putString("name", blockName)
                            .putCompound("states", new CompoundTag())
                            .putString("tags", "")
                    )
                    .putInt("speed", speed));
            return this;
        }

        /**
         * 给工具添加可挖掘的方块，及挖掘它的速度
         * <p>
         * Add a diggable block to the tool and define dig speed
         *
         * @param blocks the blocks
         * @return the tool builder
         */
        public ToolBuilder addExtraBlocks(@NotNull Map<String, Integer> blocks) {
            blocks.forEach((blockName, speed) -> {
                if (speed < 0) {
                    log.warn("speed has an invalid value!");
                    return;
                }
                this.blocks.add(new CompoundTag()
                        .putCompound("block", new CompoundTag()
                                .putString("name", blockName)
                                .putCompound("states", new CompoundTag())
                                .putString("tags", "")
                        )
                        .putInt("speed", speed));
            });
            return this;
        }

        /**
         * 给工具添加可挖掘的方块，及挖掘它的速度
         * <p>
         * Add a diggable block to the tool and define dig speed
         *
         * @param blockName the block name
         * @param property  the property
         * @return the tool builder
         */
        public ToolBuilder addExtraBlocks(@NotNull String blockName, DigProperty property) {
            if (property.getSpeed() != null && property.getSpeed() < 0) {
                log.warn("speed has an invalid value!");
                return this;
            }
            this.blocks.add(new CompoundTag()
                    .putCompound("block", new CompoundTag()
                            .putString("name", blockName)
                            .putCompound("states", property.getStates())
                            .putString("tags", "")
                    )
                    .putInt("speed", property.getSpeed()));
            return this;
        }

        /**
         * @deprecated  在1.20.60更改，会导致客户端收到错误数据包
         *
         * 物品的攻击力必须大于0才能生效<p>
         * 标记这个物品是否为武器，如果是，会在物品描述中提示{@code "+X 攻击伤害"}的信息
         * <p>
         * The item's attack damage must be greater than 0<p>
         * define the item is a weapon or not, and if so, it will prompt {@code "+X attack damage"} in the item description
         */
        @Deprecated
        public ToolBuilder isWeapon() {
            /*if (this.item.getAttackDamage() > 0 && !this.nbt.getCompound("components").containsCompound("minecraft:weapon")) {
                this.nbt.getCompound("components").putCompound(new CompoundTag("minecraft:weapon"));
            }*/
            return this;
        }

        /**
         * 给工具添加可挖掘的一类方块，用blockTag描述，挖掘它们的速度为{@link #speed(int)}的速度，如果没定义则为工具TIER对应的速度
         * <p>
         * Add a class of block to the tool that can be mined, described by blockTag, and the speed to mine them is the speed of {@link #speed(int)}, or the speed corresponding to the tool TIER if it is not defined
         *
         * @param blockTags 挖掘速度
         * @return the tool builder
         */
        public ToolBuilder addExtraBlockTags(@NotNull List<String> blockTags) {
            if (!blockTags.isEmpty()) {
                this.blockTags.addAll(blockTags);
            }
            return this;
        }

        @Override
        public CustomItemDefinition build() {
            //附加耐久 攻击伤害 tier 信息。
            //注意：不能用 item.getXxx() 作为 fallback，因为 ItemCustomTool 的覆写会调用 getDefinitionNbt()
            //→ getDefinition() → build()，造成无限递归。未设置时使用基类默认值。
            int resolvedDurability = this.maxDurability != null ? this.maxDurability : ItemTool.DURABILITY_WOODEN;
            int resolvedDamage = this.attackDamage != null ? this.attackDamage : 1;
            int resolvedTier = this.tier != null ? this.tier : 0;
            this.nbt.getCompound("components")
                    .putCompound("minecraft:durability", new CompoundTag().putInt("max_durability", resolvedDurability))
                    .getCompound("item_properties")
                    .putInt("damage", resolvedDamage)
                    .putInt("tier", resolvedTier)
                    .putInt("enchantable_value", tierToToolEnchantAbility(resolvedTier));

            if (speed == null) {
                //对齐 Block.toolBreakTimeBonus0 的 tier→speed 查表（WOODEN=1,GOLD=2,STONE=3,COPPER=4,IRON=5,DIAMOND=6,NETHERITE=7）；COPPER 等无对应原版工具，回退 1。
                speed = switch (resolvedTier) {
                    case 1 -> 2;   // TIER_WOODEN
                    case 2 -> 12;  // TIER_GOLD
                    case 3 -> 4;   // TIER_STONE
                    case 5 -> 6;   // TIER_IRON
                    case 6 -> 8;   // TIER_DIAMOND
                    case 7 -> 9;   // TIER_NETHERITE
                    default -> 1;  // TIER_COPPER(4) 等
                };
            }
            //确定工具类型：仅使用显式设置的 toolType。避免调用 item.isPickaxe() 等实例方法，
            //因为这些方法现在从本 NBT 读取，构造期调用会造成无限递归。
            //模组作者应通过 toolType(ToolType) 显式指定工具类型。
            Identifier type = null;
            boolean isPickaxe = this.toolType == ToolType.PICKAXE;
            boolean isAxe = this.toolType == ToolType.AXE;
            boolean isShovel = this.toolType == ToolType.SHOVEL;
            boolean isHoe = this.toolType == ToolType.HOE;
            boolean isSword = this.toolType == ToolType.SWORD;
            boolean isShears = this.toolType == ToolType.SHEARS;
            if (isPickaxe) {
                //添加可挖掘方块Tags
                this.blockTags.addAll(List.of("'stone'", "'metal'", "'diamond_pick_diggable'", "'mob_spawner'", "'rail'", "'slab_block'", "'stair_block'", "'smooth stone slab'", "'sandstone slab'", "'cobblestone slab'", "'brick slab'", "'stone bricks slab'", "'quartz slab'", "'nether brick slab'", "'glazed terracotta'", "coral"));
                //添加可挖掘方块
                type = ItemTag.IS_PICKAXE;
                //附加附魔信息
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", "pickaxe");
                this.tag("minecraft:is_pickaxe");
                //this.isWeapon();
            } else if (isAxe) {
                this.blockTags.addAll(List.of("'wood'", "'pumpkin'", "'plant'"));
                type = ItemTag.IS_AXE;
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", "axe");
                this.tag("minecraft:is_axe");
                //this.isWeapon();
            } else if (isShovel) {
                this.blockTags.addAll(List.of("'sand'", "'dirt'", "'gravel'", "'grass'", "'snow'"));
                type = ItemTag.IS_SHOVEL;
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", "shovel");
                this.tag("minecraft:is_shovel");
                //this.isWeapon();
            } else if (isHoe) {
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", "hoe");
                type = ItemTag.IS_HOE;
                this.tag("minecraft:is_hoe");
                //this.isWeapon();
            } else if (isSword) {
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", "sword");
                type = ItemTag.IS_SWORD;
                //this.isWeapon();
            } else if (isShears) {
                type = null;
                this.tag("minecraft:is_shears");
            } else {
                if (this.nbt.getCompound("components").contains("item_tags")) {
                    var list = this.nbt.getCompound("components").getList("item_tags", StringTag.class).getAll();
                    for (var tag : list) {
                        var id = new Identifier(tag.parseValue());
                        if (toolBlocks.containsKey(id)) {
                            type = id;
                            break;
                        }
                    }
                }
            }
            if (type != null) {
                toolBlocks.get(type).forEach(
                        (k, v) -> {
                            //不修改共享 DigProperty（static toolBlocks），否则首次 build 会污染后续不同 tier 的 build。
                            int blockSpeed = v.getSpeed() != null ? v.getSpeed() : speed;
                            blocks.add(new CompoundTag()
                                    .putCompound("block", new CompoundTag()
                                            .putString("name", k)
                                            .putCompound("states", v.getStates())
                                            .putString("tags", "")
                                    )
                                    .putInt("speed", blockSpeed));
                        }
                );
            }
            //添加可挖掘的方块tags
            if (!this.blockTags.isEmpty()) {
                var cmp = new CompoundTag();
                cmp.putCompound("block", new CompoundTag()
                                .putString("name", "")
                                .putCompound("states", new CompoundTag())
                                .putString("tags", "q.any_tag(" + String.join(", ", this.blockTags) + ")")
                        )
                        .putInt("speed", speed);
                this.diggerRoot.getList("destroy_speeds", CompoundTag.class).add(cmp);
            }

            //toolType 导出方块 + addExtraBlock 方块
            for (var k : this.blocks) {
                this.diggerRoot.getList("destroy_speeds", CompoundTag.class).add(k);
            }

            //有 destroy_speeds 条目才写回 digger：避免无 blockTags（SWORD/HOE/孤立 addExtraBlock）
            //时 digger 缺失导致 getSpeed() 返回 null。putCompound 按引用存储，前面追加的条目一并生效。
            if (!this.diggerRoot.getList("destroy_speeds", CompoundTag.class).isEmpty()) {
                this.nbt.getCompound("components")
                        .putCompound("minecraft:digger", this.diggerRoot);
            }

            return calculateID();
        }

        /**
         * tier → 工具附魔能力映射，复刻 {@link cn.nukkit.item.ItemTool#getEnchantAbility()}。
         * <p>
         * tier → tool enchantability mapping, mirroring {@link cn.nukkit.item.ItemTool#getEnchantAbility()}.
         */
        private static int tierToToolEnchantAbility(int tier) {
            return switch (tier) {
                case ItemTool.TIER_STONE -> 5;
                case ItemTool.TIER_WOODEN -> 15;
                case ItemTool.TIER_DIAMOND -> 10;
                case ItemTool.TIER_GOLD -> 22;
                case ItemTool.TIER_IRON -> 14;
                case ItemTool.TIER_NETHERITE -> 10;
                default -> 0;
            };
        }
    }

    public static class ArmorBuilder extends SimpleBuilder {
        /**
         * 自定义盔甲未显式调用 {@link #maxDurability(int)} 时的默认耐久。
         * 取原版最低值（皮革头盔 = 56）作安全下限，避免 {@code max_durability=0} 在
         * {@link cn.nukkit.entity.EntityHumanType#damageArmor} 中首次受击即摧毁护甲。
         * 需不可损坏的盔甲应显式设置较大耐久或用 {@link cn.nukkit.item.Item#setUnbreakable()}。
         */
        public static final int DURABILITY_DEFAULT = 56;

        private final ItemCustomArmor item;
        private @Nullable ArmorSlot slot = null;
        private @Nullable Integer armorPoints = null;
        private @Nullable Integer toughness = null;
        private @Nullable Integer tier = null;
        private @Nullable Integer maxDurability = null;

        private ArmorBuilder(ItemCustomArmor item, CreativeItemCategory creativeCategory) {
            super(item, creativeCategory);
            this.item = item;
            this.nbt.getCompound("components")
                    .getCompound("item_properties")
                    .putBoolean("can_destroy_in_creative", true);
        }

        public ArmorBuilder addRepairItemName(@NonNull String repairItemName, String molang) {
            super.addRepairs(List.of(repairItemName), molang);
            return this;
        }

        public ArmorBuilder addRepairItemName(@NonNull String repairItemName, int repairAmount) {
            super.addRepairs(List.of(repairItemName), String.valueOf(repairAmount));
            return this;
        }

        public ArmorBuilder addRepairItems(@NotNull List<Item> repairItems, String molang) {
            super.addRepairs(repairItems.stream().map(Item::getNamespaceId).toList(), molang);
            return this;
        }

        public ArmorBuilder addRepairItems(@NotNull List<Item> repairItems, int repairAmount) {
            super.addRepairs(repairItems.stream().map(Item::getNamespaceId).toList(), String.valueOf(repairAmount));
            return this;
        }

        /**
         * 指定盔甲装备槽位。决定 {@code wearable.slot}、{@code enchantable_slot}，
         * 并使服务端的 {@code isHelmet()/isChestplate()/isLeggings()/isBoots()} 返回 {@code true}。
         * <p>
         * <b>重要：未设置时不会写入槽位，自定义盔甲将无法装备。</b>
         * <p>
         * Specifies the armor equipment slot. Determines {@code wearable.slot}, {@code enchantable_slot},
         * and makes server-side {@code isHelmet()/isChestplate()/isLeggings()/isBoots()} return {@code true}.
         * <p>
         * <b>Important: when unset, no slot is written and the custom armor cannot be equipped.</b>
         */
        public ArmorBuilder slot(@NotNull ArmorSlot slot) {
            this.slot = slot;
            return this;
        }

        /**
         * 设置护甲值。服务端的 {@link Item#getArmorPoints()} 会读取此值。
         * 未设置时默认为 0。
         * <p>
         * Sets the armor points. Server-side {@link Item#getArmorPoints()} reads this value.
         * Defaults to 0 when unset.
         */
        public ArmorBuilder armorPoints(int armorPoints) {
            this.armorPoints = armorPoints;
            return this;
        }

        /**
         * 设置盔甲韧性（toughness）。服务端的 {@link Item#getToughness()} 会读取此值。
         * 未设置时默认为 0。
         * <p>
         * Sets the armor toughness. Server-side {@link Item#getToughness()} reads this value.
         * Defaults to 0 when unset.
         */
        public ArmorBuilder toughness(int toughness) {
            this.toughness = toughness;
            return this;
        }

        /**
         * 设置盔甲层级（tier）。影响 {@code getEnchantAbility()}。
         * 未设置时默认为 0（无附魔能力）。
         * <p>
         * Sets the armor tier. Affects {@code getEnchantAbility()}.
         * Defaults to 0 (no enchantability) when unset.
         */
        public ArmorBuilder tier(int tier) {
            this.tier = tier;
            return this;
        }

        /**
         * 设置最大耐久。服务端的 {@link Item#getMaxDurability()} 会读取此值。
         * 未设置时默认为 {@link #DURABILITY_DEFAULT}（正数安全值），避免护甲受击时被摧毁。
         */
        public ArmorBuilder maxDurability(int maxDurability) {
            this.maxDurability = maxDurability;
            return this;
        }

        @Override
        public CustomItemDefinition build() {
            //注意：不能用 item.getXxx() 作为 fallback，因为 ItemCustomArmor 的覆写会调用 getDefinitionNbt()
            //→ getDefinition() → build()，造成无限递归。未设置时使用默认值。
            int resolvedProtection = this.armorPoints != null ? this.armorPoints : 0;
            int resolvedToughness = this.toughness != null ? this.toughness : 0;
            int resolvedTier = this.tier != null ? this.tier : 0;
            int resolvedDurability = this.maxDurability != null ? this.maxDurability : DURABILITY_DEFAULT;
            this.nbt.getCompound("components")
                    .putCompound("minecraft:durability", new CompoundTag()
                            .putInt("max_durability", resolvedDurability))
                    .putCompound("minecraft:wearable", new CompoundTag()
                    .putInt("protection", resolvedProtection)
                    .putInt("toughness", resolvedToughness))
                    .getCompound("item_properties")
                    .putInt("tier", resolvedTier)
                    .putInt("enchantable_value", tierToArmorEnchantAbility(resolvedTier));
            //确定槽位：仅使用显式设置的 slot。避免调用 item.isHelmet() 等实例方法，
            //因为这些方法现在从本 NBT 读取，构造期调用会造成无限递归。
            //模组作者应通过 slot(ArmorSlot) 显式指定装备槽位。
            ArmorSlot resolvedSlot = this.slot;
            if (resolvedSlot != null) {
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", resolvedSlot.getEnchantableSlot());
                this.nbt.getCompound("components")
                        .getCompound("minecraft:wearable")
                        .putString("slot", resolvedSlot.getWearableSlot());
            }
            return calculateID();
        }

        /**
         * tier → 盔甲附魔能力映射，复刻 {@link cn.nukkit.item.ItemArmor#getEnchantAbility()}。
         * <p>
         * tier → armor enchantability mapping, mirroring {@link cn.nukkit.item.ItemArmor#getEnchantAbility()}.
         */
        private static int tierToArmorEnchantAbility(int tier) {
            return switch (tier) {
                case ItemArmor.TIER_CHAIN, ItemArmor.TIER_COPPER -> 12;
                case ItemArmor.TIER_LEATHER -> 15;
                case ItemArmor.TIER_DIAMOND -> 10;
                case ItemArmor.TIER_GOLD -> 25;
                case ItemArmor.TIER_IRON -> 9;
                case ItemArmor.TIER_NETHERITE -> 10;
                default -> 0;
            };
        }
    }

    public static class EdibleBuilder extends SimpleBuilder {

        private EdibleBuilder(ItemCustomEdible item, CreativeItemCategory creativeCategory) {
            super(item, creativeCategory);
            var food = Food.registerFood(item.getFood().getValue(), item.getFood().getKey());
            if (this.nbt.getCompound("components").contains("minecraft:food")) {
                this.nbt.getCompound("components").getCompound("minecraft:food").putBoolean("can_always_eat", item.canAlwaysEat());
            } else {
                this.nbt.getCompound("components").putCompound("minecraft:food", new CompoundTag().putBoolean("can_always_eat", item.canAlwaysEat()));
            }

            int eatingtick = food.getEatingTickSupplier() == null ? food.getEatingTick() : food.getEatingTickSupplier().getAsInt();
            this.nbt.getCompound("components")
                    .getCompound("item_properties")
                    .putInt("use_duration", eatingtick)
                    .putInt("use_animation", item.isDrink() ? 2 : 1)
                    .putBoolean("can_destroy_in_creative", true);
        }

        @Override
        public CustomItemDefinition build() {
            return calculateID();
        }
    }

    /**
     * Legacy模式物品构建器
     * <p>
     * Builder for legacy mode items
     */
    public static class LegacyItemBuilder {
        protected final String identifier;
        protected final CompoundTag nbt = new CompoundTag();
        private final Item item;

        protected LegacyItemBuilder(CustomItem customItem) {
            this.item = (Item) customItem;
            this.identifier = customItem.getNamespaceId();
        }

        /**
         * 设置物品最大堆叠数量
         * <p>
         * Set max stack size
         *
         * @param size max stack size
         * @return this builder
         */
        public LegacyItemBuilder maxStackSize(int size) {
            this.nbt.putCompound("minecraft:max_stack_size",
                new CompoundTag().putByte("value", (byte)size));
            return this;
        }

        /**
         * 添加自定义NBT组件
         * <p>
         * Add custom NBT component
         *
         * @param componentName component name (e.g., "minecraft:fuel")
         * @param componentData component data
         * @return this builder
         */
        public LegacyItemBuilder component(String componentName, CompoundTag componentData) {
            this.nbt.putCompound(componentName, componentData);
            return this;
        }

        /**
         * 直接对NBT进行自定义处理
         * <p>
         * Custom processing of NBT
         *
         * @param nbtConsumer NBT consumer
         * @return this builder
         */
        public LegacyItemBuilder customNBT(Consumer<CompoundTag> nbtConsumer) {
            nbtConsumer.accept(this.nbt);
            return this;
        }

        public CustomItemDefinition build() {
            return calculateID();
        }

        protected CustomItemDefinition calculateID() {
            var result = new CustomItemDefinition(identifier, nbt, ItemRegistrationMode.LEGACY);
            if (!INTERNAL_ALLOCATION_ID_MAP.containsKey(result.identifier())) {
                while (RuntimeItems.getMapping(GameVersion.getLastVersion()).getNamespacedIdByNetworkId(nextRuntimeId.incrementAndGet()) != null)
                    ;
                INTERNAL_ALLOCATION_ID_MAP.put(result.identifier(), nextRuntimeId.get());
                result.nbt.putString("name", result.identifier());
                result.nbt.putInt("id", nextRuntimeId.get());
            }
            return result;
        }
    }

    /**
     * Legacy模式食物构建器
     * <p>
     * Builder for legacy mode food items
     */
    public static class LegacyFoodBuilder extends LegacyItemBuilder {

        protected LegacyFoodBuilder(CustomItem customItem) {
            super(customItem);
        }

        /**
         * 设置食物属性
         * <p>
         * Set food properties
         *
         * @param foodData food component data
         * @return this builder
         */
        public LegacyFoodBuilder food(CompoundTag foodData) {
            this.nbt.putCompound("minecraft:food", foodData);
            return this;
        }

        /**
         * 设置使用持续时间
         * <p>
         * Set use duration
         *
         * @param duration duration in ticks
         * @return this builder
         */
        public LegacyFoodBuilder useDuration(int duration) {
            this.nbt.putInt("minecraft:use_duration", duration);
            return this;
        }

        /**
         * 快速创建食物属性
         * <p>
         * Quick setup for food properties
         *
         * @param nutrition nutrition value
         * @param saturationModifier saturation modifier
         * @param canAlwaysEat can always eat
         * @return this builder
         */
        public LegacyFoodBuilder foodProperties(int nutrition, float saturationModifier, boolean canAlwaysEat) {
            CompoundTag foodData = new CompoundTag()
                .putBoolean("can_always_eat", canAlwaysEat)
                .putInt("nutrition", nutrition)
                .putFloat("saturation_modifier", saturationModifier);
            return food(foodData);
        }

        /**
         * 设置食用后转换的物品
         * <p>
         * Set item to convert to after eating
         *
         * @param itemName item name (e.g., "glass_bottle")
         * @return this builder
         */
        public LegacyFoodBuilder usingConvertsTo(String itemName) {
            if (!this.nbt.containsCompound("minecraft:food")) {
                this.nbt.putCompound("minecraft:food", new CompoundTag());
            }
            this.nbt.getCompound("minecraft:food")
                .putString("using_converts_to", itemName);
            return this;
        }

        @Override
        public CustomItemDefinition build() {
            return calculateID();
        }
    }
}