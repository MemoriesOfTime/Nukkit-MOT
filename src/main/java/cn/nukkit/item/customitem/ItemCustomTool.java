package cn.nukkit.item.customitem;

import cn.nukkit.block.Block;
import cn.nukkit.item.*;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.nbt.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author lt_name
 */
public abstract class ItemCustomTool extends StringItemToolBase implements ItemDurable, CustomItem {

    private final String textureName;

    /** destroy_speeds 按 blockId 解析的速度缓存，懒加载，仅含具名条目（tags 条目不参与）。clone 浅拷贝共享，只读。 */
    private transient Map<Integer, Integer> blockSpeedCache;

    public ItemCustomTool(@NotNull String id, @Nullable String name) {
        super(id, StringItem.notEmpty(name));
        this.textureName = name;
    }

    public ItemCustomTool(@NotNull String id, @Nullable String name, @NotNull String textureName) {
        super(id, StringItem.notEmpty(name));
        this.textureName = textureName;
    }

    @Override
    public String getTextureName() {
        return textureName;
    }

    /**
     * 判断物品是否含有指定的 item_tag（写入 {@code components.item_tags} 中的标签）。
     * <p>
     * Checks whether the item has the given item_tag (written into {@code components.item_tags}).
     */
    private boolean hasItemTag(@NotNull String expected) {
        CompoundTag components = this.getDefinitionNbt().getCompound("components");
        if (!components.contains("item_tags")) {
            return false;
        }
        ListTag<? extends Tag> list = components.getList("item_tags");
        for (Tag tag : list.getAll()) {
            if (tag instanceof StringTag stringTag && expected.equals(stringTag.parseValue())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getMaxDurability() {
        return this.getDefinitionNbt()
                .getCompound("components")
                .getCompound("minecraft:durability")
                .getInt("max_durability");
    }

    @Override
    public int getTier() {
        return this.getDefinitionNbt()
                .getCompound("components")
                .getCompound("item_properties")
                .getInt("tier");
    }

    @Override
    public int getAttackDamage() {
        return this.getDefinitionNbt()
                .getCompound("components")
                .getCompound("item_properties")
                .getInt("damage");
    }

    @Override
    public boolean isPickaxe() {
        return hasItemTag("minecraft:is_pickaxe");
    }

    @Override
    public boolean isAxe() {
        return hasItemTag("minecraft:is_axe");
    }

    @Override
    public boolean isShovel() {
        return hasItemTag("minecraft:is_shovel");
    }

    @Override
    public boolean isHoe() {
        return hasItemTag("minecraft:is_hoe");
    }

    @Override
    public boolean isShears() {
        return hasItemTag("minecraft:is_shears");
    }

    @Override
    public boolean isSword() {
        //剑无 item_tag，通过 enchantable_slot 判定
        //Swords have no item tag, so determine via enchantable_slot
        String slot = this.getDefinitionNbt()
                .getCompound("components")
                .getCompound("item_properties")
                .getString("enchantable_slot");
        return "sword".equals(slot);
    }

    /**
     * 返回 destroy_speeds 首项速度。固定取第一项、不区分方块，已过时，保留仅为 API 兼容。
     *
     * @deprecated 改用 {@link #getSpeedFor(Block)}。
     */
    @Deprecated
    @Nullable
    public final Integer getSpeed() {
        var nbt = this.getDefinitionNbt();
        if (!nbt.getCompound("components").contains("minecraft:digger")) return null;
        var speeds = nbt.getCompound("components")
                .getCompound("minecraft:digger")
                .getList("destroy_speeds", CompoundTag.class);
        if (speeds.size() == 0) return null;
        return speeds.get(0).getInt("speed");
    }

    /**
     * 返回此工具挖掘指定方块的速度（取自 destroy_speeds 匹配条目），未指定返回 {@code null} 由调用方回退 tier 查表。
     * 按当前方块查找，正确实现逐方块语义：{@code addExtraBlock(name, speed)} 只对该方块生效。
     * 匹配按 blockId（name 经 {@link Item#fromString(String)} 解析）；tags 条目不参与，由 correctTool 覆盖。
     */
    @Nullable
    public final Integer getSpeedFor(@NotNull Block block) {
        return this.getSpeedFor(block.getId());
    }

    /** @see #getSpeedFor(Block) */
    @Nullable
    public final Integer getSpeedFor(int blockId) {
        return this.getBlockSpeedCache().get(blockId);
    }

    private Map<Integer, Integer> getBlockSpeedCache() {
        if (this.blockSpeedCache == null) {
            this.blockSpeedCache = this.buildBlockSpeedCache();
        }
        return this.blockSpeedCache;
    }

    private Map<Integer, Integer> buildBlockSpeedCache() {
        CompoundTag components = this.getDefinitionNbt().getCompound("components");
        if (!components.contains("minecraft:digger")) {
            return Map.of();
        }
        ListTag<CompoundTag> speeds = components.getCompound("minecraft:digger")
                .getList("destroy_speeds", CompoundTag.class);
        if (speeds.size() == 0) {
            return Map.of();
        }
        Map<Integer, Integer> cache = new HashMap<>();
        for (CompoundTag entry : speeds.getAll()) {
            CompoundTag blockTag = entry.getCompound("block");
            String name = blockTag.getString("name");
            if (name.isEmpty()) {
                continue; //tags 条目（q.any_tag(...)），由 correctTool + tier 查表处理
            }
            Block block = Item.fromString(name).getBlock();
            if (block != null && block.getId() != Block.AIR) {
                cache.put(block.getId(), entry.getInt("speed"));
            }
        }
        return cache;
    }

    @Override
    public ItemCustomTool clone() {
        return (ItemCustomTool) super.clone();
    }
}
