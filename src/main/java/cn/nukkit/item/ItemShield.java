package cn.nukkit.item;

public class ItemShield extends ItemTool {

    public ItemShield() {
        this(0, 1);
    }

    public ItemShield(Integer meta) {
        this(meta, 1);
    }

    public ItemShield(Integer meta, int count) {
        super(SHIELD, meta, count, "Shield");
    }

    /**
     * 为自定义盾牌提供的构造函数
     * <p>
     * Constructor for custom shield
     *
     * @param id    the id
     * @param meta  the meta
     * @param count the count
     * @param name  the name
     */
    public ItemShield(int id, Integer meta, int count, String name) {
        super(id, meta, count, name);
    }

    @Override
    public int getMaxDurability() {
        return DURABILITY_SHIELD;
    }
}
