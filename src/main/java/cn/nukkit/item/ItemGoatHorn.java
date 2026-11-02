package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.level.Sound;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemGoatHorn extends StringItemBase {

    public static final String COOL_DOWN_CATEGORY = "goat_horn";

    protected int coolDownTick = 140;

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
        if (!player.isItemCoolDownEnd(COOL_DOWN_CATEGORY)) {
            return false;
        }
        playSound(player);
        player.setItemCoolDown(this.coolDownTick, COOL_DOWN_CATEGORY);
        return true;
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
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_19_0;
    }
}
