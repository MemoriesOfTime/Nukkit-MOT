package cn.nukkit.block.custom;

import cn.nukkit.block.Block;
import cn.nukkit.block.custom.container.BlockContainer;
import cn.nukkit.block.custom.container.BlockStorageContainer;
import cn.nukkit.block.custom.container.data.*;
import cn.nukkit.block.custom.properties.*;
import cn.nukkit.item.customitem.data.ItemCreativeCategory;
import cn.nukkit.item.customitem.data.ItemCreativeGroup;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemCategory;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemGroup;
import com.google.common.base.Preconditions;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * CustomBlockDefinition用于获得发送给客户端的方块行为包数据。{@link Builder}中提供的方法都是控制发送给客户端数据，如果需要控制服务端部分行为，请覆写{@link Block Block}中的方法。
 * <p>
 * CustomBlockDefinition is used to get the data of the block behavior_pack sent to the client. The methods provided in {@link Builder} control the data sent to the client, if you need to control some of the server-side behavior, please override the methods in {@link Block Block}.
 */
@Log4j2
public record CustomBlockDefinition(String identifier, CompoundTag nbt, int legacyId,  Class<? extends BlockContainer> typeOf) {

    /**
     * Builder custom block definition.
     *
     * @param blockContainer the custom block
     * @return the custom block definition builder.
     */
    public static Builder builder(@NotNull BlockContainer blockContainer) {
        return new Builder(blockContainer);
    }

    public static class Builder {
        protected final String identifier;
        protected final BlockContainer blockContainer;

        protected CompoundTag nbt = new CompoundTag()
                .putCompound("components", new CompoundTag());

        protected Builder(BlockContainer blockContainer) {
            this.identifier = blockContainer.getIdentifier();
            this.blockContainer = blockContainer;
            Block b = (Block) blockContainer;
            CompoundTag components = this.nbt.getCompound("components");

            //设置一些与NK内部对应的方块属性
            components.putCompound("minecraft:friction", new CompoundTag()
                            .putFloat("value", (float) Math.min(0.9, Math.max(0, 1 - b.getFrictionFactor()))))
                    .putCompound("minecraft:destructible_by_explosion", new CompoundTag()
                            .putInt("explosion_resistance", (int) b.getResistance()))
                    .putCompound("minecraft:light_dampening", new CompoundTag()
                            .putByte("lightLevel", (byte) Block.getBlockLightFilter(blockContainer.getNukkitId())))
                    .putCompound("minecraft:light_emission", new CompoundTag()
                            .putByte("emission", (byte) b.getLightLevel()))
                    .putCompound("minecraft:destructible_by_mining", new CompoundTag()
                            .putFloat("value", 99999f));//default server-side mining time calculate
            //设置材质
            components.putCompound("minecraft:material_instances", new CompoundTag()
                    .putCompound("mappings", new CompoundTag())
                    .putCompound("materials", new CompoundTag()));

            //默认单位立方体方块
            components.putCompound("minecraft:unit_cube", new CompoundTag());
            //设置默认单位立方体方块的几何模型
            components.putCompound("minecraft:geometry", new CompoundTag()
                    .putString("identifier", "minecraft:geometry.full_block")
                    .putString("culling", "")
                    .putCompound("bone_visibility", new CompoundTag())
            );

            //设置方块在创造栏的分类
            this.nbt.putCompound("menu_category", new CompoundTag()
                    .putString("category", CreativeItemCategory.NATURE.name())
                    .putString("group", ""));
            //molang版本
            this.nbt.putInt("molangVersion", 9);

            //设置方块的properties
            var propertiesNBT = getPropertiesNBT();
            if (propertiesNBT != null) {
                nbt.putList("properties", propertiesNBT);
            }

            nbt.putCompound("vanilla_block_data", new CompoundTag().putInt("block_id", blockContainer.getNukkitId())
                    /*.putString("material", "")*/); //todo Figure what is dirt, maybe that corresponds to https://wiki.bedrock.dev/documentation/materials.html
        }

