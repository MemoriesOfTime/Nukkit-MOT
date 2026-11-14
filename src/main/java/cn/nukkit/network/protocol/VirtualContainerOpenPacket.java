package cn.nukkit.network.protocol;

import cn.nukkit.item.Item;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;

import java.util.List;

/**
 * 打开一个只存在于内存中的虚拟容器界面，初始槽位随包下发。
 * <p>
 * Opens a screen onto an in-memory virtual container; the initial slots are carried by this packet.
 *
 * @since v2250 1.26.70.23
 */
@ToString
public class VirtualContainerOpenPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.VIRTUAL_CONTAINER_OPEN_PACKET;

    /**
     * 界面窗口 ID，后续库存包使用同一 id。
     * <p>
     * Screen window id; later inventory packets use the same id.
     */
    public int containerId;
    /**
     * 连接内唯一的打开会话 ID，非零。
     * <p>
     * Connection-scoped opening session id, must be nonzero.
     */
    public long sessionId;
    public String title = "";
    /**
     * 全部初始槽位（含空格），数组长度即容器容量（1~54）。
     * <p>
     * Every initial slot including empty ones; the array length is the capacity (1-54).
     */
    public final List<Item> slots = new ObjectArrayList<>();

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        this.putByte((byte) this.containerId);
        this.putUnsignedVarLong(this.sessionId);
        this.putString(this.title);
        this.putUnsignedVarInt(this.slots.size());
        for (Item slot : this.slots) {
            this.putNetworkItemStackDescriptor(this.gameVersion, slot);
        }
    }
}
