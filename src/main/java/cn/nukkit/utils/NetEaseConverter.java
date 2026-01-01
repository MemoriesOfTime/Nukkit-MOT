package cn.nukkit.utils;

import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

/**
 * 网易版本资源转换工具
 * 负责将标准版本的方块和物品资源转换为网易版本
 */
@Log4j2
@UtilityClass
public class NetEaseConverter {

    // 需要移除的方块ID列表
    private static final String[] REMOVED_BLOCKS = {
        "minecraft:chalkboard"
    };

    // 需要修改的方块（设置自定义外观）
    private static final String[] CUSTOM_APPEARANCE_BLOCKS = {
        "minecraft:crafting_table",
        "minecraft:furnace",
        "minecraft:lit_furnace"
    };

    // NetEase特有方块
    private static final int MICRO_BLOCK_ID = 9990;
    private static final String MICRO_BLOCK_NAME = "minecraft:micro_block";

    /**
     * 转换方块状态列表为网易版本
     * @param blockStates 标准版本的方块状态列表
     * @param customAppearance 是否启用自定义外观
     * @return 转换后的网易版本方块状态列表
     */
    public static ListTag<CompoundTag> convertBlockStates(ListTag<CompoundTag> blockStates, boolean customAppearance) {
        ListTag<CompoundTag> result = new ListTag<>();

        // 遍历所有方块状态
        for (CompoundTag state : blockStates.getAll()) {
            CompoundTag blockTag = state.getCompound("block");
            if (blockTag == null) {
                // 没有block字段，直接添加
                result.add(state);
                continue;
            }

            String name = blockTag.getString("name");

            // 检查是否需要移除
            if (shouldRemoveBlock(name)) {
                log.debug("Removing block for NetEase: {}", name);
                continue;
            }

            // 检查是否需要设置自定义外观
            if (customAppearance && shouldSetCustomAppearance(name)) {
                log.debug("Setting custom appearance for NetEase block: {}", name);
                CompoundTag states = blockTag.getCompound("states");
                if (states != null) {
                    states.putByte("custom_appearance", (byte) 1);
                }
            }

            result.add(state);
        }

        // 添加网易特有方块（micro_block）
        if (customAppearance) {
            result.add(createMicroBlockState());
        }

        return result;
    }

    /**
     * 转换物品状态JSON为网易版本
     * @param itemStates 标准版本的物品状态JSON数组
     * @return 转换后的网易版本物品状态JSON数组
     */
    public static JsonArray convertItemStates(JsonArray itemStates) {
        JsonArray result = new JsonArray();

        // 遍历所有物品
        for (JsonElement element : itemStates) {
            if (!element.isJsonObject()) {
                result.add(element);
                continue;
            }

            JsonObject item = element.getAsJsonObject();
            String name = item.has("name") ? item.get("name").getAsString() : "";

            // 检查是否需要移除
            if (shouldRemoveBlock(name)) {
                log.debug("Removing item for NetEase: {}", name);
                continue;
            }

            result.add(item);
        }

        // 可以在这里添加网易特有物品
        // result.add(createMicroBlockItem());

        return result;
    }

    /**
     * 转换创造模式物品列表为网易版本
     * @param creativeItems 标准版本的创造模式物品JSON数组
     * @return 转换后的网易版本创造模式物品JSON数组
     */
    public static JsonArray convertCreativeItems(JsonArray creativeItems) {
        JsonArray result = new JsonArray();

        // 遍历所有创造模式物品
        for (JsonElement element : creativeItems) {
            if (!element.isJsonObject()) {
                result.add(element);
                continue;
            }

            JsonObject item = element.getAsJsonObject();

            // 检查是否有id字段
            if (item.has("id")) {
                String id = item.get("id").getAsString();

                // 检查是否需要移除
                if (shouldRemoveBlock(id)) {
                    log.debug("Removing creative item for NetEase: {}", id);
                    continue;
                }
            }

            result.add(item);
        }

        return result;
    }

    /**
     * 检查方块是否需要被移除
     */
    private static boolean shouldRemoveBlock(String blockName) {
        if (blockName == null || blockName.isEmpty()) {
            return false;
        }

        for (String removed : REMOVED_BLOCKS) {
            if (removed.equals(blockName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查方块是否需要设置自定义外观
     */
    private static boolean shouldSetCustomAppearance(String blockName) {
        if (blockName == null || blockName.isEmpty()) {
            return false;
        }

        for (String block : CUSTOM_APPEARANCE_BLOCKS) {
            if (block.equals(blockName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建微方块（micro_block）的方块状态
     */
    private static CompoundTag createMicroBlockState() {
        CompoundTag state = new CompoundTag();

        // 设置runtimeId（需要使用一个不冲突的ID）
        // 这里使用一个较大的数值，具体值可能需要调整
        state.putInt("runtimeId", 10000);
        state.putInt("id", MICRO_BLOCK_ID);
        state.putShort("data", 0);

        // 创建block标签
        CompoundTag blockTag = new CompoundTag();
        blockTag.putString("name", MICRO_BLOCK_NAME);
        blockTag.putInt("version", 18090528); // 使用常见的版本号

        // 创建states标签（空的状态）
        CompoundTag statesTag = new CompoundTag();
        blockTag.putCompound("states", statesTag);

        state.putCompound("block", blockTag);

        return state;
    }
}
