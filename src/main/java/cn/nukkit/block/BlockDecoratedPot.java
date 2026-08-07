package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityDecoratedPot;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.particle.GenericParticle;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Faceable;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;


public class BlockDecoratedPot extends BlockTransparentMeta implements Faceable, BlockEntityHolder<BlockEntityDecoratedPot> {

    public static final int DIRECTION_BIT = 0x3;

    public BlockDecoratedPot() {
        super();
    }

    public BlockDecoratedPot(int meta) {
        super(meta);
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
    public String getName() {
        return "Decorated Pot";
    }

    @Override
    public int getId() {
        return Block.DECORATED_POT;
    }

    @Override
    public double getHardness() {
        return 0;
    }

    @Override
    public double getResistance() {
        return 0;
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

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        BlockEntityDecoratedPot pot = this.getOrCreateBlockEntity();
        Item potItem = pot.getItem();
        if ((potItem.getId() == Block.AIR || potItem.equals(item)) && potItem.getCount() < potItem.getMaxStackSize() && item.getCount() > 0) {
            item.count--;
            if (potItem.getId() == Block.AIR) {
                potItem = item.clone();
                potItem.setCount(1);
            } else {
                potItem.setCount(potItem.getCount() + 1);
            }
            pot.setItem(potItem);

            pot.playInsertAnimation();
            level.addLevelSoundEvent(
                    this,
                    LevelSoundEventPacket.SOUND_DECORATED_POT_INSERT,
                    698 + potItem.getCount() * 8 //抓包BDS，从689开始，每增加一个数量 数据+8 实现音调变化
            );
            level.addParticle(new GenericParticle(this.add(0.5, 1.25, 0.5), (Particle.TYPE_DUST_PLUME)));
        } else {
            pot.playInsertFailAnimation();
            level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_DECORATED_POT_INSERT_FAILED);
        }
        return true;
    }

    @Override
    public boolean hasEntityCollision() {
        return true;
    }

    @Override
    public void onEntityCollide(Entity entity) {
        if (entity instanceof EntityProjectile) {
            entity.close();
            this.level.useBreakOn(this);
        }
    }

    @Override
    public Item toItem() {
        Item drop = new ItemBlock(this, 0);

        Optional.ofNullable(getBlockEntity())
                .map(BlockEntityDecoratedPot::getCleanedNBT)
                .filter(nbt -> nbt.getList("sherds", StringTag.class).getAll().stream()
                        .anyMatch(sherd -> !sherd.parseValue().equals("minecraft:brick")))
                .ifPresent(nbt -> {
                    nbt.remove("isMovable");
                    nbt.remove("item");
                    drop.setCompoundTag(nbt);
                });

        return drop;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.hasEnchantment(Enchantment.ID_SILK_TOUCH)) {
            return new Item[]{this.toItem()};
        }

        ObjectArrayList<Item> drops = new ObjectArrayList<>(4);

        Optional.ofNullable(getBlockEntity())
                .map(BlockEntityDecoratedPot::getCleanedNBT)
                .ifPresent(nbt -> {
                    nbt.getList("sherds", StringTag.class).getAll().forEach(sherd -> {
                        Item drop = Item.fromString(sherd.parseValue());
                        if (drop.getId() != Item.AIR) {
                            drops.add(drop);
                        }
                    });
                });

        return drops.toArray(Item.EMPTY_ARRAY);
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