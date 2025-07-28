package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.item.EntityPainting;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemPainting extends Item {

    private static final int[] DIRECTION = {2, 3, 4, 5};
    private static final int[] RIGHT = {4, 5, 3, 2};
    private static final double OFFSET = 0.53125;

    public ItemPainting() {
        this(0, 1);
    }

    public ItemPainting(Integer meta) {
        this(meta, 1);
    }

    public ItemPainting(Integer meta, int count) {
        super(PAINTING, 0, count, "Painting");
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Level level, Player player, Block block, Block target, BlockFace face, double fx, double fy, double fz) {
        if (player.isAdventure() || target.isTransparent() || face.getHorizontalIndex() == -1 || block.isSolid()) {
            return false;
        }

        FullChunk chunk = level.getChunk((int) block.getX() >> 4, (int) block.getZ() >> 4);
        if (chunk == null) {
            return false;
        }

        List<EntityPainting.Motive> validMotives = new ArrayList<>();
        for (EntityPainting.Motive motive : EntityPainting.motives) {
            if (motive.predicate.test(target.getLevel(), face, block, target)) {
                validMotives.add(motive);
            }
        }
        if(validMotives.isEmpty()) return false;

        int direction = DIRECTION[face.getIndex() - 2];
        EntityPainting.Motive motive = validMotives.get(ThreadLocalRandom.current().nextInt(validMotives.size()));

        Vector3 position = new Vector3(target.x + 0.5, target.y + 0.5, target.z + 0.5);
        double widthOffset = offset(motive.width);

        switch (face.getHorizontalIndex()) {
            case 0:
                position.x += widthOffset;
                position.z += OFFSET;
                break;
            case 1:
                position.x -= OFFSET;
                position.z += widthOffset;
                break;
            case 2:
                position.x -= widthOffset;
                position.z -= OFFSET;
                break;
            case 3:
                position.x += OFFSET;
                position.z -= widthOffset;
                break;
        }
        position.y += offset(motive.height);

        CompoundTag nbt = new CompoundTag()
                .putByte("Direction", direction)
                .putString("Motive", motive.title)
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("0", position.x))
                        .add(new DoubleTag("1", position.y))
                        .add(new DoubleTag("2", position.z)))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("0", 0))
                        .add(new DoubleTag("1", 0))
                        .add(new DoubleTag("2", 0)))
                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("0", direction * 90))
                        .add(new FloatTag("1", 0)));

        EntityPainting entity = new EntityPainting(chunk, nbt);

        if (!player.isCreative()) {
            player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
        }
        entity.spawnToAll();

        return true;
    }

    private static double offset(int value) {
        if(value > 1 && value != 3) {
            return 0.5;
        }
        return 0;
    }
}
