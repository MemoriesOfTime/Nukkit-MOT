package cn.nukkit.network.protocol;

import cn.nukkit.entity.Attribute;
import lombok.ToString;

/**
 * @author Nukkit Project Team
 */
@ToString
public class UpdateAttributesPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.UPDATE_ATTRIBUTES_PACKET;

    public Attribute[] entries;
    public long entityId;
    public long frame;

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

        this.putEntityRuntimeId(this.entityId);

        if (this.entries == null) {
            this.putUnsignedVarInt(0);
        } else {
            this.putUnsignedVarInt(this.entries.length);
            for (Attribute entry : this.entries) {
                this.putLFloat(entry.getMinValue());
                this.putLFloat(entry.getMaxValue());
                this.putLFloat(entry.getValue());
                this.putLFloat(entry.getDefaultValue());
                this.putString(entry.getName());
                if (protocol >= ProtocolInfo.v1_19_20) {
                    this.putUnsignedVarInt(0); // Modifiers
                }
            }
        }

        if (protocol >= ProtocolInfo.v1_16_100) {
            this.putUnsignedVarInt(this.frame);
        }
    }
}
