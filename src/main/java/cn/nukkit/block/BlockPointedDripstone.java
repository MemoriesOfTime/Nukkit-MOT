package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.custom.properties.BooleanBlockProperty;
import cn.nukkit.block.custom.properties.EnumBlockProperty;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.enums.CauldronLiquid;
import cn.nukkit.block.properties.enums.DripstoneThickness;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityCauldron;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityFallingBlock;
import cn.nukkit.event.block.BlockFallEvent;
import cn.nukkit.event.block.BlockGrowEvent;
import cn.nukkit.event.block.CauldronFilledByDrippingLiquidEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.Sound;
import cn.nukkit.level.particle.DestroyBlockParticle;
import cn.nukkit.level.particle.GenericParticle;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.level.vibration.VibrationEvent;
import cn.nukkit.level.vibration.VibrationType;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Faceable;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class BlockPointedDripstone extends BlockSolidMeta implements BlockPropertiesHelper, Faceable {

    private static final float GROWTH_PROBABILITY = 0.011377778F;
    private static final int MAX_HEIGHT = 7;

    private static final float DRIP_WATER_PROBABILITY = 45.0F / 256.0F;
    private static final float DRIP_LAVA_PROBABILITY = 15.0F / 256.0F;
    private static final int MAX_DRIPSTONE_COLUMN = 11;
    private static final int MAX_FILL_LEVEL = 3;

    private static final EnumBlockProperty<DripstoneThickness> THICKNESS = new EnumBlockProperty<>("dripstone_thickness", false, DripstoneThickness.class);
    private static final BooleanBlockProperty HANGING = new BooleanBlockProperty("hanging", false);
    private static final BlockProperties PROPERTIES = new BlockProperties(HANGING, THICKNESS);

    public BlockPointedDripstone() {
        this(0);
    }

    public BlockPointedDripstone(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Pointed Dripstone";
    }

    @Override
    public int getId() {
        return POINTED_DRIPSTONE;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:pointed_dripstone";
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        if (!this.canPlaceOn(block.down(), target)) {
            return false;
        }

        Block up = this.up();
        Block down = this.down();

        boolean hanging = false;
        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            if ((face == BlockFace.UP && !down.isSolid()) || (face == BlockFace.DOWN && !up.isSolid())) {
                return false;
            }
            hanging = face == BlockFace.DOWN;
        } else if (up.isSolid()) {
            hanging = true;
        } else if (!down.isSolid()) {
            return false;
        }


        Block tip = null;
        if (up instanceof BlockPointedDripstone && hanging) {
            tip = up;
        } else if (down instanceof BlockPointedDripstone) {
            tip = down;
        }

        if (tip != null) {
            IntObjectPair<Block> pair = this.getDripstoneHeightFromTip(tip, hanging);
            int height = pair.keyInt();
            if (height == 0 || height == MAX_HEIGHT) {
                return false;
            }
            Location location = pair.right().getLocation();
            this.growPointedDripstone(location, hanging, height);
        } else {
            this.setHanging(hanging);
            this.setThickness(DripstoneThickness.TIP);
            this.getLevel().setBlock(this, this, true, true);
        }
        return true;
    }

    @Override
    public boolean onBreak(Item item, Player player) {
        boolean hanging = this.isHanging();

        Block newTip = hanging ? this.up() : this.down();
        if (newTip instanceof BlockPointedDripstone) {
            ((BlockPointedDripstone) newTip).setThickness(DripstoneThickness.TIP);
            this.getLevel().setBlock(newTip, newTip);
        }

        DripstoneThickness thickness = this.getThickness();
        if (thickness == DripstoneThickness.TIP || thickness == DripstoneThickness.MERGE) {
            return super.onBreak(item, player);
        }

        Block block = this;
        while (block instanceof BlockPointedDripstone) {
            BlockPointedDripstone dripstone = (BlockPointedDripstone) block;
            if (this != dripstone) {
                this.getLevel().addParticle(new DestroyBlockParticle(block.add(0.5), block));
                if (hanging) {
                    this.spawnFallingBlock(dripstone);
                } else {
                    this.getLevel().dropItem(block.add(0.5, 0.5, 0.5), block.toItem());
                }
            }
            this.getLevel().setBlock(block, Block.get(BlockID.AIR), false, true);
            block = hanging ? block.down() : block.up();
        }
        return true;
    }

    @Override
    public int onUpdate(int type) {
        if (type != Level.BLOCK_UPDATE_RANDOM) {
            return 0;
        }

        // Dripping liquid into a cauldron below: a hanging tip does this
        // independently of growth, so run it before the early growth guard returns.
        if (this.isHanging() && this.getThickness() == DripstoneThickness.TIP) {
            this.dripLiquidIntoCauldron();
        }

        if (ThreadLocalRandom.current().nextFloat() >= GROWTH_PROBABILITY || !this.isHanging() || this.up().getId() == POINTED_DRIPSTONE) {
            return 0;
        }

        int height;
        if (this.canGrow() && (height = this.getDripstoneHeightFromBase(this, true)) < MAX_HEIGHT) {
            BlockGrowEvent event = new BlockGrowEvent(this, Block.get(BlockID.POINTED_DRIPSTONE));
            this.getLevel().getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return 0;
            }
            this.growPointedDripstone(this.getLocation(), true, height);
        }

        // TODO: grow from ground too
        return 0;
    }

    /**
     * Vanilla behavior: a hanging pointed dripstone tip can drip the liquid above
     * the column into a cauldron below it.
     * Water fills at 45/256, lava at 15/256, each random tick.
     */
    private void dripLiquidIntoCauldron() {
        Block root = this.up();
        int height = 1;
        while (root instanceof BlockPointedDripstone) {
            root = root.up();
            height++;
            if (height >= MAX_DRIPSTONE_COLUMN) {
                return;
            }
        }

        Block source = root.up();
        boolean mudSource = root.getId() == BlockID.MUD;
        CauldronLiquid liquid = null;
        if (mudSource) {
            liquid = CauldronLiquid.WATER;
        } else if (isWaterSource(source)) {
            liquid = CauldronLiquid.WATER;
        } else if (isLavaSource(source)) {
            liquid = CauldronLiquid.LAVA;
        }
        if (liquid == null) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean isWater = liquid == CauldronLiquid.WATER;
        if (random.nextFloat() >= (isWater ? DRIP_WATER_PROBABILITY : DRIP_LAVA_PROBABILITY)) {
            return;
        }

        spawnDripParticle(isWater);

        if (mudSource) {
            turnMudToClay(root);
            return;
        }

        Block cursor = this;
        int searched = 0;
        Block target = null;
        while (searched < MAX_DRIPSTONE_COLUMN) {
            Block below = cursor.down();
            if (below instanceof BlockCauldron) {
                target = below;
                break;
            }
            if (!canDripThrough(below)) {
                break;
            }
            cursor = below;
            searched++;
        }
        if (target == null) {
            return;
        }

        BlockCauldron cauldron = (BlockCauldron) target;
        if (!canReceiveDrip(cauldron, liquid)) {
            return;
        }

        CauldronFilledByDrippingLiquidEvent event = new CauldronFilledByDrippingLiquidEvent(cauldron, this, liquid, 1);
        this.getLevel().getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        liquid = event.getLiquid();
        int increment = event.getLiquidLevelIncrement();

        // Re-check after the event so a plugin cannot break the "lava only fills an empty
        // cauldron" invariant by swapping the liquid.
        if (!canReceiveDrip(cauldron, liquid)) {
            return;
        }

        if (liquid == CauldronLiquid.LAVA) {
            fillLava(cauldron);
        } else {
            fillWater(cauldron, increment);
        }
    }

    private void spawnDripParticle(boolean isWater) {
        int type = isWater ? Particle.TYPE_STALACTITE_DRIP_WATER : Particle.TYPE_STALACTITE_DRIP_LAVA;
        this.getLevel().addParticle(new GenericParticle(this.add(0.5, 0, 0.5), type));
    }

    /**
     * Vanilla: a mud block above a water-dripping stalactite column is converted to clay.
     * Emits a BLOCK_CHANGE vibration (vanilla gameEvent(BLOCK_CHANGE)).
     * <p>
     * The pushEntitiesUp step is omitted: mud and clay are both full 1-block solids with the
     * same collision box, so an entity standing on the mud cannot be embedded by the swap.
     */
    private void turnMudToClay(Block mud) {
        Block clay = Block.get(BlockID.CLAY_BLOCK);
        this.getLevel().setBlock(mud, clay, true, true);
        this.getLevel().updateComparatorOutputLevel(mud);
        this.getLevel().getVibrationManager().callVibrationEvent(
                new VibrationEvent(clay, mud.add(0.5, 0.5, 0.5), VibrationType.BLOCK_CHANGE));
    }

    private static boolean isWaterSource(Block block) {
        return block instanceof BlockWater && block.isLiquidSource();
    }

    private static boolean isLavaSource(Block block) {
        return block instanceof BlockLava && block.isLiquidSource();
    }

    /**
     * Mirrors vanilla {@code PointedDripstoneBlock#canDripThrough}: a drip passes through air,
     * non-solid (transparent) blocks without their own fluid, and blocks whose collision shape
     * does not fill the space below the drip. Liquids and opaque solids block the drip.
     */
    private static boolean canDripThrough(Block block) {
        if (block instanceof BlockAir) {
            return true;
        }
        if (!block.isTransparent()) {
            return false;
        }
        if (block instanceof BlockLiquid) {
            return false;
        }
        return true;
    }

    /**
     * A cauldron can receive a drip only when empty or already holding the same liquid,
     * and only when not already full. Water is rejected if the cauldron holds a potion or dye.
     */
    private static boolean canReceiveDrip(BlockCauldron cauldron, CauldronLiquid liquid) {
        if (cauldron.isFull()) {
            return false;
        }
        if (liquid == CauldronLiquid.WATER) {
            if (cauldron instanceof BlockCauldronLava) {
                return false;
            }
            if (cauldron.level != null) {
                BlockEntity be = cauldron.level.getBlockEntity(cauldron);
                if (be instanceof BlockEntityCauldron) {
                    BlockEntityCauldron entity = (BlockEntityCauldron) be;
                    if (entity.hasPotion() || entity.isCustomColor()) {
                        return false;
                    }
                }
            }
            return true;
        }
        // Lava: only into an empty cauldron. A lava cauldron is always full once
        // formed, and a water cauldron rejects lava (vanilla silently ignores it).
        if (cauldron instanceof BlockCauldronLava) {
            return false;
        }
        return cauldron.isEmpty();
    }

    private void fillWater(BlockCauldron cauldron, int increment) {
        int level = Math.min(cauldron.getFillLevel() + increment, MAX_FILL_LEVEL);
        cauldron.setFillLevel(level);
        this.getLevel().setBlock(cauldron, cauldron, true, true);
        this.getLevel().updateComparatorOutputLevel(cauldron);
        this.getLevel().addSoundToViewers(cauldron.add(0.5, 0.5, 0.5), Sound.CAULDRON_DRIP_WATER_POINTED_DRIPSTONE);
        this.getLevel().getVibrationManager().callVibrationEvent(
                new VibrationEvent(cauldron, cauldron.add(0.5, 0.5, 0.5), VibrationType.BLOCK_CHANGE));
    }

    private void fillLava(BlockCauldron cauldron) {
        BlockCauldronLava lava = new BlockCauldronLava(14);
        this.getLevel().setBlock(cauldron, lava, true, true);
        this.getLevel().updateComparatorOutputLevel(cauldron);
        this.getLevel().addSoundToViewers(cauldron.add(0.5, 0.5, 0.5), Sound.CAULDRON_DRIP_LAVA_POINTED_DRIPSTONE);
        this.getLevel().getVibrationManager().callVibrationEvent(
                new VibrationEvent(cauldron, cauldron.add(0.5, 0.5, 0.5), VibrationType.BLOCK_CHANGE));
    }

    private void growPointedDripstone(Position position, boolean hanging, int height) {
        this.buildBaseToTipColumn(height + 1, false, thickness -> {
            BlockPointedDripstone dripstone = (BlockPointedDripstone) Block.get(POINTED_DRIPSTONE);
            dripstone.setHanging(hanging);
            dripstone.setThickness(thickness);
            this.getLevel().setBlock(position, dripstone);
            position.setY(hanging ? position.getY() - 1 : position.getY() + 1);
        });
    }

    private IntObjectPair<Block> getDripstoneHeightFromTip(Block block, boolean hanging) {
        int height = 0;
        BlockPointedDripstone dripstone = null;
        while (block instanceof BlockPointedDripstone) {
            height++;
            dripstone = (BlockPointedDripstone) block;
            block = hanging ? block.up() : block.down();
        }
        return IntObjectPair.of(height, dripstone);
    }

    private int getDripstoneHeightFromBase(Block block, boolean hanging) {
        int height = 0;
        while (block instanceof BlockPointedDripstone) {
            height++;
            block = hanging ? block.down() : block.up();
        }
        return height;
    }

    private void buildBaseToTipColumn(int height, boolean merge, Consumer<DripstoneThickness> callback) {
        if (height >= 3) {
            callback.accept(DripstoneThickness.BASE);
            for(int i = 0; i < height - 3; ++i) {
                callback.accept(DripstoneThickness.MIDDLE);
            }
        }

        if (height >= 2) {
            callback.accept(DripstoneThickness.FRUSTUM);
        }

        if (height >= 1) {
            callback.accept(merge ? DripstoneThickness.MERGE : DripstoneThickness.TIP);
        }
    }

    private boolean canGrow() {
        Block up2;
        // TODO: grow from ground too
        return this.down().getId() == AIR && this.up().getId() == DRIPSTONE_BLOCK && ((up2 = this.up(2)).getId() == WATER || up2.getId() == STILL_WATER);
    }

    private void spawnFallingBlock(BlockPointedDripstone block) {
        BlockFallEvent event = new BlockFallEvent(block);
        this.level.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", this.x + 0.5))
                        .add(new DoubleTag("", this.y))
                        .add(new DoubleTag("", this.z + 0.5)))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0)))

                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("", 0))
                        .add(new FloatTag("", 0)))
                .putInt("TileID", this.getId())
                .putByte("Data", this.getDamage());

        Entity.createEntity(EntityFallingBlock.NETWORK_ID, this.getLevel().getChunk((int) this.x >> 4, (int) this.z >> 4), nbt).spawnToAll();
    }

    @Override
    public double getHardness() {
        return 1.5;
    }

    @Override
    public double getResistance() {
        return 3;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId()), 0, 1);
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BROWN_TERRACOTA_BLOCK_COLOR;
    }

    @Override
    public BlockFace getBlockFace() {
        return this.getBooleanValue(HANGING) ? BlockFace.DOWN : BlockFace.UP;
    }

    public boolean isHanging() {
        return this.getBooleanValue(HANGING);
    }

    public void setHanging(boolean hanging) {
        this.setBooleanValue(HANGING, hanging);
    }

    public DripstoneThickness getThickness() {
        return this.getPropertyValue(THICKNESS);
    }

    public void setThickness(DripstoneThickness value) {
        this.setPropertyValue(THICKNESS, value);
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }
}