        public Builder texture(String texture) {
            this.materials(Materials.builder().any(Materials.RenderMethod.OPAQUE, texture));
            return this;
        }

        public Builder name(String name) {
            Preconditions.checkArgument(!name.isBlank(), "name is blank");
            this.nbt.getCompound("components").putCompound("minecraft:display_name", new CompoundTag()
                    .putString("value", name));
            return this;
        }

        public Builder materials(Materials materials) {
            this.nbt.getCompound("components").putCompound("minecraft:material_instances", new CompoundTag()
                    .putCompound("mappings", new CompoundTag())
                    .putCompound("materials", materials.toCompoundTag()));
            return this;
        }

        @Deprecated
        public Builder creativeGroupAndCategory(ItemCreativeGroup creativeGroup, ItemCreativeCategory creativeCategory) {
            this.nbt.getCompound("menu_category")
                    .putString("category", creativeCategory.name().toLowerCase(Locale.ENGLISH))
                    .putString("group", creativeGroup.getGroupName());
            return this;
        }

        public Builder creativeGroupAndCategory(CreativeItemGroup creativeGroup, CreativeItemCategory creativeCategory) {
            this.nbt.getCompound("menu_category")
                    .putString("category", creativeCategory.name().toLowerCase(Locale.ENGLISH))
                    .putString("group", creativeGroup.getName());
            return this;
        }

        public Builder creativeCategory(String creativeCategory) {
            this.nbt.getCompound("menu_category")
                    .putString("category", creativeCategory.toLowerCase(Locale.ENGLISH));
            return this;
        }

        @Deprecated
        public Builder creativeCategory(ItemCreativeCategory creativeCategory) {
            this.nbt.getCompound("menu_category")
                    .putString("category", creativeCategory.name().toLowerCase(Locale.ENGLISH));
            return this;
        }

        public Builder creativeCategory(CreativeItemCategory creativeCategory) {
            this.nbt.getCompound("menu_category")
                    .putString("category", creativeCategory.name().toLowerCase(Locale.ENGLISH));
            return this;
        }

        /**
         * 控制自定义方块客户端侧的挖掘时间(单位秒)
         * <p>
         * 自定义方块的挖掘时间取决于服务端侧和客户端侧中最小的一个
         * <p>
         * Control the digging time (in seconds) on the client side of the custom block
         * <p>
         * The digging time of a custom cube depends on the smallest of the server-side and client-side
         */
        public Builder breakTime(double second) {
            this.nbt.getCompound("components")
                    .putCompound("minecraft:destructible_by_mining", new CompoundTag()
                            .putFloat("value", (float) second));
            return this;
        }

        /**
         * 控制自定义方块在创造栏中的组。
         * <p>
         * Control the grouping of custom blocks in the creation inventory.
         *
         * @see <a href="https://wiki.bedrock.dev/documentation/creative-categories.html">wiki.bedrock.dev</a>
         */
        public Builder creativeGroup(String creativeGroup) {
            if (creativeGroup.isBlank()) {
                log.error("creativeGroup has an invalid value!");
                return this;
            }
            this.nbt.getCompound("components").getCompound("menu_category").putString("group", creativeGroup.toLowerCase(Locale.ENGLISH));
            return this;
        }

        /**
         * 控制自定义方块在创造栏中的组。
         * <p>
         * Control the grouping of custom blocks in the creation inventory.
         *
         * @see <a href="https://wiki.bedrock.dev/documentation/creative-categories.html">wiki.bedrock.dev</a>
         */
        @Deprecated
        public Builder creativeGroup(ItemCreativeGroup creativeGroup) {
            this.nbt.getCompound("components").getCompound("menu_category").putString("group", creativeGroup.getGroupName());
            return this;
        }

        /**
         * @see <a href="https://wiki.bedrock.dev/blocks/block-components.html#crafting-table">wiki.bedrock.dev</a>
         */
        public Builder craftingTable(CraftingTable craftingTable) {
            this.nbt.getCompound("components").putCompound("minecraft:crafting_table", craftingTable.toCompoundTag());
            return this;
        }

