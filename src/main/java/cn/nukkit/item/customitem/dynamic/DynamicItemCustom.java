package cn.nukkit.item.customitem.dynamic;

import cn.nukkit.item.customitem.CustomItemDefinition;
import cn.nukkit.item.customitem.ItemCustom;
import cn.nukkit.item.customitem.data.ItemCreativeCategory;
import cn.nukkit.item.customitem.data.ItemCreativeGroup;
import cn.nukkit.item.customitem.data.RenderOffsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 动态ItemCustom实现类
 * Dynamic ItemCustom implementation class
 */
public class DynamicItemCustom extends ItemCustom {
    private final DynamicItemConfig config;

    public DynamicItemCustom(@NotNull String id, @Nullable String name, @NotNull String textureName, @NotNull DynamicItemConfig config) {
        super(id, name, textureName);
        this.config = config;
    }

    @Override
    public int getMaxStackSize() {
        return config.getMaxStackSize();
    }

    @Override
    public int getMaxDurability() {
        return config.getMaxDurability();
    }

    @Override
    public int getAttackDamage() {
        return config.getAttackDamage();
    }

    @Override
    public boolean isSword() {
        return config.isSword();
    }

    @Override
    public boolean isTool() {
        return config.isTool();
    }

    public int scaleOffset() {
        return config.getScaleOffset();
    }

    @Override
    public CustomItemDefinition getDefinition() {
        try {
            // 解析创造模式分类
            ItemCreativeCategory category =
                    ItemCreativeCategory.valueOf(config.getCreativeCategory().toUpperCase());

            // 解析创造模式组
            ItemCreativeGroup group = null;
            if (config.getCreativeGroup() != null && !config.getCreativeGroup().isEmpty()) {
                group = ItemCreativeGroup.valueOf(config.getCreativeGroup().toUpperCase());
            }

            CustomItemDefinition.SimpleBuilder builder = CustomItemDefinition.simpleBuilder(this, category);

            if (group != null) {
                builder.creativeGroup(group);
            }

            return builder
                    .allowOffHand(config.isAllowOffHand())
                    .handEquipped(config.isHandEquipped())
                    .foil(config.isFoil())
                    .canDestroyInCreative(config.isCanDestroyInCreative())
                    .renderOffsets(RenderOffsets.scaleOffset(config.getScaleOffset()))
                    .build();
        } catch (Exception e) {
            // 如果解析失败，使用默认配置
            return CustomItemDefinition
                    .simpleBuilder(this, ItemCreativeCategory.EQUIPMENT)
                    .allowOffHand(config.isAllowOffHand())
                    .handEquipped(config.isHandEquipped())
                    .renderOffsets(RenderOffsets.scaleOffset(config.getScaleOffset()))
                    .build();
        }
    }
    }