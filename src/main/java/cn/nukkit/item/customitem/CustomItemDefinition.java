package cn.nukkit.item.customitem;

import cn.nukkit.inventory.ItemTag;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.item.customitem.data.DigProperty;
import cn.nukkit.item.customitem.data.ItemCreativeCategory;
import cn.nukkit.item.customitem.data.ItemCreativeGroup;
import cn.nukkit.item.customitem.data.RenderOffsets;
import cn.nukkit.item.food.Food;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.Identifier;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
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
    private static final ConcurrentHashMap<String, Integer> INTERNAL_ALLOCATION_ID_MAP = new ConcurrentHashMap<>();
    private static final AtomicInteger nextRuntimeId = new AtomicInteger(10000);

    private final String identifier;
    private final CompoundTag nbt; //649
    private final CompoundTag nbt465;
    private final CompoundTag nbt419;

    private CustomItemDefinition(String identifier, CompoundTag nbt) {
        this.identifier = identifier;
        this.nbt = nbt;

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
    }

    public String identifier() {
        return identifier;
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
    public static CustomItemDefinition.SimpleBuilder customBuilder(CustomItem item, ItemCreativeCategory creativeCategory) {
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
    public static CustomItemDefinition.SimpleBuilder simpleBuilder(ItemCustom item, ItemCreativeCategory creativeCategory) {
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
    public static CustomItemDefinition.ToolBuilder toolBuilder(ItemCustomTool item, ItemCreativeCategory creativeCategory) {
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
    public static CustomItemDefinition.ArmorBuilder armorBuilder(ItemCustomArmor item, ItemCreativeCategory creativeCategory) {
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
    public static CustomItemDefinition.EdibleBuilder edibleBuilder(ItemCustomEdible item, ItemCreativeCategory creativeCategory) {
        return new CustomItemDefinition.EdibleBuilder(item, creativeCategory);
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

        protected SimpleBuilder(CustomItem customItem, ItemCreativeCategory creativeCategory) {
            this.item = (Item) customItem;
            this.identifier = customItem.getNamespaceId();
            //定义材质
            this.nbt.getCompound("components")
                    .getCompound("item_properties")
                    .getCompound("minecraft:icon")
                    .putCompound("textures", new CompoundTag().putString("default", customItem.getTextureName()));

            //定义显示名
            if (item.getName() != null && !item.getName().equals(Item.UNKNOWN_STR)) {
                this.nbt.getCompound("components")
                        .putCompound("minecraft:display_name", new CompoundTag().putString("value", item.getName()));
            }

            //定义最大堆叠数量
            this.nbt.getCompound("components")
                    .getCompound("item_properties")
                    .putInt("max_stack_size", item.getMaxStackSize());
            //定义在创造栏的分类
            this.nbt.getCompound("components")
                    .getCompound("item_properties")//1 none
                    .putInt("creative_category", creativeCategory.ordinal() + 1)
                    .putString("creative_group", ItemCreativeGroup.NONE.getGroupName());
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
            var list = this.nbt.getCompound("components").getList("item_tags", StringTag.class);
            if (list == null) {
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
                while (RuntimeItems.getMapping(ProtocolInfo.CURRENT_PROTOCOL).getNamespacedIdByNetworkId(nextRuntimeId.incrementAndGet()) != null)
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

            for (var name : List.of("minecraft:web", "minecraft:bamboo")) {
                swordBlocks.put(name, new DigProperty());
            }
            toolBlocks.put(ItemTag.IS_SWORD, swordBlocks);
        }

        private ToolBuilder(ItemCustomTool item, ItemCreativeCategory creativeCategory) {
            super(item, creativeCategory);
            this.item = item;
            this.nbt.getCompound("components")
                    .getCompound("item_properties")
                    .putInt("enchantable_value", item.getEnchantAbility());

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
         * 控制采集类工具的挖掘速度
         *
         * @param speed 挖掘速度
         */
        public ToolBuilder speed(int speed) {
            if (speed < 0) {
                log.warn("speed has an invalid value!");
                return this;
            }
            if (item.isPickaxe() || item.isShovel() || item.isHoe() || item.isAxe() || item.isShears()) {
                this.speed = speed;
            }
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
            //附加耐久 攻击伤害信息
            this.nbt.getCompound("components")
                    .putCompound("minecraft:durability", new CompoundTag().putInt("max_durability", item.getMaxDurability()))
                    .getCompound("item_properties")
                    .putInt("damage", item.getAttackDamage());

            if (speed == null) {
                speed = switch (item.getTier()) {
                    case 6 -> 7;
                    case 5 -> 6;
                    case 4 -> 5;
                    case 3 -> 4;
                    case 2 -> 3;
                    case 1 -> 2;
                    default -> 1;
                };
            }
            Identifier type = null;
            if (item.isPickaxe()) {
                //添加可挖掘方块Tags
                this.blockTags.addAll(List.of("'stone'", "'metal'", "'diamond_pick_diggable'", "'mob_spawner'", "'rail'", "'slab_block'", "'stair_block'", "'smooth stone slab'", "'sandstone slab'", "'cobblestone slab'", "'brick slab'", "'stone bricks slab'", "'quartz slab'", "'nether brick slab'", "'glazed terracotta'", "coral"));
                //添加可挖掘方块
                type = ItemTag.IS_PICKAXE;
                //附加附魔信息
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", "pickaxe");
                this.tag("minecraft:is_pickaxe");
                //this.isWeapon();
            } else if (item.isAxe()) {
                this.blockTags.addAll(List.of("'wood'", "'pumpkin'", "'plant'"));
                type = ItemTag.IS_AXE;
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", "axe");
                this.tag("minecraft:is_axe");
                //this.isWeapon();
            } else if (item.isShovel()) {
                this.blockTags.addAll(List.of("'sand'", "'dirt'", "'gravel'", "'grass'", "'snow'"));
                type = ItemTag.IS_SHOVEL;
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", "shovel");
                this.tag("minecraft:is_shovel");
                //this.isWeapon();
            } else if (item.isHoe()) {
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", "hoe");
                type = ItemTag.IS_HOE;
                this.tag("minecraft:is_hoe");
                //this.isWeapon();
            } else if (item.isSword()) {
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", "sword");
                type = ItemTag.IS_SWORD;
                //this.isWeapon();
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
                            if (v.getSpeed() == null) v.setSpeed(speed);
                            blocks.add(new CompoundTag()
                                    .putCompound("block", new CompoundTag()
                                            .putString("name", k)
                                            .putCompound("states", v.getStates())
                                            .putString("tags", "")
                                    )
                                    .putInt("speed", v.getSpeed()));
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
                this.nbt.getCompound("components")
                        .putCompound("minecraft:digger", this.diggerRoot);
            }

            //添加可挖掘的方块
            for (var k : this.blocks) {
                this.diggerRoot.getList("destroy_speeds", CompoundTag.class).add(k);
            }
            return calculateID();
        }
    }

    public static class ArmorBuilder extends SimpleBuilder {
        private final ItemCustomArmor item;

        private ArmorBuilder(ItemCustomArmor item, ItemCreativeCategory creativeCategory) {
            super(item, creativeCategory);
            this.item = item;
            this.nbt.getCompound("components")
                    .getCompound("item_properties")
                    .putInt("enchantable_value", item.getEnchantAbility())
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

        @Override
        public CustomItemDefinition build() {
            this.nbt.getCompound("components")
                    .putCompound("minecraft:durability", new CompoundTag()
                            .putInt("max_durability", item.getMaxDurability()))
                    .putCompound("minecraft:wearable", new CompoundTag()
                            .putInt("protection", item.getArmorPoints()));
            if (item.isHelmet()) {
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", "armor_head");
                this.nbt.getCompound("components")
                        .getCompound("minecraft:wearable")
                                .putString("slot", "slot.armor.head");
            } else if (item.isChestplate()) {
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", "armor_torso");
                this.nbt.getCompound("components")
                        .getCompound("minecraft:wearable")
                        .putString("slot", "slot.armor.chest");
            } else if (item.isLeggings()) {
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", "armor_legs");
                this.nbt.getCompound("components")
                        .getCompound("minecraft:wearable")
                                .putString("slot", "slot.armor.legs");
            } else if (item.isBoots()) {
                this.nbt.getCompound("components").getCompound("item_properties")
                        .putString("enchantable_slot", "armor_feet");
                this.nbt.getCompound("components")
                        .getCompound("minecraft:wearable")
                                .putString("slot", "slot.armor.feet");
            }
            return calculateID();
        }
    }

    public static class EdibleBuilder extends SimpleBuilder {
        private EdibleBuilder(ItemCustomEdible item, ItemCreativeCategory creativeCategory) {
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
}