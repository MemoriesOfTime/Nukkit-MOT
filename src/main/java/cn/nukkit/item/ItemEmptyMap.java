package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;

public class ItemEmptyMap extends Item {

    public ItemEmptyMap() {
        this(0, 1);
    }

    public ItemEmptyMap(Integer meta) {
        this(meta, 1);
    }

    public ItemEmptyMap(Integer meta, int count) {
        super(EMPTY_MAP, meta, count, "Empty Map");
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        this.giveMap(player);
        return true;
    }

    protected void giveMap(Player player) {
        if (!player.isCreative()) {
            this.count--;
        }
        Item map = Item.get(Item.MAP);
        if (this.getDamage() == 2) {
            CompoundTag tag = map.hasCompoundTag() ? map.getNamedTag() : new CompoundTag();
            tag.putBoolean("map_display_players", true);
            map.setNamedTag(tag);
        }
        player.getInventory().addItem(map);
    }
}
