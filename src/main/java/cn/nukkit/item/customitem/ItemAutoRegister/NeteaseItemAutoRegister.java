package cn.nukkit.item.customitem.ItemAutoRegister;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.item.Item;
import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.resourcepacks.ZippedBehaviourPack;
import cn.nukkit.resourcepacks.ZippedResourcePack;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Log4j2
public class NeteaseItemAutoRegister {

    private static final String NETEASE_ITEMS_BEH_PATH = "netease_items_beh/";
    private static final String NETEASE_ITEMS_RES_PATH = "netease_items_res/";
    private final Gson gson = new Gson();
    private final JsonParser jsonParser = new JsonParser();

    /**
     * 扫描所有behavior packs，查找包含netease_items_beh的包
     * 并自动注册其中的物品
     */
    public void scanAndRegisterNeteaseItems() {
        log.info("开始扫描behavior packs中的netease_items_beh并自动注册物品...");

        // 获取匹配的物品定义
        Map<String, ItemDefinitionPair> matchedItems = matchBehaviorAndResourceItems();

        if (matchedItems.isEmpty()) {
            log.warn("未找到任何可注册的netease物品");
            return;
        }

        log.info("找到 {} 个可注册的netease物品", matchedItems.size());

        int successCount = 0;
        int failCount = 0;

        // 遍历所有匹配的物品并注册
        for (Map.Entry<String, ItemDefinitionPair> entry : matchedItems.entrySet()) {
            String identifier = entry.getKey();
            ItemDefinitionPair itemPair = entry.getValue();

            try {
                if (registerSingleNeteaseItem(identifier, itemPair)) {
                    successCount++;
                    log.info("成功注册物品: {}", identifier);
                } else {
                    failCount++;
                    log.warn("注册物品失败: {}", identifier);
                }
            } catch (Exception e) {
                failCount++;
                log.error("注册物品 {} 时发生异常", identifier, e);
            }
        }

        log.info("物品注册完成 - 成功: {}, 失败: {}", successCount, failCount);
    }

    /**
     * 注册单个netease物品
     *
     * @param identifier 物品标识符
     * @param itemPair   物品定义对
     * @return 是否注册成功
     */
    private boolean registerSingleNeteaseItem(String identifier, ItemDefinitionPair itemPair) {
        try {
            // 从行为包定义中提取物品配置
            JsonObject behaviorDef = itemPair.getBehaviorDefinition();
            JsonObject resourceDef = itemPair.getResourceDefinition();

            // 解析物品配置
            Item.ItemConfig itemConfig = parseItemConfig(behaviorDef, resourceDef);

            // 生成纹理名称（从图标路径提取）
            String textureName = extractTextureName(itemPair.getIconPath());

            // 生成显示名称
            String displayName = extractDisplayName(behaviorDef, identifier);

            // 使用动态注册方法注册物品
            var result = Item.registerDynamicCustomItem(identifier, displayName, textureName, itemConfig, true);

            return result.ok();

        } catch (Exception e) {
            log.error("解析物品配置时发生错误: {}", identifier, e);
            return false;
        }
    }

