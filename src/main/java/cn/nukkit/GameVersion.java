package cn.nukkit;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;

/**
 * https://github.com/EaseCation/Nukkit/blob/master/src/main/java/cn/nukkit/GameVersion.java
 */
public enum GameVersion {

    V1_1_0(113, false, "1.1.0", "1.1"),
    V1_2_0(137, false, "1.2.0", "1.2"),
    V1_2_5_11(140, false, "1.2.5.11"),
    V1_2_5(141, false, "1.2.5"),
    V1_2_6(150, false, "1.2.6"),
    V1_2_7(160, false, "1.2.7"),
    V1_2_10(201, false, "1.2.10"),
    V1_2_13(223, false, "1.2.13"),
    V1_2_13_11(224, false, "1.2.13.11"),
    V1_4_0(261, false, "1.4.0", "1.4"),
    V1_5_0(274, false, "1.5.0", "1.5"),
    V1_6_0_5(281, false, "1.6.0.5"),
    V1_6_0(282, false, "1.6.0", "1.6"),
    V1_7_0(291, false, "1.7.0", "1.7"),
    V1_8_0(313, false, "1.8.0", "1.8"),
    V1_9_0(332, false, "1.9.0", "1.9"),
    V1_10_0(340, false, "1.10.0", "1.10"),
    V1_11_0(354, false, "1.11.0", "1.11"),
    V1_12_0(361, false, "1.12.0", "1.12"),
    V1_13_0(388, false, "1.13.0", "1.13"),
    V1_14_0(389, false, "1.14.0", "1.14"),
    V1_14_60(390, false, "1.14.60"),
    V1_16_0(407, false, "1.16.0", "1.16"),
    V1_16_20(408, false, "1.16.20"),
    V1_16_100_0(409, false, "1.16.100.0"),
    V1_16_100_51(410, false, "1.16.100.51"),
    V1_16_100_52(411, false, "1.16.100.52"),
    V1_16_100(419, false, "1.16.100"),
    V1_16_200(422, false, "1.16.200"),
    V1_16_200_51(420, false, "1.16.200.51"),
    V1_16_210_50(423, false, "1.16.210.50"),
    V1_16_210_53(424, false, "1.16.210.53"),
    V1_16_210(428, false, "1.16.210"),
    V1_16_220(431, false, "1.16.220"),
    V1_16_230_50(433, false, "1.16.230.50"),
    V1_16_230(434, false, "1.16.230"),
    V1_16_230_54(435, false, "1.16.230_54"),
    V1_17_0(440, false, "1.17.0", "1.17"),
    V1_17_10(448, false, "1.17.10"),
    V1_17_20_20(453, false, "1.17.20"),
    V1_17_30(465, false, "1.17.30"),
    V1_17_40(471, false, "1.17.40"),
    V1_18_0(475, false, "1.18.0", "1.18"),
    V1_18_10_26(485, false, "1.18.10.26"),
    V1_18_10(486, false, "1.18.10"),
    V1_18_30(503, false, "1.18.30"),
    V1_19_0_29(524, false, "1.19.0"),
    V1_19_0_31(526, false, "1.19.0.31"),
    V1_19_0(527, false, "1.19.0", "1.19"),
    V1_19_10(534, false, "1.19.10"),
    V1_19_20(544, false, "1.19.20"),
    V1_19_21(545, false, "1.19.21"),
    V1_19_30_23(553, false, "1.19.30.23"),
    V1_19_30(554, false, "1.19.30"),
    V1_19_40(557, false, "1.19.40"),
    V1_19_50_20(558, false, "1.19.50.20"),
    V1_19_50(560, false, "1.19.50"),
    V1_19_60(567, false, "1.19.60"),
    V1_19_63(568, false, "1.19.63"),
    V1_19_70_24(574, false, "1.19.70.24"),
    V1_19_70(575, false, "1.19.70"),
    V1_19_80(582, false, "1.19.80"),
    V1_20_0_23(588, false, "1.20.0.23"),
    V1_20_0(589, false, "1.20.0", "1.20"),
    V1_20_10_21(593, false, "1.20.10.21"),
    V1_20_10(594, false, "1.20.10"),
    V1_20_30_24(617, false, "1.20.30.24"),
    V1_20_30(618, false, "1.20.30"),
    V1_20_40(622, false, "1.20.40"),
    V1_20_50(630, false, "1.20.50"),
    V1_20_60(649, false, "1.20.60"),
    V1_20_70(662, false, "1.20.70"),
    V1_20_80(671, false, "1.20.80"),
    V1_21_0(685, false, "1.21.0", "1.21"),
    V1_21_2(686, false, "1.21.2"),
    V1_21_20(712, false, "1.21.20"),
    V1_21_30(729, false, "1.21.30"),
    V1_21_40(748, false, "1.21.40"),
    V1_21_50_26(765, false, "1.21.50_26"),
    V1_21_50(766, false, "1.21.50"),
    V1_21_60(776, false, "1.21.60"),
    V1_21_70_24(785, false, "1.21.70_24"), //TODO
    V1_21_70(786, false, "1.21.70"),
    V1_21_80(800, false, "1.21.80"),
    V1_21_90(818, false, "1.21.90"),
    V1_21_93(819, false, "1.21.93"),
    V1_21_100(827, false, "1.21.100"),
    V1_21_110_26(843, false, "1.21.110"),
    V1_21_110(844, false, "1.21.110"),

