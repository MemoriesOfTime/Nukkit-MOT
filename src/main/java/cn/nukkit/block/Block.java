package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.custom.CustomBlockManager;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.customitem.ItemCustomTool;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.MovingObjectPosition;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.persistence.PersistentDataContainer;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.metadata.MetadataValue;
import cn.nukkit.metadata.Metadatable;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.BlockColor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static cn.nukkit.utils.Utils.dynamic;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@Log4j2
public abstract class Block extends Position implements Metadatable, Cloneable, AxisAlignedBB, BlockID {

    public static final Block[] EMPTY_ARRAY = new Block[0];

    public static final int ID_BITS = dynamic(11);
    public static final int MAX_BLOCK_ID = dynamic(1 << ID_BITS);
    public static final int ID_MASK = dynamic(MAX_BLOCK_ID - 1);
    public static final int DATA_BITS = dynamic(13);
    public static final int DATA_SIZE = dynamic(1 << DATA_BITS);
    public static final int DATA_MASK = dynamic(DATA_SIZE - 1);
    public static final int FULL_BITS = ID_BITS + DATA_BITS;
    public static final int FULL_SIZE = dynamic(1 << FULL_BITS);

    @SuppressWarnings("rawtypes")
    public static Class[] list = null;
    public static Block[] fullList = null;
    public static int[] light = null;
    public static int[] lightFilter = null;
    public static boolean[] solid = null;
    public static double[] hardness = null;
    public static boolean[] transparent = null;
    public static boolean[] diffusesSkyLight = null;
    public static boolean[] hasMeta = null;

    public AxisAlignedBB boundingBox = null;
    public int layer = 0;

    /**
     * A commonly used block face pattern
     */
    protected static final int[] FACES2534 = {2, 5, 3, 4};

    protected Block() {}

    public static void init() {
        if (list == null) {
            list = new Class[MAX_BLOCK_ID];
            fullList = new Block[MAX_BLOCK_ID * (1 << DATA_BITS)];
            light = new int[MAX_BLOCK_ID];
            lightFilter = new int[MAX_BLOCK_ID];
            solid = new boolean[MAX_BLOCK_ID];
            hardness = new double[MAX_BLOCK_ID];
            transparent = new boolean[MAX_BLOCK_ID];
            diffusesSkyLight = new boolean[MAX_BLOCK_ID];
            hasMeta = new boolean[MAX_BLOCK_ID];

            Blocks.init();

            int processors = Runtime.getRuntime().availableProcessors();
            IntStream idStream = IntStream.range(0, MAX_BLOCK_ID);
            (processors > 3 ? idStream.parallel() : idStream).forEach(id -> {
                Class<?> c = list[id];
                if (c != null) {
                    Block block;
                    try {
                        block = (Block) c.getDeclaredConstructor().newInstance();
                        try {
                            @SuppressWarnings("rawtypes")
                            Constructor constructor = c.getDeclaredConstructor(int.class);
                            constructor.setAccessible(true);
                            for (int data = 0; data < DATA_SIZE; ++data) {
                                int fullId = (id << DATA_BITS) | data;
                                Block b;
                                try {
                                    b = (Block) constructor.newInstance(data);
                                    if (b.getDamage() != data) {
                                        //b = new BlockUnknown(id, data);
                                        continue;
                                    }
                                } catch (Exception e) {
                                    Server.getInstance().getLogger().error("Error while registering " + c.getName(), e);
                                    //b = new BlockUnknown(id, data);
                                    continue;
                                }
                                fullList[fullId] = b;
                            }
                            hasMeta[id] = true;
                        } catch (NoSuchMethodException ignore) {
                            for (int data = 0; data < DATA_SIZE; ++data) {
                                int fullId = (id << DATA_BITS) | data;
                                fullList[fullId] = block;
                            }
                        }
                    } catch (Exception e) {
                        Server.getInstance().getLogger().error("Error while registering " + c.getName(), e);
                        /*for (int data = 0; data < DATA_SIZE; ++data) {
                            fullList[(id << DATA_BITS) | data] = new BlockUnknown(id, data);
                        }*/
                        return;
                    }

                    solid[id] = block.isSolid();
                    transparent[id] = block.isTransparent();
                    diffusesSkyLight[id] = block.diffusesSkyLight();
                    hardness[id] = block.getHardness();
                    light[id] = block.getLightLevel();

                    if (block.isSolid()) {
                        if (block.isTransparent()) {
                            if (block instanceof BlockLiquid || block instanceof BlockIce) {
                                lightFilter[id] = 2;
                            } else {
                                lightFilter[id] = 1;
                            }
                        } else if (block instanceof BlockSlime) {
                            lightFilter[id] = 1;
                        } else if (id == CAULDRON_BLOCK) {
                            lightFilter[id] = 3;
                        } else {
                            lightFilter[id] = 15;
                        }
                    } else {
                        lightFilter[id] = 1;
                    }
                } else {
                    lightFilter[id] = 1;
                    /*for (int data = 0; data < DATA_SIZE; ++data) {
                        fullList[(id << DATA_BITS) | data] = new BlockUnknown(id, data);
                    }*/
                }
            });
        }
    }

    @NotNull
    public static Block get(int id) {
        return get(id, null);
    }

    @NotNull
    public static Block get(int id, Integer meta) {
        if (id < 0) {
            id = 255 - id;
        }

        if (id >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            return CustomBlockManager.get().getBlock(id, meta == null ? 0 : meta);
        }

        int fullId = id << DATA_BITS;
        if (meta != null) {
            int iMeta = meta;
            if (iMeta <= DATA_SIZE) {
                fullId = fullId | meta;
                if (fullId >= fullList.length || fullList[fullId] == null) {
                    log.debug("Found an unknown BlockId:Meta combination: {}:{}", id, iMeta);
                    BlockUnknown blockUnknown = new BlockUnknown(id, iMeta);
                    fullList[fullId] = blockUnknown;
                    return blockUnknown.clone();
                }
                return fullList[fullId].clone();
            } else {
                if (fullId >= fullList.length || fullList[fullId] == null) {
                    log.debug("Found an unknown BlockId:Meta combination: {}:{}", id, iMeta);
                    BlockUnknown blockUnknown = new BlockUnknown(id, iMeta);
                    fullList[fullId] = blockUnknown;
                    return blockUnknown.clone();
                }
                Block block = fullList[fullId].clone();
                block.setDamage(iMeta);
                return block;
            }
        } else {
            if (fullId >= fullList.length || fullList[fullId] == null) {
                log.debug("Found an unknown BlockId:Meta combination: {}:{}", id, 0);
                return new BlockUnknown(id, 0);
            }
            return fullList[fullId].clone();
        }
    }

