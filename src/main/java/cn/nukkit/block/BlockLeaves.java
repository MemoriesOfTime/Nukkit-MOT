package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.block.LeavesDecayEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Angelic47
 * Nukkit Project
 */
public class BlockLeaves extends BlockTransparentMeta {

    public static final int OAK = 0;
    public static final int SPRUCE = 1;
    public static final int BIRCH = 2;
    public static final int JUNGLE = 3;

    public BlockLeaves() {
        this(0);
    }

    public BlockLeaves(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return LEAVES;
    }

    @Override
    public double getHardness() {
        return 0.2;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_HOE;
    }

    @Override
    public String getName() {
        String[] names = new String[]{
                "Oak Leaves",
                "Spruce Leaves",
                "Birch Leaves",
                "Jungle Leaves"
        };
        return names[this.getDamage() & 0x03];
    }

    @Override
    public int getBurnChance() {
        return 30;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }

    @Override
    public boolean breaksWhenMoved() {
        return true;
    }

    @Override
    public boolean sticksToPiston() {
        return false;
    }

    @Override
    public int getBurnAbility() {
        return 60;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        setPersistent(true);
        this.getLevel().setBlock(this, this, true);
        return true;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(this, this.getDamage() & 0x3, 1);
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isShears()) {
            return new Item[]{
                    toItem()
            };
        } else {
            if (item.hasEnchantment(Enchantment.ID_SILK_TOUCH)) {
                return new Item[]{this.toItem()};
            }
            int fortune = item.getEnchantmentLevel(Enchantment.ID_FORTUNE_DIGGING);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int appleOdds;
            int stickOdds;
            int saplingOdds;
            boolean isJungle = (this.getDamage() & 0x03) == JUNGLE;
            switch (fortune) {
                case 0 -> {
                    appleOdds = 200;
                    stickOdds = 50;
                    saplingOdds = isJungle ? 40 : 20;
                }
                case 1 -> {
                    appleOdds = 180;
                    stickOdds = 45;
                    saplingOdds = isJungle ? 36 : 16;
                }
                case 2 -> {
                    appleOdds = 160;
                    stickOdds = 40;
                    saplingOdds = isJungle ? 32 : 12;
                }
                default -> {
                    appleOdds = 120;
                    stickOdds = 30;
                    saplingOdds = isJungle ? 24 : 10;
                }
            }
            if (this.canDropApple() && random.nextInt(appleOdds) == 0) {
                return new Item[]{
                        Item.get(Item.APPLE)
                };
            }
            if (random.nextInt(stickOdds) == 0) {
                return new Item[]{
                        Item.get(Item.STICK, 0, random.nextInt(1, 3))
                };
            }
            if (random.nextInt(saplingOdds) == 0) {
                return new Item[]{
                        this.getSapling()
                };
            }
        }
        return Item.EMPTY_ARRAY;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL && !isPersistent() && !isCheckDecay()) {
            if (this.level.getBlockIdAt((int) this.x, (int) this.y, (int) this.z) != this.getId()) {
                return 0;
            }

            setCheckDecay(true);
            getLevel().setBlock(this, this, false, false);

            return Level.BLOCK_UPDATE_NORMAL;
        } else if (type == Level.BLOCK_UPDATE_RANDOM && isCheckDecay() && !isPersistent()) {
            LeavesDecayEvent ev = new LeavesDecayEvent(this);
            Server.getInstance().getPluginManager().callEvent(ev);

            if (ev.isCancelled() || findLog()) {
                setCheckDecay(false);
                getLevel().setBlock(this, this, false, false);
            } else {
                getLevel().useBreakOn(this);
            }

            return Level.BLOCK_UPDATE_RANDOM;
        }
        return 0;
    }

    public boolean isCheckDecay() {
        return (this.getDamage() & 0x08) != 0;
    }

    public void setCheckDecay(boolean checkDecay) {
        if (checkDecay) {
            this.setDamage(this.getDamage() | 0x08);
        } else {
            this.setDamage(this.getDamage() & -9);
        }
    }

    public boolean isPersistent() {
        return (this.getDamage() & 0x04) != 0;
    }

    public void setPersistent(boolean persistent) {
        if (persistent) {
            this.setDamage(this.getDamage() | 0x04);
        } else {
            this.setDamage(this.getDamage() & -5);
        }
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.FOLIAGE_BLOCK_COLOR;
    }

    @Override
    public boolean canSilkTouch() {
        return true;
    }

    protected boolean canDropApple() {
        return (this.getDamage() & 0x03) == OAK;
    }

    protected Item getSapling() {
        return Item.get(BlockID.SAPLING, this.getDamage() & 0x03);
    }

    private boolean findLog() {
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        Map<Block, Integer> distance = new HashMap<>();

        queue.offer(this);
        visited.add(this);
        distance.put(this, 0);

        while (!queue.isEmpty()) {
            Block currentBlock = queue.poll();
            int currentDistance = distance.get(currentBlock);

            if (currentDistance > 4) {
                return false;
            }

            for (BlockFace face : BlockFace.values()) {
                Block nextBlock = currentBlock.getSideIfLoadedOrNull(face); // If side chunk not loaded, do not load or decay
                if (nextBlock == null || isLog(nextBlock.getId(), nextBlock.getDamage())) {
                    return true;
                }

                if (isLeaves(nextBlock.getId()) && !visited.contains(nextBlock)) {
                    queue.offer(nextBlock);
                    visited.add(nextBlock);
                    distance.put(nextBlock, currentDistance + 1);
                }
            }
        }

        return false;
    }

    protected boolean isLeaves(int id) {
        return switch (id) {
            case LEAVES, LEAVES2, AZALEA_LEAVES, AZALEA_LEAVES_FLOWERED, MANGROVE_LEAVES, CHERRY_LEAVES -> true;
            default -> false;
        };
    }

    protected boolean isLog(int id, int damage) {
        return switch (id) {
            case LOG, LOG2, MANGROVE_LOG, CHERRY_LOG, MANGROVE_WOOD, CHERRY_WOOD -> true;
            case WOOD_BARK -> (damage & BlockWoodBark.STRIPPED_BIT) == 0;
            default -> false;
        };
    }

    @Override
    public boolean diffusesSkyLight() {
        return true;
    }
}