    V1_20_50_NETEASE(630, true, "1.20.50_NetEase"),
    V1_21_2_NETEASE(686, true, "1.21.2_NetEase"),
    ;

    private static GameVersion FEATURE_VERSION = GameVersion.V1_21_70;
    private static final GameVersion LAST_VERSION = GameVersion.V1_21_110; //TODO MultiVersion

    private final int protocol;
    private final boolean isNetEase;
    private final String name;
    private final String[] aliases;

    GameVersion(int protocol, boolean isNetEase, String name, String... aliases) {
        this.protocol = protocol;
        this.isNetEase = isNetEase;
        this.name = name;
        this.aliases = aliases;
    }

    public int getProtocol() {
        return protocol;
    }

    public boolean isNetEase() {
        return isNetEase;
    }

    @Override
    public String toString() {
        return name;
    }

    public String[] getAliases() {
        return aliases;
    }

    private static final GameVersion[] VALUES = values();
    private static final Map<String, GameVersion> BY_NAME = new Object2ObjectOpenHashMap<>();
    private static final Map<String, GameVersion> BY_ALIAS = new Object2ObjectOpenHashMap<>();
    private static final GameVersion[] BY_PROTOCOL;
    private static final GameVersion[] BY_PROTOCOL_NETEASE;

    static {
        for (GameVersion version : VALUES) {
            BY_NAME.put(version.name, version);
            String[] aliases = version.aliases;
            if (aliases != null) {
                for (String alias : aliases) {
                    BY_ALIAS.put(alias, version);
                }
            }
        }
        BY_PROTOCOL = buildProtocolMapping(false);
        BY_PROTOCOL_NETEASE = buildProtocolMapping(true);
    }

    private static GameVersion[] buildProtocolMapping(boolean isNetEase) {
        GameVersion[] mapping = new GameVersion[LAST_VERSION.protocol + 1];
        GameVersion previous = null;

        for (GameVersion version : VALUES) {
            if (version.isNetEase() != isNetEase) continue;

            if (previous != null) {
                for (int i = previous.protocol; i < version.protocol; i++) {
                    mapping[i] = previous;
                }
            }
            previous = version;
        }

        if (previous != null) {
            for (int i = previous.protocol; i <= LAST_VERSION.protocol; i++) {
                mapping[i] = previous;
            }
        }

        return mapping;
    }

    @Nullable
    public static GameVersion byName(String name) {
        return byName(name, true);
    }

    @Nullable
    public static GameVersion byName(String name, boolean alias) {
        GameVersion version = BY_NAME.get(name);
        if (version == null && alias) {
            return BY_ALIAS.get(name);
        }
        return version;
    }

    public static GameVersion byProtocol(int protocol, boolean isNetEase) {
        if (protocol < 0 || protocol > LAST_VERSION.protocol) {
            return getLastVersion();
        }
        if (isNetEase) {
            return BY_PROTOCOL_NETEASE[protocol];
        }
        return BY_PROTOCOL[protocol];
    }

    public static GameVersion[] getValues() {
        return VALUES;
    }

    public static GameVersion getFeatureVersion() {
        return FEATURE_VERSION;
    }

    static void setFeatureVersion(GameVersion featureVersion) {
        Objects.requireNonNull(featureVersion, "version");
        FEATURE_VERSION = featureVersion;
    }

    public static GameVersion getLastVersion() {
        return LAST_VERSION;
    }
}
