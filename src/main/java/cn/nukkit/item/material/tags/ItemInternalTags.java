package cn.nukkit.item.material.tags;

import cn.nukkit.item.material.ItemTypes;
import cn.nukkit.item.material.tags.impl.SimpleItemTag;

public interface ItemInternalTags {
    ItemTag DYE = ItemTags.register("lumi:dye", new SimpleItemTag(
            ItemTypes.WHITE_DYE,
            ItemTypes.LIGHT_GRAY_DYE,
            ItemTypes.GRAY_DYE,
            ItemTypes.BLACK_DYE,
            ItemTypes.BROWN_DYE,
            ItemTypes.RED_DYE,
            ItemTypes.ORANGE_DYE,
            ItemTypes.YELLOW_DYE,
            ItemTypes.LIME_DYE,
            ItemTypes.GREEN_DYE,
            ItemTypes.CYAN_DYE,
            ItemTypes.LIGHT_BLUE_DYE,
            ItemTypes.BLUE_DYE,
            ItemTypes.PURPLE_DYE,
            ItemTypes.MAGENTA_DYE,
            ItemTypes.PINK_DYE
    ));
}
