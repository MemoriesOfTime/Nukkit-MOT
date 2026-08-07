package cn.nukkit.network.protocol;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import lombok.ToString;

import java.io.IOException;
import java.nio.ByteOrder;

@ToString
public class AddVolumeEntityPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ADD_VOLUME_ENTITY_PACKET;

    private long id;
    private CompoundTag data;
    /**
     * @since v465
     */
    private String engineVersion;
    /**
     * @since v485
     */
    private String identifier;
    /**
     * @since v485
     */
    private String instanceName;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        id = getUnsignedVarInt();
        data = getTagNetworkLE();
        if (protocol >= 486) {
            identifier = getString();
            instanceName = getString();
            if (protocol >= 503) {
                getBlockVector3(); // minBounds
                getBlockVector3(); // maxBounds
                getVarInt(); // dimension
            }
            engineVersion = getString();
        } else if (protocol >= 465) {
            engineVersion = getString();
        }
    }

    @Override
    public void encode() {
        reset();
        putUnsignedVarInt(id);
        try {
            this.put(NBTIO.write(data, ByteOrder.LITTLE_ENDIAN, true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (protocol >= 486) {
            // v486+: identifier, instanceName before engineVersion
            putString(identifier);
            putString(instanceName);
            if (protocol >= 503) {
                // v503+: minBounds, maxBounds, dimension (not currently used by server)
                putBlockVector3(0, 0, 0);
                putBlockVector3(0, 0, 0);
                putVarInt(0);
            }
            putString(engineVersion);
        } else if (protocol >= 465) {
            // v465-v485: only engineVersion
            putString(engineVersion);
        }
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public CompoundTag getData() {
        return data;
    }

    public void setData(CompoundTag data) {
        this.data = data;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }
}