        /**
         * supports rotation, scaling, and translation. The component can be added to the whole block and/or to individual block permutations. Transformed geometries still have the same restrictions that non-transformed geometries have such as a maximum size of 30/16 units.
         */
        public Builder transformation(@NotNull Transformation transformation) {
            this.nbt.getCompound("components").putCompound("minecraft:transformation", transformation.toCompoundTag());
            return this;
        }

        /**
         * 以度为单位设置块围绕立方体中心的旋转,旋转顺序为 xyz.角度必须是90的倍数。
         * <p>
         * Set the rotation of the block around the center of the block in degrees, the rotation order is xyz. The angle must be a multiple of 90.
         */
        public Builder rotation(@NotNull Vector3f rotation) {
            this.transformation(new Transformation(new Vector3(0, 0, 0), new Vector3(1, 1, 1), rotation.asVector3()));
            return this;
        }

        /**
         * @see #geometry(Geometry)
         * 默认不设置骨骼显示
         * <p>
         * defalut not set boneVisibilities
         */
        public Builder geometry(String geometry) {
            if (geometry.isBlank()) {
                log.error("geometry has an invalid value!");
                return this;
            }
            var components = this.nbt.getCompound("components");
            //默认单位立方体方块，如果定义几何模型需要移除
            if (components.contains("minecraft:unit_cube")) components.remove("minecraft:unit_cube");
            //设置方块对应的几何模型
            components.putCompound("minecraft:geometry", new CompoundTag()
                    .putString("identifier", geometry.toLowerCase(Locale.ENGLISH)));
            return this;
        }

        /**
         * 控制自定义方块的几何模型,如果不设置默认为单位立方体
         * <p>
         * Control the geometric model of the custom block, if not set the default is the unit cube.<br>
         * Geometry identifier from geo file in 'RP/models/blocks' folder
         */

        public Builder geometry(@NotNull Geometry geometry) {
            var components = this.nbt.getCompound("components");
            //默认单位立方体方块，如果定义几何模型需要移除
            if (components.contains("minecraft:unit_cube")) components.remove("minecraft:unit_cube");
            //设置方块对应的几何模型
            components.putCompound("minecraft:geometry", geometry.toCompoundTag());
            return this;
        }

        /**
         * 控制自定义方块的变化特征，例如条件渲染，部分渲染等
         * <p>
         * Control custom block permutation features such as conditional rendering, partial rendering, etc.
         */

        public Builder permutation(Permutation permutation) {
            if (!this.nbt.contains("permutations")) {
                this.nbt.putList("permutations", new ListTag<CompoundTag>());
            }
            ListTag<CompoundTag> permutations = this.nbt.getList("permutations", CompoundTag.class);
            permutations.add(permutation.toCompoundTag());
            this.nbt.putList("permutations", permutations);
            return this;
        }

        /**
         * 控制自定义方块的变化特征，例如条件渲染，部分渲染等
         * <p>
         * Control custom block permutation features such as conditional rendering, partial rendering, etc.
         */
        public Builder permutations(Permutation... permutations) {
            var per = new ListTag<CompoundTag>();
            for (var permutation : permutations) {
                per.add(permutation.toCompoundTag());
            }
            this.nbt.putList("permutations", per);
            return this;
        }

        /**
         * 设置此方块的客户端碰撞箱。
         * <p>
         * Set the client collision box for this block.
         *
         * @param origin 碰撞箱的原点 The origin of the collision box
         * @param size   碰撞箱的大小 The size of the collision box
         */
        public Builder collisionBox(@NotNull Vector3f origin, @NotNull Vector3f size) {
            this.nbt.getCompound("components")
                    .putCompound("minecraft:collision_box", new CompoundTag()
                            .putBoolean("enabled", true)
                            .putList("origin", new ListTag<FloatTag>()
                                    .add(new FloatTag(origin.x))
                                    .add(new FloatTag(origin.y))
                                    .add(new FloatTag(origin.z)))
                            .putList("size", new ListTag<FloatTag>()
                                    .add(new FloatTag(size.x))
                                    .add(new FloatTag(size.y))
                                    .add(new FloatTag(size.z))));
            return this;
        }

