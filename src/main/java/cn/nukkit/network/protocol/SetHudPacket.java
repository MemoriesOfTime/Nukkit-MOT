package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.HudElement;
import cn.nukkit.network.protocol.types.HudVisibility;
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

    private final Set<HudElement> elements = new ObjectOpenHashSet<>();
    private HudVisibility visibility;

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void decode() {
        this.elements.clear();
        this.getArray(this.elements, value -> HudElement.values()[(int) this.getUnsignedVarInt()]);
        this.visibility = HudVisibility.values()[this.getByte()];
    }

    @Override
    public void encode() {
        this.reset();
        this.putArray(this.elements, (buf, element) -> this.putUnsignedVarInt(element.ordinal()));
        this.putByte((byte) this.visibility.ordinal());
    }
}
