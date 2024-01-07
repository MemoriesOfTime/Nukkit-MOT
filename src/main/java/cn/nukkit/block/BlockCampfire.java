package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityCampfire;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageByBlockEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.inventory.CampfireInventory;
import cn.nukkit.inventory.CampfireRecipe;
import cn.nukkit.inventory.ContainerInventory;
import cn.nukkit.item.*;
import cn.nukkit.level.Level;
import cn.nukkit.level.Sound;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class BlockCampfire extends BlockTransparentMeta implements Faceable, BlockEntityHolder<BlockEntityCampfire> {
    public BlockCampfire() {
        super(0);
    }

    public BlockCampfire(final int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CAMPFIRE_BLOCK;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.CAMPFIRE;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityCampfire> getBlockEntityClass() {
        return BlockEntityCampfire.class;
    }

    @Override
    public int getLightLevel() {
        return isExtinguished() ? 0 : 15;
    }

    @Override
    public double getResistance() {
        return 10;
    }

    @Override
    public double getHardness() {
        return 2.0;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public Item[] getDrops(final Item item) {
        return new Item[]{new ItemCoal(0, 1 + ThreadLocalRandom.current().nextInt(1))};
    }

    @Override
    public boolean canSilkTouch() {
        return true;
    }

    @Override
    public int getWaterloggingLevel() {
        return 1;
    }

    @Override
    public boolean place(final Item item, final Block block, final Block target, final BlockFace face, final double fx, final double fy, final double fz, final Player player) {
        if (down().getId() == CAMPFIRE_BLOCK) {
            return false;
        }

        setDamage(player != null ? player.getDirection().getOpposite().getHorizontalIndex() : 0);
        final Block layer1 = block.getLevelBlockAtLayer(1);
        final boolean defaultLayerCheck = block instanceof BlockWater && block.getDamage() == 0 || block.getDamage() >= 8 || block instanceof BlockIceFrosted;
        final boolean layer1Check = layer1 instanceof BlockWater && layer1.getDamage() == 0 || layer1.getDamage() >= 8 || layer1 instanceof BlockIceFrosted;
        if (defaultLayerCheck || layer1Check) {
            setExtinguished(true);
            level.addSound(this, Sound.RANDOM_FIZZ, 0.5f, 2.2f);
            level.setBlock(this, 1, defaultLayerCheck ? block : layer1, false, false);
        } else {
            level.setBlock(this, 1, Block.get(Block.AIR), false, false);
        }

        level.setBlock(block, this, true, false);
        createBlockEntity(item);
        level.updateAround(this);
        return true;
    }

    private BlockEntityCampfire createBlockEntity(final Item item) {
        final CompoundTag nbt = new CompoundTag()
                .putString("id", BlockEntity.CAMPFIRE)
                .putInt("x", (int) x)
                .putInt("y", (int) y)
                .putInt("z", (int) z);

        if (item.hasCustomBlockData()) {
            final Map<String, Tag> customData = item.getCustomBlockData().getTags();
            for (final Map.Entry<String, Tag> tag : customData.entrySet()) {
                nbt.put(tag.getKey(), tag.getValue());
            }
        }

        return (BlockEntityCampfire) BlockEntity.createBlockEntity(BlockEntity.CAMPFIRE, level.getChunk((int) x >> 4, (int) z >> 4), nbt);
    }

    @Override
    public boolean hasEntityCollision() {
        return true;
    }

    @Override
    public void onEntityCollide(final Entity entity) {
        if (!isExtinguished() && !entity.isSneaking()) {
            entity.attack(new EntityDamageByBlockEvent(this, entity, EntityDamageEvent.DamageCause.FIRE, 1));
        }
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public int onUpdate(final int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (!isExtinguished()) {
                final Block layer1 = getLevelBlockAtLayer(1);
                if (layer1 instanceof BlockWater || layer1 instanceof BlockIceFrosted) {
                    setExtinguished(true);
                    level.setBlock(this, this, true, true);
                    level.addSound(this, Sound.RANDOM_FIZZ, 0.5f, 2.2f);
                }
            }
            return type;
        }
        return 0;
    }

    @Override
    public boolean onActivate(final Item item, final Player player) {
        if (item.getId() == AIR || item.getCount() <= 0) {
            return false;
        }

        BlockEntity entity = level.getBlockEntity(this);
        if (!(entity instanceof BlockEntityCampfire)) {
            entity = createBlockEntity(Item.get(BlockID.AIR));
        }

        boolean itemUsed = false;
        if (item.isShovel() && !isExtinguished()) {
            setExtinguished(true);
            level.setBlock(this, this, true, true);
            level.addSound(this, Sound.RANDOM_FIZZ, 0.5f, 2.2f);
            itemUsed = true;
        } else if (item.getId() == ItemID.FLINT_AND_STEEL) {
            item.useOn(this);
            setExtinguished(false);
            level.setBlock(this, this, true, true);
            if (entity != null) {
                entity.scheduleUpdate();
            }
            level.addSound(this, Sound.FIRE_IGNITE);
            itemUsed = true;
        }

        if (entity == null) {
            return itemUsed;
        }

        final BlockEntityCampfire campfire = (BlockEntityCampfire) entity;
        final Item cloned = item.clone();
        cloned.setCount(1);
        final CampfireInventory inventory = campfire.getInventory();
        if (inventory.canAddItem(cloned)) {
            final CampfireRecipe recipe = level.getServer().getCraftingManager().matchCampfireRecipe(cloned);
            if (recipe != null) {
                inventory.addItem(cloned);
                item.setCount(item.getCount() - 1);
                return true;
            }
        }

        return itemUsed;
    }

    @Override
    public double getMaxY() {
        return y + 0.4371948;
    }

    @Override
    protected AxisAlignedBB recalculateCollisionBoundingBox() {
        return new SimpleAxisAlignedBB(x, y, z, x + 1, y + 1, z + 1);
    }

    public final boolean isExtinguished() {
        return (getDamage() & 0x4) == 0x4;
    }

    public final void setExtinguished(final boolean extinguished) {
        setDamage(getDamage() & 0x3 | (extinguished ? 0x4 : 0x0));
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(getDamage() & 0x3);
    }

    public void setBlockFace(final BlockFace face) {
        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            return;
        }

        setDamage(getDamage() & 0x4 | face.getHorizontalIndex());
    }

    @Override
    public String getName() {
        return "Campfire";
    }

    @Override
    public Item toItem() {
        return new ItemCampfire();
    }

    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride() {
        final BlockEntity tile = level.getBlockEntity(this);

        if (tile instanceof BlockEntityCampfire) {
            return ContainerInventory.calculateRedstone(((BlockEntityCampfire) tile).getInventory());
        }

        return super.getComparatorInputOverride();
    }
}
