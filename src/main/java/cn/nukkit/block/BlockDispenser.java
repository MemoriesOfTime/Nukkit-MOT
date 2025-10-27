package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityDispenser;
import cn.nukkit.dispenser.DispenseBehavior;
import cn.nukkit.dispenser.DispenseBehaviorRegister;
import cn.nukkit.inventory.ContainerInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;

import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by CreeperFace on 15.4.2017.
 */
public class BlockDispenser extends BlockSolidMeta implements Faceable, BlockEntityHolder<BlockEntityDispenser> {

    public BlockDispenser() {
        this(0);
    }

    public BlockDispenser(int meta) {
        super(meta);
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public String getName() {
        return "Dispenser";
    }

    @Override
    public int getId() {
        return DISPENSER;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityDispenser> getBlockEntityClass() {
        return BlockEntityDispenser.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.DISPENSER;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(this, 0);
    }

    @Override
    public int getComparatorInputOverride() {
        BlockEntity blockEntity = this.level.getBlockEntity(this);

        if (blockEntity instanceof BlockEntityDispenser) {
            return ContainerInventory.calculateRedstone(((BlockEntityDispenser) blockEntity).getInventory());
        }

        return 0;
    }

    public boolean isTriggered() {
        return (this.getDamage() & 8) > 0;
    }

    public void setTriggered(boolean value) {
        int i = 0;
        i |= getBlockFace().getIndex();

        if (value) {
            i |= 8;
        }

        this.setDamage(i);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (player == null) {
            return false;
        }

        BlockEntity blockEntity = this.level.getBlockEntity(this);

        if (!(blockEntity instanceof BlockEntityDispenser)) {
            return false;
        }

        player.addWindow(((BlockEntityDispenser) blockEntity).getInventory());
        return true;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        if (player != null) {
            if (Math.abs(player.x - this.x) < 2 && Math.abs(player.z - this.z) < 2) {
                double y = player.y + player.getEyeHeight();

                if (y - this.y > 2) {
                    this.setDamage(BlockFace.UP.getIndex());
                } else if (this.y - y > 0) {
                    this.setDamage(BlockFace.DOWN.getIndex());
                } else {
                    this.setDamage(player.getHorizontalFacing().getOpposite().getIndex());
                }
            } else {
                this.setDamage(player.getHorizontalFacing().getOpposite().getIndex());
            }
        }

        this.getLevel().setBlock(block, this, true);

        BlockEntity.createBlockEntity(BlockEntity.DISPENSER, this.getChunk(), BlockEntity.getDefaultCompound(this, BlockEntity.DISPENSER));
        return true;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_SCHEDULED) {
            this.setTriggered(false);
            this.level.setBlock(this, this, false, false);

            dispense();
            return type;
        }

        if (type == Level.BLOCK_UPDATE_REDSTONE) {
            if ((level.isBlockPowered(this) || level.isBlockPowered(this.up())) && !isTriggered()) {
                this.setTriggered(true);
                this.level.setBlock(this, this, false, false);
                level.scheduleUpdate(this, this, 4);
            }

            return type;
        }

        return 0;
    }

    public void dispense() {
        BlockEntity blockEntity = this.level.getBlockEntity(this);

        if (!(blockEntity instanceof BlockEntityDispenser)) {
            return;
        }

        Random rand = ThreadLocalRandom.current();
        int r = 1;
        int slot = -1;
        Item original = null;

        Inventory inv = ((BlockEntityDispenser) blockEntity).getInventory();
        for (Entry<Integer, Item> entry : inv.getContents().entrySet()) {
            Item item = entry.getValue();

            if (!item.isNull() && rand.nextInt(r++) == 0) {
                original = item;
                slot = entry.getKey();
            }
        }

        LevelEventPacket pk = new LevelEventPacket();

        BlockFace facing = getBlockFace();

        pk.x = 0.5f + facing.getXOffset() * 0.7f;
        pk.y = 0.5f + facing.getYOffset() * 0.7f;
        pk.z = 0.5f + facing.getZOffset() * 0.7f;

        if (original == null) {
            pk.evid = LevelEventPacket.EVENT_SOUND_CLICK_FAIL;
            pk.data = 1200;

            level.addChunkPacket(getChunkX(), getChunkZ(), pk.clone());
            return;
        }

        pk.evid = LevelEventPacket.EVENT_SOUND_CLICK;
        pk.data = 1000;

        level.addChunkPacket(getChunkX(), getChunkZ(), pk.clone());

        pk.evid = LevelEventPacket.EVENT_PARTICLE_SHOOT;
        pk.data = 7;
        level.addChunkPacket(getChunkX(), getChunkZ(), pk);

        Item origin = original;
        original = original.clone();

        DispenseBehavior behavior = DispenseBehaviorRegister.getBehavior(original.getId());
        Item result = behavior.dispense(this, facing, original);

        pk.evid = LevelEventPacket.EVENT_SOUND_CLICK;

        original.count--;
        inv.setItem(slot, original);

        if (result != null) {
            if (result.getId() != origin.getId() || result.getDamage() != origin.getDamage()) {
                Item[] fit = inv.addItem(result);

                for (Item drop : fit) {
                    level.dropItem(this, drop);
                }
            } else {
                inv.setItem(slot, result);

                // TODO: Better solution. Give back empty buckets if a stack was in original slot.
                if (result.getId() == Item.HONEY_BOTTLE || result.getId() == Item.GLASS_BOTTLE || (result.getId() == Item.BUCKET && result.getDamage() > 0)) {
                    Item[] invFull = inv.addItem(original.decrement(result.count));
                    for (Item drop : invFull) {
                        DispenseBehaviorRegister.getBehavior(-1).dispense(this, getBlockFace(), drop);
                    }
                }
            }
        }
    }

    public Vector3 getDispensePosition() {
        BlockFace facing = getBlockFace();
        return this.add(
                0.5 + 0.7 * facing.getXOffset(),
                0.5 + 0.7 * facing.getYOffset(),
                0.5 + 0.7 * facing.getZOffset()
        );
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromIndex(this.getDamage() & 0x07);
    }

    @Override
    public double getHardness() {
        return 0.5;
    }
}
