package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityPiglin;
import cn.nukkit.event.block.BlockRedstoneEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockFace.Plane;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.nbt.tag.Tag;

import java.util.Map;

public class BlockTrappedChest extends BlockChest {

    private static final int[] faces = {2, 5, 3, 4};

    public BlockTrappedChest() {
        this(0);
    }

    public BlockTrappedChest(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return TRAPPED_CHEST;
    }

    @Override
    public String getName() {
        return "Trapped Chest";
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        BlockEntityChest chest = null;
        this.setDamage(faces[player != null ? player.getDirection().getHorizontalIndex() : 0]);

        for (BlockFace side : Plane.HORIZONTAL) {
            if ((this.getDamage() == 4 || this.getDamage() == 5) && (side == BlockFace.WEST || side == BlockFace.EAST)) {
                continue;
            } else if ((this.getDamage() == 3 || this.getDamage() == 2) && (side == BlockFace.NORTH || side == BlockFace.SOUTH)) {
                continue;
            }
            Block c = this.getSide(side);
            if (c instanceof BlockTrappedChest && c.getDamage() == this.getDamage()) {
                BlockEntity blockEntity = this.getLevel().getBlockEntity(c);
                if (blockEntity instanceof BlockEntityChest && !((BlockEntityChest) blockEntity).isPaired()) {
                    chest = (BlockEntityChest) blockEntity;
                    break;
                }
            }
        }

        this.getLevel().setBlock(block, this, true, true);
        CompoundTag nbt = new CompoundTag("")
                .putList(new ListTag<>("Items"))
                .putString("id", BlockEntity.CHEST)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z);

        if (item.hasCustomName()) {
            nbt.putString("CustomName", item.getCustomName());
        }

        if (item.hasCustomBlockData()) {
            Map<String, Tag> customData = item.getCustomBlockData().getTags();
            for (Map.Entry<String, Tag> tag : customData.entrySet()) {
                nbt.put(tag.getKey(), tag.getValue());
            }
        }

        BlockEntityChest blockEntity = (BlockEntityChest) BlockEntity.createBlockEntity(BlockEntity.CHEST, this.getChunk(), nbt);

        if (chest != null) {
            chest.pairWith(blockEntity);
            blockEntity.pairWith(chest);
        }

        return true;
    }


    @Override
    public boolean onActivate(Item item, Player player){
        if (player != null) {
            Block top = this.up();
            if (!top.isTransparent() && !(top instanceof BlockSlab && (top.getDamage() & 0x07) <= 0)) { // avoid issues with the slab hack
                return true;
            }

            BlockEntity t = this.getLevel().getBlockEntity(this);
            BlockEntityChest chest;
            if (t instanceof BlockEntityChest) {
                chest = (BlockEntityChest) t;
            } else {
                CompoundTag nbt = new CompoundTag("")
                        .putList(new ListTag<>("Items"))
                        .putString("id", BlockEntity.CHEST)
                        .putInt("x", (int) this.x)
                        .putInt("y", (int) this.y)
                        .putInt("z", (int) this.z);
                chest = (BlockEntityChest) BlockEntity.createBlockEntity(BlockEntity.CHEST, this.getChunk(), nbt);
            }

            if (chest.namedTag.contains("Lock") && chest.namedTag.get("Lock") instanceof StringTag) {
                if (!chest.namedTag.getString("Lock").equals(item.getCustomName())) {
                    return true;
                }
            }

            player.addWindow(chest.getInventory());
            this.level.getServer().getPluginManager().callEvent(new BlockRedstoneEvent(this, 0, getWeakPower(getBlockFace())));
            onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
            for (Entity e : this.getChunk().getEntities().values()) {
                if (e instanceof EntityPiglin) {
                    ((EntityPiglin) e).setAngry(600);
                }
            }
        }

        return true;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_SCHEDULED) {
            BlockEntity blockEntity = this.level.getBlockEntity(this);
            if (blockEntity instanceof BlockEntityChest){
                if (!((BlockEntityChest) blockEntity).getInventory().getViewers().isEmpty()){
                    this.level.updateAroundRedstone(getLocation(),this.getBlockFace());
                    this.level.updateAroundRedstone(getLocation(),this.getBlockFace().getOpposite());
                    this.level.scheduleUpdate(this, 2);
                }else {
                    this.level.updateAroundRedstone(getLocation(),this.getBlockFace());
                    this.level.updateAroundRedstone(getLocation(),this.getBlockFace().getOpposite());
                    this.level.getServer().getPluginManager().callEvent(new BlockRedstoneEvent(this, getWeakPower(getBlockFace()), 0));
                }
            }
            return type;
            }
        return 0;
    }


    @Override
    public int getWeakPower(BlockFace face) {
        int playerCount = 0;

        BlockEntity blockEntity = this.level.getBlockEntity(this);

        if (blockEntity instanceof BlockEntityChest) {
            playerCount = ((BlockEntityChest) blockEntity).getInventory().getViewers().size();
        }

        return Math.min(playerCount, 15);
    }

    @Override
    public int getStrongPower(BlockFace side) {
        return side == BlockFace.UP ? this.getWeakPower(side) : 0;
    }

    @Override
    public boolean isPowerSource() {
        return true;
    }
}
