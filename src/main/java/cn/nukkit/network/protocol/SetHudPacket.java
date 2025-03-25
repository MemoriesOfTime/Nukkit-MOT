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
        this.getArray(this.elements, value -> HudElement.values()[(int) this.getUnsignedVarInt()]);
        if (this.protocol >= ProtocolInfo.v1_21_70) {
            this.visibility = HudVisibility.values()[this.getInt()];
        } else {
            this.visibility = HudVisibility.values()[this.getByte()];
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putArray(this.elements, (buf, element) -> this.putUnsignedVarInt(element.ordinal()));
        if (this.protocol >= ProtocolInfo.v1_21_70) {
            this.putInt(this.visibility.ordinal());
        } else {
            this.putByte((byte) this.visibility.ordinal());
        }
    }
}
