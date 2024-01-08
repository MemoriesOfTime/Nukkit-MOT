package cn.nukkit.inventory.special;

import cn.nukkit.item.Item;

import java.util.List;

/**
 * @author glorydark
 * @date {2024/1/8} {16:02}
 */
public interface SpecialRecipe {

    boolean canExecute(List<Item> inputs, Item outputItem);

}
