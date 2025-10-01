package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.*;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.entity.projectile.EntityBlueWitherSkull;
import cn.nukkit.entity.projectile.EntityWitherSkull;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityExplosionPrimeEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Location;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.DummyBossBar;
import cn.nukkit.utils.Utils;
import org.apache.commons.math3.util.FastMath;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

public class EntityWither extends EntityFlyingMob implements EntityBoss, EntitySmite {

    public static final int NETWORK_ID = 52;

    /**
     *  Whether the wither is exploded and dying
     */
    protected boolean exploded;
    protected boolean wasExplosion;
    protected int invul;

    private HashMap<UUID, DummyBossBar> dummyBossBars;

    public EntityWither(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.9f;
    }

    @Override
    public float getHeight() {
        return 3.5f;
    }

    @Override
    public double getSpeed() {
        return 1.3;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(witherMaxHealth());

        super.initEntity();

        if (this.namedTag.contains("Invul")) {
            this.invul = this.namedTag.getInt("Invul");
        } else {
            this.invul = 200;
        }

        this.dummyBossBars = new HashMap<>();

        this.fireProof = true;
        this.setDamage(new int[]{0, 2, 4, 6});
        if (this.invul > 0) {
            this.setDataProperty(new IntEntityData(DATA_WITHER_INVULNERABLE_TICKS, this.invul));

            this.stayTime = this.invul + 20;
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.namedTag.putInt("Invul", this.invul);
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (creature instanceof Player player) {
            if (!player.isSurvival() && !player.isAdventure()) {
                return false;
            }
        }
        return creature.isAlive() && !creature.closed && distance <= 10000;
    }

    @Override
    public int getKillExperience() {
        return 50;
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.invul <= 0 && this.attackDelay > 40 && this.distanceSquared(player) <= 4096) {
            this.attackDelay = 0;

            double f = 1;
            double yaw = this.yaw + Utils.rand(-4.0, 4.0);
            double pitch = this.pitch + Utils.rand(-4.0, 4.0);
            Location pos = new Location(this.x - Math.sin(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * 0.5, this.y + this.getEyeHeight(),
                    this.z + Math.cos(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * 0.5, yaw, pitch, this.level);

            if (this.getLevel().getBlockIdAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ()) != Block.AIR) {
                return;
            }

            Entity k;
            ProjectileLaunchEvent launch;
            EntityWitherSkull skull;
            if (Utils.rand(0, 200) > 180 || Utils.rand(0, 200) < 20) {
                f = 0.8;
                k = Entity.createEntity("BlueWitherSkull", pos, this);
                skull = (EntityBlueWitherSkull) k;
                ((EntityBlueWitherSkull) skull).setExplode(true);
            } else {
                k = Entity.createEntity("WitherSkull", pos, this);
                skull = (EntityWitherSkull) k;
            }
            skull.setMotion(new Vector3(-Math.sin(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * f * f, -Math.sin(FastMath.toRadians(pitch)) * f * f,
                    Math.cos(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * f * f));
            launch = new ProjectileLaunchEvent(skull);

            this.server.getPluginManager().callEvent(launch);
            if (launch.isCancelled()) {
                skull.close();
            } else {
                skull.spawnToAll();
                this.level.addSoundToViewers(this, Sound.MOB_WITHER_SHOOT);
            }
        }
    }

    @Override
    public Item[] getDrops() {
        return new Item[]{Item.get(Item.NETHER_STAR, 0, 1)};
    }

    @Override
    protected DataPacket createAddEntityPacket() {
        AddEntityPacket addEntity = new AddEntityPacket();
        addEntity.type = NETWORK_ID;
        addEntity.entityUniqueId = this.getId();
        addEntity.entityRuntimeId = this.getId();
        addEntity.yaw = (float) this.yaw;
        addEntity.headYaw = (float) this.yaw;
        addEntity.pitch = (float) this.pitch;
        addEntity.x = (float) this.x;
        addEntity.y = (float) this.y;
        addEntity.z = (float) this.z;
        addEntity.speedX = (float) this.motionX;
        addEntity.speedY = (float) this.motionY;
        addEntity.speedZ = (float) this.motionZ;
        addEntity.metadata = this.dataProperties.clone();
        addEntity.attributes = new Attribute[]{Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(witherMaxHealth()).setValue(witherMaxHealth())};
        return addEntity;
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (getServer().getDifficulty() == 0) {
            this.close();
            return true;
        }

        if (!this.closed && this.age == 200) {
            this.explode();
            this.setDataProperty(new IntEntityData(DATA_WITHER_INVULNERABLE_TICKS, 0));
        }

        return super.entityBaseTick(tickDiff);
    }

    @Override
    public int nearbyDistanceMultiplier() {
        return 30;
    }

    @Override
    public void kill() {
        if (!this.isAlive()) {
            return;
        }

        if (!this.exploded && this.lastDamageCause != null && EntityDamageEvent.DamageCause.SUICIDE != this.lastDamageCause.getCause()) {
            if (this.lastDamageCause instanceof EntityDamageByEntityEvent event) {
                if (event.getDamager() instanceof Player player) {
                    player.awardAchievement("killWither");
                }
            }

            this.exploded = true;
            this.explode();
        }

        super.kill();

        this.updateBossBars();
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        if (this.invul > 0 && ev.getCause() != EntityDamageEvent.DamageCause.SUICIDE) {
            return false;
        }

        boolean r = super.attack(ev);

        if (r && !this.closed) {
            this.updateBossBars();
        }

        if (this.wasExplosion) {
            this.wasExplosion = false;
        } else if (r && ev instanceof EntityDamageByEntityEvent && ((EntityDamageByEntityEvent) ev).getDamager() instanceof Player && !this.closed && this.isAlive() && Utils.rand() && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)) {
            this.wasExplosion = true;
            this.level.addSound(this, Sound.MOB_WITHER_BREAK_BLOCK);
            int fx = this.getFloorX();
            int fy = this.getFloorY();
            int fz = this.getFloorZ();
            Vector3 pos = new Vector3(0, 0, 0);
            Item tool = Item.get(Item.DIAMOND_PICKAXE);
            for (int x = fx - 2; x <= fx + 2; x++) {
                for (int y = fy; y <= fy + 4; y++) {
                    for (int z = fz - 2; z <= fz + 2; z++) {
                        Block block = this.level.getBlock(x, y, z);
                        if (block.isBreakable(tool)) {
                            pos.setComponents(x, y, z);
                            this.level.setBlock(pos, Block.get(Block.AIR));
                            BlockEntity blockEntity = this.level.getBlockEntityIfLoaded(pos);
                            if (blockEntity != null) {
                                blockEntity.onBreak();
                                blockEntity.close();
                            }
                            if (this.level.getGameRules().getBoolean(GameRule.DO_TILE_DROPS) && Math.random() * 100 < 14) {
                                for (Item drop : block.getDrops(tool)) {
                                    this.level.dropItem(block, drop);
                                }
                            }
                        }
                    }
                }
            }
        }
        return r;
    }

    private void updateBossBars() {
        float progress;
        if (this.invul > 0) {
            progress = (200 - this.invul) / 2f;
        } else {
            progress = (this.health / this.getMaxHealth()) * 100;
        }

        if (this.isAlive()) {
            this.getViewers().forEach((id, player) -> {
                DummyBossBar bossBar = this.dummyBossBars.get(player.getUniqueId());
                if (bossBar == null) {
                    bossBar = new DummyBossBar.Builder(player)
                            .text(this.getName())
                            .length(progress)
                            .build();
                    player.createBossBar(bossBar);
                    this.dummyBossBars.put(player.getUniqueId(), bossBar);
                }
                if (bossBar.getLength() != progress) {
                    bossBar.setLength(progress);
                }
            });
        } else {
            this.getViewers().forEach((id, player) -> {
                DummyBossBar bossBar = this.dummyBossBars.get(player.getUniqueId());
                if (bossBar != null) {
                    player.removeBossBar(bossBar.getBossBarId());
                    this.dummyBossBars.remove(player.getUniqueId());
                }
            });
        }
    }

    private int witherMaxHealth() {
        return switch (this.getServer().getDifficulty()) {
            case 2 -> 450;
            case 3 -> 600;
            default -> 300;
        };
    }

    private void explode() {
        EntityExplosionPrimeEvent ev = new EntityExplosionPrimeEvent(this, 7);
        this.server.getPluginManager().callEvent(ev);

        if (!ev.isCancelled()) {
            Explosion explosion = new Explosion(this, (float) ev.getForce(), this);

            if (ev.isBlockBreaking() && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)) {
                explosion.explodeA();
            }

            explosion.explodeB();
        }
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.invul > 0) {
            int tickDiff = currentTick - this.lastUpdate;
            this.invul -= Math.max(0, tickDiff);
        }

        this.updateBossBars();

        return super.onUpdate(currentTick);
    }

