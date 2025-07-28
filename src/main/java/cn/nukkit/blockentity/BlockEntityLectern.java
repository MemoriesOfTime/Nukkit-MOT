package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockLectern;
import cn.nukkit.event.block.LecternDropBookEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.IntTag;

public class BlockEntityLectern extends BlockEntitySpawnable {

    private int totalPages;

    public BlockEntityLectern(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        super.initBlockEntity();

        if (!(this.namedTag.get("book") instanceof CompoundTag)) {
            this.namedTag.remove("book");
        }

        if (!(this.namedTag.get("page") instanceof IntTag)) {
            this.namedTag.remove("page");
        }

        updateTotalPages();
    }

    @Override
    public CompoundTag getSpawnCompound() {
        CompoundTag c = new CompoundTag()
                .putString("id", BlockEntity.LECTERN)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z)
                .putBoolean("isMovable", this.movable);

        Item book = getBook();
        if (book.getId() != Item.AIR) {
            c.putCompound("book", NBTIO.putItemHelper(book));
            c.putBoolean("hasBook", true);
            c.putInt("page", getRawPage());
            c.putInt("totalPages", totalPages);
        } else {
            c.putBoolean("hasBook", false);
        }

        return c;
    }

    @Override
    public boolean isBlockEntityValid() {
        return getBlock().getId() == BlockID.LECTERN;
    }

    @Override
    public void onBreak() {
        level.dropItem(this, getBook());
    }

    public boolean hasBook() {
        return this.namedTag.contains("book") && this.namedTag.get("book") instanceof CompoundTag;
    }

    public Item getBook() {
        if (!hasBook()) {
            return new ItemBlock(new BlockAir(), 0, 0);
        } else {
            return NBTIO.getItemHelper(this.namedTag.getCompound("book"));
        }
    }

    public void setBook(Item item) {
        if (item != null && (item.getId() == Item.WRITTEN_BOOK || item.getId() == Item.BOOK_AND_QUILL)) {
            this.namedTag.putCompound("book", NBTIO.putItemHelper(item));
            this.namedTag.putInt("page", 0);
        } else {
            this.namedTag.remove("book");
            this.namedTag.remove("page");
        }
        updateTotalPages();
        setDirty();
    }

    public int getLeftPage() {
        return (getRawPage() * 2) + 1;
    }

    public int getRightPage() {
        return getLeftPage() + 1;
    }

    public void setLeftPage(int newLeftPage) {
        setRawPage((newLeftPage - 1) /2);
    }

    public void setRightPage(int newRightPage) {
        setLeftPage(newRightPage -1);
    }

    public void setRawPage(int page) {
        this.namedTag.putInt("page", Math.min(page, totalPages));
        setDirty();

        Block block = getLevelBlock();
        if (block instanceof BlockLectern) {
            ((BlockLectern) block).onPageChange(hasBook());
        }
    }

    public int getRawPage() {
        return this.namedTag.getInt("page");
    }

    public int getTotalPages() {
        return totalPages;
    }

    private void updateTotalPages() {
        Item book = getBook();
        if (book.getId() == Item.AIR || !book.hasCompoundTag()) {
            totalPages = 0;
        } else {
            totalPages = book.getNamedTag().getList("pages", CompoundTag.class).size();
        }
        level.updateAroundRedstone(this, null);
    }

    public boolean dropBook(Player player) {
        Item item = this.getBook();
        if (item != null && item.getId() != Item.AIR) {
            LecternDropBookEvent dropBookEvent = new LecternDropBookEvent(player, this, item);
            this.getLevel().getServer().getPluginManager().callEvent(dropBookEvent);
            if (dropBookEvent.isCancelled()) {
                return false;
            }

            this.setBook(null);
            this.level.dropItem(this.add(0.5, 1, 0.5), item);

            Block block = getLevelBlock();
            if (block instanceof BlockLectern) {
                ((BlockLectern) block).onPageChange(false);
            }
            return true;
        }
        return false;
    }

    @Override
    public void setDirty() {
        super.setDirty();
        this.spawnToAll();
    }
}
