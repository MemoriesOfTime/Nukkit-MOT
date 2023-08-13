package cn.nukkit.item;

/**
 * @author Glorydark
 */
public class ItemDuneArmorTrimSmithingTemplate extends Item {

    public ItemDuneArmorTrimSmithingTemplate() {
        this(0, 1);
    }

    public ItemDuneArmorTrimSmithingTemplate(Integer meta) {
        this(meta, 1);
    }

    public ItemDuneArmorTrimSmithingTemplate(Integer meta, int count) {
        super(DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, meta, count, "Dune Armor Trim Smithing Template");
    }

}
