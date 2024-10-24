package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityFirework;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.DyeColor;
import org.apache.commons.math3.util.FastMath;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author CreeperFace
 */
public class ItemFirework extends Item {

    public ItemFirework() {
        this(0);
    }

    public ItemFirework(Integer meta) {
        this(meta, 1);
    }

    public ItemFirework(Integer meta, int count) {
        super(FIREWORKS, meta, count, "Fireworks");

        if (!hasCompoundTag() || !this.getNamedTag().contains("Fireworks")) {
            CompoundTag tag = getNamedTag();
            if (tag == null) {
                tag = new CompoundTag();
                // Avoid the NPE problems in the following operations while getting the ListTag "Explosions"
                // Getting the value of "flight" has operations checking whether it's null or not.
                tag.putCompound("Fireworks", new CompoundTag("Fireworks")
                        .putList(new ListTag<CompoundTag>("Explosions"))
                );
                this.setNamedTag(tag);
            }
        }
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Level level, Player player, Block block, Block target, BlockFace face, double fx, double fy, double fz) {
        if (block.canPassThrough()) {
            this.spawnFirework(level, block);

            if (!player.isCreative()) {
                player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        if (player.getInventory().getChestplateFast() instanceof ItemElytra && player.isGliding()) {
            this.spawnFirework(player.getLevel(), player);

            if (!player.isCreative()) {
                this.count--;
            }

            player.setMotion(new Vector3(
                    -Math.sin(FastMath.toRadians(player.yaw)) * Math.cos(FastMath.toRadians(player.pitch)) * 2,
                    -Math.sin(FastMath.toRadians(player.pitch)) * 2,
                    Math.cos(FastMath.toRadians(player.yaw)) * Math.cos(FastMath.toRadians(player.pitch)) * 2));

            return true;
        }

        return false;
    }

    public void addExplosion(FireworkExplosion explosion) {
        List<DyeColor> colors = explosion.getColors();
        List<DyeColor> fades = explosion.getFades();

        if (colors.isEmpty()) {
            return;
        }
        byte[] clrs = new byte[colors.size()];
        for (int i = 0; i < clrs.length; i++) {
            clrs[i] = (byte) colors.get(i).getDyeData();
        }

        byte[] fds = new byte[fades.size()];
        for (int i = 0; i < fds.length; i++) {
            fds[i] = (byte) fades.get(i).getDyeData();
        }

        CompoundTag namedTag = this.getNamedTag();
        ListTag<CompoundTag> explosions = namedTag.getCompound("Fireworks").getList("Explosions", CompoundTag.class);
        CompoundTag tag = new CompoundTag()
                .putByteArray("FireworkColor", clrs)
                .putByteArray("FireworkFade", fds)
                .putBoolean("FireworkFlicker", explosion.flicker)
                .putBoolean("FireworkTrail", explosion.trail)
                .putByte("FireworkType", explosion.type.ordinal());
        explosions.add(tag);
        this.setNamedTag(namedTag);
    }

    public void clearExplosions() {
        this.getNamedTag().getCompound("Fireworks").putList(new ListTag<CompoundTag>("Explosions"));
    }

    public EntityFirework spawnFirework(Level level, Vector3 pos) {
        return spawnFirework(level, pos, null);
    }

    public EntityFirework spawnFirework(Level level, Vector3 pos, @Nullable Vector3 motion) {
        boolean projectile = motion != null;
        CompoundTag nbt = Entity.getDefaultNBT(pos, motion, projectile ? (float) motion.yRotFromDirection() : 0, projectile ? (float) motion.xRotFromDirection() : 0)
                .putCompound("FireworkItem", NBTIO.putItemHelper(this));

        EntityFirework entity = new EntityFirework(level.getChunk(pos.getChunkX(), pos.getChunkZ()), nbt, projectile);
        entity.spawnToAll();
        return entity;
    }

    public int getFlight() {
        int flight = 0;
        Tag tag = this.getNamedTag();
        if (tag != null && (tag = ((CompoundTag)tag).get("Fireworks")) instanceof CompoundTag) {
            flight = ((CompoundTag)tag).getByte("Flight");
        }
        return flight;
    }

    public void setFlight(int flight) {
        CompoundTag compoundTag = this.getNamedTag();
        compoundTag.putCompound("Fireworks", compoundTag.getCompound("Fireworks").putByte("Flight", flight));
        this.setNamedTag(compoundTag);
    }

    public static class FireworkExplosion {

        private final List<DyeColor> colors = new ArrayList<>();
        private final List<DyeColor> fades = new ArrayList<>();
        private boolean flicker = false;
        private boolean trail = false;
        private ExplosionType type = ExplosionType.CREEPER_SHAPED;

        public List<DyeColor> getColors() {
            return this.colors;
        }

        public List<DyeColor> getFades() {
            return this.fades;
        }

        public boolean hasFlicker() {
            return this.flicker;
        }

        public boolean hasTrail() {
            return this.trail;
        }

        public ExplosionType getType() {
            return this.type;
        }

        public FireworkExplosion setFlicker(boolean flicker) {
            this.flicker = flicker;
            return this;
        }

        public FireworkExplosion setTrail(boolean trail) {
            this.trail = trail;
            return this;
        }

        public FireworkExplosion type(ExplosionType type) {
            this.type = type;
            return this;
        }

        public FireworkExplosion addColor(DyeColor color) {
            colors.add(color);
            return this;
        }

        public FireworkExplosion addFade(DyeColor fade) {
            fades.add(fade);
            return this;
        }

        public enum ExplosionType {
            SMALL_BALL,
            LARGE_BALL,
            STAR_SHAPED,
            CREEPER_SHAPED,
            BURST
        }
    }
}