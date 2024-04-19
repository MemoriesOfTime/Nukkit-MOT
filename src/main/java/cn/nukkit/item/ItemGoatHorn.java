package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.Sound;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.plugin.InternalPlugin;

import java.util.concurrent.atomic.AtomicBoolean;

public class ItemGoatHorn extends StringItemBase {

    protected int coolDownTick = 140;

    private final AtomicBoolean banUse = new AtomicBoolean(false);

    public ItemGoatHorn() {
        this(0);
    }

    public ItemGoatHorn(Integer meta) {
        this(meta, 1);
    }

    public ItemGoatHorn(Integer meta, int count) {
        super("minecraft:goat_horn", "Goat Horn");
        this.meta = meta;
        this.count = count;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        if (!this.banUse.getAndSet(true)) {
            playSound(player);
            Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, () -> this.banUse.set(false), this.coolDownTick);
            player.setItemCoolDown(this.coolDownTick, "goat_horn");
            return true;
        } else {
            return false;
        }
    }

    /**
     * Sets cool down tick
     *
     * @param coolDownTick the cool down tick
     */
    public void setCoolDown(int coolDownTick) {
        this.coolDownTick = coolDownTick;
    }

    public void playSound(Player player) {
        switch (this.getDamage()) {
            case 0 -> player.getLevel().addSound(player, Sound.HORN_CALL_0);
            case 1 -> player.getLevel().addSound(player, Sound.HORN_CALL_1);
            case 2 -> player.getLevel().addSound(player, Sound.HORN_CALL_2);
            case 3 -> player.getLevel().addSound(player, Sound.HORN_CALL_3);
            case 4 -> player.getLevel().addSound(player, Sound.HORN_CALL_4);
            case 5 -> player.getLevel().addSound(player, Sound.HORN_CALL_5);
            case 6 -> player.getLevel().addSound(player, Sound.HORN_CALL_6);
            case 7 -> player.getLevel().addSound(player, Sound.HORN_CALL_7);
        }
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_0;
    }
}
