package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.block.properties.enums.CrafterOrientation;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityCrafter;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemNamespaceId;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Minimal crafter implementation with a block entity backed 3x3 inventory.
 * <p>
 * Adapted from PowerNukkitX.
 */
public class BlockCrafter extends BlockSolidMeta implements Faceable, BlockEntityHolder<BlockEntityCrafter>, BlockPropertiesHelper {

    private static final BlockProperties PROPERTIES = new BlockProperties(
            VanillaProperties.CRAFTER_ORIENTATION,
            VanillaProperties.CRAFTING,
            VanillaProperties.TRIGGERED
    );

    public BlockCrafter() {
        this(0);
    }

    public BlockCrafter(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Crafter";
    }

    @Override
    public int getId() {
        return CRAFTER;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:crafter";
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityCrafter> getBlockEntityClass() {
        return BlockEntityCrafter.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.CRAFTER;
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
        if (player.protocol < ProtocolInfo.v1_20_50) {
            return true;
        }

        player.addWindow(this.getOrCreateBlockEntity().getInventory());
        return true;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face,
                         double fx, double fy, double fz, @Nullable Player player) {
        if (player != null) {
            if (Math.abs(player.x - this.x) < 2 && Math.abs(player.z - this.z) < 2) {
                double y = player.y + player.getEyeHeight();
                if (y - this.y > 2) {
                    this.setOrientation(BlockFace.UP, player.getHorizontalFacing().getOpposite());
                } else if (this.y - y > 0) {
                    this.setOrientation(BlockFace.DOWN, player.getHorizontalFacing().getOpposite());
                } else {
                    this.setBlockFace(player.getHorizontalFacing().getOpposite());
                }
            } else {
                this.setBlockFace(player.getHorizontalFacing().getOpposite());
            }
        } else {
            this.setBlockFace(BlockFace.NORTH);
        }

        CompoundTag nbt = new CompoundTag();
        if (item.hasCustomName()) {
            nbt.putString("CustomName", item.getCustomName());
        }
        if (item.hasCustomBlockData()) {
            for (Map.Entry<String, Tag> tag : item.getCustomBlockData().getTags().entrySet()) {
                nbt.put(tag.getKey(), tag.getValue());
            }
        }
        return BlockEntityHolder.setBlockAndCreateEntity(this, true, true, nbt) != null;
    }

    @Override
    public Item toItem() {
        return Item.fromString(ItemNamespaceId.CRAFTER_NAMESPACE_ID);
    }

    @Override
    public BlockFace getBlockFace() {
        return this.getPropertyValue(VanillaProperties.CRAFTER_ORIENTATION).getPrimaryFace();
    }

    @Override
    public void setBlockFace(BlockFace face) {
        this.setOrientation(face, BlockFace.EAST);
    }

    private void setOrientation(BlockFace primary, BlockFace secondary) {
        this.setPropertyValue(VanillaProperties.CRAFTER_ORIENTATION, CrafterOrientation.byFaces(primary, secondary));
    }

    public boolean isTriggered() {
        return this.getBooleanValue(VanillaProperties.TRIGGERED);
    }

    public void setTriggered(boolean triggered) {
        this.setBooleanValue(VanillaProperties.TRIGGERED, triggered);
    }

    public boolean isCrafting() {
        return this.getBooleanValue(VanillaProperties.CRAFTING);
    }

    public void setCrafting(boolean crafting) {
        this.setBooleanValue(VanillaProperties.CRAFTING, crafting);
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride() {
        BlockEntityCrafter crafter = this.getBlockEntity();
        return crafter == null ? 0 : crafter.getInventory().getComparatorOutput();
    }

    @Override
    public double getHardness() {
        return 1.5;
    }

    @Override
    public double getResistance() {
        return 3.5;
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
    public boolean canBePushed() {
        BlockEntityCrafter crafter = this.getBlockEntity();
        return crafter == null || crafter.getInventory().getViewers().isEmpty();
    }

    @Override
    public boolean canBePulled() {
        return this.canBePushed();
    }
}