    /**
     * 从JSON定义中解析物品配置
     */
    private Item.ItemConfig parseItemConfig(JsonObject behaviorDef, JsonObject resourceDef) {
        // 默认配置
        int maxStackSize = 64;
        int maxDurability = 0;
        int attackDamage = 1;
        int scaleOffset = 32;
        boolean isSword = false;
        boolean isTool = false;
        boolean allowOffHand = false;
        boolean handEquipped = false;
        boolean foil = false;
        String creativeCategory = "EQUIPMENT";
        String creativeGroup = "";
        boolean canDestroyInCreative = true;

        try {
            // 从行为包定义中解析配置
            JsonObject minecraftItem = behaviorDef.getAsJsonObject("minecraft:item");
            if (minecraftItem != null) {
                JsonObject components = minecraftItem.getAsJsonObject("components");
                if (components != null) {
                    // 解析最大堆叠数量
                    if (components.has("minecraft:max_stack_size")) {
                        maxStackSize = components.get("minecraft:max_stack_size").getAsInt();
                    }

                    // 解析耐久度
                    if (components.has("minecraft:durability")) {
                        JsonObject durability = components.getAsJsonObject("minecraft:durability");
                        if (durability.has("max_durability")) {
                            maxDurability = durability.get("max_durability").getAsInt();
                        }
                    }

                    // 解析攻击伤害
                    if (components.has("minecraft:weapon")) {
                        JsonObject weapon = components.getAsJsonObject("minecraft:weapon");
                        if (weapon.has("on_hurt_entity")) {
                            JsonObject onHurt = weapon.getAsJsonObject("on_hurt_entity");
                            if (onHurt.has("event") && onHurt.get("event").getAsString().contains("sword")) {
                                isSword = true;
                                attackDamage = 4; // 默认剑伤害
                            }
                        }
                    }

                    // 解析工具属性
                    if (components.has("minecraft:digger")) {
                        isTool = true;
                    }
                }


                  // category对网易客户端没用 实际都是资源包控制

//                // 解析创造模式分类（从description中查找）
//                JsonObject description = minecraftItem.getAsJsonObject("description");
//                if (description != null && description.has("category")) {
//                    String category = description.get("category").getAsString();
//                    creativeCategory = mapCreativeCategory(category);
//                }
            }

        } catch (Exception e) {
            log.warn("解析物品配置时发生错误，使用默认配置", e);
        }

        return new Item.ItemConfig(maxStackSize, maxDurability, attackDamage, scaleOffset, isSword, isTool, allowOffHand, handEquipped, foil, creativeCategory, creativeGroup, canDestroyInCreative);
    }

//    /**
//     * 映射创造模式分类
//     */
//    private String mapCreativeCategory(String category) {
//        if (category == null) return "EQUIPMENT";
//
//        switch (category.toLowerCase()) {
//            case "construction":
//                return "CONSTRUCTION";
//            case "equipment":
//                return "EQUIPMENT";
//            case "items":
//                return "ITEMS";
//            case "nature":
//                return "NATURE";
//            default:
//                return "EQUIPMENT";
//        }
//    }

