/*
 * https://PowerNukkit.org - The Nukkit you know but Powerful!
 * Copyright (C) 2021  José Roberto de Araújo Júnior
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cn.nukkit.event.inventory;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.inventory.SmithingInventory;
import cn.nukkit.item.Item;

/**
 * @author joserobjr
 * @since 2021-05-16
 */
public class SmithingTableEvent extends InventoryEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Item equipmentItem;
    private final Item resultItem;
    private final Item ingredientItem;
    private final Item templateItem;
    private final Player player;
    public SmithingTableEvent(SmithingInventory inventory, Item equipmentItem, Item resultItem, Item ingredientItem, Item templateItem, Player player) {
        super(inventory);
        this.equipmentItem = equipmentItem;
        this.resultItem = resultItem;
        this.ingredientItem = ingredientItem;
        this.templateItem = templateItem;
        this.player = player;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public Item getEquipmentItem() {
        return this.equipmentItem;
    }

    public Item getResultItem() {
        return this.resultItem;
    }

    public Item getIngredientItem() {
        return this.ingredientItem;
    }

    public Item getTemplateItem() {
        return templateItem;
    }

    public Player getPlayer() {
        return this.player;
    }
}
