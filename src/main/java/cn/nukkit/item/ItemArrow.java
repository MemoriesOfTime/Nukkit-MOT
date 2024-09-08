package cn.nukkit.item;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemArrow extends Item {

    public static final int NORMAL_ARROW = 0;
    public static final int TIPPED_ARROW = 1;

    public ItemArrow() {
        this(0, 1);
    }

    public ItemArrow(Integer meta) {
        this(meta, 1);
    }

    public ItemArrow(Integer meta, int count) {
        super(ARROW, meta, count, "Arrow");
    }

    public boolean isTipped() {
        return getDamage() > NORMAL_ARROW;
    }

    public int getPotionId() {
        return getDamage() - TIPPED_ARROW;
    }
}
