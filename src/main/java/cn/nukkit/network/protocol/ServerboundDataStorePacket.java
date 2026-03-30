package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.datastore.DataStorePropertyType;
import cn.nukkit.network.protocol.types.datastore.DataStoreUpdate;

/**
 * Serverbound data store packet for data-driven UI.
 * This packet is sent from the client to the server to update data store properties.
 */
public class ServerboundDataStorePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SERVERBOUND_DATA_STORE_PACKET;

    private DataStoreUpdate update;

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
        DataStoreUpdate update = new DataStoreUpdate();
        update.setDataStoreName(this.getString());
        update.setProperty(this.getString());
        update.setPath(this.getString());
        int control = (int) this.getUnsignedVarInt();
        switch (control) {
            case 0:
                // 数字类型读取 double 的 IEEE 754 位表示，转换为 double
                long bits = this.getLLong();
                double numericValue = Double.longBitsToDouble(bits);
                update.setData(numericValue);
                update.setType(DataStorePropertyType.INT64);
                break;
            case 1:
                boolean boolValue = this.getBoolean();
                update.setData(boolValue);
                update.setType(DataStorePropertyType.BOOLEAN);
                break;
            case 2:
                String stringValue = this.getString();
                update.setData(stringValue);
                update.setType(DataStorePropertyType.STRING);
                break;
            default:
                throw new IllegalStateException("Invalid data store update control: " + control);
        }
        update.setPropertyUpdateCount(this.getLInt());
        if (this.protocol >= ProtocolInfo.v1_26_0) {
            update.setPathUpdateCount(this.getLInt());
        } else {
            update.setPathUpdateCount(0);
        }
        this.update = update;
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    public DataStoreUpdate getUpdate() {
        return update;
    }

    public void setUpdate(DataStoreUpdate update) {
        this.update = update;
    }
}
