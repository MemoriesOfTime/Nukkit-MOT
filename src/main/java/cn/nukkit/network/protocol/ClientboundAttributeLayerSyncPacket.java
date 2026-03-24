package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.attributelayer.*;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Syncs attribute layers from server to client.
 *
 * @since v944
 */
@ToString
public class ClientboundAttributeLayerSyncPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_ATTRIBUTE_LAYER_SYNC_PACKET;

    private static final List<String> BOOL_OPERATIONS = Arrays.asList("override", "alpha_blend", "and", "nand", "or", "nor", "xor", "xnor");
    private static final List<String> FLOAT_OPERATIONS = Arrays.asList("override", "alpha_blend", "add", "subtract", "multiply", "minimum", "maximum");
    private static final List<String> COLOR_OPERATIONS = Arrays.asList("override", "alpha_blend", "add", "subtract", "multiply");

    public AttributeLayerSyncPayload data;

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
        int type = (int) this.getUnsignedVarInt();
        switch (type) {
            case 0:
                this.data = readUpdateAttributeLayers();
                return;
            case 1:
                this.data = readUpdateAttributeLayerSettings();
                return;
            case 2:
                this.data = readUpdateEnvironmentAttributes();
                return;
            case 3:
                this.data = readRemoveEnvironmentAttributes();
                return;
        }
        throw new IllegalArgumentException(type + " is not oneOf<UpdateAttributeLayersData, UpdateAttributeLayerSettingsData, UpdateEnvironmentAttributesData, RemoveEnvironmentAttributesData>");
    }

    @Override
    public void encode() {
        this.reset();
        if (this.data instanceof UpdateAttributeLayersData) {
            this.putUnsignedVarInt(0);
            writeUpdateAttributeLayers((UpdateAttributeLayersData) this.data);
        } else if (this.data instanceof UpdateAttributeLayerSettingsData) {
            this.putUnsignedVarInt(1);
            writeUpdateAttributeLayerSettings((UpdateAttributeLayerSettingsData) this.data);
        } else if (this.data instanceof UpdateEnvironmentAttributesData) {
            this.putUnsignedVarInt(2);
            writeUpdateEnvironmentAttributes((UpdateEnvironmentAttributesData) this.data);
        } else if (this.data instanceof RemoveEnvironmentAttributesData) {
            this.putUnsignedVarInt(3);
            writeRemoveEnvironmentAttributes((RemoveEnvironmentAttributesData) this.data);
        } else {
            throw new IllegalArgumentException("Not oneOf<UpdateAttributeLayersData, UpdateAttributeLayerSettingsData, UpdateEnvironmentAttributesData, RemoveEnvironmentAttributesData>");
        }
    }

    private void writeUpdateAttributeLayers(UpdateAttributeLayersData data) {
        this.putArray(data.attributeLayers, (layer) -> {
            this.putString(layer.layerName);
            this.putVarInt(layer.dimension);
            writeAttributeLayerSettings(layer.settings);
            this.putArray(layer.attributes, this::writeEnvironmentAttribute);
        });
    }

    private UpdateAttributeLayersData readUpdateAttributeLayers() {
        List<AttributeLayerData> layers = new ArrayList<>();
        this.getArray(layers, (s) -> {
            String name = s.getString();
            int dim = s.getVarInt();
            AttributeLayerSettings settings = readAttributeLayerSettings();
            List<EnvironmentAttributeData> attrs = new ArrayList<>();
            s.getArray(attrs, (s2) -> readEnvironmentAttribute());
            return new AttributeLayerData(name, dim, settings, attrs);
        });
        return new UpdateAttributeLayersData(layers);
    }

    private void writeUpdateAttributeLayerSettings(UpdateAttributeLayerSettingsData data) {
        this.putString(data.layerName);
        this.putVarInt(data.dimension);
        writeAttributeLayerSettings(data.settings);
    }

    private UpdateAttributeLayerSettingsData readUpdateAttributeLayerSettings() {
        String name = this.getString();
        int dim = this.getVarInt();
        AttributeLayerSettings settings = readAttributeLayerSettings();
        return new UpdateAttributeLayerSettingsData(name, dim, settings);
    }

    private void writeUpdateEnvironmentAttributes(UpdateEnvironmentAttributesData data) {
        this.putString(data.layerName);
        this.putVarInt(data.dimension);
        this.putArray(data.attributes, this::writeEnvironmentAttribute);
    }

    private UpdateEnvironmentAttributesData readUpdateEnvironmentAttributes() {
        String name = this.getString();
        int dim = this.getVarInt();
        List<EnvironmentAttributeData> attrs = new ArrayList<>();
        this.getArray(attrs, (s) -> readEnvironmentAttribute());
        return new UpdateEnvironmentAttributesData(name, dim, attrs);
    }

    private void writeRemoveEnvironmentAttributes(RemoveEnvironmentAttributesData data) {
        this.putString(data.layerName);
        this.putVarInt(data.dimension);
        this.putArray(data.attributes, this::putString);
    }

    private RemoveEnvironmentAttributesData readRemoveEnvironmentAttributes() {
        String name = this.getString();
        int dim = this.getVarInt();
        List<String> attrs = new ArrayList<>();
        this.getArray(attrs, (s) -> s.getString());
        return new RemoveEnvironmentAttributesData(name, dim, attrs);
    }

    private void writeAttributeLayerSettings(AttributeLayerSettings s) {
        this.putLInt(s.priority);
        writeWeight(s.weight);
        this.putBoolean(s.enabled);
        this.putBoolean(s.transitionsPaused);
    }

    private AttributeLayerSettings readAttributeLayerSettings() {
        int priority = this.getLInt();
        AttributeLayerSettings.Weight weight = readWeight();
        boolean enabled = this.getBoolean();
        boolean paused = this.getBoolean();
        return new AttributeLayerSettings(priority, weight, enabled, paused);
    }

    private void writeWeight(AttributeLayerSettings.Weight w) {
        if (w instanceof AttributeLayerSettings.FloatWeight) {
            this.putUnsignedVarInt(0);
            this.putLFloat(((AttributeLayerSettings.FloatWeight) w).value);
        } else if (w instanceof AttributeLayerSettings.StringWeight) {
            this.putUnsignedVarInt(1);
            this.putString(((AttributeLayerSettings.StringWeight) w).value);
        } else {
            throw new IllegalArgumentException("Unknown Weight: " + w);
        }
    }

    private AttributeLayerSettings.Weight readWeight() {
        int type = (int) this.getUnsignedVarInt();
        switch (type) {
            case 0:
                return new AttributeLayerSettings.FloatWeight(this.getLFloat());
            case 1:
                return new AttributeLayerSettings.StringWeight(this.getString());
        }
        throw new IllegalArgumentException("Unknown Weight type: " + type);
    }

    private void writeEnvironmentAttribute(EnvironmentAttributeData e) {
        this.putString(e.attributeName);
        this.putOptionalNull(e.from, this::writeAttributeData);
        writeAttributeData(e.attribute);
        this.putOptionalNull(e.to, this::writeAttributeData);
        this.putLInt(e.currentTransitionTicks);
        this.putLInt(e.totalTransitionTicks);
        this.putString(e.easing.getSerializeName());
    }

    private EnvironmentAttributeData readEnvironmentAttribute() {
        String name = this.getString();
        AttributeData from = this.getOptional(null, (s) -> readAttributeData());
        AttributeData attribute = readAttributeData();
        AttributeData to = this.getOptional(null, (s) -> readAttributeData());
        int currentTicks = this.getLInt();
        int totalTicks = this.getLInt();
        EnvironmentAttributeData.CameraEase easing = EnvironmentAttributeData.CameraEase.fromName(this.getString());
        return new EnvironmentAttributeData(name, from, attribute, to, currentTicks, totalTicks, easing);
    }

    private void writeAttributeData(AttributeData data) {
        if (data instanceof BoolAttributeData) {
            BoolAttributeData at = (BoolAttributeData) data;
            this.putUnsignedVarInt(0);
            this.putBoolean(at.value);
            this.putString(BOOL_OPERATIONS.get(at.operation.ordinal()));
        } else if (data instanceof FloatAttributeData) {
            FloatAttributeData at = (FloatAttributeData) data;
            this.putUnsignedVarInt(1);
            this.putLFloat(at.value);
            this.putString(FLOAT_OPERATIONS.get(at.operation.ordinal()));
            this.putOptionalNull(at.constraintMin, this::putLFloat);
            this.putOptionalNull(at.constraintMax, this::putLFloat);
        } else if (data instanceof ColorAttributeData) {
            ColorAttributeData at = (ColorAttributeData) data;
            this.putUnsignedVarInt(2);
            writeColor255(at.value);
            this.putString(COLOR_OPERATIONS.get(at.operation.ordinal()));
        } else {
            throw new IllegalArgumentException("Unknown AttributeData: " + data);
        }
    }

    private AttributeData readAttributeData() {
        int type = (int) this.getUnsignedVarInt();
        switch (type) {
            case 0:
                boolean value = this.getBoolean();
                String op = this.getString();
                return new BoolAttributeData(value, BoolAttributeData.Operation.values()[BOOL_OPERATIONS.indexOf(op)]);
            case 1:
                float fValue = this.getLFloat();
                String fOp = this.getString();
                Float constraintMin = this.getOptional(null, (s) -> s.getLFloat());
                Float constraintMax = this.getOptional(null, (s) -> s.getLFloat());
                return new FloatAttributeData(fValue, FloatAttributeData.Operation.values()[FLOAT_OPERATIONS.indexOf(fOp)], constraintMin, constraintMax);
            case 2:
                ColorAttributeData.Color255RGBA color = readColor255();
                String cOp = this.getString();
                return new ColorAttributeData(color, ColorAttributeData.Operation.values()[COLOR_OPERATIONS.indexOf(cOp)]);
        }
        throw new IllegalArgumentException("Unknown AttributeData type: " + type);
    }

    private void writeColor255(ColorAttributeData.Color255RGBA c) {
        if (c instanceof ColorAttributeData.StringColor) {
            this.putUnsignedVarInt(0);
            this.putString(((ColorAttributeData.StringColor) c).value);
        } else if (c instanceof ColorAttributeData.ArrayColor) {
            this.putUnsignedVarInt(1);
            int[] v = ((ColorAttributeData.ArrayColor) c).value;
            for (int i = 0; i < 4; i++) {
                this.putLInt(v[i]);
            }
        } else {
            throw new IllegalArgumentException("Unknown Color255RGBA: " + c);
        }
    }

    private ColorAttributeData.Color255RGBA readColor255() {
        int type = (int) this.getUnsignedVarInt();
        switch (type) {
            case 0:
                return new ColorAttributeData.StringColor(this.getString());
            case 1:
                int[] v = new int[4];
                for (int i = 0; i < 4; i++) {
                    v[i] = this.getLInt();
                }
                return new ColorAttributeData.ArrayColor(v);
        }
        throw new IllegalArgumentException("Unknown Color255RGBA type: " + type);
    }
}
