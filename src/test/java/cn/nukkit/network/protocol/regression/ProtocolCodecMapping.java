package cn.nukkit.network.protocol.regression;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v291.Bedrock_v291;
import org.cloudburstmc.protocol.bedrock.codec.v313.Bedrock_v313;
import org.cloudburstmc.protocol.bedrock.codec.v332.Bedrock_v332;
import org.cloudburstmc.protocol.bedrock.codec.v340.Bedrock_v340;
import org.cloudburstmc.protocol.bedrock.codec.v354.Bedrock_v354;
import org.cloudburstmc.protocol.bedrock.codec.v388.Bedrock_v388;
import org.cloudburstmc.protocol.bedrock.codec.v407.Bedrock_v407;
import org.cloudburstmc.protocol.bedrock.codec.v419.Bedrock_v419;
import org.cloudburstmc.protocol.bedrock.codec.v428.Bedrock_v428;
import org.cloudburstmc.protocol.bedrock.codec.v440.Bedrock_v440;
import org.cloudburstmc.protocol.bedrock.codec.v448.Bedrock_v448;
import org.cloudburstmc.protocol.bedrock.codec.v465.Bedrock_v465;
import org.cloudburstmc.protocol.bedrock.codec.v471.Bedrock_v471;
import org.cloudburstmc.protocol.bedrock.codec.v475.Bedrock_v475;
import org.cloudburstmc.protocol.bedrock.codec.v486.Bedrock_v486;
import org.cloudburstmc.protocol.bedrock.codec.v503.Bedrock_v503;
import org.cloudburstmc.protocol.bedrock.codec.v527.Bedrock_v527;
import org.cloudburstmc.protocol.bedrock.codec.v534.Bedrock_v534;
import org.cloudburstmc.protocol.bedrock.codec.v544.Bedrock_v544;
import org.cloudburstmc.protocol.bedrock.codec.v545.Bedrock_v545;
import org.cloudburstmc.protocol.bedrock.codec.v554.Bedrock_v554;
import org.cloudburstmc.protocol.bedrock.codec.v557.Bedrock_v557;
import org.cloudburstmc.protocol.bedrock.codec.v560.Bedrock_v560;
import org.cloudburstmc.protocol.bedrock.codec.v567.Bedrock_v567;
import org.cloudburstmc.protocol.bedrock.codec.v568.Bedrock_v568;
import org.cloudburstmc.protocol.bedrock.codec.v575.Bedrock_v575;
import org.cloudburstmc.protocol.bedrock.codec.v582.Bedrock_v582;
import org.cloudburstmc.protocol.bedrock.codec.v589.Bedrock_v589;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
import org.cloudburstmc.protocol.bedrock.codec.v618.Bedrock_v618;
import org.cloudburstmc.protocol.bedrock.codec.v622.Bedrock_v622;
import org.cloudburstmc.protocol.bedrock.codec.v630.Bedrock_v630;
import org.cloudburstmc.protocol.bedrock.codec.v649.Bedrock_v649;
import org.cloudburstmc.protocol.bedrock.codec.v662.Bedrock_v662;
import org.cloudburstmc.protocol.bedrock.codec.v671.Bedrock_v671;
import org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685;
import org.cloudburstmc.protocol.bedrock.codec.v686.Bedrock_v686;
import org.cloudburstmc.protocol.bedrock.codec.v712.Bedrock_v712;
import org.cloudburstmc.protocol.bedrock.codec.v729.Bedrock_v729;
import org.cloudburstmc.protocol.bedrock.codec.v748.Bedrock_v748;
import org.cloudburstmc.protocol.bedrock.codec.v766.Bedrock_v766;
import org.cloudburstmc.protocol.bedrock.codec.v776.Bedrock_v776;
import org.cloudburstmc.protocol.bedrock.codec.v786.Bedrock_v786;
import org.cloudburstmc.protocol.bedrock.codec.v800.Bedrock_v800;
import org.cloudburstmc.protocol.bedrock.codec.v818.Bedrock_v818;
import org.cloudburstmc.protocol.bedrock.codec.v819.Bedrock_v819;
import org.cloudburstmc.protocol.bedrock.codec.v827.Bedrock_v827;
import org.cloudburstmc.protocol.bedrock.codec.v844.Bedrock_v844;
import org.cloudburstmc.protocol.bedrock.codec.v859.Bedrock_v859;
import org.cloudburstmc.protocol.bedrock.codec.v860.Bedrock_v860;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;
import org.cloudburstmc.protocol.bedrock.codec.v924.Bedrock_v924;
import org.cloudburstmc.protocol.bedrock.codec.v944.Bedrock_v944;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps Nukkit-MOT protocol version numbers to CloudburstMC Protocol BedrockCodec instances.
 */
public final class ProtocolCodecMapping {

    private static final Map<Integer, BedrockCodec> CODECS;

