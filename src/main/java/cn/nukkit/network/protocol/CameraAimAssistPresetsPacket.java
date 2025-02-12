package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.camera.aimassist.CameraAimAssistCategories;
import cn.nukkit.network.protocol.types.camera.aimassist.CameraAimAssistCategory;
import cn.nukkit.network.protocol.types.camera.aimassist.CameraAimAssistCategoryPriorities;
import cn.nukkit.network.protocol.types.camera.aimassist.CameraAimAssistPreset;
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
    private final List<CameraAimAssistPreset> presets = new ObjectArrayList<>();

    @Override
    public void decode() {
        this.categories.addAll(List.of(this.getArray(CameraAimAssistCategories.class, binaryStream -> this.readCategories())));
        this.presets.addAll(List.of(this.getArray(CameraAimAssistPreset.class, binaryStream -> this.readPreset())));
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
        writePriorities(category.getPriorities());
    }

    private void writePriorities(CameraAimAssistCategoryPriorities priorities) {
        this.putArray(priorities.entities.entrySet(), this::writePriority);
        this.putArray(priorities.blocks.entrySet(), this::writePriority);
    }

    private void writePriority(Map.Entry<String, Integer> priority) {
        this.putString(priority.getKey());
        this.putInt(priority.getValue());
    }

    private void writeCameraAimAssist(CameraAimAssistPreset preset) {
        this.putString(preset.getIdentifier());
        this.putString(preset.getCategories());
        this.putArray(preset.getExclusionList(), this::putString);
        this.putArray(preset.getLiquidTargetingList(), this::putString);
        this.putArray(preset.getItemSettings().entrySet(), this::writeItemSetting);
        this.putOptional(Objects::nonNull, preset.getDefaultItemSettings(), this::putString);
        this.putOptional(Objects::nonNull, preset.getHandSettings(), this::putString);
    }

    private void writeItemSetting(Map.Entry<String, String> itemSetting) {
        this.putString(itemSetting.getKey());
        this.putString(itemSetting.getValue());
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
        category.setPriorities(readPriorities());
        return category;
    }

    public CameraAimAssistCategoryPriorities readPriorities() {
        CameraAimAssistCategoryPriorities priorities = new CameraAimAssistCategoryPriorities();
        long entityPriorityLength = this.getUnsignedVarInt();
        for(int i = 0; i < entityPriorityLength; i++) {
            Map.Entry<String, Integer> entry = readPriority();
            priorities.getEntities().put(entry.getKey(), entry.getValue());
        }
        long blockPriorityLength = this.getUnsignedVarInt();
        for(int i = 0; i < blockPriorityLength; i++) {
            Map.Entry<String, Integer> entry = readPriority();
            priorities.getBlocks().put(entry.getKey(), entry.getValue());
        }
        return priorities;
    }

    private Map.Entry<String, Integer> readPriority() {
        return Map.entry(this.getString(), this.getInt());
    }

    private CameraAimAssistPreset readPreset() {
        CameraAimAssistPreset preset = new CameraAimAssistPreset();
        preset.setIdentifier(this.getString());
        preset.setCategories(this.getString());
        preset.getExclusionList().addAll(List.of(this.getArray(String.class, BinaryStream::getString)));
        preset.getLiquidTargetingList().addAll(List.of(this.getArray(String.class, BinaryStream::getString)));
        long itemSettingsLength = this.getUnsignedVarInt();
        for(int i = 0; i < itemSettingsLength; i++) {
            Map.Entry<String, String> entry = readItemSetting();
            preset.getItemSettings().put(entry.getKey(), entry.getValue());
        }
        preset.setDefaultItemSettings(this.getOptional(null, BinaryStream::getString));
        preset.setHandSettings(this.getOptional(null, BinaryStream::getString));
        return preset;
    }
    private Map.Entry<String, String> readItemSetting() {
        return Map.entry(this.getString(), this.getString());
    }
}