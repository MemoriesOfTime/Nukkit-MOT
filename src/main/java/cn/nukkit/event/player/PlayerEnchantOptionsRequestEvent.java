package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.inventory.EnchantInventory;
import cn.nukkit.network.protocol.PlayerEnchantOptionsPacket;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class PlayerEnchantOptionsRequestEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private EnchantInventory table;
    private List<PlayerEnchantOptionsPacket.EnchantOptionData> options;

    public PlayerEnchantOptionsRequestEvent(Player player, EnchantInventory table, List<PlayerEnchantOptionsPacket.EnchantOptionData> options) {
        this.player = player;
        this.table = table;
        this.options = options;
    }
}