    @NotNull
    public static Block get(int id, Integer meta, Position pos) {
        return get(id, meta, pos, 0);
    }

    @NotNull
    public static Block get(int id, Integer meta, Position pos, int layer) {
        if (id < 0) {
            id = 255 - id;
        }

        Block block;
        if (id >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            block = CustomBlockManager.get().getBlock(id, meta == null ? 0 : meta);
        } else {
            int fullId = id << DATA_BITS;
            if (meta != null && meta > DATA_SIZE) {
                if (fullId >= fullList.length || fullList[fullId] == null) {
                    log.debug("Found an unknown BlockId:Meta combination: {}:{}", id, meta);
                    BlockUnknown blockUnknown = new BlockUnknown(id, meta);
                    fullList[fullId] = blockUnknown;
                    return blockUnknown.clone();
                }
                block = fullList[fullId].clone();
                block.setDamage(meta);
            } else {
                meta = meta == null ? 0 : meta;
                fullId = fullId | meta;
                if (fullId >= fullList.length || fullList[fullId] == null) {
                    log.debug("Found an unknown BlockId:Meta combination: {}:{}", id, meta);
                    BlockUnknown blockUnknown = new BlockUnknown(id, meta);
                    fullList[fullId] = blockUnknown;
                    return blockUnknown.clone();
                }
                block = fullList[fullId].clone();
            }
        }

        if (pos != null) {
            block.x = pos.x;
            block.y = pos.y;
            block.z = pos.z;
            block.level = pos.level;
            block.layer = layer;
        }
        return block;
    }

    @NotNull
    public static Block get(int id, int data) {
        if (id < 0) {
            id = 255 - id;
        }

        if (id >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            return CustomBlockManager.get().getBlock(id, data);
        }

        int fullId = id << DATA_BITS;
        if (fullId >= fullList.length) {
            log.debug("Found an unknown BlockId:Meta combination: {}:{}", id, data);
            return new BlockUnknown(id, data);
        }
        if (data < DATA_SIZE) {
            fullId = fullId | data;
            if (fullList[fullId] == null) {
                log.debug("Found an unknown BlockId:Meta combination: {}:{}", id, data);
                BlockUnknown blockUnknown = new BlockUnknown(id, data);
                fullList[fullId] = blockUnknown;
                return blockUnknown.clone();
            }
            return fullList[fullId].clone();
        } else {
            Block block = fullList[fullId].clone();
            block.setDamage(data);
            return block;
        }
    }

    @NotNull
    public static Block get(int fullId, Level level, int x, int y, int z) {
        return get(fullId, level, x, y, z, 0);
    }

    @NotNull
    public static Block get(int fullId, Level level, int x, int y, int z, int layer) {
        int id = fullId >> DATA_BITS;

        Block block;
        if (id >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            block = CustomBlockManager.get().getBlock(id, fullId & DATA_MASK);
        } else {
            if (fullId >= fullList.length || fullList[fullId] == null) {
                int meta = fullId & DATA_MASK;
                log.debug("Found an unknown BlockId:Meta combination: {}:{}", id, meta);
                BlockUnknown blockUnknown = new BlockUnknown(id, meta);
                fullList[fullId] = blockUnknown;
                return blockUnknown.clone();
            }
            block = fullList[fullId].clone();
        }
        block.x = x;
        block.y = y;
        block.z = z;
        block.level = level;
        block.layer = layer;
        return block;
    }

    @NotNull
    public static Block get(int id, int meta, Level level, int x, int y, int z) {
        return get(id, meta, level, x, y, z, 0);
    }

    @NotNull
    public static Block get(int id, int meta, Level level, int x, int y, int z, int layer) {
        Block block;
        if (id >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            block = CustomBlockManager.get().getBlock(id, meta);
        } else {
            if (meta <= DATA_SIZE) {
                int index = id << DATA_BITS | meta;
                if (fullList[index] == null) {
                    log.debug("Found an unknown BlockId:Meta combination: {}:{}", id, meta);
                    BlockUnknown blockUnknown = new BlockUnknown(id, meta);
                    fullList[index] = blockUnknown;
                    return blockUnknown.clone();
                }
                block = fullList[index].clone();
            } else {
                int index = id << DATA_BITS;
                if (fullList[index] == null) {
                    log.debug("Found an unknown BlockId:Meta combination: {}:{}", id, meta);
                    BlockUnknown blockUnknown = new BlockUnknown(id, meta);
                    fullList[index] = blockUnknown;
                    return blockUnknown.clone();
                }
                block = fullList[index].clone();
                block.setDamage(meta);
            }
        }
        block.x = x;
        block.y = y;
        block.z = z;
        block.level = level;
        block.layer = layer;
        return block;
    }

    public static int getBlockLight(int blockId) {
        if (blockId >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            return light[0]; // TODO: just temporary
        }
        return light[blockId];
    }

    public static int getBlockLightFilter(int blockId) {
        if (blockId >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            return lightFilter[0]; // TODO: just temporary
        }
        return lightFilter[blockId];
    }

    public static boolean isBlockSolidById(int blockId) {
        if (blockId >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            return solid[1]; // TODO: just temporary
        }
        return solid[blockId];
    }

    public static boolean isBlockTransparentById(int blockId) {
        if (blockId >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            return transparent[1]; // TODO: just temporary
        }
        return transparent[blockId];
    }

