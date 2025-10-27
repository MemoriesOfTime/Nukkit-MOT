package cn.nukkit.item.customitem.dynamic;

import cn.nukkit.item.customitem.CustomItemDefinition;
import cn.nukkit.item.customitem.ItemCustom;
import cn.nukkit.item.customitem.data.RenderOffsets;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemCategory;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 动态ItemCustom实现类
 * Dynamic ItemCustom implementation class
 */
@Log4j2
public class DynamicItemCustom extends ItemCustom {

    private final DynamicItemConfig config;
    private CustomItemDefinition definitionCache;

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

    @Override
    public CustomItemDefinition getDefinition() {
        if (definitionCache == null) {
            try {
                // 解析创造模式分类
                CreativeItemCategory category = CreativeItemCategory.valueOf(config.getCreativeCategory().toUpperCase());

                CustomItemDefinition.SimpleBuilder builder = CustomItemDefinition.simpleBuilder(this, category);

                // 解析创造模式组
                if (config.getCreativeGroup() != null && !config.getCreativeGroup().isEmpty()) {
                    builder.creativeGroup(config.getCreativeGroup());
                }

                definitionCache = builder
                        .allowOffHand(config.isAllowOffHand())
                        .handEquipped(config.isHandEquipped())
                        .foil(config.isFoil())
                        .canDestroyInCreative(config.isCanDestroyInCreative())
                        .renderOffsets(RenderOffsets.scaleOffset(config.getScaleOffset()))
                        .build();
            } catch (Exception e) {
                // 如果解析失败，使用默认配置
                log.error("Failed to parse creative category or group for item {}: {}", this.getId(), e.getMessage());
                definitionCache = CustomItemDefinition
                        .simpleBuilder(this, CreativeItemCategory.EQUIPMENT)
                        .allowOffHand(config.isAllowOffHand())
                        .handEquipped(config.isHandEquipped())
                        .renderOffsets(RenderOffsets.scaleOffset(config.getScaleOffset()))
                        .build();
            }
        }
        return definitionCache;
    }
}