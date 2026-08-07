package cn.nukkit.network.protocol;

import cn.nukkit.api.OnlyNetEase;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
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
        boolean netease = this.gameVersion.isNetEase();
        for (int i = 0; i < size; i++) {
            int minLevel = (!netease && this.protocol >= ProtocolInfo.v1_26_20_26)
                    ? this.getByte()
                    : (int) this.getUnsignedVarInt();
            int slot = this.getLInt();

            List<EnchantData> enchants0 = this.readEnchantDataList(netease);
            List<EnchantData> enchants1 = this.readEnchantDataList(netease);
            List<EnchantData> enchants2 = this.readEnchantDataList(netease);
            List<EnchantData> enchantsCustom = netease ? this.readEnchantDataList(netease) : Collections.emptyList();
            String enchantName = this.getString();
            int eNetId = (int) this.getUnsignedVarInt();
            this.options.add(new EnchantOptionData(
                    minLevel, slot, enchants0, enchants1, enchants2, enchantsCustom, enchantName, eNetId));
        }
    }

    @Override
    public void encode() {
        this.reset();
        boolean netease = this.gameVersion.isNetEase();
        this.putUnsignedVarInt(this.options.size());
        for (EnchantOptionData option : this.options) {
            // NetEase clients are capped below v1_26_20_26, so they always use the
            // VarUInt min-level encoding.
            if (!netease && this.protocol >= ProtocolInfo.v1_26_20_26) {
                this.putByte((byte) option.getMinLevel());
            } else {
                this.putUnsignedVarInt(option.getMinLevel());
            }
            this.putLInt(option.getPrimarySlot());
            this.writeEnchantDataList(option.getEnchants0(), netease);
            this.writeEnchantDataList(option.getEnchants1(), netease);
            this.writeEnchantDataList(option.getEnchants2(), netease);
            if (netease) {
                this.writeEnchantDataList(option.getEnchantsCustom(), netease);
            }
            this.putString(option.getEnchantName());
            this.putUnsignedVarInt(option.getEnchantNetId());
        }
    }

    private List<EnchantData> readEnchantDataList(boolean netease) {
        int eSize = (int) this.getUnsignedVarInt();
        if (eSize > 1000) {
            throw new RuntimeException("Enchantment list too big: " + eSize);
        }
        List<EnchantData> list = new ObjectArrayList<>(eSize);
        if (!netease && this.protocol >= ProtocolInfo.v1_26_20_26) {
            for (int j = 0; j < eSize; j++) {
                list.add(new EnchantData((int) this.getUnsignedVarInt(), this.getByte()));
            }
        } else {
            for (int j = 0; j < eSize; j++) {
                int type = this.getByte();
                int level = this.getByte();
                String modId = netease ? this.getString() : "";
                list.add(new EnchantData(type, level, modId));
            }
        }
        return list;
    }

    private void writeEnchantDataList(List<EnchantData> list, boolean netease) {
        this.putUnsignedVarInt(list.size());
        if (!netease && this.protocol >= ProtocolInfo.v1_26_20_26) {
            for (EnchantData data : list) {
                this.putUnsignedVarInt(data.getType());
                this.putByte((byte) data.getLevel());
            }
        } else {
            for (EnchantData data : list) {
                this.putByte((byte) data.getType());
                this.putByte((byte) data.getLevel());
                if (netease) {
                    // NetEase appends a mod-enchant identifier string to every entry;
                    // empty for vanilla enchantments. Skipping it corrupts the stream.
                    this.putString(data.getModEnchantIdentifier());
                }
            }
        }
    }

    @ToString(callSuper = true)
    @Value
    public static class EnchantOptionData {
        int minLevel;
        int primarySlot;
        List<EnchantData> enchants0;
        List<EnchantData> enchants1;
        List<EnchantData> enchants2;
        /**
         * NetEase 第四组自定义（mod）附魔列表；标准协议不传输。
         * <p>
         * Fourth group of custom (mod) enchantments carried only by the NetEase protocol.
         */
        @OnlyNetEase
        List<EnchantData> enchantsCustom;
        String enchantName;
        int enchantNetId;

        /**
         * Backwards-compatible constructor used by all standard-protocol call sites;
         * the NetEase-only custom list defaults to empty.
         */
        public EnchantOptionData(int minLevel, int primarySlot,
                                 List<EnchantData> enchants0, List<EnchantData> enchants1, List<EnchantData> enchants2,
                                 String enchantName, int enchantNetId) {
            this(minLevel, primarySlot, enchants0, enchants1, enchants2, Collections.emptyList(), enchantName, enchantNetId);
        }

        public EnchantOptionData(int minLevel, int primarySlot,
                                 List<EnchantData> enchants0, List<EnchantData> enchants1, List<EnchantData> enchants2,
                                 @OnlyNetEase List<EnchantData> enchantsCustom,
                                 String enchantName, int enchantNetId) {
            this.minLevel = minLevel;
            this.primarySlot = primarySlot;
            this.enchants0 = enchants0;
            this.enchants1 = enchants1;
            this.enchants2 = enchants2;
            this.enchantsCustom = enchantsCustom == null ? Collections.emptyList() : enchantsCustom;
            this.enchantName = enchantName;
            this.enchantNetId = enchantNetId;
        }
    }

    @ToString(callSuper = true)
    @Value
    public static class EnchantData {
        int type;
        int level;
        /**
         * NetEase mod 附魔标识符；标准协议不传输，原版附魔为空字符串。
         * <p>
         * NetEase-only mod-enchant identifier suffix; empty string for vanilla enchantments.
         */
        @OnlyNetEase
        String modEnchantIdentifier;

        public EnchantData(int type, int level) {
            this(type, level, "");
        }

        public EnchantData(int type, int level, @OnlyNetEase String modEnchantIdentifier) {
            this.type = type;
            this.level = level;
            this.modEnchantIdentifier = modEnchantIdentifier == null ? "" : modEnchantIdentifier;
        }
    }
}
