package cn.nukkit.item.randomitem;

import cn.nukkit.item.Item;
import cn.nukkit.utils.Utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by Snake1999 on 2016/1/15.
 * Package cn.nukkit.item.randomitem in project nukkit.
 */
public class ConstantItemSelector extends Selector {

    protected final Item item;

    protected final boolean randomDurability;

    public ConstantItemSelector(int id, Selector parent) {
        this(id, 0, parent, true);
    }

    public ConstantItemSelector(int id, Selector parent, boolean randomDurability) {
        this(id, 0, parent, randomDurability);
    }

    public ConstantItemSelector(int id, Integer meta, Selector parent) {
        this(id, meta, 1, parent, false);
    }

    public ConstantItemSelector(int id, Integer meta, Selector parent, boolean randomDurability) {
        this(id, meta, 1, parent, randomDurability);
    }

    public ConstantItemSelector(int id, Integer meta, int count, Selector parent) {
        this(Item.get(id, meta, count), parent, false);
    }

    public ConstantItemSelector(int id, Integer meta, int count, Selector parent, boolean randomDurability) {
        this(Item.get(id, meta, count), parent, randomDurability);
    }

    public ConstantItemSelector(Item item, Selector parent, boolean randomDurability) {
        super(parent);
        this.item = item;
        this.randomDurability = randomDurability;
    }

    public Item getItem() {
        return item;
    }

    @Override
    public Object select() {
        Item result = item.clone();
        if (this.randomDurability) {
            this.endowRandomDurability(result);
        }
        return result;
    }

    public void endowRandomDurability(Item item) {
        int maxDurability = item.getMaxDurability();
        if (maxDurability == -1) {
            return;
        }
        int splitValue = maxDurability / 10;
        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            item.setDamage(Utils.rand(0, splitValue));
        } else {
            item.setDamage(Utils.rand(splitValue, maxDurability));
        }
    }
}
