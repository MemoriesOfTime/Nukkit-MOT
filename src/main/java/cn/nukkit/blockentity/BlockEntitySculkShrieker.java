package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.Server;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bedrock sculk shrieker: shrieks when a player steps on it or receives a sculk-sensor
 * tendrils-clicking vibration, applies Darkness to nearby Survival/Adventure players, and
 * tracks a per-player warden warning level (reaching 4 spawns a warden after the shriek ends).
 * Each activation blames all players within 16 blocks together, raising everyone to one above
 * the highest current level. Adapted from PowerNukkitX.
 */
public class BlockEntitySculkShrieker extends BlockEntity implements VibrationListener {

    protected static final int LISTENER_RADIUS = 8;
    protected static final int SHRIEKING_TICKS = 90;
    protected static final int PLAYER_COOLDOWN_TICKS = 200;
    protected static final int WARNING_DECAY_TICKS = 12000;
    protected static final int DARKNESS_DURATION_TICKS = 260;
    protected static final int DARKNESS_RANGE = 40;
    /**
     * Minimum remaining ticks on a player's existing Darkness to skip refreshing it, so repeated
     * shrieks don't keep resetting the full duration.
     */
    protected static final int DARKNESS_DISPLAY_LIMIT_TICKS = 200;
    protected static final int WARDEN_WARNING_LEVEL = 4;
    protected static final int WARDEN_SPAWN_RANGE_XZ = 5;
    protected static final int WARDEN_SPAWN_RANGE_Y = 6;
    /**
     * Radius within which players share the warning-level update (the triggerer is always included
     * even if outside) — the 16-block blame radius.
     */
    protected static final double PLAYER_SEARCH_RADIUS = 16.0;

    protected UUID triggeringPlayer;
    protected boolean summonAfterShriek;
    protected long shriekEndTick;
    /**
     * Warning level resolved for the in-progress shriek: set by {@link #tryWarn}, read by
     * {@link #tryRespond}. 0 means no warning was added this shriek. Shrieker-local snapshot that
     * drives the warden spawn/reply; the authoritative per-player level lives on
     * {@link WardenWarningData}.
     */
    protected int warningLevel = 0;

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
        // Shriekers only listen to sculk_sensor_tendrils_clicking (chained from a sensor).
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
     * Resolves the triggering player from the vibration initiator (direct player, vehicle
     * passenger, projectile shooter, or dropped-item owner) — i.e. whether the vibration was
     * caused by a player.
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

