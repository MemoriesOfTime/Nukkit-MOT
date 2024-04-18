package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityEnderChest;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityPiglin;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BlockEnderChest extends BlockTransparentMeta implements Faceable, BlockEntityHolder<BlockEntityEnderChest> {

    private static final int[] FACES = {2, 5, 3, 4};

    private final Set<Player> viewers = new HashSet<>();

    public BlockEnderChest() {
        this(0);
    }

    public BlockEnderChest(int meta) {
        super(meta);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public int getId() {
        return ENDER_CHEST;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.ENDER_CHEST;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityEnderChest> getBlockEntityClass() {
        return BlockEntityEnderChest.class;
    }

    @Override
    public int getLightLevel() {
        return 7;
    }

    @Override
    public int getWaterloggingLevel() {
        return 1;
    }

    @Override
    public String getName() {
        return "Ender Chest";
    }

    @Override
    public double getHardness() {
        return 22.5;
    }

    @Override
    public double getResistance() {
        return 3000;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public AxisAlignedBB recalculateBoundingBox() {
        return new SimpleAxisAlignedBB(
                this.x + 0.0625,
                this.y,
                this.z + 0.0625,
                this.x + 0.9375,
                this.y + 0.9475,
                this.z + 0.9375
        );
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        this.setDamage(FACES[player != null ? player.getDirection().getHorizontalIndex() : 0]);

        this.getLevel().setBlock(block, this, true, true);
        CompoundTag nbt = new CompoundTag("")
                .putString("id", BlockEntity.ENDER_CHEST)
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

        BlockEntity.createBlockEntity(BlockEntity.ENDER_CHEST, this.getChunk(), nbt);
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (player != null) {
            Block top = this.up();
            if (!top.isTransparent() && !(top instanceof BlockSlab && (top.getDamage() & 0x07) <= 0)) { // avoid issues with the slab hack
                return true;
            }

            BlockEntity t = this.getLevel().getBlockEntity(this);
            BlockEntityEnderChest chest;
            if (t instanceof BlockEntityEnderChest) {
                chest = (BlockEntityEnderChest) t;
            } else {
                CompoundTag nbt = new CompoundTag("")
                        .putString("id", BlockEntity.ENDER_CHEST)
                        .putInt("x", (int) this.x)
                        .putInt("y", (int) this.y)
                        .putInt("z", (int) this.z);
                chest = (BlockEntityEnderChest) BlockEntity.createBlockEntity(BlockEntity.ENDER_CHEST, this.getChunk(), nbt);
            }

            if (chest.namedTag.contains("Lock") && chest.namedTag.get("Lock") instanceof StringTag) {
                if (!chest.namedTag.getString("Lock").equals(item.getCustomName())) {
                    return true;
                }
            }

            player.setViewingEnderChest(this);
            player.addWindow(player.getEnderChestInventory());

            for (Entity e : this.getChunk().getEntities().values()) {
                if (e instanceof EntityPiglin) {
                    ((EntityPiglin) e).setAngry(600);
                }
            }
        }

        return true;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() >= getToolTier()) {
            if (item.hasEnchantment(Enchantment.ID_SILK_TOUCH)) {
                return new Item[]{this.toItem()};
            }
            return new Item[]{
                    Item.get(Item.OBSIDIAN, 0, 8)
            };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.OBSIDIAN_BLOCK_COLOR;
    }

    public Set<Player> getViewers() {
        return viewers;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean canBePulled() {
        return false;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public boolean canSilkTouch() {
        return true;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(this, 0);
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getDamage() & 0x7);
    }

    @Override
    public void setBlockFace(BlockFace face) {
        if (face.getIndex() > 1) {
            this.setDamage(FACES[face.getHorizontalIndex()]);
        }
    }
}
