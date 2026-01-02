package cn.nukkit.utils;

import cn.nukkit.block.custom.comparator.HashedPaletteComparator;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // NetEase特有方块
    private static final int MICRO_BLOCK_ID = 9990;
    private static final String MICRO_BLOCK_NAME = "minecraft:micro_block";

    /**
     * 转换方块状态列表为网易版本
     * @param blockStates 标准版本的方块状态列表
     * @param customAppearance 是否启用自定义外观（当前未使用，保留以备将来扩展）
     * @return 转换后的网易版本方块状态列表
     */
    public static ListTag<CompoundTag> convertBlockStates(ListTag<CompoundTag> blockStates, boolean customAppearance) {
        List<CompoundTag> filteredStates = new ObjectArrayList<>();
        int removedCount = 0;
        int addedCount = 0;

        for (CompoundTag state : blockStates.getAll()) {
            String name = state.getString("name");

            if (shouldRemoveBlock(name)) {
                log.debug("Removing {} for NetEase", name);
                removedCount++;
                continue;
            }

            filteredStates.add(state);
        }

        CompoundTag microBlock = createMicroBlockState();
        filteredStates.add(microBlock);
        addedCount++;
        log.debug("Added micro_block for NetEase");


        Map<String, List<CompoundTag>> groupedByName = new LinkedHashMap<>();
        for (CompoundTag state : filteredStates) {
            String name = state.getString("name");
            groupedByName.computeIfAbsent(name, k -> new ArrayList<>()).add(state);
        }

        List<String> sortedNames = new ArrayList<>(groupedByName.keySet());
        sortedNames.sort(HashedPaletteComparator.INSTANCE);

        ListTag<CompoundTag> result = new ListTag<>();
        int runtimeId = 0;
        for (String name : sortedNames) {
            List<CompoundTag> blocks = groupedByName.get(name);
            for (CompoundTag state : blocks) {
                CompoundTag newState = state.copy();
                newState.putInt("runtimeId", runtimeId++);
                result.add(newState);
            }
        }

        log.info("NetEase conversion: {} blocks total, {} removed, {} added, sorted by hash",
                 result.size(), removedCount, addedCount);

        return result;
    }

    /**
     * 转换物品状态JSON为网易版本
     * @param itemStates 标准版本的物品状态JSON数组
     * @return 转换后的网易版本物品状态JSON数组
     */
    public static JsonArray convertItemStates(JsonArray itemStates) {
        JsonArray result = new JsonArray();

        for (JsonElement element : itemStates) {
            if (!element.isJsonObject()) {
                result.add(element);
                continue;
            }

            JsonObject item = element.getAsJsonObject();
            String name = item.has("name") ? item.get("name").getAsString() : "";

            if (shouldRemoveBlock(name)) {
                log.debug("Removing item for NetEase: {}", name);
                continue;
            }

            result.add(item);
        }

        return result;
    }

    /**
     * 转换创造模式物品列表为网易版本
     * @param creativeItems 标准版本的创造模式物品JSON数组
     * @return 转换后的网易版本创造模式物品JSON数组
     */
    public static JsonArray convertCreativeItems(JsonArray creativeItems) {
        JsonArray result = new JsonArray();

        for (JsonElement element : creativeItems) {
            if (!element.isJsonObject()) {
                result.add(element);
                continue;
            }

            JsonObject item = element.getAsJsonObject();

            if (item.has("id")) {
                String id = item.get("id").getAsString();
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
     * 创建微方块（micro_block）的方块状态
     * 使用1.21.50的直接格式（name直接在state层级）
     * 注意：runtimeId会在后续步骤中统一重新分配
     */
    private static CompoundTag createMicroBlockState() {
        CompoundTag state = new CompoundTag();

        state.putString("name", MICRO_BLOCK_NAME);
        state.putInt("id", MICRO_BLOCK_ID);
        state.putShort("data", 0);
        state.putInt("runtimeId", 0);
        state.putInt("version", 18163713); // 使用1.21.50的版本号
        state.putBoolean("stateOverload", false);
        state.putCompound("states", new CompoundTag());

        return state;
    }
}
