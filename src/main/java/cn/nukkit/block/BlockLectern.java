package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityLectern;
import cn.nukkit.event.block.BlockRedstoneEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.network.protocol.ContainerOpenPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockLectern extends BlockTransparentMeta implements Faceable, BlockEntityHolder<BlockEntityLectern> {

    public BlockLectern() {
        this(0);
    }

    public BlockLectern(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Lectern";
    }

    @Override
    public int getId() {
        return LECTERN;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityLectern> getBlockEntityClass() {
        return BlockEntityLectern.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.LECTERN;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public double getResistance() {
        return 12.5;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public double getMaxY() {
        return y + 0.89999;
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride() {
        int power = 0;
        BlockEntity blockEntity = this.getLevel().getBlockEntity(this);
        if (blockEntity instanceof BlockEntityLectern lectern && lectern.hasBook()) {
            int maxPage = lectern.getTotalPages();
            int page = lectern.getLeftPage() + 1;
            power = (int) (((float) page / maxPage) * 16);
        }
        return power;
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(getDamage() & 0b11);
    }

    @Override
    public void setBlockFace(BlockFace face) {
        int horizontalIndex = face.getHorizontalIndex();
        if (horizontalIndex >= 0) {
            setDamage(getDamage() & (DATA_MASK ^ 0b11) | (horizontalIndex & 0b11));
        }
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @Nullable Player player) {
        this.setBlockFace(player != null ? player.getDirection().getOpposite() : BlockFace.SOUTH);
        BlockEntity.createBlockEntity(BlockEntity.LECTERN, this.getChunk(), BlockEntity.getDefaultCompound(this, BlockEntity.LECTERN));
        return super.place(item, block, target, face, fx, fy, fz, player);
    }

    @Override
    public boolean onActivate(@NotNull Item item, @Nullable Player player) {
        BlockEntity t = this.getLevel().getBlockEntity(this);

        BlockEntityLectern lectern;
        if (t instanceof BlockEntityLectern) {
            lectern = (BlockEntityLectern) t;
        } else {
            lectern = (BlockEntityLectern) BlockEntity.createBlockEntity(BlockEntity.LECTERN, this.getChunk(), BlockEntity.getDefaultCompound(this, BlockEntity.LECTERN));;
        }
        Item currentBook = lectern.getBook();
        if (!currentBook.isNull()) {
            if (player != null && player.protocol >= ProtocolInfo.v1_20_60) {
                ContainerOpenPacket pk = new ContainerOpenPacket();
                pk.windowId = Player.LECTERN_WINDOW_ID;
                pk.type = ContainerOpenPacket.TYPE_LECTERN;
                pk.x = this.getFloorX();
                pk.y = this.getFloorY();
                pk.z = this.getFloorZ();
                player.dataPacket(pk);
            }
            return true;
        }

        if (item.getId() != ItemID.WRITTEN_BOOK && item.getId() != ItemID.BOOK_AND_QUILL) {
            return false;
        }

        if (player == null || !player.isCreative()) {
            item.count--;
        }

        Item newBook = item.clone();
        newBook.setCount(1);
        lectern.setBook(newBook);
        lectern.spawnToAll();
        this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_ITEM_BOOK_PUT);
        return true;
    }

    @Override
    public int onTouch(@Nullable Player player, PlayerInteractEvent.Action action) {
        if (action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            BlockEntity blockEntity = this.getLevel().getBlockEntity(this);
            if (blockEntity instanceof BlockEntityLectern lectern && lectern.dropBook(player)) {
                return 1;
            }
        }
        return 0;
    }

    @Override
    public boolean isPowerSource() {
        return true;
    }

    public boolean isActivated() {
        return (this.getDamage() & 0x04) == 0x04;
    }

    public void setActivated(boolean activated) {
        if (activated) {
            setDamage(getDamage() | 0x04);
        } else {
            setDamage(getDamage() ^ 0x04);
        }
    }

    @Override
    public int getWeakPower(BlockFace face) {
        return isActivated() ? 15 : 0;
    }

    @Override
    public int getStrongPower(BlockFace face) {
        return face == BlockFace.DOWN ? this.getWeakPower(face) : 0;
    }

    public void onPageChange(boolean active) {
        if (isActivated() != active) {
            setActivated(active);
            this.level.getServer().getPluginManager().callEvent(new BlockRedstoneEvent(this, 15, 0));
            level.setBlock((int) this.x, (int) this.y, (int) this.z, 0, this, false, false); // No need to send this to client
            level.updateAroundRedstone(this, null);
            if (active) {
                level.scheduleUpdate(this, 1);
            }
        }
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_SCHEDULED || type == Level.BLOCK_UPDATE_NORMAL) {
            onPageChange(false);
        }
        return 0;
    }

    public void dropBook(Player player) {
        BlockEntity blockEntity = this.getLevel().getBlockEntity(this);
        if (blockEntity instanceof BlockEntityLectern lectern) {
            lectern.dropBook(player);
        }
    }
}
