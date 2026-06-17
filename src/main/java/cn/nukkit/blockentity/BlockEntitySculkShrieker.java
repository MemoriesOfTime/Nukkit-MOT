package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockSculkShrieker;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.warden.WardenWarningData;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.mob.EntityWarden;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.ParticleEffect;
import cn.nukkit.level.Position;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.vibration.VibrationEvent;
import cn.nukkit.level.vibration.VibrationListener;
import cn.nukkit.level.vibration.VibrationType;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.potion.Effect;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shrieks when it receives a tendrils-clicking vibration (chained from a sculk sensor), applying
 * Darkness and incrementing the triggerer's warden warning level; summons a warden at level 4.
 * Adapted from PowerNukkitX.
 */
public class BlockEntitySculkShrieker extends BlockEntity implements VibrationListener {

    protected static final int LISTENER_RADIUS = 8;
    protected static final int SHRIEKING_TICKS = 90;
    protected static final int PLAYER_COOLDOWN_TICKS = 200;
    protected static final int WARNING_DECAY_TICKS = 12000;
    protected static final int DARKNESS_DURATION_TICKS = 260;
    protected static final int DARKNESS_RANGE = 40;
    protected static final int WARDEN_WARNING_LEVEL = 4;
    protected static final int WARDEN_SPAWN_RANGE_XZ = 5;
    protected static final int WARDEN_SPAWN_RANGE_Y = 6;

    protected UUID triggeringPlayer;
    protected boolean summonAfterShriek;
    protected long shriekEndTick;

    public BlockEntitySculkShrieker(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        super.initBlockEntity();
        this.level.getVibrationManager().addListener(this);
        // On reload, shrieking timing (shriekEndTick) is not persisted. If the block was saved
        // mid-shriek, clear the shrieking flag so it is not stuck on forever.
        if (getLevelBlock() instanceof BlockSculkShrieker shrieker && shrieker.isShrieking()) {
            shrieker.setShrieking(false);
        }
    }

    @Override
    public void close() {
        if (this.level != null) {
            this.level.getVibrationManager().removeListener(this);
        }
        super.close();
    }

    @Override
    public void onBreak() {
        if (this.level != null) {
            this.level.getVibrationManager().removeListener(this);
        }
        if (summonAfterShriek && getLevelBlock() instanceof BlockSculkShrieker shrieker && shrieker.isShrieking()) {
            tryRespond();
        }
    }

    @Override
    public boolean isBlockEntityValid() {
        return getLevelBlock().getId() == BlockID.SCULK_SHRIEKER;
    }

    @Override
    public boolean onUpdate() {
        if (!isBlockEntityValid()) {
            close();
            return false;
        }
        if (!(getLevelBlock() instanceof BlockSculkShrieker shrieker) || !shrieker.isShrieking()) {
            return false;
        }
        if (level.getCurrentTick() >= shriekEndTick) {
            finishShrieking();
            return false;
        }
        return true;
    }

    @Override
    public Vector3 getListenerVector() {
        return new Vector3(this.getFloorX() + 0.5, this.getFloorY() + 0.5, this.getFloorZ() + 0.5);
    }

    @Override
    public boolean onVibrationOccur(VibrationEvent event) {
        if (!this.isBlockEntityValid()) {
            return false;
        }
        // JE: shriekers only listen to sculk_sensor_tendrils_clicking
        if (event.type() != VibrationType.SCULK_SENSOR_TENDRILS_CLICKING) {
            return false;
        }
        if (getLevelBlock() instanceof BlockSculkShrieker shrieker && shrieker.isShrieking()) {
            return false;
        }
        return resolvePlayer(event) != null;
    }

    @Override
    public void onVibrationArrive(VibrationEvent event) {
        Player player = resolvePlayer(event);
        if (player != null) {
            tryShriek(player);
        }
    }

    @Override
    public double getListenRange() {
        return LISTENER_RADIUS;
    }

    /**
     * JE tryGetPlayer: resolve the triggering player from the initiator (direct player, vehicle
     * controlling passenger, projectile shooter, or dropped item owner).
     */
    protected Player resolvePlayer(VibrationEvent event) {
        Object initiator = event.initiator();
        if (initiator instanceof Player player) {
            return player;
        }
        if (initiator instanceof Entity entity) {
            // Player riding an entity (e.g. boat/minecart) that triggered the vibration
            Entity passenger = entity.getPassenger();
            if (passenger instanceof Player player) {
                return player;
            }
            // Projectile: owner is the shooter
            if (entity instanceof EntityProjectile projectile && projectile.shootingEntity instanceof Player player) {
                return player;
            }
            // Dropped item: owner string is the player name
            if (entity instanceof EntityItem itemEntity) {
                String owner = itemEntity.getOwner();
                if (owner != null && !owner.isEmpty()) {
                    Player player = level.getServer().getPlayerExact(owner);
                    if (player != null) {
                        return player;
                    }
                }
            }
        }
        return null;
    }

