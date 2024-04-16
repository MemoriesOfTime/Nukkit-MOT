package cn.nukkit.item;

import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.nbt.tag.ByteTag;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author glorydark
 */
public class StringItemToolBase extends StringItemBase implements ItemDurable {

    public StringItemToolBase(@NotNull String id, @Nullable String name) {
        super(id, name);
    }

    @Override
    public boolean isTool() {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean useOn(Block block) {
        if (this.isUnbreakable() || isDurable() || noDamageOnBreak()) {
            return true;
        }

        if (block.getToolType() == ItemTool.TYPE_PICKAXE && this.isPickaxe() ||
                block.getToolType() == ItemTool.TYPE_SHOVEL && this.isShovel() ||
                block.getToolType() == ItemTool.TYPE_AXE && this.isAxe() ||
                block.getToolType() == ItemTool.TYPE_HOE && this.isHoe() ||
                block.getToolType() == ItemTool.TYPE_SWORD && this.isSword() ||
                block.getToolType() == ItemTool.TYPE_SHEARS && this.isShears()
        ) {
            this.meta++;
        } else if (!this.isShears() && block.calculateBreakTime(this) > 0) {
            this.meta += 2;
        } else if (this.isSword() && block.getHardness() > 0.0) {
            this.meta += 2;
        } else if (this.isHoe()) {
            if (block.getId() == GRASS || block.getId() == DIRT) {
                this.meta++;
            }
        } else {
            this.meta++;
        }
        return true;
    }

    @Override
    public boolean useOn(Entity entity) {
        if (this.isUnbreakable() || isDurable() || noDamageOnAttack()) {
            return true;
        }

        if ((entity != null) && !this.isSword()) {
            this.meta += 2;
        } else {
            this.meta++;
        }

        return true;
    }

    private boolean isDurable() {
        if (!hasEnchantments()) {
            return false;
        }

        Enchantment durability = getEnchantment(Enchantment.ID_DURABILITY);
        return durability != null && durability.getLevel() > 0 && (100 / (durability.getLevel() + 1)) <= Utils.random.nextInt(100);
    }

    @Override
    public boolean isUnbreakable() {
        Tag tag = this.getNamedTagEntry("Unbreakable");
        return tag instanceof ByteTag && ((ByteTag) tag).data > 0;
    }

    public void setUnbreakable(boolean unbreakable) {
        var tag = this.getNamedTag();
        if (tag == null) {
            tag = new CompoundTag();
        }
        tag.putByte("Unbreakable", (byte) (unbreakable ? 1 : 0));
        this.setNamedTag(tag);
    }

    @Override
    public int getEnchantAbility() {
        return switch (this.getTier()) {
            case ItemTool.TIER_STONE -> 5;
            case ItemTool.TIER_WOODEN -> 15;
            case ItemTool.TIER_DIAMOND -> 10;
            case ItemTool.TIER_GOLD -> 22;
            case ItemTool.TIER_IRON -> 14;
            //TODO
            case ItemTool.TIER_NETHERITE -> 10;
            default -> 0;
        };
    }

    /**
     * No damage to item when it's used to attack entities
     *
     * @return whether the item should take damage when used to attack entities
     */
    public boolean noDamageOnAttack() {
        return false;
    }

    /**
     * No damage to item when it's used to break blocks
     *
     * @return whether the item should take damage when used to break blocks
     */
    public boolean noDamageOnBreak() {
        return false;
    }
}
