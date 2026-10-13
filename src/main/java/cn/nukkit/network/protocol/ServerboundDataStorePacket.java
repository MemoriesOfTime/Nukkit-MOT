package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.datastore.DataStorePropertyType;
import cn.nukkit.network.protocol.types.datastore.DataStoreUpdate;

/**
 * Serverbound data store packet for data-driven UI.
 * This packet is sent from the client to the server to update data store properties.
 */
public class ServerboundDataStorePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SERVERBOUND_DATA_STORE_PACKET;

    private static final int MAX_KEY_CHARS = 1000;
    private static final int MAX_STRING_VALUE_CHARS = 5000;

    private DataStoreUpdate update;

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
        DataStoreUpdate update = new DataStoreUpdate();
        update.setDataStoreName(requireKey(this.getString(MAX_KEY_CHARS), "Data Store Name"));
        update.setProperty(requireKey(this.getString(MAX_KEY_CHARS), "Property"));
        update.setPath(this.getString(MAX_KEY_CHARS));
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
                String stringValue = this.getString(MAX_STRING_VALUE_CHARS);
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

    @Override
    public boolean isLevelSyncPacket() {
        return true;
    }

    public DataStoreUpdate getUpdate() {
        return update;
    }

    public void setUpdate(DataStoreUpdate update) {
        this.update = update;
    }

    private static String requireKey(String key, String field) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException(field + " MinLength is 1");
        }
        return key;
    }
}
