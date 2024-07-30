package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.event.block.ComposterEmptyEvent;
import cn.nukkit.event.block.ComposterFillEvent;
import cn.nukkit.item.*;
import cn.nukkit.level.Sound;
import cn.nukkit.utils.DyeColor;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import javax.annotation.Nonnull;
import java.util.concurrent.ThreadLocalRandom;

public class BlockComposter extends BlockSolidMeta implements ItemID {

    private static Int2IntOpenHashMap compostableItems = new Int2IntOpenHashMap();

    static {
        registerDefaults();
    }

    public BlockComposter() {
        this(0);
    }

    public BlockComposter(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return COMPOSTER;
    }

    @Override
    public String getName() {
        return "Composter";
    }

    @Override
    public double getHardness() {
        return 0.6;
    }

    @Override
    public double getResistance() {
        return 0.6;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public int getWaterloggingLevel() {
        return 1;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(this, 0);
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride() {
        return this.getDamage();
    }

    public boolean incrementLevel() {
        int damage = this.getDamage() + 1;
        this.setDamage(damage);
        this.level.setBlock(this, this, true, true);
        return damage == 8;
    }

    public boolean isFull() {
        return this.getDamage() == 8;
    }

    public boolean isEmpty() {
        return this.getDamage() == 0;
    }

    @Override
    public boolean onActivate(@Nonnull Item item, Player player) {
        if (item.getCount() <= 0 || item.getId() == Item.AIR) {
            return false;
        }

        if (this.isFull()) {
            ComposterEmptyEvent event = new ComposterEmptyEvent(this, player, item, new ItemDye(DyeColor.WHITE), 0);
            this.level.getServer().getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                this.setDamage(event.getNewLevel());
                this.level.setBlock(this, this, true, true);
                this.level.dropItem(this.add(0.5, 0.85, 0.5), event.getDrop(), event.getMotion(), false, 10);
                this.level.addSound(this.add(0.5 , 0.5, 0.5), Sound.BLOCK_COMPOSTER_EMPTY);
            }
            return true;
        }

        int chance = getChance(item);
        if (chance <= 0) {
            return false;
        }

        boolean success = ThreadLocalRandom.current().nextInt(100) < chance;
        ComposterFillEvent event = new ComposterFillEvent(this, player, item, chance, success);
        this.level.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return true;
        }

        if (!player.isCreative()) {
            item.setCount(item.getCount() - 1);
        }

        if (event.isSuccess()) {
            if (this.incrementLevel()) {
                level.addSound(this.add(0.5, 0.5, 0.5), Sound.BLOCK_COMPOSTER_READY);
            } else {
                level.addSound(this.add(0.5, 0.5, 0.5), Sound.BLOCK_COMPOSTER_FILL_SUCCESS);
            }
        } else {
            level.addSound(this.add(0.5, 0.5, 0.5), Sound.BLOCK_COMPOSTER_FILL);
        }

        return true;
    }

    public static void registerItem(int chance, int itemId) {
        registerItem(chance, itemId, 0);
    }

    public static void registerItem(int chance, int itemId, int meta) {
        compostableItems.put(itemId << 6 | meta & 0x3F, chance);
    }

    public static void registerItems(int chance, int... itemIds) {
        for (int itemId : itemIds) {
            registerItem(chance, itemId, 0);
        }
    }

    public static void registerBlocks(int chance, int... blockIds) {
        for (int blockId : blockIds) {
            registerBlock(chance, blockId, 0);
        }
    }

    public static void registerBlock(int chance, int blockId) {
        registerBlock(chance, blockId, 0);
    }

    public static void registerBlock(int chance, int blockId, int meta) {
        if (blockId > 255) {
            blockId = 255 - blockId;
        }
        registerItem(chance, blockId, meta);
    }

    public static void register(int chance, Item item) {
        registerItem(chance, item.getId(), item.getDamage());
    }

    public static int getChance(Item item) {
        int chance = compostableItems.get(item.getId() << 6 | item.getDamage());
        if (chance == 0) {
            chance = compostableItems.get(item.getId() << 6);
        }
        return chance;
    }

    private static void registerDefaults() {
        registerItems(30, KELP, BEETROOT_SEEDS, DRIED_KELP, MELON_SEEDS, PUMPKIN_SEEDS, SWEET_BERRIES, SWEET_BERRIES, WHEAT_SEEDS);
        registerItems(50, MELON_SLICE, SUGAR_CANE);
        registerItems(65, APPLE, BEETROOT, CARROT, COCOA, POTATO, WHEAT);
        registerItems(85, BAKED_POTATOES, BREAD, COOKIE);
        registerItems(100, CAKE, PUMPKIN_PIE);

        registerBlocks(30, BLOCK_KELP, LEAVES, LEAVES2, SAPLINGS, SEAGRASS, SWEET_BERRY_BUSH);
        registerBlocks(50, GRASS, CACTUS, DRIED_KELP_BLOCK, VINES);
        registerBlocks(65, DANDELION, RED_FLOWER, DOUBLE_PLANT, LILY_PAD, MELON_BLOCK,
                                  PUMPKIN, CARVED_PUMPKIN, SEA_PICKLE, BROWN_MUSHROOM, RED_MUSHROOM); //TODO: pumpkin
        registerBlocks(85, HAY_BALE, BROWN_MUSHROOM_BLOCK, RED_MUSHROOM_BLOCK, MUSHROOM_STEW);
        registerBlocks(100, CAKE_BLOCK);

        registerBlock(50, TALL_GRASS, 0);
        registerBlock(50, TALL_GRASS, 1);
        registerBlock(65, TALL_GRASS, 2);
        registerBlock(65, TALL_GRASS, 3);
    }
}
