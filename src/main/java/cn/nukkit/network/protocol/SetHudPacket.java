package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.hub.HudElement;
import cn.nukkit.network.protocol.types.hub.HudVisibility;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Set;

/**
 * @since v649
 */
@Setter
@Getter
@ToString(doNotUseGetters = true)
public class SetHudPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SET_HUD_PACKET;

    public final Set<HudElement> elements = new ObjectOpenHashSet<>();
    public HudVisibility visibility;

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        return 0;
    }

    @Override
    public void decode() {
        this.elements.clear();
        if (this.protocol >= ProtocolInfo.v1_21_70_24) {
            this.getArray(this.elements, value -> HudElement.values()[(int) this.getVarInt()]);
            this.visibility = HudVisibility.values()[this.getInt()];
        } else {
            this.getArray(this.elements, value -> HudElement.values()[(int) this.getUnsignedVarInt()]);
            this.visibility = HudVisibility.values()[this.getByte()];
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_21_70_24) {
            this.putArray(this.elements, (buf, element) -> this.putVarInt(element.ordinal()));
            this.putInt(this.visibility.ordinal());
        } else {
            this.putArray(this.elements, (buf, element) -> this.putUnsignedVarInt(element.ordinal()));
            this.putByte((byte) this.visibility.ordinal());
        }
    }
}