    /** JE tryShriek: shriek unless can summon and currently on cooldown (tryToWarn returned false). */
    public void tryShriek(Player player) {
        Block block = getLevelBlock();
        if (!(block instanceof BlockSculkShrieker shrieker) || shrieker.isShrieking()) {
            return;
        }
        WardenWarningData warning = warningFor(player);
        boolean canRespond = canRespond(shrieker);
        boolean warned = false;
        if (canRespond) {
            if (level.getCurrentTick() - warning.lastShriekTick >= PLAYER_COOLDOWN_TICKS) {
                warning.warningLevel++;
                warning.lastWarningTick = level.getCurrentTick();
                warning.lastShriekTick = level.getCurrentTick();
                warned = true;
                this.summonAfterShriek = warning.warningLevel >= WARDEN_WARNING_LEVEL;
            }
        }
        // JE: shriek unless canRespond && !warned (i.e. on cooldown)
        if (canRespond && !warned) {
            return;
        }
        this.triggeringPlayer = player.getUniqueId();
        shrieker.setShrieking(true);
        this.shriekEndTick = level.getCurrentTick() + SHRIEKING_TICKS;
        level.addSound(this.add(0.5, 1.0, 0.5), Sound.SHRIEK_SCULK_SHRIEKER);
        level.addParticleEffect(this.add(0.5, 1.0, 0.5), ParticleEffect.SHRIEK);
        scheduleUpdate();
    }

    public void finishShrieking() {
        Block block = getLevelBlock();
        if (!(block instanceof BlockSculkShrieker shrieker)) {
            return;
        }
        shrieker.setShrieking(false);
        tryRespond();
        triggeringPlayer = null;
        summonAfterShriek = false;
        shriekEndTick = 0;
    }

    /**
     * JE canRespond: only naturally-generated shriekers respond, in non-peaceful difficulty, and
     * only when mob spawning is permitted by the game rules.
     */
    protected boolean canRespond(BlockSculkShrieker shrieker) {
        return shrieker.canSummon()
                && level.getServer().getDifficulty() != 0 // 0 == PEACEFUL
                && level.getGameRules().getBoolean(GameRule.DO_MOB_SPAWNING);
    }

    /** JE tryRespond: if can summon, summon warden (or play reply sound) + apply Darkness. */
    public void tryRespond() {
        Block block = getLevelBlock();
        if (!(block instanceof BlockSculkShrieker shrieker) || !canRespond(shrieker)) {
            return;
        }
        Player player = triggeringPlayer != null ? level.getServer().getPlayer(triggeringPlayer).orElse(null) : null;
        boolean summoned = false;
        if (summonAfterShriek) {
            summoned = spawnWarden();
        }
        if (!summoned && player != null) {
            playWardenReplySound(warningFor(player).warningLevel);
        }
        addDarkness();
    }

    protected WardenWarningData warningFor(Player player) {
        WardenWarningData warning = player.getWardenWarningData();
        // JE WardenSpawnTracker.tick: after 12000 ticks the level drops by exactly 1 and resets.
        long elapsed = level.getCurrentTick() - warning.lastWarningTick;
        if (elapsed >= WARNING_DECAY_TICKS && warning.warningLevel > 0) {
            warning.warningLevel--;
            warning.lastWarningTick = level.getCurrentTick();
        }
        return warning;
    }

    protected void playWardenReplySound(int warningLevel) {
        Sound sound = switch (warningLevel) {
            case 1 -> Sound.MOB_WARDEN_NEARBY_CLOSE;
            case 2 -> Sound.MOB_WARDEN_NEARBY_CLOSER;
            case 3 -> Sound.MOB_WARDEN_NEARBY_CLOSEST;
            case 4 -> Sound.MOB_WARDEN_LISTENING_ANGRY;
            default -> null;
        };
        if (sound != null) {
            level.addSound(this, sound);
        }
    }

    protected void addDarkness() {
        for (Player player : level.getPlayers().values()) {
            if ((player.isSurvival() || player.isAdventure()) && player.distanceSquared(this) <= DARKNESS_RANGE * DARKNESS_RANGE) {
                player.addEffect(Effect.getEffect(Effect.DARKNESS).setDuration(DARKNESS_DURATION_TICKS));
            }
        }
    }

    protected boolean spawnWarden() {
        if (hasNearbyWarden()) {
            return false;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 20; i++) {
            int x = getFloorX() + random.nextInt(-WARDEN_SPAWN_RANGE_XZ, WARDEN_SPAWN_RANGE_XZ + 1);
            int z = getFloorZ() + random.nextInt(-WARDEN_SPAWN_RANGE_XZ, WARDEN_SPAWN_RANGE_XZ + 1);
            for (int y = getFloorY() + WARDEN_SPAWN_RANGE_Y; y >= getFloorY() - WARDEN_SPAWN_RANGE_Y; y--) {
                if (!canSpawnAt(x, y, z)) {
                    continue;
                }
                Entity warden = Entity.createEntity("Warden", new Position(x + 0.5, y, z + 0.5, level));
                if (warden != null) {
                    warden.spawnToAll();
                    return true;
                }
            }
        }
        level.addSound(this, Sound.MOB_WARDEN_ANGRY);
        return false;
    }

    protected boolean hasNearbyWarden() {
        // JE WardenSpawnTracker.hasNearbyWarden: a 48x48x48 AABB centered on the shrieker
        // (i.e. +-24 blocks on each axis).
        var box = new SimpleAxisAlignedBB(x - 24, y - 24, z - 24, x + 24, y + 24, z + 24);
        for (Entity entity : level.getNearbyEntities(box)) {
            if (entity instanceof EntityWarden) {
                return true;
            }
        }
        return false;
    }

    protected boolean canSpawnAt(int x, int y, int z) {
        Block feet = level.getBlock(new Vector3(x, y, z));
        Block head = level.getBlock(new Vector3(x, y + 1, z));
        Block below = level.getBlock(new Vector3(x, y - 1, z));
        return feet.canPassThrough() && head.canPassThrough() && below.isSolid();
    }
}
