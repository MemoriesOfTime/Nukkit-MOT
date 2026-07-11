package cn.nukkit.network.protocol;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@ToString
public class PlayerEnchantOptionsPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.PLAYER_ENCHANT_OPTIONS_PACKET;

    /**
     * Base recipe ID for enchantment options. Values >= this are treated as
     * enchantment recipes by the ItemStackRequest CraftRecipe flow.
     */
    public static final int ENCH_RECIPEID = 0x10000000;

    private static final AtomicInteger ENCH_COUNTER = new AtomicInteger(0);

    /**
     * Lookup table of enchantment option data by assigned enchant net ID. Populated
     * by the server when sending enchantment options to a player so the subsequent
     * CraftRecipeAction can resolve the selected option.
     */
    public static final Int2ObjectMap<EnchantOptionData> RECIPE_MAP = new Int2ObjectOpenHashMap<>();

    /**
     * Allocate a new enchant recipe ID and register the option in RECIPE_MAP.
     * The caller should ensure the returned ID is written into the option's
     * enchantNetId field before sending the packet to the client.
     */
    public static int assignRecipeId(EnchantOptionData option) {
        int id = ENCH_RECIPEID + ENCH_COUNTER.incrementAndGet();
        RECIPE_MAP.put(id, option);
        return id;
    }

    public final List<EnchantOptionData> options = new ArrayList<>();

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        int size = (int) this.getUnsignedVarInt();
        if (size > 1000) {
            throw new RuntimeException("EnchantOptions too big: " + size);
        }
        for (int i = 0; i < size; i++) {
            int minLevel = this.protocol >= ProtocolInfo.v1_26_20_26 ? this.getByte() : (int) this.getUnsignedVarInt();
            int slot = this.getLInt();

            List<EnchantData> enchants0 = this.readEnchantDataList();
            List<EnchantData> enchants1 = this.readEnchantDataList();
            List<EnchantData> enchants2 = this.readEnchantDataList();
            String enchantName = this.getString();
            int eNetId = (int) this.getUnsignedVarInt();
            this.options.add(new EnchantOptionData(minLevel, slot, enchants0, enchants1, enchants2, enchantName, eNetId));
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(this.options.size());
        for (EnchantOptionData option : this.options) {
            if (this.protocol >= ProtocolInfo.v1_26_20_26) {
                this.putByte((byte) option.getMinLevel());
            } else {
                this.putUnsignedVarInt(option.getMinLevel());
            }
            this.putLInt(option.getPrimarySlot());
            this.writeEnchantDataList(option.getEnchants0());
            this.writeEnchantDataList(option.getEnchants1());
            this.writeEnchantDataList(option.getEnchants2());
            this.putString(option.getEnchantName());
            this.putUnsignedVarInt(option.getEnchantNetId());
        }
    }

    private List<EnchantData> readEnchantDataList() {
        int eSize = (int) this.getUnsignedVarInt();
        if (eSize > 1000) {
            throw new RuntimeException("Enchantment list too big: " + eSize);
        }
        List<EnchantData> list = new ObjectArrayList<>(eSize);
        if (this.protocol >= ProtocolInfo.v1_26_20_26) {
            for (int j = 0; j < eSize; j++) {
                list.add(new EnchantData((int) this.getUnsignedVarInt(), this.getByte()));
            }
        } else {
            for (int j = 0; j < eSize; j++) {
                list.add(new EnchantData(this.getByte(), this.getByte()));
            }
        }
        return list;
    }

    private void writeEnchantDataList(List<EnchantData> list) {
        this.putUnsignedVarInt(list.size());
        if (this.protocol >= ProtocolInfo.v1_26_20_26) {
            for (EnchantData data : list) {
                this.putUnsignedVarInt(data.getType());
                this.putByte((byte) data.getLevel());
            }
        } else {
            for (EnchantData data : list) {
                this.putByte((byte) data.getType());
                this.putByte((byte) data.getLevel());
            }
        }
    }

    @Value
    public static class EnchantOptionData {
        int minLevel;
        int primarySlot;
        List<EnchantData> enchants0;
        List<EnchantData> enchants1;
        List<EnchantData> enchants2;
        String enchantName;
        int enchantNetId;
    }

    @Value
    public static class EnchantData {
        int type;
        int level;
    }
}
