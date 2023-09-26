package cn.nukkit.network.protocol;

import cn.nukkit.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;

public class PlayerEnchantOptionsPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.PLAYER_ENCHANT_OPTIONS_PACKET;

    public List<EnchantOptionData> options = new ArrayList<>();

    private static int nextId = 100000;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        // TODO
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(this.options.size());
        for (EnchantOptionData option : this.options) {
            this.putVarInt(option.minLevel());
            this.putInt(0);
            this.putUnsignedVarInt(option.enchantments.size());
            for (Enchantment data : option.enchantments) {
                this.putByte((byte) data.getId());
                this.putByte((byte) data.getLevel());
            }
            this.putUnsignedVarInt(0);
            this.putUnsignedVarInt(0);
            this.putString(option.enchantName);
            this.putUnsignedVarInt(nextId++);
        }
    }

    public record EnchantOptionData(
        int minLevel, String enchantName, List<Enchantment> enchantments
    ) {
    }
}
