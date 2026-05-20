package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.datastore.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientboundDataStorePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_DATA_STORE_PACKET;

    private static final int TYPE_NONE = 0;
    private static final int TYPE_BOOL = 1;
    private static final int TYPE_INT64 = 2;
    private static final int TYPE_STRING = 4;
    private static final int TYPE_OBJECT = 6;

    private List<DataStoreAction> updates = new ArrayList<>();

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
        long size = this.getUnsignedVarInt();
        this.updates = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            final DataStoreAction.Type action = DataStoreAction.Type.from((int) this.getUnsignedVarInt());
            switch (action) {
                case UPDATE -> this.updates.add(readDataStoreUpdate());
                case CHANGE -> this.updates.add(readDataStoreChange());
                case REMOVAL -> this.updates.add(readDataStoreRemoval());
            }
        }
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

    protected DataStoreRemoval readDataStoreRemoval() {
        final DataStoreRemoval removal = new DataStoreRemoval();
        removal.setDataStoreName(this.getString());
        return removal;
    }

    public DataStoreUpdate readDataStoreUpdate() {
        final DataStoreUpdate update = new DataStoreUpdate();
        update.setDataStoreName(this.getString());
        update.setProperty(this.getString());
        update.setPath(this.getString());
        int control = (int) this.getUnsignedVarInt();
        switch (control) {
            case 0:
                long bits = this.getLLong();
                update.setData(Double.longBitsToDouble(bits));
                update.setType(DataStorePropertyType.INT64);
                break;
            case 1:
                update.setData(this.getBoolean());
                update.setType(DataStorePropertyType.BOOLEAN);
                break;
            case 2:
                update.setData(this.getString());
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
        return update;
    }

    protected DataStoreChange readDataStoreChange() {
        DataStoreChange change = new DataStoreChange();
        change.setDataStoreName(this.getString());
        change.setProperty(this.getString());
        change.setUpdateCount(this.getLInt());
        final DataStorePropertyType valueType = DataStorePropertyType.from(this.getLInt());
        change.setNewValue(this.readTheNewPropertyValue(valueType));
        return change;
    }

    protected Object readTheNewPropertyValue(DataStorePropertyType type) {
        switch (type) {
            case BOOLEAN:
                return this.getBoolean();
            case INT64:
                return this.getLLong();
            case STRING:
                return this.getString();
            case OBJECT:
                final int length = (int) this.getUnsignedVarInt();
                final Map<String, Object> map = new HashMap<>();
                for (int i = 0; i < length; i++) {
                    final String key = this.getString();
                    final DataStorePropertyType valueType = DataStorePropertyType.from(this.getLInt());
                    map.put(key, this.readTheNewPropertyValue(valueType));
                }
                return map;
            default:
                throw new IllegalStateException("Read invalid DataStorePropertyValueType");
        }
    }

    public void setUpdates(List<DataStoreAction> updates) {
        this.updates = updates;
    }

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
        if (this.protocol >= ProtocolInfo.v1_26_0) {
            this.putLInt(update.getPathUpdateCount());
        }
    }

    private void writeDataStoreChange(DataStoreChange change) {
        this.putString(change.getDataStoreName());
        this.putString(change.getProperty());
        this.putLInt(change.getUpdateCount());
        writeChangeValue(change.getNewValue());
    }

    private void writeChangeValue(Object value) {
        if (value == null) {
            // no-op
        } else if (value instanceof Boolean b) {
            this.putLInt(TYPE_BOOL);
            this.putBoolean(b);
        } else if (value instanceof Number n) {
            this.putLInt(TYPE_INT64);
            this.putLLong(n.longValue());
        } else if (value instanceof String s) {
            this.putLInt(TYPE_STRING);
            this.putString(s);
        } else if (value instanceof Map<?, ?> map) {
            this.putLInt(TYPE_OBJECT);
            this.putUnsignedVarInt(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                this.putString((String) entry.getKey());
                writeChangeValue(entry.getValue());
            }
        }
    }

    private void writeDataStoreRemoval(DataStoreRemoval removal) {
        this.putString(removal.getDataStoreName());
    }
}