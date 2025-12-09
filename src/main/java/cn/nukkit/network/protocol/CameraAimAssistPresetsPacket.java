package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.camera.*;
import cn.nukkit.utils.BinaryStream;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @since v766
 */
public class CameraAimAssistPresetsPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CAMERA_AIM_ASSIST_PRESETS_PACKET;

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    private final List<CameraAimAssistCategories> categories = new ObjectArrayList<>();
    private final List<CameraAimAssistPresetDefinition> presets = new ObjectArrayList<>();

    @Override
    public void decode() {
        this.categories.addAll(List.of(this.getArray(CameraAimAssistCategories.class, binaryStream -> this.readCategories())));
        this.presets.addAll(List.of(this.getArray(CameraAimAssistPresetDefinition.class, binaryStream -> this.readPreset())));
    }

    @Override
    public void encode() {
        this.putArray(categories, this::writeCategories);
        this.putArray(presets, this::writeCameraAimAssist);
    }

    private void writeCategories(CameraAimAssistCategories categories) {
        this.putString(categories.getIdentifier());
        this.putArray(categories.getCategories(), this::writeCategory);
    }

    private void writeCategory(CameraAimAssistCategory category) {
        this.putString(category.getName());
        this.putArray(category.getEntityPriorities(), this::writePriority);
        this.putArray(category.getBlockPriorities(), this::writePriority);
        if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            if (category.getEntityDefaultPriorities() != null) {
                this.putInt(category.getEntityDefaultPriorities());
            }
            if (category.getBlockDefaultPriorities() != null) {
                this.putInt(category.getBlockDefaultPriorities());
            }
        }
    }

    private void writePriority(CameraAimAssistPriority priority) {
        this.putString(priority.getName());
        this.putInt(priority.getPriority());
    }

    private void writeCameraAimAssist(CameraAimAssistPresetDefinition preset) {
        this.putString(preset.getIdentifier());
        if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            this.putArray(preset.getExclusionList(), BinaryStream::putString);
            this.putArray(preset.getBlockTagExclusionList(), BinaryStream::putString);
        }
        this.putString(preset.getCategories()); // todo: multi-version support
        this.putArray(preset.getExclusionList(), this::putString);
        this.putArray(preset.getLiquidTargetingList(), this::putString);
        this.putArray(preset.getItemSettings(), this::writeItemSetting);
        this.putOptional(Objects::nonNull, preset.getDefaultItemSettings(), this::putString);
        this.putOptional(Objects::nonNull, preset.getHandSettings(), this::putString);
    }

    private void writeItemSetting(CameraAimAssistItemSettings cameraAimAssistItemSetting) {
        this.putString(cameraAimAssistItemSetting.getItemId());
        this.putString(cameraAimAssistItemSetting.getCategory());
    }

    // READ
    public CameraAimAssistCategories readCategories() {
        CameraAimAssistCategories categories = new CameraAimAssistCategories();
        categories.setIdentifier(this.getString());
        long categoryLength = this.getUnsignedVarInt();
        for(int i = 0; i < categoryLength; i++) {
            categories.getCategories().add(readCategory());
        }
        return categories;
    }

    public CameraAimAssistCategory readCategory() {
        CameraAimAssistCategory category = new CameraAimAssistCategory();
        category.setName(this.getString());
        long entityPriorityLength = this.getUnsignedVarInt();
        for(int i = 0; i < entityPriorityLength; i++) {
            Map.Entry<String, Integer> entry = readPriority();
            category.getEntityPriorities().add(new CameraAimAssistPriority(entry.getKey(), entry.getValue()));
        }
        long blockPriorityLength = this.getUnsignedVarInt();
        for(int i = 0; i < blockPriorityLength; i++) {
            Map.Entry<String, Integer> entry = readPriority();
            category.getBlockPriorities().add(new CameraAimAssistPriority(entry.getKey(), entry.getValue()));
        }
        if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            long blockTagPriorityLength = this.getUnsignedVarInt();
            for(int i = 0; i < blockTagPriorityLength; i++) {
                Map.Entry<String, Integer> entry = readPriority();
                category.getBlocktags().put(entry.getKey(), entry.getValue());
            }
            Integer i1 = this.getOptional(null, BinaryStream::getInt);
            if (i1 != null) {
                category.setEntityDefaultPriorities(i1);
            }
            Integer i2 = this.getOptional(null, BinaryStream::getInt);
            if (i2 != null) {
                category.setBlockDefaultPriorities(this.getInt());
            }
        }
        return category;
    }

    private Map.Entry<String, Integer> readPriority() {
        return Map.entry(this.getString(), this.getInt());
    }

    private CameraAimAssistPresetDefinition readPreset() {
        CameraAimAssistPresetDefinition preset = new CameraAimAssistPresetDefinition();
        preset.setIdentifier(this.getString());
        if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            preset.getExclusionList().addAll(List.of(this.getArray(String.class, BinaryStream::getString)));
            preset.getExclusionList().addAll(List.of(this.getArray(String.class, BinaryStream::getString)));
            preset.getBlockTagExclusionList().addAll(List.of(this.getArray(String.class, BinaryStream::getString)));
        }
        preset.setCategories(this.getString());
        preset.getExclusionList().addAll(List.of(this.getArray(String.class, BinaryStream::getString)));
        preset.getLiquidTargetingList().addAll(List.of(this.getArray(String.class, BinaryStream::getString)));
        long itemSettingsLength = this.getUnsignedVarInt();
        for(int i = 0; i < itemSettingsLength; i++) {
            Map.Entry<String, String> entry = readItemSetting();
            preset.getItemSettings().add(new CameraAimAssistItemSettings(entry.getKey(), entry.getValue()));
        }
        preset.setDefaultItemSettings(this.getOptional(null, BinaryStream::getString));
        preset.setHandSettings(this.getOptional(null, BinaryStream::getString));
        return preset;
    }
    private Map.Entry<String, String> readItemSetting() {
        return Map.entry(this.getString(), this.getString());
    }
}