        /**
         * 设置此方块的客户端选择箱。
         * <p>
         * Set the client collision box for this block.
         *
         * @param origin 选择箱的原点 The origin of the collision box
         * @param size   选择箱的大小 The size of the collision box
         */
        public Builder selectionBox(@NotNull Vector3f origin, @NotNull Vector3f size) {
            this.nbt.getCompound("components")
                    .putCompound("minecraft:selection_box", new CompoundTag()
                            .putBoolean("enabled", true)
                            .putList("origin", new ListTag<FloatTag>()
                                    .add(new FloatTag(origin.x))
                                    .add(new FloatTag(origin.y))
                                    .add(new FloatTag(origin.z)))
                            .putList("size", new ListTag<FloatTag>()
                                    .add(new FloatTag(size.x))
                                    .add(new FloatTag(size.y))
                                    .add(new FloatTag(size.z))));
            return this;
        }

        public Builder blockTags(String... tag) {
            Preconditions.checkNotNull(tag);
            Preconditions.checkArgument(tag.length > 0);
            ListTag<StringTag> stringTagListTag = new ListTag<>();
            for (String s : tag) {
                stringTagListTag.add(new StringTag(s));
            }
            this.nbt.putList("blockTags", stringTagListTag);
            return this;
        }

        /**
         * @return Block Properties in NBT Tag format
         */
        @Nullable
        private ListTag<CompoundTag> getPropertiesNBT() {
            if (this.blockContainer instanceof BlockStorageContainer block) {
                BlockProperties properties = block.getBlockProperties();
                Set<BlockProperty<?>> propertyTypeSet = properties.getPropertyTypeSet();
                if (propertyTypeSet.isEmpty()) {
                    return null;
                }
                var nbtList = new ListTag<CompoundTag>();
                for (var each : propertyTypeSet) {
                    if (each instanceof BooleanBlockProperty booleanBlockProperty) {
                        nbtList.add(new CompoundTag().putString("name", booleanBlockProperty.getName())
                                .putList("enum", new ListTag<>()
                                        .add(new ByteTag(0))
                                        .add(new ByteTag(1))));
                    } else if (each instanceof IntBlockProperty intBlockProperty) {
                        var enumList = new ListTag<IntTag>();
                        for (int i = intBlockProperty.getMinValue(); i <= intBlockProperty.getMaxValue(); i++) {
                            enumList.add(new IntTag(i));
                        }
                        nbtList.add(new CompoundTag().putString("name", intBlockProperty.getName()).putList("enum", enumList));
                    } else if (each instanceof EnumBlockProperty<?> arrayBlockProperty) {
                        var enumList = new ListTag<StringTag>();
                        for (String string : arrayBlockProperty.getPersistenceNames()) {
                            enumList.add(new StringTag("", string));
                        }
                        nbtList.add(new CompoundTag().putString("name", arrayBlockProperty.getName()).putList("enum", enumList));
                    }
                }
                return nbtList;
            }
            return null;
        }

        /**
         * 对要发送给客户端的方块ComponentNBT进行自定义处理，这里包含了所有对自定义方块的定义。在符合条件的情况下，你可以任意修改。
         * <p>
         * Custom processing of the block to be sent to the client ComponentNBT, which contains all definitions for custom block. You can modify them as much as you want, under the right conditions.
         */
        public CustomBlockDefinition customBuild(@NotNull Consumer<CompoundTag> nbt) {
            var def = this.build();
            nbt.accept(def.nbt);
            return def;
        }

        public CustomBlockDefinition build() {
            return new CustomBlockDefinition(this.identifier, this.nbt, this.blockContainer.getNukkitId(), this.blockContainer.getClass());
        }
    }
}