    public static Block fromFullId(int fullId) {
        return get(fullId >> DATA_BITS, fullId & DATA_MASK);
    }

    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @Nullable Player player) {
        return this.canPlaceOn(block.down(), target) && this.getLevel().setBlock(this, this, true, true);
    }

    public boolean canPlaceOn(Block floor, Position pos) {
        return this.canBePlaced();
    }

    public boolean canHarvestWithHand() {
        return true;
    }

    public boolean isBreakable(Item item) {
        return true;
    }

    public int tickRate() {
        return 10;
    }

    public boolean onBreak(Item item, Player player) {
        return this.onBreak(item);
    }

    public boolean onBreak(Item item) {
        return this.getLevel().setBlock(this, Block.get(BlockID.AIR), true, true);
    }

    public int onUpdate(int type) {
        return 0;
    }

    public void onNeighborChange(@NotNull BlockFace side) {

    }


    /**
     * 当玩家使用与左键或者右键方块时会触发，常被用于处理例如物品展示框左键掉落物品这种逻辑<br>
     * 触发点在{@link Player}的onBlockBreakStart中
     * <p>
     * It will be triggered when the player uses the left or right click on the block, which is often used to deal with logic such as left button dropping items in the item frame<br>
     * The trigger point is in the onBlockBreakStart of {@link Player}
     *
     * @param player the player
     * @param action the action
     * @return 状态值，返回值不为0代表这是一个touch操作而不是一个挖掘方块的操作<br>Status value, if the return value is not 0, it means that this is a touch operation rather than a mining block operation
     */
    public int onTouch(@Nullable Player player, PlayerInteractEvent.Action action) {
        this.onUpdate(Level.BLOCK_UPDATE_TOUCH);
        return 0;
    }

    public boolean onActivate(Item item) {
        return this.onActivate(item, null);
    }

    public boolean onActivate(Item item, Player player) {
        return false;
    }

    /**
     * @return 是否可以被灵魂疾行附魔加速<br>Whether it can be accelerated by the soul speed enchantment
     */
    public boolean isSoulSpeedCompatible() {
        return false;
    }

    public double getHardness() {
        return 10;
    }

    public double getResistance() {
        return 1;
    }

    public int getBurnChance() {
        return 0;
    }

    public int getBurnAbility() {
        return 0;
    }

    public int getToolType() {
        return ItemTool.TYPE_NONE;
    }

    public int getToolTier() {
        return 0;
    }

    public double getFrictionFactor() {
        return 0.6;
    }

    public int getLightLevel() {
        return 0;
    }

    public boolean canBePlaced() {
        return true;
    }

    public boolean canBeReplaced() {
        return false;
    }

    public boolean isTransparent() {
        return false;
    }

    public boolean isSolid() {
        return true;
    }

    public boolean isSolid(BlockFace side) {
        return isSideFull(side);
    }

    public boolean diffusesSkyLight() {
        return false;
    }

    /**
     * @deprecated use {@link #getWaterloggingType()} instead
     */
    @Deprecated
    public int getWaterloggingLevel() {
        return getWaterloggingType().ordinal();
    }

    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.NO_WATERLOGGING;
    }

    public enum WaterloggingType {
        /**
         * Block is not waterloggable
         */
        NO_WATERLOGGING,
        /**
         * If possible, water will be set to second layer when the block is placed into water
         */
        WHEN_PLACED_IN_WATER,
        /**
         * Water will flow into the block and water will be set to second layer
         */
        FLOW_INTO_BLOCK
    }

    public final boolean canWaterloggingFlowInto() {
        return this.canBeFlowedInto() || this.getWaterloggingType() == WaterloggingType.FLOW_INTO_BLOCK;
    }

    public boolean canBeFlowedInto() {
        return false;
    }

    public boolean canBeActivated() {
        return false;
    }

    public boolean hasEntityCollision() {
        return false;
    }

    public boolean canPassThrough() {
        return false;
    }

    public boolean canBePushed() {
        return true;
    }

    public boolean canBePulled() {
        return true;
    }

    public boolean breaksWhenMoved() {
        return false;
    }

    public boolean sticksToPiston() {
        return true;
    }

    public boolean hasComparatorInputOverride() {
        return false;
    }

    public int getComparatorInputOverride() {
        return 0;
    }

    public boolean canHarvest(Item item) {
        return this.getToolTier() == 0 || this.getToolType() == 0 || correctTool0(this.getToolType(), item, this.getId()) && item.getTier() >= this.getToolTier();
    }

    public boolean canBeClimbed() {
        return false;
    }

    public BlockColor getColor() {
        return BlockColor.VOID_BLOCK_COLOR;
    }

    public abstract String getName();

    public abstract int getId();

    public int getItemId() {
        int id = getId();

        if (id > 255) {
            return 255 - id;
        }

        return id;
    }

    /**
     * The full id is a combination of the id and data.
     * @return full id
     */
    public int getFullId() {
        return getId() << DATA_BITS;
    }

    public void addVelocityToEntity(Entity entity, Vector3 vector) {

    }

    public int getDamage() {
        return 0;
    }

    public void setDamage(int meta) {
    }

    public final void setDamage(Integer meta) {
        setDamage((meta == null ? 0 : meta & 0x0f));
    }

    final public void position(Position v) {
        this.x = (int) v.x;
        this.y = (int) v.y;
        this.z = (int) v.z;
        this.level = v.level;
        this.boundingBox = null;
    }

    /**
     * 是否直接掉落方块物品
     * Whether to drop block items directly
     *
     * @param player 玩家
     * @return true - 直接掉落方块物品, false - 通过getDrops方法获取掉落物品
     *         true - Drop block items directly, false - Get dropped items through the getDrops method
     */
    public boolean isDropOriginal(Player player) {
        return false;
    }

    public Item[] getDrops(Item item) {
        if (this.getId() < 0 || this.getId() > list.length) {
            return Item.EMPTY_ARRAY;
        }

        if (this.canHarvestWithHand() || this.canHarvest(item)) {
            return new Item[]{this.toItem()};
        }
        return Item.EMPTY_ARRAY;
    }

    public Item[] getDrops(@Nullable Player player, Item item) {
        if (player != null && !player.isSurvival() && !player.isAdventure()) {
            return Item.EMPTY_ARRAY;
        }

        // 几乎所有方块都是重写getDrops(Item)方法，所以我们需要调用这个方法
        return this.getDrops(item);
    }

    private double customToolBreakTimeBonus(int toolType, @Nullable Integer speed) {
        if (speed != null) return speed;
        else if (toolType == ItemTool.TYPE_SWORD) {
            if (this instanceof BlockCobweb) {
                return 15.0;
            } else if (this instanceof BlockBamboo) {
                return 30.0;
            } else return 1.0;
        } else if (toolType == ItemTool.TYPE_SHEARS) {
            if (this instanceof BlockWool || this instanceof BlockLeaves) {
                return 5.0;
            } else if (this instanceof BlockCobweb) {
                return 15.0;
            } else return 1.0;
        } else if (toolType == ItemTool.TYPE_NONE) return 1.0;
        return 0;
    }

    private double toolBreakTimeBonus0(Item item) {
        if (item instanceof ItemCustomTool itemCustomTool && itemCustomTool.getSpeed() != null) {
            return customToolBreakTimeBonus(customToolType(item), itemCustomTool.getSpeed());
        }
        return toolBreakTimeBonus0(toolType0(item, getId()), item.getTier(), this.getId());
    }

    private static double toolBreakTimeBonus0(int toolType, int toolTier, int blockId) {
        if (toolType == ItemTool.TYPE_SWORD) return blockId == Block.COBWEB ? 15.0 : 1.0;
        if (toolType == ItemTool.TYPE_SHEARS) {
            boolean isLeaves = blockId == LEAVES || blockId == LEAVES2 || blockId == AZALEA_LEAVES
                    || blockId == AZALEA_LEAVES_FLOWERED || blockId == MANGROVE_LEAVES || blockId == CHERRY_LEAVES || blockId == PALE_OAK_LEAVES;
            if (blockId == Block.WOOL || isLeaves) {
                return 5.0;
            } else if (blockId == COBWEB) {
                return 15.0;
            }
            return 1.0;
        }
        if (toolType == ItemTool.TYPE_NONE) return 1.0;
        switch (toolTier) {
            case ItemTool.TIER_WOODEN:
                return 2.0;
            case ItemTool.TIER_STONE:
                return 4.0;
            case ItemTool.TIER_IRON:
                return 6.0;
            case ItemTool.TIER_DIAMOND:
                return 8.0;
            case ItemTool.TIER_NETHERITE:
                return 9.0;
            case ItemTool.TIER_GOLD:
                return 12.0;
            default:
                return 1.0;
        }
    }

    private static double speedBonusByEfficiencyLore0(int efficiencyLoreLevel) {
        if (efficiencyLoreLevel == 0) return 0;
        return efficiencyLoreLevel * efficiencyLoreLevel + 1;
    }

    private static double speedRateByHasteLore0(int hasteLoreLevel) {
        return 1.0 + (0.2 * hasteLoreLevel);
    }

    private int customToolType(Item item) {
        return toolType0(item, this.getId());
    }

    private static int toolType0(Item item, int blockId) {
        if (item.isHoe()) {
            switch (blockId) {
                case LEAVES:
                case LEAVES2:
                case AZALEA_LEAVES:
                case AZALEA_LEAVES_FLOWERED:
                case MANGROVE_LEAVES:
                case CHERRY_LEAVES:
                case PALE_OAK_LEAVES:
                    return ItemTool.TYPE_SHEARS;
            }
        }
        if (item.isSword()) return ItemTool.TYPE_SWORD;
        if (item.isShovel()) return ItemTool.TYPE_SHOVEL;
        if (item.isPickaxe()) return ItemTool.TYPE_PICKAXE;
        if (item.isAxe()) return ItemTool.TYPE_AXE;
        if (item.isHoe()) return ItemTool.TYPE_HOE;
        if (item.isShears()) return ItemTool.TYPE_SHEARS;
        return ItemTool.TYPE_NONE;
    }

    private static boolean correctTool0(int blockToolType, Item item, int blockId) {
        boolean isLeaves = blockId == LEAVES || blockId == LEAVES2 || blockId == AZALEA_LEAVES
                || blockId == AZALEA_LEAVES_FLOWERED || blockId == MANGROVE_LEAVES || blockId == CHERRY_LEAVES || blockId == PALE_OAK_LEAVES;

        if (item.isShears() && (blockId == COBWEB || isLeaves)) {
            return true;
        }

        if (isLeaves && item.isHoe()) {
            return blockToolType == ItemTool.TYPE_SHEARS;
        }

        return (blockToolType == ItemTool.TYPE_SWORD && item.isSword()) ||
                (blockToolType == ItemTool.TYPE_SHOVEL && item.isShovel()) ||
                (blockToolType == ItemTool.TYPE_PICKAXE && item.isPickaxe()) ||
                (blockToolType == ItemTool.TYPE_AXE && item.isAxe()) ||
                (blockToolType == ItemTool.TYPE_HOE && item.isHoe()) ||
                (blockToolType == ItemTool.TYPE_SHEARS && item.isShears()) ||
                blockToolType == ItemTool.TYPE_NONE;
    }

    private static double breakTime0(double blockHardness, boolean correctTool, boolean canHarvestWithHand,
                                     int blockId, int toolType, int toolTier, int efficiencyLoreLevel, int hasteEffectLevel,
                                     boolean insideOfWaterWithoutAquaAffinity, boolean outOfWaterButNotOnGround) {
        double baseTime = ((correctTool || canHarvestWithHand) ? 1.5 : 5.0) * blockHardness;
        double speed = 1.0 / baseTime;
        if (correctTool) speed *= toolBreakTimeBonus0(toolType, toolTier, blockId);
        speed += correctTool ? speedBonusByEfficiencyLore0(efficiencyLoreLevel) : 0;
        speed *= speedRateByHasteLore0(hasteEffectLevel);
        if (insideOfWaterWithoutAquaAffinity) speed *= 0.2;
        if (outOfWaterButNotOnGround) speed *= 0.2;
        return 1.0 / speed;
    }

    public double calculateBreakTime(@NotNull Item item) {
        return calculateBreakTime(item, null);
    }

    public double calculateBreakTime(@NotNull Item item, Player player) {
        double seconds = this.calculateBreakTimeNotInAir(item, player);

        if (player != null) {
            //玩家距离上次在空中过去5tick之后，才认为玩家是在地上挖掘。
            //如果单纯用onGround检测，这个方法返回的时间将会不连续。
            if (player.getServer().getTick() - player.getLastInAirTick() < 5) {
                seconds *= 5;
            }
        }
        return seconds;
    }

    public double calculateBreakTimeNotInAir(@NotNull Item item, @Nullable Player player) {
        Objects.requireNonNull(item, "Block#calculateBreakTime(): Item can not be null");
        double seconds = 0;
        double blockHardness = this.getHardness();
        boolean canHarvest = this.canHarvest(item);

        if (canHarvest) {
            seconds = blockHardness * 1.5;
        } else {
            seconds = blockHardness * 5;
        }

        double speedMultiplier = 1;
        boolean hasConduitPower = false;
        boolean hasAquaAffinity = false;
        int hasteEffectLevel = 0;
        int miningFatigueLevel = 0;
        if (player != null) {
            hasConduitPower = player.hasEffect(Effect.CONDUIT_POWER);
            hasAquaAffinity = Optional.ofNullable(player.getInventory().getHelmet().getEnchantment(Enchantment.ID_WATER_WORKER))
                    .map(Enchantment::getLevel).map(l -> l >= 1).orElse(false);
            hasteEffectLevel = Optional.ofNullable(player.getEffect(Effect.HASTE))
                    .map(Effect::getAmplifier).orElse(-1) + 1;
            miningFatigueLevel = Optional.ofNullable(player.getEffect(Effect.MINING_FATIGUE))
                    .map(Effect::getAmplifier).orElse(-1) + 1;
        }


        int blockId = this.getId();

        if (blockId == BAMBOO && item.isSword()) {
            return 0; //用剑挖竹子时瞬间破坏
        }

        if (correctTool0(this.getToolType(), item, blockId)) {
            speedMultiplier = toolBreakTimeBonus0(item);

            int efficiencyLevel = Optional.ofNullable(item.getEnchantment(Enchantment.ID_EFFICIENCY))
                    .map(Enchantment::getLevel).orElse(0);

            if (canHarvest && efficiencyLevel > 0) {
                speedMultiplier += efficiencyLevel * efficiencyLevel + 1;
            }

            if (hasConduitPower) hasteEffectLevel = Integer.max(hasteEffectLevel, 2);

            if (hasteEffectLevel > 0) {
                speedMultiplier *= 1 + (0.2 * hasteEffectLevel);
            }
        }

        if (miningFatigueLevel > 0) {
            speedMultiplier *= Math.pow(0.3, miningFatigueLevel);
        }

        seconds /= speedMultiplier;

        if (player != null) {
            if (player.isSubmerged() && !hasAquaAffinity) {
                seconds *= hasConduitPower && blockHardness >= 0.5 ? 2.5 : 5;
            }
        }
        return seconds;
    }

    public double getBreakTime(@NotNull Item item, Player player) {
        return calculateBreakTime(item, player);
        /*Objects.requireNonNull(item, "getBreakTime: Item can not be null");
        Objects.requireNonNull(player, "getBreakTime: Player can not be null");
        double blockHardness = getHardness();

        if (blockHardness == 0) {
            return 0;
        }

        int blockId = getId();
        boolean correctTool = correctTool0(getToolType(), item, blockId)
                || item.isShears() && (
                        blockId == COBWEB || blockId == LEAVES || blockId == LEAVES2
                                || blockId == AZALEA_LEAVES || blockId == AZALEA_LEAVES_FLOWERED
                                || blockId == MANGROVE_LEAVES || blockId == CHERRY_LEAVES || blockId == PALE_OAK_LEAVES);
        boolean canHarvestWithHand = canHarvestWithHand();
        int itemToolType = toolType0(item, blockId);
        int itemTier = item.getTier();
        int efficiencyLoreLevel = Optional.ofNullable(item.getEnchantment(Enchantment.ID_EFFICIENCY))
                .map(Enchantment::getLevel).orElse(0);
        int hasteEffectLevel = Optional.ofNullable(player.getEffect(Effect.HASTE))
                .map(Effect::getAmplifier).orElse(0);
        boolean insideOfWaterWithoutAquaAffinity = player.isInsideOfWater() &&
                Optional.ofNullable(player.getInventory().getHelmet().getEnchantment(Enchantment.ID_WATER_WORKER))
                        .map(Enchantment::getLevel).map(l -> l >= 1).orElse(false);
        boolean outOfWaterButNotOnGround = (!player.isInsideOfWater()) && (!player.isOnGround());
        return breakTime0(blockHardness, correctTool, canHarvestWithHand, blockId, itemToolType, itemTier,
                efficiencyLoreLevel, hasteEffectLevel, insideOfWaterWithoutAquaAffinity, outOfWaterButNotOnGround);*/
    }

    public boolean canBeBrokenWith(Item item) {
        return this.getHardness() != -1;
    }

    @Override
    public Block getSide(BlockFace face) {
        return this.getSide(face, 1);
    }

    @Override
    public Block getSide(BlockFace face, int step) {
        return this.getSideAtLayer(layer, face, step);
    }

    public Block getSideAtLayer(int layer, BlockFace face) {
        if (this.isValid()) {
            return this.getLevel().getBlock((int) x + face.getXOffset(), (int) y + face.getYOffset(), (int) z + face.getZOffset(), layer);
        }
        return this.getSide(face, 1);
    }

    public Block getSideAtLayer(int layer, BlockFace face, int step) {
        if (this.isValid()) {
            if (step == 1) {
                return this.getLevel().getBlock((int) x + face.getXOffset(), (int) y + face.getYOffset(), (int) z + face.getZOffset(), layer);
            } else {
                return this.getLevel().getBlock((int) x + face.getXOffset() * step, (int) y + face.getYOffset() * step, (int) z + face.getZOffset() * step, layer);
            }
        }
        Block block = Block.get(Item.AIR, 0);
        block.x = (int) x + face.getXOffset() * step;
        block.y = (int) y + face.getYOffset() * step;
        block.z = (int) z + face.getZOffset() * step;
        block.layer = layer;
        return block;
    }

    protected Block getSideIfLoaded(BlockFace face) {
        if (this.isValid()) {
            return this.level.getBlock(null,
                    (int) this.x + face.getXOffset(), (int) this.y + face.getYOffset(), (int) this.z + face.getZOffset(),
                    0, false);
        }
        return Block.get(AIR, 0, Position.fromObject(new Vector3(this.x, this.y, this.z).getSide(face, 1)), 0);
    }

    protected Block getSideIfLoadedOrNull(BlockFace face) {
        if (this.isValid()) {
            int cx = ((int) this.x + face.getXOffset()) >> 4;
            int cz = ((int) this.z + face.getZOffset()) >> 4;

            FullChunk chunk = this.level.getChunkIfLoaded(cx, cz);
            if (chunk == null) {
                return null;
            }

            return this.level.getBlock(chunk,
                    (int) this.x + face.getXOffset(), (int) this.y + face.getYOffset(), (int) this.z + face.getZOffset(),
                    0, false);
        }

        return Block.get(AIR, 0, Position.fromObject(new Vector3(this.x, this.y, this.z).getSide(face, 1)), 0);
    }

    @Override
    public Block up() {
        return up(1);
    }

    @Override
    public Block up(int step) {
        return getSide(BlockFace.UP, step);
    }

    public Block up(int step, int layer) {
        return this.getSideAtLayer(layer, BlockFace.UP, step);
    }

    @Override
    public Block down() {
        return down(1);
    }

    @Override
    public Block down(int step) {
        return getSide(BlockFace.DOWN, step);
    }

    public Block down(int step, int layer) {
        return this.getSideAtLayer(layer, BlockFace.DOWN, step);
    }

    @Override
    public Block north() {
        return north(1);
    }

    @Override
    public Block north(int step) {
        return getSide(BlockFace.NORTH, step);
    }

    public Block north(int step, int layer) {
        return this.getSideAtLayer(layer, BlockFace.NORTH, step);
    }

    @Override
    public Block south() {
        return south(1);
    }

    @Override
    public Block south(int step) {
        return getSide(BlockFace.SOUTH, step);
    }

    public Block south(int step, int layer) {
        return this.getSideAtLayer(layer, BlockFace.SOUTH, step);
    }

    @Override
    public Block east() {
        return east(1);
    }

    @Override
    public Block east(int step) {
        return getSide(BlockFace.EAST, step);
    }

    public Block east(int step, int layer) {
        return this.getSideAtLayer(layer, BlockFace.EAST, step);
    }

    @Override
    public Block west() {
        return west(1);
    }

    @Override
    public Block west(int step) {
        return getSide(BlockFace.WEST, step);
    }

    public Block west(int step, int layer) {
        return this.getSideAtLayer(layer, BlockFace.WEST, step);
    }

    @Override
    public String toString() {
        return "Block[" + this.getName() + "] (" + this.getId() + ':' + this.getDamage() + ')';
    }

    public boolean collidesWithBB(AxisAlignedBB bb) {
        return collidesWithBB(bb, false);
    }

    public boolean collidesWithBB(AxisAlignedBB bb, boolean collisionBB) {
        AxisAlignedBB bb1 = collisionBB ? this.getCollisionBoundingBox() : this.getBoundingBox();
        return bb1 != null && bb.intersectsWith(bb1);
    }

    public void onEntityCollide(Entity entity) {
    }

    public AxisAlignedBB getBoundingBox() {
        return this.recalculateBoundingBox();
    }

    public AxisAlignedBB getCollisionBoundingBox() {
        return this.recalculateCollisionBoundingBox();
    }

    protected AxisAlignedBB recalculateBoundingBox() {
        return this;
        //return new AxisAlignedBB(this.x, this.y, this.z, this.x + 1.0D, this.y + 1.0D, this.z + 1.0D);
    }

    @Override
    public double getMinX() {
        return this.x;
    }

    @Override
    public double getMinY() {
        return this.y;
    }

    @Override
    public double getMinZ() {
        return this.z;
    }

    @Override
    public double getMaxX() {
        return this.x + 1;
    }

    @Override
    public double getMaxY() {
        return this.y + 1;
    }

    @Override
    public double getMaxZ() {
        return this.z + 1;
    }

    protected AxisAlignedBB recalculateCollisionBoundingBox() {
        return getBoundingBox();
    }

    @Override
    public MovingObjectPosition calculateIntercept(Vector3 pos1, Vector3 pos2) {
        AxisAlignedBB bb = this.getBoundingBox();
        if (bb == null) {
            return null;
        }

        Vector3 v1 = pos1.getIntermediateWithXValue(pos2, bb.getMinX());
        Vector3 v2 = pos1.getIntermediateWithXValue(pos2, bb.getMaxX());
        Vector3 v3 = pos1.getIntermediateWithYValue(pos2, bb.getMinY());
        Vector3 v4 = pos1.getIntermediateWithYValue(pos2, bb.getMaxY());
        Vector3 v5 = pos1.getIntermediateWithZValue(pos2, bb.getMinZ());
        Vector3 v6 = pos1.getIntermediateWithZValue(pos2, bb.getMaxZ());

        if (v1 != null && !bb.isVectorInYZ(v1)) {
            v1 = null;
        }

        if (v2 != null && !bb.isVectorInYZ(v2)) {
            v2 = null;
        }

        if (v3 != null && !bb.isVectorInXZ(v3)) {
            v3 = null;
        }

        if (v4 != null && !bb.isVectorInXZ(v4)) {
            v4 = null;
        }

        if (v5 != null && !bb.isVectorInXY(v5)) {
            v5 = null;
        }

        if (v6 != null && !bb.isVectorInXY(v6)) {
            v6 = null;
        }

        Vector3 vector = v1;

        if (v2 != null && (vector == null || pos1.distanceSquared(v2) < pos1.distanceSquared(vector))) {
            vector = v2;
        }

        if (v3 != null && (vector == null || pos1.distanceSquared(v3) < pos1.distanceSquared(vector))) {
            vector = v3;
        }

        if (v4 != null && (vector == null || pos1.distanceSquared(v4) < pos1.distanceSquared(vector))) {
            vector = v4;
        }

        if (v5 != null && (vector == null || pos1.distanceSquared(v5) < pos1.distanceSquared(vector))) {
            vector = v5;
        }

        if (v6 != null && (vector == null || pos1.distanceSquared(v6) < pos1.distanceSquared(vector))) {
            vector = v6;
        }

        if (vector == null) {
            return null;
        }

        int f = -1;

        if (vector == v1) {
            f = 4;
        } else if (vector == v2) {
            f = 5;
        } else if (vector == v3) {
            f = 0;
        } else if (vector == v4) {
            f = 1;
        } else if (vector == v5) {
            f = 2;
        } else if (vector == v6) {
            f = 3;
        }

        return MovingObjectPosition.fromBlock((int) this.x, (int) this.y, (int) this.z, f, vector.add(this.x, this.y, this.z));
    }

    public boolean isSideFull(BlockFace face) {
        AxisAlignedBB boundingBox = getBoundingBox();
        if (boundingBox == null) {
            return false;
        }

        if (face.getAxis().getPlane() == BlockFace.Plane.HORIZONTAL) {
            if (boundingBox.getMinY() != getY() || boundingBox.getMaxY() != getY() + 1) {
                return false;
            }
            int offset = face.getXOffset();
            if (offset < 0) {
                return boundingBox.getMinX() == getX()
                        && boundingBox.getMinZ() == getZ() && boundingBox.getMaxZ() == getZ() + 1;
            } else if (offset > 0) {
                return boundingBox.getMaxX() == getX() + 1
                        && boundingBox.getMaxZ() == getZ() + 1 && boundingBox.getMinZ() == getZ();
            }

            offset = face.getZOffset();
            if (offset < 0) {
                return boundingBox.getMinZ() == getZ()
                        && boundingBox.getMinX() == getX() && boundingBox.getMaxX() == getX() + 1;
            }

            return boundingBox.getMaxZ() == getZ() + 1
                    && boundingBox.getMaxX() == getX() + 1 && boundingBox.getMinX() == getX();
        }

        if (boundingBox.getMinX() != getX() || boundingBox.getMaxX() != getX() + 1 ||
                boundingBox.getMinZ() != getZ() || boundingBox.getMaxZ() != getZ() + 1) {
            return false;
        }

        if (face.getYOffset() < 0) {
            return boundingBox.getMinY() == getY();
        }

        return boundingBox.getMaxY() == getY() + 1;
    }

    public String getSaveId() {
        String name = getClass().getName();
        return name.substring(16);
    }

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) throws Exception {
        if (this.getLevel() != null) {
            this.getLevel().getBlockMetadata().setMetadata(this, metadataKey, newMetadataValue);
        }
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) throws Exception {
        if (this.getLevel() != null) {
            return this.getLevel().getBlockMetadata().getMetadata(this, metadataKey);

        }
        return null;
    }

    @Override
    public boolean hasMetadata(String metadataKey) throws Exception {
        return this.getLevel() != null && this.getLevel().getBlockMetadata().hasMetadata(this, metadataKey);
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) throws Exception {
        if (this.getLevel() != null) {
            this.getLevel().getBlockMetadata().removeMetadata(this, metadataKey, owningPlugin);
        }
    }

    @NotNull
    public final Block getBlock() {
        return clone();
    }

    @Override
    public Block clone() {
        return (Block) super.clone();
    }

    public int getWeakPower(BlockFace face) {
        return 0;
    }

    public int getStrongPower(BlockFace side) {
        return 0;
    }

    public boolean isPowerSource() {
        return false;
    }

    public String getLocationHash() {
        return this.getFloorX() + ":" + this.getFloorY() + ':' + this.getFloorZ();
    }

    public int getDropExp() {
        return 0;
    }

    public boolean isNormalBlock() {
        return !isTransparent() && isSolid() && !isPowerSource();
    }

    /**
     * Compare whether two blocks are the same, this method compares block entities
     *
     * @param obj the obj
     * @return the boolean
     */
    public boolean equalsBlock(Object obj) {
        if (obj instanceof Block otherBlock) {
            if (!(this instanceof BlockEntityHolder<?>) && !(otherBlock instanceof BlockEntityHolder<?>)) {
                return this.getId() == otherBlock.getId() && this.getDamage() == otherBlock.getDamage();
            }
            if (this instanceof BlockEntityHolder<?> holder1 && otherBlock instanceof BlockEntityHolder<?> holder2) {
                BlockEntity be1 = holder1.getOrCreateBlockEntity();
                BlockEntity be2 = holder2.getOrCreateBlockEntity();
                return this.getId() == otherBlock.getId() && this.getDamage() == otherBlock.getDamage() && be1.getCleanedNBT().equals(be2.getCleanedNBT());
            }
        }
        return false;
    }

    public static boolean equals(Block b1, Block b2) {
        return equals(b1, b2, true);
    }

    public static boolean equals(Block b1, Block b2, boolean checkDamage) {
        return b1 != null && b2 != null && b1.getId() == b2.getId() && (!checkDamage || b1.getDamage() == b2.getDamage());
    }

    @Override
    public int hashCode() {
        return  ((int) x ^ ((int) z << 12)) ^ ((int) (y + 64)/*这里不删除+64，为以后支持384世界高度准备*/ << 23);
    }

    public Item toItem() {
        return new ItemBlock(this, this.getDamage(), 1);
    }

    public Optional<Block> firstInLayers(Predicate<Block> condition) {
        return firstInLayers(0, condition);
    }

    public Optional<Block> firstInLayers(int startingLayer, Predicate<Block> condition) {
        int maximumLayer = this.level.getProvider().getMaximumLayer();
        for (int layer = startingLayer; layer <= maximumLayer; layer++) {
            Block block = this.getLevelBlockAtLayer(layer);
            if (condition.test(block)) {
                return Optional.of(block);
            }
        }

        return Optional.empty();
    }

    public boolean canSilkTouch() {
        return false;
    }

    public boolean isAir() {
        return false;
    }

    public boolean isLiquid() {
        return false;
    }

    public boolean isLiquidSource() {
        return false;
    }

    public boolean isWater() {
        return false;
    }

    public boolean isWaterSource() {
        return false;
    }

    public boolean isWaxed() {
        return false;
    }

    public boolean hasCopperBehavior() {
        return false;
    }

    public int getCopperAge() {
        return -1;
    }

    protected static boolean canConnectToFullSolid(Block down) {
        if (down.isTransparent()) {
            switch (down.getId()) {
                case BEACON:
                case ICE:
                case GLASS:
                case STAINED_GLASS:
                case HARD_GLASS:
                case HARD_STAINED_GLASS:
                case BARRIER:
                case GLOWSTONE:
                case SEA_LANTERN:
                case MANGROVE_ROOTS:
                case MUDDY_MANGROVE_ROOTS:
                case MONSTER_SPAWNER:
                    return true;
            }
            return false;
        }
        return true;
    }

    protected static boolean canStayOnFullSolid(Block down) {
        if (canConnectToFullSolid(down)) {
            return true;
        }

        switch (down.getId()) {
            case SCAFFOLDING:
            case HOPPER_BLOCK:
                return true;
        }

        if (down instanceof BlockSlab slab) {
            return slab.hasTopBit();
        }

        return down instanceof BlockTrapdoor && ((BlockTrapdoor) down).isTop() && !((BlockTrapdoor) down).isOpen();
    }

    protected boolean isNarrowSurface() {
        return this instanceof BlockGlassPane || this instanceof BlockFence || this instanceof BlockWall || this instanceof BlockChain || this instanceof BlockIronBars;
    }


    /**
     * 被爆炸破坏时必定掉落<br>
     * Drop when destroyed by explosion
     *
     * @return 是否必定掉落<br>Whether to drop
     */
    public boolean alwaysDropsOnExplosion() {
        return false;
    }

    /**
     * Returns true for WATER or STILL_WATER, false for others
     */
    public static boolean isWater(int id) {
        return BlockTypes.isWater(id);
    }

    /**
     * Returns true for LAVA or STILL_LAVA, false for others
     */
    public static boolean isLava(int id) {
        return BlockTypes.isLava(id);
    }

    /**
     * Returns true for SLAB, false for others
     */
    public static boolean isSlab(int id) {
        return BlockTypes.isSlab(id);
    }

    /**
     * Returns true for STAIRS, false for others
     */
    public static boolean isStairs(int id) {
        return BlockTypes.isStairs(id);
    }

    /**
     * Returns true for PRESSURE_PLATE, false for others
     */
    public static boolean isPressurePlate(int id) {
        return BlockTypes.isPressurePlate(id);
    }

    /**
     * Returns true for BUTTON, false for others
     */
    public static boolean isButton(int id) {
        return BlockTypes.isButton(id);
    }

    /**
     * Returns true for FENCE, false for others
     */
    public static boolean isFence(int id) {
        return BlockTypes.isFence(id);
    }

    /**
     * Returns true for FENCE_GATE, false for others
     */
    public static boolean isFenceGate(int id) {
        return BlockTypes.isFenceGate(id);
    }

    /**
     * Returns true for TRAPDOOR, false for others
     */
    public static boolean isTrapdoor(int id) {
        return BlockTypes.isTrapdoor(id);
    }

    /**
     * Returns true for DOOR, false for others
     */
    public static boolean isDoor(int id) {
        return BlockTypes.isDoor(id);
    }

    public boolean isSuspiciousBlock() {
        return false;
    }

    public PersistentDataContainer getPersistentDataContainer() {
        if (!this.isValid()) {
            throw new IllegalStateException("Block does not have valid level");
        }
        return this.level.getPersistentDataContainer(this);
    }

    @SuppressWarnings("unused")
    public boolean hasPersistentDataContainer() {
        if (!this.isValid()) {
            throw new IllegalStateException("Block does not have valid level");
        }
        return this.level.hasPersistentDataContainer(this);
    }

    public boolean cloneTo(Position pos) {
        return cloneTo(pos, true);
    }

    /**
     * 将方块克隆到指定位置<p/>
     * 此方法会连带克隆方块实体<p/>
     * 注意，此方法会先清除指定位置的方块为空气再进行克隆
     *
     * @param pos    要克隆到的位置
     * @param update 是否需要更新克隆的方块
     * @return 是否克隆成功
     */
    @SuppressWarnings("null")
    public boolean cloneTo(Position pos, boolean update) {
        //清除旧方块
        level.setBlock(pos, this.layer, Block.get(Block.AIR), false, false);
        if (this instanceof BlockEntityHolder<?> holder && holder.getBlockEntity() != null) {
            var clonedBlock = this.clone();
            clonedBlock.position(pos);
            CompoundTag tag = holder.getBlockEntity().getCleanedNBT();
            //方块实体要求direct=true
            return BlockEntityHolder.setBlockAndCreateEntity((BlockEntityHolder<?>) clonedBlock, true, update, tag) != null;
        } else {
            return pos.level.setBlock(pos, this.layer, this.clone(), true, update);
        }
    }
}
