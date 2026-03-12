package cn.nukkit.network.protocol;

import lombok.ToString;

import java.util.EnumSet;
import java.util.Set;

@ToString
public class PlayerArmorDamagePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.PLAYER_ARMOR_DAMAGE_PACKET;

    public final Set<PlayerArmorDamageFlag> flags = EnumSet.noneOf(PlayerArmorDamageFlag.class);
    public final int[] damage = new int[5];

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        int flagsval = this.getByte();
        for (int i = 0; i < 5; i++) {
            if (i == 4 && this.protocol < ProtocolInfo.v1_21_20) {
                continue;
            }
            if ((flagsval & (1 << i)) != 0) {
                this.flags.add(PlayerArmorDamageFlag.values()[i]);
                this.damage[i] = this.getVarInt();
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        int outflags = 0;
        for (PlayerArmorDamageFlag flag : this.flags) {
            if (flag == PlayerArmorDamageFlag.BODY && this.protocol < ProtocolInfo.v1_21_20) {
                continue;
            }
            outflags |= 1 << flag.ordinal();
        }
        this.putByte((byte) outflags);

        for (PlayerArmorDamageFlag flag : this.flags) {
            if (flag == PlayerArmorDamageFlag.BODY && this.protocol < ProtocolInfo.v1_21_20) {
                continue;
            }
            this.putVarInt(this.damage[flag.ordinal()]);
        }
    }

    public enum PlayerArmorDamageFlag {
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS,
        BODY
    }
}
