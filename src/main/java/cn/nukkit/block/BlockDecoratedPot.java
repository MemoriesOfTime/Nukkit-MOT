package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityDecoratedPot;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;


public class BlockDecoratedPot extends BlockTransparentMeta implements Faceable, BlockEntityHolder<BlockEntityDecoratedPot> {

    public static final int DIRECTION_BIT = 0x3;

    public BlockDecoratedPot() {
        super();
    }

    public BlockDecoratedPot(int meta) {
        super(meta);
    }

    public String getName() {
        return "Decorated Pot";
    }

    @Override
    public int getId() {
        return Block.DECORATED_POT;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @Nullable Player player) {
        CompoundTag nbt = new CompoundTag();

        nbt.putString("id", BlockEntity.DECORATED_POT);
        nbt.putByte("isMovable", 1);

        if (item.getNamedTag() != null) {
            Map<String, Tag> customData = item.getNamedTag().getTags();
            for (Map.Entry<String, Tag> tag : customData.entrySet()) {
                nbt.put(tag.getKey(), tag.getValue());
            }
        }

        nbt.putInt("x", (int) this.x);
        nbt.putInt("y", (int) this.y);
        nbt.putInt("z", (int) this.y);

        this.setBlockFace(player.getDirection().getOpposite());
        return BlockEntityHolder.setBlockAndCreateEntity(this, true, true, nbt) != null;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityDecoratedPot> getBlockEntityClass() {
        return BlockEntityDecoratedPot.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.DECORATED_POT;
    }

    @Override
    public void setBlockFace(BlockFace face) {
        this.setDamage(DIRECTION_BIT, Objects.requireNonNullElse(face, BlockFace.SOUTH).getHorizontalIndex() & 0x3);
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getDamage(DIRECTION_BIT));
    }
}