package cn.nukkit.item.customitem.data;

import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemCategory;

/**
 * 控制自定义物品在创造栏的大分类,例如建材栏,材料栏
 * <br>可选值:1 CONSTRUCTOR 2 NATURE 3 EQUIPMENT 4 ITEMS 5 NONE
 *
 * @deprecated use {@link CreativeItemCategory} instead
 * @return 自定义物品的在创造栏的大分类
 * @see <a href="https://wiki.bedrock.dev/documentation/creative-categories.html#list-of-creative-tabs">bedrock wiki</a>
 */
@Deprecated
public enum ItemCreativeCategory {
    CONSTRUCTOR,
    NATURE,
    EQUIPMENT,
    ITEMS,
    NONE;

    @Deprecated
    public static ItemCreativeCategory fromID(int num) {
        return switch (num) {
            case 1 -> CONSTRUCTOR;
            case 2 -> NATURE;
            case 3 -> EQUIPMENT;
            case 4 -> ITEMS;
            default -> NONE;
        };
    }
}
