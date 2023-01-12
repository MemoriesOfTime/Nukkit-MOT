package cn.nukkit.network.protocol;

import cn.nukkit.Nukkit;
import com.google.common.io.ByteStreams;
import lombok.ToString;

@ToString()
public class BiomeDefinitionListPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.BIOME_DEFINITION_LIST_PACKET;

    private static final byte[] TAG_361;
    private static final byte[] TAG_419;

    // Call the latest version "TAG"
    private static final byte[] TAG; // 486

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
            TAG = ByteStreams.toByteArray(Nukkit.class.getClassLoader().getResourceAsStream("biome_definitions_486.dat"));
        } catch (Exception e) {
            throw new AssertionError("Error whilst loading biome definitions 486", e);
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
        if (this.protocol >= ProtocolInfo.v1_18_10) {
            this.put(TAG);
        }else if (this.protocol >= ProtocolInfo.v1_16_100) {
            this.put(TAG_419);
        }else {
            this.put(TAG_361);
        }
    }
}
