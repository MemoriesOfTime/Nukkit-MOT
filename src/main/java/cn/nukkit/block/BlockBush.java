package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import org.jetbrains.annotations.NotNull;

public class BlockBush extends BlockFlowable {
    public BlockBush() {
        this(0);
    }

    public BlockBush(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Bush";
    }

    @Override
    public int getId() {
        return BUSH;
    }

    @Override
    public boolean canBeReplaced() {
        return true;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        if (isSupportValid(down())) {
            this.getLevel().setBlock(block, this, true);
            return true;
        }
        return false;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (!isSupportValid(down())) {
                this.getLevel().useBreakOn(this);
            }
        }
        return 0;
    }

    public static boolean isSupportValid(Block block) {
        return switch (block.getId()) {
            case GRASS, DIRT, MYCELIUM, PODZOL, FARMLAND, ROOTED_DIRT, MOSS_BLOCK,
                    MUD, MUDDY_MANGROVE_ROOTS, PALE_MOSS_BLOCK -> true;
            default -> false;
        };
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isShears() || item.hasEnchantment(Enchantment.ID_SILK_TOUCH)) {
            return new Item[]{
                toItem()
            };
        }

        return Item.EMPTY_ARRAY;
    }
}
