package cn.nukkit.entity.item;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntityPistonArm;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHanging;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.ItemPainting;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.AddPaintingPacket;
import cn.nukkit.network.protocol.DataPacket;
import com.google.common.collect.Sets;

import java.util.*;
import java.util.function.Function;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EntityPainting extends EntityHanging {

    public static final int NETWORK_ID = 83;

    private static final Function<Integer, PaintingPlacePredicate> predicateFor4Width = (height) -> (level, face, block, target) -> {
        for (int x = -1; x < 3; x++) {
            for (int z = 0; z < height; z++) {
                if (checkPlacePaint(x, z, level, face, block, target)) return false;
            }
        }
        return true;
    };

    private static final PaintingPlacePredicate predicateFor4WidthHeight = (level, face, block, target) -> {
        for (int x = -1; x < 3; x++) {
            for (int z = -1; z < 3; z++) {
                if (checkPlacePaint(x, z, level, face, block, target)) return false;
            }
        }
        return true;
    };

    private static final PaintingPlacePredicate predicateFor3WidthHeight = (level, face, block, target) -> {
        for (int x = -1; x < 2; x++) {
            for (int z = -1; z < 2; z++) {
                if (checkPlacePaint(x, z, level, face, block, target)) return false;
            }
        }
        return true;
    };

    private static final Function<Integer, PaintingPlacePredicate> predicateFor3Width = (height) -> (level, face, block, target) -> {
        for (int x = -1; x < 2; x++) {
            for (int z = 0; z < height; z++) {
                if (checkPlacePaint(x, z, level, face, block, target)) return false;
            }
        }
        return true;
    };

    @FunctionalInterface
    public interface PaintingPlacePredicate {
        boolean test(Level level, BlockFace blockFace, Block block, Block target);
    }

    public final static Motive[] motives = Arrays.stream(Motive.values())
            .filter(m -> (!m.newPainting || Server.getInstance().enableNewPaintings))
            .toArray(Motive[]::new);

    private Motive motive;

    private float width;
    private float length;
    private float height;

    public EntityPainting(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    public static Motive getMotive(String name) {
        return Motive.BY_NAME.getOrDefault(name, Motive.KEBAB);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return this.width;
    }

    @Override
    public float getLength() {
        return this.length;
    }

    @Override
    public float getHeight() {
        return this.height;
    }

    @Override
    protected void initEntity() {
        this.motive = getMotive(this.namedTag.getString("Motive"));
        if (this.motive == null) {
            this.width = 0;
            this.height = 0;
            this.length = 0;
        } else {
            BlockFace face = getHorizontalFacing();

            Vector3 size = new Vector3(this.motive.width, this.motive.height, this.motive.width).multiply(0.5);

            if (face.getAxis() == BlockFace.Axis.Z) {
                size.z = 0.5;
            } else {
                size.x = 0.5;
            }

            this.width = (float) size.x;
            this.length = (float) size.z;
            this.height = (float) size.y;

            this.boundingBox = new SimpleAxisAlignedBB(
                    this.x - size.x,
                    this.y - size.y,
                    this.z - size.z,
                    this.x + size.x,
                    this.y + size.y,
                    this.z + size.z
            );
        }
        super.initEntity();
    }

    @Override
    public DataPacket createAddEntityPacket() {
        AddPaintingPacket addPainting = new AddPaintingPacket();
        addPainting.entityUniqueId = this.getId();
        addPainting.entityRuntimeId = this.getId();
        addPainting.x = (float) this.x;
        addPainting.y = (float) this.y;
        addPainting.z = (float) this.z;
        addPainting.direction = this.getDirection().getHorizontalIndex();
        addPainting.title = this.namedTag.getString("Motive");
        return addPainting;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        if (super.attack(source)) {
            if (source instanceof EntityDamageByEntityEvent) {
                Entity damager = ((EntityDamageByEntityEvent) source).getDamager();
                if (damager instanceof Player && ((Player) damager).isSurvival() && this.level.getGameRules().getBoolean(GameRule.DO_ENTITY_DROPS)) {
                    this.level.dropItem(this, new ItemPainting());
                }
            }
            this.close();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putString("Motive", this.motive.title);
    }

    @Override
    public void onPushByPiston(BlockEntityPistonArm piston, BlockFace moveDirection) {
        if (this.level.getGameRules().getBoolean(GameRule.DO_ENTITY_DROPS)) {
            this.level.dropItem(this, new ItemPainting());
        }

        this.close();
    }

    private static boolean checkPlacePaint(int x, int z, Level level, BlockFace face, Block block, Block target) {
        if (target.getSide(face.rotateYCCW(), x).up(z).isTransparent() ||
                block.getSide(face.rotateYCCW(), x).up(z).isSolid()) {
            return true;
        } else {
            Block side = block.getSide(face.rotateYCCW(), x);
            Block up = side.up(z).getLevelBlock();
            Block up1 = block.up(z);
            Set<FullChunk> chunks = Sets.newHashSet(side.getChunk(), up.getChunk(), up1.getChunk());
            Collection<Entity> entities = chunks.stream().map(c -> c.getEntities().values()).reduce(new ArrayList<>(), (e1, e2) -> {
                e1.addAll(e2);
                return e1;
            }, (entities1, entities2) -> {
                entities1.addAll(entities2);
                return entities1;
            });
            for (var e : entities) {
                if (e instanceof EntityPainting painting) {
                    if (painting.getBoundingBox().intersectsWith(side) || painting.getBoundingBox().intersectsWith(up) || painting.getBoundingBox().intersectsWith(up1)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Motive getArt() {
        return getMotive();
    }

    public Motive getMotive() {
        return Motive.BY_NAME.get(namedTag.getString("Motive"));
    }

    public enum Motive {
        KEBAB("Kebab", 1, 1),
        AZTEC("Aztec", 1, 1),
        ALBAN("Alban", 1, 1),
        AZTEC2("Aztec2", 1, 1),
        BOMB("Bomb", 1, 1),
        PLANT("Plant", 1, 1),
        WASTELAND("Wasteland", 1, 1),
        MEDITATIVE("meditative", 1, 1, true),
        WANDERER("Wanderer", 1, 2),
        GRAHAM("Graham", 1, 2),
        PRAIRIE_RIDE("prairie_ride", 1, 2, true),
        POOL("Pool", 2, 1),
        COURBET("Courbet", 2, 1),
        SUNSET("Sunset", 2, 1),
        SEA("Sea", 2, 1),
        CREEBET("Creebet", 2, 1),
        MATCH("Match", 2, 2),
        BUST("Bust", 2, 2),
        STAGE("Stage", 2, 2),
        VOID("Void", 2, 2),
        SKULL_AND_ROSES("SkullAndRoses", 2, 2),
        WITHER("Wither", 2, 2),
        BAROQUE("baroque", 2, 2, true),
        HUMBLE("humble", 2, 2, true),
        BOUQUET("bouquet", 3, 3, true, predicateFor3WidthHeight),
        CAVEBIRD("cavebird", 3, 3, true, predicateFor3WidthHeight),
        COTAN("cotan", 3, 3, true, predicateFor3WidthHeight),
        ENDBOSS("endboss", 3, 3, true, predicateFor3WidthHeight),
        FERN("fern", 3, 3, true, predicateFor3WidthHeight),
        OWLEMONS("owlemons", 3, 3, true, predicateFor3WidthHeight),
        SUNFLOWERS("sunflowers", 3, 3, true, predicateFor3WidthHeight),
        TIDES("tides", 3, 3, true, predicateFor3WidthHeight),
        BACKYARD("backyard", 3, 4, true, predicateFor3Width.apply(4)),
        POND("pond", 3, 4, true, predicateFor3Width.apply(4)),
        FIGHTERS("Fighters", 4, 2, predicateFor4Width.apply(2)),
        CHANGING("changing", 4, 2, true, predicateFor4Width.apply(2)),
        FINDING("finding", 4, 2, true, predicateFor4Width.apply(2)),
        LOWMIST("lowmist", 4, 2, true, predicateFor4Width.apply(2)),
        PASSAGE("passage", 4, 2, true, predicateFor4Width.apply(2)),
        SKELETON("Skeleton", 4, 3, predicateFor4Width.apply(3)),
        DONKEY_KONG("DonkeyKong", 4, 3, predicateFor4Width.apply(3)),
        POINTER("Pointer", 4, 4, predicateFor4WidthHeight),
        PIG_SCENE("Pigscene", 4, 4, predicateFor4WidthHeight),
        BURNING_SKULL("BurningSkull", 4, 4, predicateFor4WidthHeight),
        ORB("orb", 4, 4, true, predicateFor4WidthHeight),
        UNPACKED("unpacked", 4, 4, true, predicateFor4WidthHeight);

        private static final Map<String, Motive> BY_NAME = new HashMap<>();

        static {
            for (Motive motive : values()) {
                BY_NAME.put(motive.title, motive);
            }
        }

        public final String title;
        public final int width;
        public final int height;
        public final PaintingPlacePredicate predicate;
        public final boolean newPainting;

        Motive(String title, int width, int height) {
            this(title, width, height, false);
        }

        Motive(String title, int width, int height, boolean newPainting) {
            this.title = title;
            this.width = width;
            this.height = height;
            this.newPainting = newPainting;
            this.predicate = (level, face, block, target) -> {
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < height; z++) {
                        if (checkPlacePaint(x, z, level, face, block, target)) return false;
                    }
                }
                return true;
            };
        }

        Motive(String title, int width, int height, PaintingPlacePredicate predicate) {
            this(title, width, height, false, predicate);
        }

        Motive(String title, int width, int height, boolean newPainting, PaintingPlacePredicate predicate) {
            this.title = title;
            this.width = width;
            this.height = height;
            this.newPainting = newPainting;
            this.predicate = predicate;
        }
    }
}