    /**
     * 从图标路径提取纹理名称
     */
    private String extractTextureName(String iconPath) {
        if (iconPath == null || iconPath.isEmpty()) {
            return "default_item";
        }

        // 移除路径和扩展名，只保留文件名
        String fileName = iconPath;
        if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        if (fileName.contains("\\")) {
            fileName = fileName.substring(fileName.lastIndexOf("\\") + 1);
        }
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf("."));
        }

        return fileName;
    }

    /**
     * 提取显示名称
     */
    private String extractDisplayName(JsonObject behaviorDef, String identifier) {
        try {
            JsonObject minecraftItem = behaviorDef.getAsJsonObject("minecraft:item");
            if (minecraftItem != null && minecraftItem.has("description")) {
                JsonObject description = minecraftItem.getAsJsonObject("description");
                if (description.has("identifier")) {
                    String id = description.get("identifier").getAsString();
                    // 将identifier转换为友好的显示名称
                    return id.replace(":", "_").replace("minecraft_", "").replace("_", " ");
                }
            }
        } catch (Exception e) {
            log.debug("无法提取显示名称，使用默认名称", e);
        }

        // 使用identifier作为默认显示名称
        return identifier.replace(":", "_").replace("minecraft_", "").replace("_", " ");
    }

    /**
     * 扫描单个behavior pack
     */
    private void scanBehaviorPack(ResourcePack pack) {
        log.info("扫描behavior pack: {}", pack.getPackName());

        try {
            File packFile = getPackFile(pack);
            if (packFile == null || !packFile.exists()) {
                log.warn("无法获取pack文件: {}", pack.getPackName());
                return;
            }

            try (ZipFile zipFile = new ZipFile(packFile)) {
                // 检查是否包含netease_items_beh目录
                boolean hasNeteaseItemsBeh = zipFile.stream().anyMatch(entry -> entry.getName().startsWith(NETEASE_ITEMS_BEH_PATH) && !entry.isDirectory());

                if (!hasNeteaseItemsBeh) {
                    log.debug("Pack {} 不包含netease_items_beh目录", pack.getPackName());
                    return;
                }

                log.info("Pack {} 包含netease_items_beh，开始读取JSON文件...", pack.getPackName());

                // 读取netease_items_beh目录下的所有JSON文件
                Map<String, JsonObject> jsonFiles = readNeteaseItemsJson(zipFile);

                if (!jsonFiles.isEmpty()) {
                    log.info("从pack {} 中读取到 {} 个JSON文件", pack.getPackName(), jsonFiles.size());
                    outputJsonContents(pack.getPackName(), jsonFiles);
                } else {
                    log.warn("Pack {} 的netease_items_beh目录中未找到JSON文件", pack.getPackName());
                }
            }

        } catch (Exception e) {
            log.error("扫描behavior pack {} 时发生错误", pack.getPackName(), e);
        }
    }

    /**
     * 获取ResourcePack对应的文件
     */
    private File getPackFile(ResourcePack pack) {
        try {
            // 通过反射获取ZippedResourcePack的file字段
            if (pack instanceof ZippedResourcePack) {
                var field = ZippedResourcePack.class.getDeclaredField("file");
                field.setAccessible(true);
                return (File) field.get(pack);
            }
        } catch (Exception e) {
            log.error("无法获取pack文件", e);
        }
        return null;
    }

    /**
     * 读取netease_items_beh目录下的所有JSON文件
     */
    private Map<String, JsonObject> readNeteaseItemsJson(ZipFile zipFile) {
        Map<String, JsonObject> jsonFiles = new HashMap<>();

        zipFile.stream().filter(entry -> entry.getName().startsWith(NETEASE_ITEMS_BEH_PATH)).filter(entry -> !entry.isDirectory()).filter(entry -> entry.getName().toLowerCase().endsWith(".json")).forEach(entry -> {
            try {
                String fileName = entry.getName();
                log.debug("读取JSON文件: {}", fileName);

                try (InputStreamReader reader = new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8)) {

                    JsonObject jsonObject = jsonParser.parse(reader).getAsJsonObject();
                    jsonFiles.put(fileName, jsonObject);

                }
            } catch (Exception e) {
                log.error("读取JSON文件 {} 时发生错误", entry.getName(), e);
            }
        });

        return jsonFiles;
    }

    /**
     * 输出JSON文件内容
     */
    private void outputJsonContents(String packName, Map<String, JsonObject> jsonFiles) {
        log.info("========== Pack: {} ==========", packName);

        for (Map.Entry<String, JsonObject> entry : jsonFiles.entrySet()) {
            String fileName = entry.getKey();
            JsonObject jsonObject = entry.getValue();

            log.info("文件: {}", fileName);
            log.info("内容: {}", gson.toJson(jsonObject));
            log.info("----------------------------------------");

            // 如果JSON包含特定的物品信息，可以进一步解析
            analyzeItemJson(fileName, jsonObject);
        }

        log.info("========== 结束 Pack: {} ==========", packName);
    }

    /**
     * 分析物品JSON内容
     */
    private void analyzeItemJson(String fileName, JsonObject jsonObject) {
        try {
            // 检查是否是物品定义文件
            if (jsonObject.has("minecraft:item")) {
                JsonObject itemDef = jsonObject.getAsJsonObject("minecraft:item");

                if (itemDef.has("description")) {
                    JsonObject description = itemDef.getAsJsonObject("description");
                    String identifier = description.has("identifier") ? description.get("identifier").getAsString() : "未知";

                    log.info("发现物品定义 - 标识符: {}, 文件: {}", identifier, fileName);
                }

                // 输出组件信息
                if (itemDef.has("components")) {
                    JsonObject components = itemDef.getAsJsonObject("components");
                    log.info("物品组件: {}", components.keySet());
                }
            }

            // 检查是否是配方文件
            if (jsonObject.has("minecraft:recipe_shaped") || jsonObject.has("minecraft:recipe_shapeless")) {
                log.info("发现配方文件: {}", fileName);
            }

        } catch (Exception e) {
            log.error("分析JSON文件 {} 时发生错误", fileName, e);
        }
    }

    /**
     * 获取所有netease_items_beh的JSON内容作为Map
     */
    public Map<String, Map<String, JsonObject>> getAllNeteaseItemsAsMap() {
        Map<String, Map<String, JsonObject>> allItems = new HashMap<>();

        ResourcePack[] behaviorPacks = Server.getInstance().getResourcePackManager().getBehaviorStackIgnoreNetease(GameVersion.getLastVersion());

        for (ResourcePack pack : behaviorPacks) {
            if (pack instanceof ZippedBehaviourPack) {
                try {
                    File packFile = getPackFile(pack);
                    if (packFile != null && packFile.exists()) {
                        try (ZipFile zipFile = new ZipFile(packFile)) {
                            Map<String, JsonObject> jsonFiles = readNeteaseItemsJson(zipFile);
                            if (!jsonFiles.isEmpty()) {
                                allItems.put(pack.getPackName(), jsonFiles);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("读取pack {} 时发生错误", pack.getPackName(), e);
                }
            }
        }

        return allItems;
    }

    /**
     * 获取所有netease_items_res的JSON内容作为Map
     */
    public Map<String, Map<String, JsonObject>> getAllNeteaseResourceItemsAsMap() {
        Map<String, Map<String, JsonObject>> allItems = new HashMap<>();

        ResourcePack[] resourcePacks = Server.getInstance().getResourcePackManager().getResourceStackIgnoreNetease(GameVersion.getLastVersion());

        for (ResourcePack pack : resourcePacks) {
            if (pack instanceof ZippedResourcePack) {
                try {
                    File packFile = getPackFile(pack);
                    if (packFile != null && packFile.exists()) {
                        try (ZipFile zipFile = new ZipFile(packFile)) {
                            Map<String, JsonObject> jsonFiles = readNeteaseResourceItemsJson(zipFile);
                            if (!jsonFiles.isEmpty()) {
                                allItems.put(pack.getPackName(), jsonFiles);
                                log.info("从资源包 {} 中找到 {} 个物品资源定义", pack.getPackName(), jsonFiles.size());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("读取资源包 {} 时发生错误", pack.getPackName(), e);
                }
            }
        }

        return allItems;
    }

    /**
     * 读取资源包中的netease_items_res JSON文件
     */
    private Map<String, JsonObject> readNeteaseResourceItemsJson(ZipFile zipFile) {
        Map<String, JsonObject> jsonFiles = new HashMap<>();

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();

            if (entryName.startsWith(NETEASE_ITEMS_RES_PATH) && entryName.endsWith(".json")) {
                try (InputStreamReader reader = new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8)) {

                    JsonObject jsonObject = jsonParser.parse(reader).getAsJsonObject();
                    String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                    jsonFiles.put(fileName, jsonObject);

                    log.info("读取资源文件: {}", entryName);
                    log.info("内容: {}", gson.toJson(jsonObject));
                    log.info("----------------------------------------");

                } catch (Exception e) {
                    log.error("解析资源JSON文件 {} 时发生错误", entryName, e);
                }
            }
        }

        return jsonFiles;
    }

    /**
     * 根据identifier匹配行为包和资源包的物品定义
     */
    public Map<String, ItemDefinitionPair> matchBehaviorAndResourceItems() {
        Map<String, ItemDefinitionPair> matchedItems = new HashMap<>();

        // 获取行为包物品定义
        Map<String, Map<String, JsonObject>> behaviorItems = getAllNeteaseItemsAsMap();
        // 获取资源包物品定义
        Map<String, Map<String, JsonObject>> resourceItems = getAllNeteaseResourceItemsAsMap();

        // 遍历行为包物品，寻找对应的资源包物品
        for (Map.Entry<String, Map<String, JsonObject>> packEntry : behaviorItems.entrySet()) {
            String packName = packEntry.getKey();
            Map<String, JsonObject> items = packEntry.getValue();

            for (Map.Entry<String, JsonObject> itemEntry : items.entrySet()) {
                String fileName = itemEntry.getKey();
                JsonObject behaviorJson = itemEntry.getValue();

                // 提取identifier
                String identifier = extractIdentifier(behaviorJson);
                if (identifier != null) {
                    // 在资源包中查找对应的物品
                    JsonObject resourceJson = findResourceItemByIdentifier(resourceItems, identifier);

                    if (resourceJson != null) {
                        ItemDefinitionPair pair = new ItemDefinitionPair(identifier, behaviorJson, resourceJson);
                        matchedItems.put(identifier, pair);
                        log.info("成功匹配物品: {} (行为包: {}, 资源包: 已找到)", identifier, fileName);
                    } else {
                        log.warn("未找到物品 {} 的资源包定义", identifier);
                    }
                }
            }
        }

        return matchedItems;
    }

    /**
     * 从JSON中提取identifier
     */
    private String extractIdentifier(JsonObject jsonObject) {
        try {
            return jsonObject.getAsJsonObject("minecraft:item").getAsJsonObject("description").get("identifier").getAsString();
        } catch (Exception e) {
            log.error("提取identifier时发生错误", e);
            return null;
        }
    }

    /**
     * 在资源包中根据identifier查找对应的物品定义
     */
    private JsonObject findResourceItemByIdentifier(Map<String, Map<String, JsonObject>> resourceItems, String identifier) {
        for (Map<String, JsonObject> packItems : resourceItems.values()) {
            for (JsonObject resourceJson : packItems.values()) {
                String resourceIdentifier = extractIdentifier(resourceJson);
                if (identifier.equals(resourceIdentifier)) {
                    return resourceJson;
                }
            }
        }
        return null;
    }

    /**
     * 从资源包JSON中提取图标路径
     */
    private String extractIconPath(JsonObject resourceJson) {
        try {
            return resourceJson.getAsJsonObject("minecraft:item").getAsJsonObject("components").get("minecraft:icon").getAsString();
        } catch (Exception e) {
            log.debug("未找到图标路径或格式不正确", e);
            return null;
        }
    }

    /**
     * 静态方法，方便外部调用
     */
    public static void registerNeteaseItems() {
        NeteaseItemAutoRegister register = new NeteaseItemAutoRegister();
        register.scanAndRegisterNeteaseItems();
    }

    /**
     * 获取所有netease物品的静态方法
     */
    public static Map<String, Map<String, JsonObject>> getNeteaseItemsMap() {
        NeteaseItemAutoRegister register = new NeteaseItemAutoRegister();
        return register.getAllNeteaseItemsAsMap();
    }

    /**
     * 获取匹配的物品定义的静态方法
     */
    public static Map<String, ItemDefinitionPair> getMatchedNeteaseItems() {
        NeteaseItemAutoRegister register = new NeteaseItemAutoRegister();
        return register.matchBehaviorAndResourceItems();
    }

    /**
     * 物品定义对，包含行为包和资源包的定义
     */
    public static class ItemDefinitionPair {
        private final String identifier;
        private final JsonObject behaviorDefinition;
        private final JsonObject resourceDefinition;

        public ItemDefinitionPair(String identifier, JsonObject behaviorDefinition, JsonObject resourceDefinition) {
            this.identifier = identifier;
            this.behaviorDefinition = behaviorDefinition;
            this.resourceDefinition = resourceDefinition;
        }

        public String getIdentifier() {
            return identifier;
        }

        public JsonObject getBehaviorDefinition() {
            return behaviorDefinition;
        }

        public JsonObject getResourceDefinition() {
            return resourceDefinition;
        }

        /**
         * 获取图标路径
         */
        public String getIconPath() {
            try {
                return resourceDefinition.getAsJsonObject("minecraft:item").getAsJsonObject("components").get("minecraft:icon").getAsString();
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * 获取Java标识符（如果存在）
         */
        public String getJavaIdentifier() {
            try {
                return behaviorDefinition.getAsJsonObject("minecraft:item").get("java_identifier").getAsString();
            } catch (Exception e) {
                return null;
            }
        }
    }
}