    static {
        Map<Integer, BedrockCodec> codecs = new LinkedHashMap<>();
        codecs.put(291, Bedrock_v291.CODEC);   // 1.7.0
        codecs.put(313, Bedrock_v313.CODEC);   // 1.8.0
        codecs.put(332, Bedrock_v332.CODEC);   // 1.9.0
        codecs.put(340, Bedrock_v340.CODEC);   // 1.10.0
        codecs.put(354, Bedrock_v354.CODEC);   // 1.11.0
        codecs.put(388, Bedrock_v388.CODEC);   // 1.13.0
        codecs.put(407, Bedrock_v407.CODEC);   // 1.16.0
        codecs.put(419, Bedrock_v419.CODEC);   // 1.16.100
        codecs.put(428, Bedrock_v428.CODEC);   // 1.16.210
        codecs.put(440, Bedrock_v440.CODEC);   // 1.17.0
        codecs.put(448, Bedrock_v448.CODEC);   // 1.17.10
        codecs.put(465, Bedrock_v465.CODEC);   // 1.17.30
        codecs.put(471, Bedrock_v471.CODEC);   // 1.17.40
        codecs.put(475, Bedrock_v475.CODEC);   // 1.18.0
        codecs.put(486, Bedrock_v486.CODEC);   // 1.18.10
        codecs.put(503, Bedrock_v503.CODEC);   // 1.18.30
        codecs.put(527, Bedrock_v527.CODEC);   // 1.19.0
        codecs.put(534, Bedrock_v534.CODEC);   // 1.19.10
        codecs.put(544, Bedrock_v544.CODEC);   // 1.19.20
        codecs.put(545, Bedrock_v545.CODEC);   // 1.19.21
        codecs.put(554, Bedrock_v554.CODEC);   // 1.19.30
        codecs.put(557, Bedrock_v557.CODEC);   // 1.19.40
        codecs.put(560, Bedrock_v560.CODEC);   // 1.19.50
        codecs.put(567, Bedrock_v567.CODEC);   // 1.19.60
        codecs.put(568, Bedrock_v568.CODEC);   // 1.19.63
        codecs.put(575, Bedrock_v575.CODEC);   // 1.19.70
        codecs.put(582, Bedrock_v582.CODEC);   // 1.19.80
        codecs.put(589, Bedrock_v589.CODEC);   // 1.20.0
        codecs.put(594, Bedrock_v594.CODEC);   // 1.20.10
        codecs.put(618, Bedrock_v618.CODEC);   // 1.20.30
        codecs.put(622, Bedrock_v622.CODEC);   // 1.20.40
        codecs.put(630, Bedrock_v630.CODEC);   // 1.20.50
        codecs.put(649, Bedrock_v649.CODEC);   // 1.20.60
        codecs.put(662, Bedrock_v662.CODEC);   // 1.20.70
        codecs.put(671, Bedrock_v671.CODEC);   // 1.20.80
        codecs.put(685, Bedrock_v685.CODEC);   // 1.21.0
        codecs.put(686, Bedrock_v686.CODEC);   // 1.21.2
        codecs.put(712, Bedrock_v712.CODEC);   // 1.21.20
        codecs.put(729, Bedrock_v729.CODEC);   // 1.21.30
        codecs.put(748, Bedrock_v748.CODEC);   // 1.21.40
        codecs.put(766, Bedrock_v766.CODEC);   // 1.21.50
        codecs.put(776, Bedrock_v776.CODEC);   // 1.21.60
        codecs.put(786, Bedrock_v786.CODEC);   // 1.21.70
        codecs.put(800, Bedrock_v800.CODEC);   // 1.21.80
        codecs.put(818, Bedrock_v818.CODEC);   // 1.21.90
        codecs.put(819, Bedrock_v819.CODEC);   // 1.21.93
        codecs.put(827, Bedrock_v827.CODEC);   // 1.21.100
        codecs.put(844, Bedrock_v844.CODEC);   // 1.21.110
        codecs.put(859, Bedrock_v859.CODEC);   // 1.21.120
        codecs.put(860, Bedrock_v860.CODEC);   // 1.21.124
        codecs.put(898, Bedrock_v898.CODEC);   // 1.21.130
        codecs.put(924, Bedrock_v924.CODEC);   // 1.26.0
        codecs.put(944, Bedrock_v944.CODEC);   // 1.26.10
        CODECS = Collections.unmodifiableMap(codecs);
    }

    private ProtocolCodecMapping() {
    }

    public static BedrockCodec getCodec(int protocolVersion) {
        BedrockCodec codec = CODECS.get(protocolVersion);
        if (codec == null) {
            throw new IllegalArgumentException("No CB Protocol codec for protocol version: " + protocolVersion);
        }
        return codec;
    }

    public static Set<Integer> getSupportedVersions() {
        return CODECS.keySet();
    }
}
