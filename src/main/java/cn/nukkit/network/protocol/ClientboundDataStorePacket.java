package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.datastore.DataStoreAction;
import cn.nukkit.network.protocol.types.datastore.DataStoreChange;
import cn.nukkit.network.protocol.types.datastore.DataStorePropertyValue;
import cn.nukkit.network.protocol.types.datastore.DataStorePropertyType;
import cn.nukkit.network.protocol.types.datastore.DataStoreRemoval;
import cn.nukkit.network.protocol.types.datastore.DataStoreUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClientboundDataStorePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_DATA_STORE_PACKET;

    private List<DataStoreAction> updates = new ArrayList<>();

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(updates.size());
        for (DataStoreAction action : updates) {
            this.putUnsignedVarInt(action.getType().ordinal());
            switch (action.getType()) {
                case UPDATE:
                    writeDataStoreUpdate((DataStoreUpdate) action);
                    break;
                case CHANGE:
                    writeDataStoreChange((DataStoreChange) action);
                    break;
                case REMOVAL:
                    writeDataStoreRemoval((DataStoreRemoval) action);
                    break;
            }
        }
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    public List<DataStoreAction> getUpdates() {
        return updates;
    }

    public void setUpdates(List<DataStoreAction> updates) {
        this.updates = updates;
    }

    // Helper methods for writing
    private void writeDataStoreUpdate(DataStoreUpdate update) {
        this.putString(update.getDataStoreName());
        this.putString(update.getProperty());
        this.putString(update.getPath());
        Object value = update.getData();
        DataStorePropertyType type = update.getPropertyType();

        if (type == null) {
            if (value instanceof Boolean) {
                type = DataStorePropertyType.BOOLEAN;
            } else if (value instanceof String) {
                type = DataStorePropertyType.STRING;
            } else {
                type = DataStorePropertyType.INT64;
            }
        }

        int control;
        switch (type) {
            case BOOLEAN:
                control = 1;
                break;
            case STRING:
                control = 2;
                break;
            case INT64:
            case OBJECT:
            default:
                control = 0;
                break;
        }

        this.putUnsignedVarInt(control);
        switch (control) {
            case 0:
                // 数字类型写入 double 的 IEEE 754 位表示（小端序）
                if (value instanceof Number n) {
                    this.putLLong(Double.doubleToRawLongBits(n.doubleValue()));
                } else {
                    throw new IllegalStateException("Invalid numeric data store update value: " + value);
                }
                break;
            case 1:
                this.putBoolean((boolean) value);
                break;
            case 2:
                this.putString((String) value);
                break;
            default:
                throw new IllegalStateException("Invalid data store update control: " + control);
        }
        this.putLInt(update.getPropertyUpdateCount());
        this.putLInt(update.getPathUpdateCount());
    }

    private void writeDataStoreChange(DataStoreChange change) {
        this.putString(change.getDataStoreName());
        this.putString(change.getProperty());
        this.putLInt(change.getUpdateCount());
        // newValue is Object, we need to write as DataStorePropertyValue
        // For simplicity, assume newValue is DataStorePropertyValue
        if (change.getNewValue() instanceof DataStorePropertyValue) {
            DataStorePropertyValue value = (DataStorePropertyValue) change.getNewValue();
            this.putLInt(value.getType().getId());
            writeDataStorePropertyValue(value.getValue(), convertPropertyValueToPropertyType(value.getType()));
        } else {
            // Fallback: write as string
            this.putLInt(DataStorePropertyValue.Type.STRING.getId());
            this.putString(String.valueOf(change.getNewValue()));
        }
    }

    private DataStorePropertyType convertPropertyValueToPropertyType(DataStorePropertyValue.Type valueType) {
        switch (valueType) {
            case BOOL:
                return DataStorePropertyType.BOOLEAN;
            case INT64:
                return DataStorePropertyType.INT64;
            case STRING:
                return DataStorePropertyType.STRING;
            case TYPE:
                return DataStorePropertyType.OBJECT;
            case NONE:
                // Not sure what to map NONE to, maybe throw or default to STRING
                throw new IllegalArgumentException("Cannot convert NONE to DataStorePropertyType");
            default:
                throw new IllegalArgumentException("Unknown DataStorePropertyValue.Type: " + valueType);
        }
    }

    private void writeDataStoreRemoval(DataStoreRemoval removal) {
        this.putString(removal.getDataStoreName());
    }

    private void writeDataStorePropertyValue(Object value, DataStorePropertyType propertyType) {
        DataStorePropertyValue.Type type = convertPropertyType(propertyType);
        switch (type) {
            case NONE:
                break;
            case BOOL:
                this.putBoolean((Boolean) value);
                break;
            case INT64:
                this.putLLong((Long) value);
                break;
            case STRING:
                this.putString((String) value);
                break;
            case TYPE:
                Map<String, DataStorePropertyValue> map = (Map<String, DataStorePropertyValue>) value;
                this.putUnsignedVarInt(map.size());
                for (Map.Entry<String, DataStorePropertyValue> entry : map.entrySet()) {
                    this.putString(entry.getKey());
                    this.putLInt(entry.getValue().getType().getId());
                    writeDataStorePropertyValue(entry.getValue().getValue(), convertPropertyValueToPropertyType(entry.getValue().getType()));
                }
                break;
        }
    }

    private DataStorePropertyValue.Type convertPropertyType(DataStorePropertyType propertyType) {
        switch (propertyType) {
            case BOOLEAN:
                return DataStorePropertyValue.Type.BOOL;
            case INT64:
                return DataStorePropertyValue.Type.INT64;
            case STRING:
                return DataStorePropertyValue.Type.STRING;
            case OBJECT:
                return DataStorePropertyValue.Type.TYPE;
            default:
                throw new IllegalArgumentException("Unknown DataStorePropertyType: " + propertyType);
        }
    }
}