    @Override
    public boolean canTarget(Entity entity) {
        return entity.canBeFollowed();
    }

    @Override
    public void spawnTo(Player player) {
        super.spawnTo(player);

        if (!this.dummyBossBars.containsKey(player.getUniqueId())) {
            DummyBossBar dummyBossBar = new DummyBossBar.Builder(player)
                    .text(this.getName())
                    .length((this.health / this.getMaxHealth()) * 100)
                    .build();
            player.createBossBar(dummyBossBar);

            this.dummyBossBars.put(player.getUniqueId(), dummyBossBar);
        }
    }

    @Override
    public void despawnFrom(Player player) {
        super.despawnFrom(player);
        DummyBossBar dummyBossBar = this.dummyBossBars.get(player.getUniqueId());
        if (dummyBossBar != null) {
            player.removeBossBar(dummyBossBar.getBossBarId());
            this.dummyBossBars.remove(player.getUniqueId());
        }
    }

    @Override
    public String getName() {
        String name = this.getNameTag();
        return !name.isEmpty() ? name : "Wither";
    }

    @Override
    public boolean canBeAffected(int effectId) {
        return effectId == Effect.INSTANT_DAMAGE || effectId == Effect.INSTANT_HEALTH;
    }

    /**
     * 检查并生成凋零实体 <br/>
     * Check and spawn wither entity
     *
     * @param block 要检查的方块实例 <br/>
     *              The block instance to check
     * @return 如果成功生成凋零实体则返回 true，否则返回 false <br/>
     * Returns true if the wither entity is successfully spawned, otherwise false
     */
    public static boolean checkAndSpawnWither(Block block) {
        Block check = block;
        BlockFace skullFace = null;
        if (Server.getInstance().mobsFromBlocks && block.getLevel().getGameRules().getBoolean(GameRule.DO_MOB_SPAWNING)) {
            for (BlockFace face : Set.of(BlockFace.NORTH, BlockFace.EAST)) {
                boolean[] skulls = new boolean[5];
                for (int i = -2; i <= 2; i++) {
                    skulls[i + 2] = block.getSide(face, i).getId() == Block.WITHER_SKELETON_SKULL;
                }
                int inrow = 0;
                for (int i = 0; i < skulls.length; i++) {
                    if (skulls[i]) {
                        inrow++;
                        if (inrow == 2) check = block.getSide(face, i - 2);
                    } else if (inrow < 3) {
                        inrow = 0;
                    }
                }
                if (inrow >= 3) {
                    skullFace = face;
                }
            }
            if (skullFace == null) return false;
            if (check.getId() == Block.WITHER_SKELETON_SKULL) {
                faces:
                for (BlockFace blockFace : BlockFace.values()) {
                    for (int i = 1; i <= 2; i++) {
                        if (!(check.getSide(blockFace, i).getId() == Block.SOUL_SAND)) {
                            continue faces;
                        }
                    }
                    faces1:
                    for (BlockFace face : Set.of(BlockFace.UP, BlockFace.NORTH, BlockFace.EAST)) {
                        for (int i = -1; i <= 1; i++) {
                            if (!(check.getSide(blockFace).getSide(face, i).getId() == Block.SOUL_SAND)) {
                                continue faces1;
                            }
                        }

                        for (int i = 0; i <= 2; i++) {
                            Block location = check.getSide(blockFace, i);
                            location.level.breakBlock(location);
                        }
                        for (int i = -1; i <= 1; i++) {
                            Block location = check.getSide(blockFace).getSide(face, i);
                            location.level.breakBlock(location);
                            location.level.breakBlock(location.getSide(blockFace.getOpposite()));
                        }
                        Block pos = check.getSide(blockFace, 2);
                        CompoundTag nbt = new CompoundTag()
                                .putList("Pos", new ListTag<DoubleTag>()
                                        .add(new DoubleTag(pos.x + 0.5))
                                        .add(new DoubleTag(pos.y))
                                        .add(new DoubleTag(pos.z + 0.5)))
                                .putList("Motion", new ListTag<DoubleTag>()
                                        .add(new DoubleTag(0))
                                        .add(new DoubleTag(0))
                                        .add(new DoubleTag(0)))
                                .putList("Rotation", new ListTag<FloatTag>()
                                        .add(new FloatTag(0f))
                                        .add(new FloatTag(0f)));

                        EntityWither wither = (EntityWither) Entity.createEntity("Wither", check.level.getChunk(check.getChunkX(), check.getChunkZ()), nbt);
                        if (wither != null) {
                            wither.stayTime = 220;
                            wither.spawnToAll();
                            wither.getLevel().addSoundToViewers(wither, Sound.MOB_WITHER_SPAWN);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
