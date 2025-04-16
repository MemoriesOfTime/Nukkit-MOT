package cn.nukkit.network.protocol;

import cn.nukkit.Nukkit;
import com.google.common.io.ByteStreams;
import lombok.ToString;

@ToString()
public class BiomeDefinitionListPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.BIOME_DEFINITION_LIST_PACKET;

    private static final byte[] TAG_361;
    private static final byte[] TAG_419;
    private static final byte[] TAG_486;
    private static final byte[] TAG_527;
    private static final byte[] TAG_544;

    // Call the latest version "TAG"
    private static final byte[] TAG; // 786

    static {
        try {
            TAG_361 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_361.dat"));
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 361", e);
        }
        try {
            TAG_419 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_419.dat"));
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 419", e);
        }
        try {
            TAG_486 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_486.dat"));
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 486", e);
        }
        try {
            TAG_527 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_527.dat"));
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 527", e);
        }
        try {
            TAG_544 = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_554.dat"));
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 554", e);
        }
        try {
            TAG = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_786.dat"));
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 554", e);
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_21_70_24) {
            this.put(TAG);
        } else if (this.protocol >= ProtocolInfo.v1_19_30_23) {
            this.put(TAG_544);
        } else if (this.protocol >= ProtocolInfo.v1_19_0) {
            this.put(TAG_527);
        } else if (this.protocol >= ProtocolInfo.v1_18_10) {
            this.put(TAG_486);
        } else if (this.protocol >= ProtocolInfo.v1_16_100) {
            this.put(TAG_419);
        } else {
            this.put(TAG_361);
        }
    }
}