    /**
     * Resets the resolved warning level, then (if the shrieker can respond) tries to warn nearby
     * players via {@link #tryWarn}. The shriek still happens even when no warning is added — only
     * the warden-response is gated by a successful warning.
     */
    public void tryShriek(Player player) {
        Block block = getLevelBlock();
        if (!(block instanceof BlockSculkShrieker shrieker) || shrieker.isShrieking()) {
            return;
        }
        this.warningLevel = 0;
        boolean canRespond = canRespond(shrieker);
        if (canRespond) {
            // tryWarn sets warningLevel/summonAfterShriek on success; on failure the shriek still
            // proceeds without advancing the warning.
            tryWarn(player);
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
     * Only naturally-generated shriekers (can_summon) respond — in non-peaceful difficulty when
     * mob spawning is enabled.
     */
    protected boolean canRespond(BlockSculkShrieker shrieker) {
        return shrieker.canSummon()
                && level.getServer().getDifficulty() != 0 // 0 == PEACEFUL
                && level.getGameRules().getBoolean(GameRule.DO_MOB_SPAWNING);
    }

    /**
     * Advances the warden warning level for the triggerer and every player within 16 blocks
     * (blamed together), setting each to one above the highest current level and resetting their
     * decay timers.
     * <p>
     * Returns false when a warden is already within 48 blocks or any blamed player is on the
     * per-player 10-second cooldown; the shriek still proceeds in that case.
     */
    protected boolean tryWarn(Player triggerPlayer) {
        if (hasNearbyWarden()) {
            return false;
        }
        List<Player> players = getNearbyPlayers();
        if (!players.contains(triggerPlayer)) {
            players.add(triggerPlayer);
        }
        // If any blamed player is still on cooldown, no warning is added.
        for (Player p : players) {
            if (level.getCurrentTick() - warningFor(p).lastShriekTick < PLAYER_COOLDOWN_TICKS) {
                return false;
            }
        }
        // Set every blamed player to +1 of the highest current level.
        Player highest = players.stream()
                .max(Comparator.comparingInt(p -> warningFor(p).warningLevel))
                .orElse(triggerPlayer);
        WardenWarningData winner = warningFor(highest);
        int newLevel = Math.min(WARDEN_WARNING_LEVEL, winner.warningLevel + 1);
        long tick = level.getCurrentTick();
        this.warningLevel = newLevel;
        this.summonAfterShriek = newLevel >= WARDEN_WARNING_LEVEL;
        for (Player p : players) {
            WardenWarningData w = warningFor(p);
            w.warningLevel = newLevel;
            w.lastShriekTick = tick;
            w.lastWarningTick = tick;
        }
        return true;
    }

    /**
     * When shrieking ends a summon-capable shrieker spawns a warden (at warning level 4) or plays
     * a reply sound, and applies Darkness. Darkness is tied to can_summon, not to whether the
     * warning level was incremented this shriek.
     */
    public void tryRespond() {
        Block block = getLevelBlock();
        if (!(block instanceof BlockSculkShrieker shrieker) || !canRespond(shrieker)) {
            return;
        }
        boolean summoned = false;
        if (summonAfterShriek) {
            summoned = spawnWarden();
        }
        if (!summoned && this.warningLevel > 0) {
            playWardenReplySound(this.warningLevel);
        }
        addDarkness();
    }

    /**
     * Lazily decays a player's warning level: every 12000 ticks (10 minutes) without an activation
     * drops it by 1, computed from the time since the last warning.
     */
    protected WardenWarningData warningFor(Player player) {
        WardenWarningData warning = player.getWardenWarningData();
        long elapsed = level.getCurrentTick() - warning.lastWarningTick;
        if (elapsed >= WARNING_DECAY_TICKS && warning.warningLevel > 0) {
            int decay = (int) (elapsed / WARNING_DECAY_TICKS);
            warning.warningLevel = Math.max(0, warning.warningLevel - decay);
            warning.lastWarningTick = level.getCurrentTick();
        }
        if (warning.warningLevel > WARDEN_WARNING_LEVEL) {
            warning.warningLevel = WARDEN_WARNING_LEVEL;
        }
        return warning;
    }

    /** Non-spectator, alive players within PLAYER_SEARCH_RADIUS of the shrieker. */
    protected List<Player> getNearbyPlayers() {
        List<Player> result = new ArrayList<>();
        double rangeSq = PLAYER_SEARCH_RADIUS * PLAYER_SEARCH_RADIUS;
        for (Player player : level.getPlayers().values()) {
            if (!player.isSpectator() && player.isAlive() && player.distanceSquared(this) <= rangeSq) {
                result.add(player);
            }
        }
        return result;
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
            if (!(player.isSurvival() || player.isAdventure()) || player.distanceSquared(this) > DARKNESS_RANGE * DARKNESS_RANGE) {
                continue;
            }
            // Skip players whose existing Darkness still has at least DARKNESS_DISPLAY_LIMIT ticks.
            Effect existing = player.getEffect(Effect.DARKNESS);
            if (existing != null && existing.getDuration() >= DARKNESS_DISPLAY_LIMIT_TICKS) {
                continue;
            }
            player.addEffect(Effect.getEffect(Effect.DARKNESS).setDuration(DARKNESS_DURATION_TICKS));
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
