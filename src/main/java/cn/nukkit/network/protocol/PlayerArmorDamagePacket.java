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
        if (this.protocol >= ProtocolInfo.v1_21_110) {
            this.getArray(this.flags, stream -> {
                int ordinal = stream.getVarInt();
                PlayerArmorDamageFlag flag = PlayerArmorDamageFlag.values()[ordinal];
                this.damage[ordinal] = stream.getLShort();
                return flag;
            });
        } else {
            int flagsval = this.getByte();
            int maxIndex = this.protocol >= ProtocolInfo.v1_21_20 ? 4 : 3;
            for (int i = 0; i <= maxIndex; i++) {
                if ((flagsval & (1 << i)) != 0) {
                    this.flags.add(PlayerArmorDamageFlag.values()[i]);
                    this.damage[i] = this.getVarInt();
                }
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_21_110) {
            this.putArray(this.flags, flag -> {
                this.putVarInt(flag.ordinal());
                this.putLShort(this.damage[flag.ordinal()]);
            });
        } else {
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
    }

    public enum PlayerArmorDamageFlag {
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS,
        BODY
    }
}
