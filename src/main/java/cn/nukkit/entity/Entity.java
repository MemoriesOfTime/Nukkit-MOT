package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.*;
import cn.nukkit.blockentity.BlockEntityPistonArm;
import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.entity.data.*;
import cn.nukkit.entity.data.property.*;
import cn.nukkit.entity.item.EntityVehicle;
import cn.nukkit.entity.mob.EntityCreeper;
import cn.nukkit.entity.mob.EntityWolf;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.*;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerInteractEvent.Action;
import cn.nukkit.event.player.PlayerTeleportEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTotem;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.*;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.*;
import cn.nukkit.metadata.MetadataValue;
import cn.nukkit.metadata.Metadatable;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.EntityLink;
import cn.nukkit.network.protocol.types.PropertySyncData;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.ChunkException;
import cn.nukkit.utils.Identifier;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.Utils;
import com.google.common.collect.Iterables;
import org.apache.commons.math3.util.FastMath;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static cn.nukkit.network.protocol.SetEntityLinkPacket.*;
import static cn.nukkit.utils.Utils.dynamic;

/**
 * @author MagicDroidX
 */
@SuppressWarnings("rawtypes")
public abstract class Entity extends Location implements Metadatable {

    public static final int NETWORK_ID = -1;

    public abstract int getNetworkId();

    public static final int DATA_TYPE_BYTE = 0;
    public static final int DATA_TYPE_SHORT = 1;
    public static final int DATA_TYPE_INT = 2;
    public static final int DATA_TYPE_FLOAT = 3;
    public static final int DATA_TYPE_STRING = 4;
    public static final int DATA_TYPE_NBT = 5;
    public static final int DATA_TYPE_POS = 6;
    public static final int DATA_TYPE_LONG = 7;
    public static final int DATA_TYPE_VECTOR3F = 8;

    /**
     * 0~63 DATA_FLAGS
     * 64~128 DATA_FLAGS_EXTENDED (DATA_FLAGS2)
     */
    public static final int DATA_FLAGS = 0;
    public static final int DATA_HEALTH = 1; //int (minecart/boat)
    public static final int DATA_VARIANT = 2; //int
    public static final int DATA_COLOR = 3, DATA_COLOUR = DATA_COLOR; //byte
    public static final int DATA_NAMETAG = 4; //string
    public static final int DATA_OWNER_EID = 5; //long
    public static final int DATA_TARGET_EID = 6; //long
    public static final int DATA_AIR = 7; //short
    public static final int DATA_EFFECT_COLOR = 8, DATA_POTION_COLOR = DATA_EFFECT_COLOR; //int (ARGB!)
    public static final int DATA_POTION_AMBIENT = 9; //byte
    public static final int DATA_JUMP_DURATION = 10; //long
    public static final int DATA_HURT_TIME = 11; //int (minecart/boat)
    public static final int DATA_HURT_DIRECTION = 12; //int (minecart/boat)
    public static final int DATA_PADDLE_TIME_LEFT = 13; //float
    public static final int DATA_PADDLE_TIME_RIGHT = 14; //float
    public static final int DATA_EXPERIENCE_VALUE = 15; //int (xp orb)
    public static final int DATA_DISPLAY_ITEM = 16; //int (id | (data << 16))
    public static final int DATA_DISPLAY_OFFSET = 17; //int
    public static final int DATA_FIREWORK_DIRECTION = DATA_DISPLAY_OFFSET; //vec3f
    public static final int DATA_HAS_DISPLAY = 18; //byte (must be 1 for minecart to show block inside)
    public static final int DATA_ARROW_AUX_VALUE = DATA_HAS_DISPLAY; //byte (tipped arrow item meta)
    public static final int DATA_SWELL = 19;
    public static final int DATA_OLD_SWELL = 20;
    public static final int DATA_SWELL_DIR = 21;
    public static final int DATA_CHARGE_AMOUNT = 22;
    public static final int DATA_ENDERMAN_HELD_RUNTIME_ID = 23; //int (block runtime id)
    public static final int DATA_ENTITY_AGE = 24; //short
    public static final int DATA_PLAYER_FLAGS = 26; //byte
    public static final int DATA_PLAYER_INDEX = 27;
    public static final int DATA_PLAYER_BED_POSITION = 28; //block coords
    public static final int DATA_FIREBALL_POWER_X = 29; //float
    public static final int DATA_FIREBALL_POWER_Y = 30;
    public static final int DATA_FIREBALL_POWER_Z = 31;
    public static final int DATA_AUX_POWER = 32;
    public static final int DATA_FISH_X = 33;
    public static final int DATA_FISH_Z = 34;
    public static final int DATA_FISH_ANGLE = 35;
    public static final int DATA_AUX_VALUE_DATA = 36, DATA_POTION_AUX_VALUE = DATA_AUX_VALUE_DATA; //short
    public static final int DATA_LEAD_HOLDER_EID = 37; //long
    public static final int DATA_SCALE = 38; //float
    public static final int DATA_HAS_NPC_COMPONENT = 39; //byte
    public static final int DATA_NPC_SKIN_DATA = dynamic(40); //string
    @Deprecated
    public static final int DATA_NPC_SKIN_ID = DATA_NPC_SKIN_DATA; //string
    public static final int DATA_NPC_ACTIONS = dynamic(41); //string
    @Deprecated
    public static final int DATA_URL_TAG = DATA_NPC_ACTIONS; //string
    public static final int DATA_MAX_AIR = 42; //short
    public static final int DATA_MARK_VARIANT = 43; //int
    public static final int DATA_CONTAINER_TYPE = 44; //byte
    public static final int DATA_CONTAINER_BASE_SIZE = 45; //int
    public static final int DATA_CONTAINER_EXTRA_SLOTS_PER_STRENGTH = 46; //int
    public static final int DATA_BLOCK_TARGET = 47; //block coords (ender crystal)
    public static final int DATA_WITHER_INVULNERABLE_TICKS = 48; //int
    public static final int DATA_WITHER_TARGET_1 = 49; //long
    public static final int DATA_WITHER_TARGET_2 = 50; //long
    public static final int DATA_WITHER_TARGET_3 = 51; //long
    public static final int DATA_AERIAL_ATTACK = 52;
    public static final int DATA_BOUNDING_BOX_WIDTH = 53; //float
    public static final int DATA_BOUNDING_BOX_HEIGHT = 54; //float
    public static final int DATA_FUSE_LENGTH = 55; //int
    public static final int DATA_RIDER_SEAT_POSITION = 56; //vector3f
    public static final int DATA_RIDER_ROTATION_LOCKED = 57; //byte
    public static final int DATA_RIDER_MAX_ROTATION = 58; //float
    public static final int DATA_RIDER_MIN_ROTATION = 59; //float
    public static final int DATA_RIDER_ROTATION_OFFSET = 60;
    public static final int DATA_AREA_EFFECT_CLOUD_RADIUS = 61; //float
    public static final int DATA_AREA_EFFECT_CLOUD_WAITING = 62; //int
    public static final int DATA_AREA_EFFECT_CLOUD_PARTICLE_ID = 63; //int
    public static final int DATA_SHULKER_PEEK_ID = 64; //int
    public static final int DATA_SHULKER_ATTACH_FACE = 65; //byte
    public static final int DATA_SHULKER_ATTACHED = 66; //short
    public static final int DATA_SHULKER_ATTACH_POS = 67; //block coords
    public static final int DATA_TRADING_PLAYER_EID = 68; //long
    public static final int DATA_TRADING_CAREER = 69;
    public static final int DATA_HAS_COMMAND_BLOCK = 70;
    public static final int DATA_COMMAND_BLOCK_COMMAND = 71; //string
    public static final int DATA_COMMAND_BLOCK_LAST_OUTPUT = 72; //string
    public static final int DATA_COMMAND_BLOCK_TRACK_OUTPUT = 73; //byte
    public static final int DATA_CONTROLLING_RIDER_SEAT_NUMBER = 74; //byte
    public static final int DATA_STRENGTH = 75; //int
    public static final int DATA_MAX_STRENGTH = 76; //int
    public static final int DATA_SPELL_CASTING_COLOR = 77; //int
    public static final int DATA_LIMITED_LIFE = 78;
    public static final int DATA_ARMOR_STAND_POSE_INDEX = 79; // int
    public static final int DATA_ENDER_CRYSTAL_TIME_OFFSET = 80; // int
    public static final int DATA_ALWAYS_SHOW_NAMETAG = 81; // byte
    public static final int DATA_COLOR_2 = 82; // byte
    public static final int DATA_NAME_AUTHOR = 83;
    public static final int DATA_SCORE_TAG = 84; // String
    public static final int DATA_BALLOON_ATTACHED_ENTITY = 85; // long
    public static final int DATA_PUFFERFISH_SIZE = 86;
    public static final int DATA_BUBBLE_TIME = 87;
    public static final int DATA_AGENT = 88;
    public static final int DATA_SITTING_AMOUNT = 89;
    public static final int DATA_SITTING_AMOUNT_PREVIOUS = 90;
    public static final int DATA_EATING_COUNTER = 91;
    public static final int DATA_FLAGS_EXTENDED = 92, DATA_FLAGS2 = DATA_FLAGS_EXTENDED; //long (extended data flags)
    public static final int DATA_LAYING_AMOUNT = 93;
    public static final int DATA_LAYING_AMOUNT_PREVIOUS = 94;
    public static final int DATA_AREA_EFFECT_CLOUD_DURATION = 95, DATA_DURATION = DATA_AREA_EFFECT_CLOUD_DURATION; // int
    public static final int DATA_AREA_EFFECT_CLOUD_SPAWN_TIME = 96, DATA_SPAWN_TIME = DATA_AREA_EFFECT_CLOUD_SPAWN_TIME; // int
    public static final int DATA_AREA_EFFECT_CLOUD_CHANGE_RATE = 97, DATA_CHANGE_RATE = DATA_AREA_EFFECT_CLOUD_CHANGE_RATE; // float
    public static final int DATA_AREA_EFFECT_CLOUD_CHANGE_ON_PICKUP = 98, DATA_CHANGE_ON_PICKUP = DATA_AREA_EFFECT_CLOUD_CHANGE_ON_PICKUP; // float
    public static final int DATA_AREA_EFFECT_CLOUD_PICKUP_COUNT = 99, DATA_PICKUP_COUNT = DATA_AREA_EFFECT_CLOUD_PICKUP_COUNT; //int
    public static final int DATA_INTERACTIVE_TAG = 100; // string (button text)
    public static final int DATA_TRADE_TIER = 101;
    public static final int DATA_MAX_TRADE_TIER = 102;
    public static final int DATA_TRADE_EXPERIENCE = 103;
    public static final int DATA_SKIN_ID = 104; // int
    public static final int DATA_SPAWNING_FRAMES = 105;
    public static final int DATA_COMMAND_BLOCK_TICK_DELAY = 106; // int
    public static final int DATA_COMMAND_BLOCK_EXECUTE_ON_FIRST_TICK = 107; // byte
    public static final int DATA_AMBIENT_SOUND_INTERVAL = 108;
    public static final int DATA_AMBIENT_SOUND_INTERVAL_RANGE = 109;
    public static final int DATA_AMBIENT_SOUND_EVENT_NAME = 110;
    public static final int DATA_FALL_DAMAGE_MULTIPLIER = 111;
    public static final int DATA_NAME_RAW_TEXT = 112;
    public static final int DATA_CAN_RIDE_TARGET = 113;
    public static final int DATA_LOW_TIER_CURED_DISCOUNT = 114;
    public static final int DATA_HIGH_TIER_CURED_DISCOUNT = 115;
    public static final int DATA_NEARBY_CURED_DISCOUNT = 116;
    public static final int DATA_NEARBY_CURED_DISCOUNT_TIMESTAMP = 117;
    public static final int DATA_HITBOX = 118;
    public static final int DATA_IS_BUOYANT = 119;
    public static final int DATA_FREEZING_EFFECT_STRENGTH = 120;
    public static final int DATA_BUOYANCY_DATA = 121;
    public static final int DATA_GOAT_HORN_COUNT = 122;
    public static final int DATA_BASE_RUNTIME_ID = 123;
    public static final int DATA_MOVEMENT_SOUND_DISTANCE_OFFSET = 124;
    public static final int DATA_HEARTBEAT_INTERVAL_TICKS = 125;
    public static final int DATA_HEARTBEAT_SOUND_EVENT = 126;
    public static final int DATA_PLAYER_LAST_DEATH_POS = 127;
    public static final int DATA_PLAYER_LAST_DEATH_DIMENSION = 128;
    public static final int DATA_PLAYER_HAS_DIED = 129;
    public static final int DATA_COLLISION_BOX = 130; //vector3f
    /**
     * @since v685
     */
    public static final int DATA_VISIBLE_MOB_EFFECTS = 131; //long
    /**
     * @since v776 1.21.60
     */
    public static final int DATA_FILTERED_NAME = 132; //string
    /**
     * @since v776 1.21.60
     */
    public static final int DATA_BED_ENTER_POSITION = 133; //vector3f
    /**
     * @since v800
     */
    public static final int DATA_SEAT_THIRD_PERSON_CAMERA_RADIUS = 134; //float
    /**
     * @since v800
     */
    public static final int DATA_SEAT_CAMERA_RELAX_DISTANCE_SMOOTHING = 135; //float

    // Flags
    public static final int DATA_FLAG_ONFIRE = 0;
    public static final int DATA_FLAG_SNEAKING = 1;
    public static final int DATA_FLAG_RIDING = 2;
    public static final int DATA_FLAG_SPRINTING = 3;
    public static final int DATA_FLAG_ACTION = 4;
    public static final int DATA_FLAG_INVISIBLE = 5;
    public static final int DATA_FLAG_TEMPTED = 6;
    public static final int DATA_FLAG_INLOVE = 7;
    public static final int DATA_FLAG_SADDLED = 8;
    public static final int DATA_FLAG_POWERED = 9;
    public static final int DATA_FLAG_IGNITED = 10;
    public static final int DATA_FLAG_BABY = 11;
    public static final int DATA_FLAG_CONVERTING = 12;
    public static final int DATA_FLAG_CRITICAL = 13;
    public static final int DATA_FLAG_CAN_SHOW_NAMETAG = 14;
    public static final int DATA_FLAG_ALWAYS_SHOW_NAMETAG = 15;
    public static final int DATA_FLAG_IMMOBILE = 16, DATA_FLAG_NO_AI = DATA_FLAG_IMMOBILE;
    public static final int DATA_FLAG_SILENT = 17;
    public static final int DATA_FLAG_WALLCLIMBING = 18;
    public static final int DATA_FLAG_CAN_CLIMB = 19;
    public static final int DATA_FLAG_SWIMMER = 20;
    public static final int DATA_FLAG_CAN_FLY = 21;
    public static final int DATA_FLAG_WALKER = 22;
    public static final int DATA_FLAG_RESTING = 23;
    public static final int DATA_FLAG_SITTING = 24;
    public static final int DATA_FLAG_ANGRY = 25;
    public static final int DATA_FLAG_INTERESTED = 26;
    public static final int DATA_FLAG_CHARGED = 27;
    public static final int DATA_FLAG_TAMED = 28;
    public static final int DATA_FLAG_ORPHANED = 29;
    public static final int DATA_FLAG_LEASHED = 30;
    public static final int DATA_FLAG_SHEARED = 31;
    public static final int DATA_FLAG_GLIDING = 32;
    public static final int DATA_FLAG_ELDER = 33;
    public static final int DATA_FLAG_MOVING = 34;
    public static final int DATA_FLAG_BREATHING = 35;
    public static final int DATA_FLAG_CHESTED = 36;
    public static final int DATA_FLAG_STACKABLE = 37;
    public static final int DATA_FLAG_SHOWBASE = 38;
    public static final int DATA_FLAG_REARING = 39;
    public static final int DATA_FLAG_VIBRATING = 40;
    public static final int DATA_FLAG_IDLING = 41;
    public static final int DATA_FLAG_EVOKER_SPELL = 42;
    public static final int DATA_FLAG_CHARGE_ATTACK = 43;
    public static final int DATA_FLAG_WASD_CONTROLLED = 44;
    public static final int DATA_FLAG_CAN_POWER_JUMP = 45;
    public static final int DATA_FLAG_CAN_DASH = 46;
    public static final int DATA_FLAG_LINGER = 47;
    public static final int DATA_FLAG_HAS_COLLISION = 48;
    public static final int DATA_FLAG_GRAVITY = 49;
    public static final int DATA_FLAG_FIRE_IMMUNE = 50;
    public static final int DATA_FLAG_DANCING = 51;
    public static final int DATA_FLAG_ENCHANTED = 52;
    public static final int DATA_FLAG_SHOW_TRIDENT_ROPE = 53; // tridents show an animated rope when enchanted with loyalty after they are thrown and return to their owner. To be combined with DATA_OWNER_EID
    public static final int DATA_FLAG_CONTAINER_PRIVATE = 54; //inventory is private, doesn't drop contents when killed if true
    public static final int DATA_FLAG_IS_TRANSFORMING = 55;
    public static final int DATA_FLAG_SPIN_ATTACK = 56;
    public static final int DATA_FLAG_SWIMMING = 57;
    public static final int DATA_FLAG_BRIBED = 58; //dolphins have this set when they go to find treasure for the player
    public static final int DATA_FLAG_PREGNANT = 59;
    public static final int DATA_FLAG_LAYING_EGG = 60;
    public static final int DATA_FLAG_RIDER_CAN_PICK = 61;
    public static final int DATA_FLAG_TRANSITION_SETTING = 62;
    public static final int DATA_FLAG_EATING = 63;
    public static final int DATA_FLAG_LAYING_DOWN = 64;
    public static final int DATA_FLAG_SNEEZING = 65;
    public static final int DATA_FLAG_TRUSTING = 66;
    public static final int DATA_FLAG_ROLLING = 67;
    public static final int DATA_FLAG_SCARED = 68;
    public static final int DATA_FLAG_IN_SCAFFOLDING = 69;
    public static final int DATA_FLAG_OVER_SCAFFOLDING = 70;
    public static final int DATA_FLAG_FALL_THROUGH_SCAFFOLDING = 71;
    public static final int DATA_FLAG_BLOCKING = 72;
    public static final int DATA_FLAG_TRANSITION_BLOCKING = 73;
    public static final int DATA_FLAG_BLOCKED_USING_SHIELD = 74;
    public static final int DATA_FLAG_BLOCKED_USING_DAMAGED_SHIELD = 75;
    public static final int DATA_FLAG_SLEEPING = 76;
    public static final int DATA_FLAG_ENTITY_GROW_UP = 77;
    public static final int DATA_FLAG_TRADE_INTEREST = 78;
    public static final int DATA_FLAG_DOOR_BREAKER = 79;
    public static final int DATA_FLAG_BREAKING_OBSTRUCTION = 80;
    public static final int DATA_FLAG_DOOR_OPENER = 81;
    public static final int DATA_FLAG_IS_ILLAGER_CAPTAIN = 82;
    public static final int DATA_FLAG_STUNNED = 83;
    public static final int DATA_FLAG_ROARING = 84;
    public static final int DATA_FLAG_DELAYED_ATTACK = 85;
    public static final int DATA_FLAG_IS_AVOIDING_MOBS = 86;
    public static final int DATA_FLAG_IS_AVOIDING_BLOCKS = 87;
    public static final int DATA_FLAG_FACING_TARGET_TO_RANGE_ATTACK = 88;
    public static final int DATA_FLAG_HIDDEN_WHEN_INVISIBLE = 89;
    public static final int DATA_FLAG_IS_IN_UI = 90;
    public static final int DATA_FLAG_STALKING = 91;
    public static final int DATA_FLAG_EMOTING = 92;
    public static final int DATA_FLAG_CELEBRATING = 93;
    public static final int DATA_FLAG_ADMIRING = 94;
    public static final int DATA_FLAG_CELEBRATING_SPECIAL = 95;
    public static final int DATA_FLAG_OUT_OF_CONTROL = 96;
    public static final int DATA_FLAG_RAM_ATTACK = 97;
    public static final int DATA_FLAG_PLAYING_DEAD = 98;
    public static final int DATA_FLAG_IN_ASCENDABLE_BLOCK = 99;
    public static final int DATA_FLAG_OVER_DESCENDABLE_BLOCK = 100;
    public static final int DATA_FLAG_CROAKING = 101;
    public static final int DATA_FLAG_EAT_MOB = 102;
    public static final int DATA_FLAG_JUMP_GOAL_JUMP = 103;
    public static final int DATA_FLAG_EMERGING = 104;
    public static final int DATA_FLAG_SNIFFING = 105;
    public static final int DATA_FLAG_DIGGING = 106;
    public static final int DATA_FLAG_SONIC_BOOM = 107;
    public static final int DATA_FLAG_HAS_DASH_COOLDOWN = 108;
    public static final int DATA_FLAG_PUSH_TOWARDS_CLOSEST_SPACE = 109;
    public static final int DATA_FLAG_SCENTING = 110;
    public static final int DATA_FLAG_RISING = 111;
    public static final int DATA_FLAG_FEELING_HAPPY = 112;
    public static final int DATA_FLAG_SEARCHING = 113;
    public static final int DATA_FLAG_CRAWLING = 114;
    public static final int DATA_TIMER_FLAG_1 = 115;
    public static final int DATA_TIMER_FLAG_2 = 116;
    public static final int DATA_TIMER_FLAG_3 = 117;
    public static final int DATA_FLAG_BODY_ROTATION_BLOCKED = 118;
    /**
     * @since v776 1.21.60
     */
    public static final int DATA_FLAG_RENDER_WHEN_INVISIBLE = 119;
    /**
     * @since v786 1.21.70
     */
    public static final int DATA_FLAG_BODY_ROTATION_AXIS_ALIGNED= 120;
    /**
     * @since v786 1.21.70
     */
    public static final int DATA_FLAG_COLLIDABLE = 121;
    /**
     * @since v786 1.21.70
     */
    public static final int DATA_FLAG_WASD_AIR_CONTROLLED = 122;
    /**
     * @since v800 1.21.80
     */
    public static final int DATA_FLAG_DOES_SERVER_AUTH_ONLY_DISMOUNT = 123;
    /**
     * @since v818 1.21.90
     */
    public static final int DATA_FLAG_BODY_ROTATION_ALWAYS_FOLLOWS_HEAD = 124;

    public static final double STEP_CLIP_MULTIPLIER = 0.4;
    public static final int ENTITY_COORDINATES_MAX_VALUE = 2100000000;

    public static long entityCount = 1;

    private static final Map<String, Class<? extends Entity>> knownEntities = new HashMap<>();
    private static final Map<String, String> shortNames = new HashMap<>();

    private static final Map<Integer, String> entityRuntimeMappingOld = new HashMap<>();
    private static final Map<Integer, String> entityRuntimeMapping407 = new HashMap<>();
    private static final Map<Integer, String> entityRuntimeMapping440 = new HashMap<>();
    private static final Map<Integer, String> entityRuntimeMapping527 = new HashMap<>();
    private static final Map<Integer, String> entityRuntimeMapping589 = new HashMap<>();

    private static final Map<Integer, CompoundTag> entityIdentifiersMap = new HashMap<>();
    private static final Map<Integer, byte[]> entityIdentifiersCache = new HashMap<>();

    static {
        AddEntityPacket.setupLegacyIdentifiers(entityRuntimeMappingOld, ProtocolInfo.v1_2_0);
        AddEntityPacket.setupLegacyIdentifiers(entityRuntimeMapping407, ProtocolInfo.v1_16_0);
        AddEntityPacket.setupLegacyIdentifiers(entityRuntimeMapping440, ProtocolInfo.v1_17_0);
        AddEntityPacket.setupLegacyIdentifiers(entityRuntimeMapping527, ProtocolInfo.v1_19_0);
        AddEntityPacket.setupLegacyIdentifiers(entityRuntimeMapping589, ProtocolInfo.v1_20_0);

        initEntityIdentifiers(ProtocolInfo.v1_2_0, Base64.getDecoder().decode(AvailableEntityIdentifiersPacket.NBT313));
        initEntityIdentifiers(ProtocolInfo.v1_10_0, Base64.getDecoder().decode(AvailableEntityIdentifiersPacket.NBT340));
        initEntityIdentifiers(ProtocolInfo.v1_16_100, AvailableEntityIdentifiersPacket.NBT419);
        initEntityIdentifiers(ProtocolInfo.v1_17_0, AvailableEntityIdentifiersPacket.NBT440);
        initEntityIdentifiers(ProtocolInfo.v1_19_0, AvailableEntityIdentifiersPacket.NBT527);
        initEntityIdentifiers(ProtocolInfo.v1_19_20, AvailableEntityIdentifiersPacket.NBT544);
        initEntityIdentifiers(ProtocolInfo.v1_19_80, AvailableEntityIdentifiersPacket.TAG);
    }

    public final Map<Integer, Player> hasSpawned = new ConcurrentHashMap<>();

    protected final Map<Integer, Effect> effects = new ConcurrentHashMap<>();

    protected UUID entityUniqueId;

    protected long id;

    protected final EntityMetadata dataProperties = new EntityMetadata()
            .putLong(DATA_FLAGS, 0)
            .putByte(DATA_COLOR, 0)
            .putShort(DATA_AIR, 400)
            .putShort(DATA_MAX_AIR, 400)
            .putString(DATA_NAMETAG, "")
            .putLong(DATA_LEAD_HOLDER_EID, -1)
            .putFloat(DATA_SCALE, 1f);

    public final List<Entity> passengers = new ArrayList<>();

    public Entity riding = null;

    public FullChunk chunk;

    protected EntityDamageEvent lastDamageCause = null;

    public List<Block> blocksAround = new ArrayList<>();
    public List<Block> collisionBlocks = new ArrayList<>();

    public double lastX;
    public double lastY;
    public double lastZ;

    public boolean firstMove = true;

    public double motionX;
    public double motionY;
    public double motionZ;

    public Vector3 temporalVector;
    public double lastMotionX;
    public double lastMotionY;
    public double lastMotionZ;

    public double lastYaw;
    public double lastPitch;
    public double lastHeadYaw;

    public double entityCollisionReduction = 0; // Higher than 0.9 will result a fast collisions
    public AxisAlignedBB boundingBox;
    public boolean onGround;
    public int deadTicks = 0;
    public boolean positionChanged;
    public int age = 0;
    public int ticksLived = 0;
    protected int airTicks = 0;

    protected float health = 20;
    protected int maxHealth = 20;

    protected float absorption = 0;

    protected float ySize = 0;
    public boolean keepMovement = false;

    protected boolean noFallDamage;
    public float fallDistance = 0;
    public int lastUpdate;
    public int inLavaTicks = 0;
    public int fireTicks = 0;
    public int inPortalTicks = 0;
    public int freezingTicks = 0;//0 - 140
    public int inEndPortalTicks = 0;
    public Position portalPos = null;

    public float scale = 1;

    public CompoundTag namedTag;

    public boolean isCollided = false;
    public boolean isCollidedHorizontally = false;
    public boolean isCollidedVertically = false;

    public int noDamageTicks;
    public boolean justCreated;
    public boolean fireProof;
    public boolean invulnerable;

    private boolean gliding;
    private boolean immobile;
    private boolean sprinting;
    private boolean swimming;
    private boolean sneaking;
    private boolean crawling;
    protected Server server;

    public double highestPosition;

    public boolean closed = false;

    public boolean noClip = false;

    public final boolean isPlayer;

    private volatile boolean init;
    private volatile boolean initEntity;

    protected volatile boolean saveWithChunk = true;

    protected boolean passThroughBarrier = false;

    private Map<String, Integer> intProperties = new LinkedHashMap<>();
    private Map<String, Float> floatProperties = new LinkedHashMap<>();

    public float getHeight() {
        return 0;
    }

    public float getEyeHeight() {
        return this.getHeight() / 2 + 0.1f;
    }

    public float getEyeY() {
        return (float) y + getEyeHeight();
    }

    public Vector3 getEyePosition() {
        return new Vector3(getX(), getEyeY(), getZ());
    }

    public float getWidth() {
        return 0;
    }

    public float getLength() {
        return 0;
    }

    protected double getStepHeight() {
        return 0;
    }

    public boolean canCollide() {
        return true;
    }

    public boolean canBeFollowed() {
        return true;
    }

    protected float getGravity() {
        return 0;
    }

    protected float getDrag() {
        return 0;
    }

    protected float getBaseOffset() {
        return 0;
    }

    public int getFrostbiteInjury() {
        return 1;
    }

    public Entity(FullChunk chunk, CompoundTag nbt) {
        this.isPlayer = this instanceof Player;
        if (this.isPlayer) {
            initEntityProperties("minecraft:player");
            return;
        }
        initEntityProperties();
        this.init(chunk, nbt);
    }

    protected void initEntity() {
        if (this.initEntity) {
            throw new RuntimeException("Entity is already initialized: " + this.getName() + " (" + this.id + ')');
        }

        this.initEntity = true;

        if (this.namedTag.contains("ActiveEffects")) {
            ListTag<CompoundTag> effects = this.namedTag.getList("ActiveEffects", CompoundTag.class);
            for (CompoundTag e : effects.getAll()) {
                Effect effect = Effect.getEffect(e.getByte("Id"));
                if (effect == null) {
                    continue;
                }

                effect.setAmplifier(e.getByte("Amplifier")).setDuration(e.getInt("Duration")).setVisible(e.getBoolean("ShowParticles"));

                this.addEffect(effect, null); // No event
            }
        }

        if (this.namedTag.contains("CustomName")) {
            this.setNameTag(this.namedTag.getString("CustomName"));

            if (this.namedTag.contains("CustomNameVisible")) {
                this.setNameTagVisible(this.namedTag.getBoolean("CustomNameVisible"));
            }

            if (this.namedTag.contains("CustomNameAlwaysVisible")) {
                this.setNameTagAlwaysVisible(this.namedTag.getBoolean("CustomNameAlwaysVisible"));
            }
        }

        this.setDataFlag(DATA_FLAGS, DATA_FLAG_HAS_COLLISION, true, false);
        //this.dataProperties.put(new LongEntityData(DATA_FLAGS, this.getDataPropertyLong(DATA_FLAGS) ^ 1L << DATA_FLAG_HAS_COLLISION));
        this.dataProperties.putFloat(DATA_BOUNDING_BOX_HEIGHT, this.getHeight());
        this.dataProperties.putFloat(DATA_BOUNDING_BOX_WIDTH, this.getWidth());
        this.dataProperties.putInt(DATA_HEALTH, (int) this.health);

        this.scheduleUpdate();

        if (this.isPlayer) {
            this.sendData((Player) this);
        } else {
            if (this.namedTag.contains("uuid")) {
                this.entityUniqueId = UUID.fromString(this.namedTag.getString("uuid"));
            } else {
                this.entityUniqueId = UUID.randomUUID();
            }
        }
    }

    protected final void init(FullChunk chunk, CompoundTag nbt) {
        if (chunk == null || chunk.getProvider() == null) {
            throw new ChunkException("Invalid garbage Chunk given to Entity");
        }

        if (this.init) {
            throw new RuntimeException("Entity is already initialized: " + this.getName() + " (" + this.id + ')');
        }

        this.init = true;

        this.temporalVector = new Vector3();

        this.id = entityCount++;
        this.justCreated = true;
        this.namedTag = nbt;

        this.chunk = chunk;
        this.setLevel(chunk.getProvider().getLevel());
        this.server = chunk.getProvider().getLevel().getServer();

        this.boundingBox = new SimpleAxisAlignedBB(0, 0, 0, 0, 0, 0);

        ListTag<DoubleTag> posList = this.namedTag.getList("Pos", DoubleTag.class);
        ListTag<FloatTag> rotationList = this.namedTag.getList("Rotation", FloatTag.class);
        ListTag<DoubleTag> motionList = this.namedTag.getList("Motion", DoubleTag.class);
        this.setPositionAndRotation(
                this.temporalVector.setComponents(
                        posList.get(0).data,
                        posList.get(1).data,
                        posList.get(2).data
                ),
                rotationList.get(0).data,
                rotationList.get(1).data
        );

        this.setMotion(this.temporalVector.setComponents(
                motionList.get(0).data,
                motionList.get(1).data,
                motionList.get(2).data
        ));

        if (!this.namedTag.contains("FallDistance")) {
            this.namedTag.putFloat("FallDistance", 0);
        }
        this.fallDistance = this.namedTag.getFloat("FallDistance");
        this.highestPosition = this.y + this.namedTag.getFloat("FallDistance");

        if (!this.namedTag.contains("Fire") || this.namedTag.getShort("Fire") > 32767) {
            this.namedTag.putShort("Fire", 0);
        }
        this.fireTicks = this.namedTag.getShort("Fire");

        if (!this.namedTag.contains("Air")) {
            this.namedTag.putShort("Air", 300);
        }
        this.setDataProperty(new ShortEntityData(DATA_AIR, this.namedTag.getShort("Air")), false);

        if (!this.namedTag.contains("OnGround")) {
            this.namedTag.putBoolean("OnGround", false);
        }
        this.onGround = this.namedTag.getBoolean("OnGround");

        if (!this.namedTag.contains("Invulnerable")) {
            this.namedTag.putBoolean("Invulnerable", false);
        }
        this.invulnerable = this.namedTag.getBoolean("Invulnerable");

        if (!this.namedTag.contains("Scale")) {
            this.namedTag.putFloat("Scale", 1);
        }
        this.scale = this.namedTag.getFloat("Scale");
        this.setDataProperty(new FloatEntityData(DATA_SCALE, scale), false);

        this.chunk.addEntity(this);
        this.level.addEntity(this);

        this.initEntity();

        this.lastUpdate = this.server.getTick();
        this.server.getPluginManager().callEvent(new EntitySpawnEvent(this));

        this.scheduleUpdate();
    }

    /**
     * 获取该实体的标识符
     * <p>
     * Get the identifier of the entity
     *
     * @return the identifier
     */
    @Nullable
    public Identifier getIdentifier() {
        return Entity.getIdentifier(this.getNetworkId());
    }

    public boolean hasCustomName() {
        return !this.getNameTag().isEmpty();
    }

    public String getNameTag() {
        return this.getDataPropertyString(DATA_NAMETAG);
    }

    public boolean isNameTagVisible() {
        return this.getDataFlag(DATA_FLAGS, DATA_FLAG_CAN_SHOW_NAMETAG);
    }

    public boolean isNameTagAlwaysVisible() {
        return this.getDataPropertyByte(DATA_ALWAYS_SHOW_NAMETAG) == 1;
    }

    public void setNameTag(String name) {
        this.setDataProperty(new StringEntityData(DATA_NAMETAG, name));
    }

    public void setNameTagVisible() {
        this.setNameTagVisible(true);
    }

    public void setNameTagVisible(boolean value) {
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_CAN_SHOW_NAMETAG, value);
    }

    public void setNameTagAlwaysVisible() {
        this.setNameTagAlwaysVisible(true);
    }

    public void setNameTagAlwaysVisible(boolean value) {
        this.setDataProperty(new ByteEntityData(DATA_ALWAYS_SHOW_NAMETAG, value ? 1 : 0));
    }

    public void setScoreTag(String score) {
        this.setDataProperty(new StringEntityData(DATA_SCORE_TAG, score));
    }

    public String getScoreTag() {
        return this.getDataPropertyString(DATA_SCORE_TAG);
    }

    public boolean isSneaking() {
        return this.sneaking;
    }

    public boolean isShortSneaking() {
        return this.sneaking && canShortSneak();
    }

    protected boolean canShortSneak() {
        return true;
    }

    public void setSneaking() {
        this.setSneaking(true);
    }

    public void setSneaking(boolean value) {
        this.sneaking = value;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_SNEAKING, value);
        if (this.canShortSneak()) {
            this.recalculateBoundingBox(true);
        }
    }

    public boolean isSwimming() {
        return this.swimming;
    }

    public void setSwimming() {
        this.setSwimming(true);
    }

    public void setSwimming(boolean value) {
        if (this.swimming != value) {
            this.swimming = value;
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_SWIMMING, value);
            this.recalculateBoundingBox(true);
        }
    }

    public boolean isSprinting() {
        return this.sprinting;
    }

    public void setSprinting() {
        this.setSprinting(true);
    }

    public void setSprinting(boolean value) {
        if (this.sprinting != value) {
            this.sprinting = value;
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_SPRINTING, value);
        }
    }

    public boolean isGliding() {
        return this.gliding;
    }

    public void setGliding() {
        this.setGliding(true);
    }

    public void setGliding(boolean value) {
        if (this.gliding != value) {
            this.gliding = value;
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_GLIDING, value);
            this.recalculateBoundingBox(true);
        }
    }

    public void setCrawling() {
        this.setCrawling(true);
    }

    public void setCrawling(boolean value) {
        if (this.crawling != value) {
            this.crawling = value;
            this.setDataFlag(DATA_FLAGS_EXTENDED, DATA_FLAG_CRAWLING, value);
            this.recalculateBoundingBox(true);
        }
    }

    public boolean isCrawling() {
        return crawling;
    }

    public boolean isImmobile() {
        return this.immobile;
    }

    public void setImmobile() {
        this.setImmobile(true);
    }

    public void setImmobile(boolean value) {
        this.immobile = value;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_IMMOBILE, value);
    }

    public boolean canClimb() {
        return this.getDataFlag(DATA_FLAGS, DATA_FLAG_CAN_CLIMB);
    }

    public void setCanClimb() {
        this.setCanClimb(true);
    }

    public void setCanClimb(boolean value) {
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_CAN_CLIMB, value);
    }

    public boolean canClimbWalls() {
        return this.getDataFlag(DATA_FLAGS, DATA_FLAG_WALLCLIMBING);
    }

    public void setCanClimbWalls() {
        this.setCanClimbWalls(true);
    }

    public void setCanClimbWalls(boolean value) {
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_WALLCLIMBING, value);
    }

    public void setScale(float scale) {
        if (this.scale != scale) {
            this.scale = scale;
            this.setDataProperty(new FloatEntityData(DATA_SCALE, this.scale));
            this.recalculateBoundingBox(true);
        }
    }

    public float getScale() {
        return this.scale;
    }

    public List<Entity> getPassengers() {
        return passengers;
    }

    public Entity getPassenger() {
        return Iterables.getFirst(this.passengers, null);
    }

    public boolean isPassenger(Entity entity) {
        return this.passengers.contains(entity);
    }

    public boolean isControlling(Entity entity) {
        return this.passengers.indexOf(entity) == 0;
    }

    public boolean hasControllingPassenger() {
        return !this.passengers.isEmpty() && isControlling(this.passengers.get(0));
    }

    public Entity getRiding() {
        return riding;
    }

    public Map<Integer, Effect> getEffects() {
        return effects;
    }

    public void removeAllEffects() {
        this.removeAllEffects(EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public void removeAllEffects(EntityPotionEffectEvent.Cause cause) {
        for (Effect effect : this.effects.values()) {
            this.removeEffect(effect.getId(), cause);
        }
    }

    public void removeEffect(int effectId) {
        this.removeEffect(effectId, EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    /**
     * Remove an effect from the entity
     * @param effectId the effect id
     * @param cause the cause of the removal
     */
    public void removeEffect(int effectId, EntityPotionEffectEvent.Cause cause) {
        Effect effect = this.effects.get(effectId);
        if (effect != null) {
            if (cause != null) {
                EntityPotionEffectEvent event = new EntityPotionEffectEvent(this, effect, null, EntityPotionEffectEvent.Action.REMOVED, cause);
                event.call();
                if (event.isCancelled()) {
                    return;
                }
            }

            this.effects.remove(effectId);

            effect.remove(this);

            this.recalculateEffectColor();
        }
    }

    public Effect getEffect(int effectId) {
        return this.effects.getOrDefault(effectId, null);
    }

    public boolean hasEffect(int effectId) {
        return this.effects.containsKey(effectId);
    }

    /**
     * Check if the entity can be affected by the effect
     *
     * @param effectId the effect id
     * @return true if the entity can be affected by the effect
     */
    public boolean canBeAffected(int effectId) {
        return false;
    }

    public void addEffect(Effect effect) {
        this.addEffect(effect, EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public void addEffect(Effect effect, EntityPotionEffectEvent.Cause cause) {
        if (effect == null) {
            return;
        }

        if (cause != null) {
            Effect oldEffect = this.effects.get(effect.getId());

            EntityPotionEffectEvent event = new EntityPotionEffectEvent(
                    this,
                    oldEffect,
                    effect,
                    oldEffect == null ? EntityPotionEffectEvent.Action.ADDED : EntityPotionEffectEvent.Action.CHANGED,
                    cause);

            if (!canBeAffected(effect.getId())) {
                event.setCancelled();
            }
            event.call();
            if (event.isCancelled()) {
                return;
            }
        } else if (!canBeAffected(effect.getId())) {
            return;
        }

        effect.add(this);

        this.effects.put(effect.getId(), effect);

        this.recalculateEffectColor();

        if (effect.getId() == Effect.HEALTH_BOOST) {
            this.setHealth(this.health + ((effect.getAmplifier() + 1) << 2));
        }
    }

    public void recalculateBoundingBox() {
        this.recalculateBoundingBox(true);
    }

    public void recalculateBoundingBox(boolean send) {
        float height = this.getHeight();
        double radius = (this.getWidth() * this.scale) / 2d;
        this.boundingBox.setBounds(
                this.x - radius,
                this.y + this.ySize,
                z - radius,
                x + radius,
                y + height * this.scale + this.ySize,
                z + radius
        );

        if (send) {
            FloatEntityData bbH = new FloatEntityData(DATA_BOUNDING_BOX_HEIGHT, height);
            FloatEntityData bbW = new FloatEntityData(DATA_BOUNDING_BOX_WIDTH, this.getWidth());
            this.dataProperties.put(bbH);
            this.dataProperties.put(bbW);
            sendData(this.hasSpawned.values().toArray(Player.EMPTY_ARRAY), new EntityMetadata().put(bbH).put(bbW));
        }
    }

    protected void recalculateEffectColor() {
        int[] color = new int[3];
        int count = 0;
        boolean ambient = true;
        long effectsData = 0;
        int packedEffectsCount = 0;
        for (Effect effect : this.effects.values()) {
            if (effect.isVisible()) {
                int[] c = effect.getColor();
                color[0] += c[0] * (effect.getAmplifier() + 1);
                color[1] += c[1] * (effect.getAmplifier() + 1);
                color[2] += c[2] * (effect.getAmplifier() + 1);
                count += effect.getAmplifier() + 1;
                if (!effect.isAmbient()) {
                    ambient = false;
                }
                if (packedEffectsCount < 8) {
                    effectsData = effectsData << 7 | ((effect.getId() & 0x3f) << 1) | (effect.isAmbient() ? 1 : 0);
                    packedEffectsCount++;
                }
            }
        }

        if (count > 0) {
            int r = (color[0] / count) & 0xff;
            int g = (color[1] / count) & 0xff;
            int b = (color[2] / count) & 0xff;

            this.setDataProperty(new IntEntityData(Entity.DATA_POTION_COLOR, (r << 16) + (g << 8) + b));
            this.setDataProperty(new ByteEntityData(Entity.DATA_POTION_AMBIENT, ambient ? 1 : 0));
        } else {
            this.setDataProperty(new IntEntityData(Entity.DATA_POTION_COLOR, 0));
            this.setDataProperty(new ByteEntityData(Entity.DATA_POTION_AMBIENT, 0));
        }
        this.setDataProperty(new LongEntityData(Entity.DATA_VISIBLE_MOB_EFFECTS, effectsData));
    }

    public static Entity createEntity(String name, Position pos, Object... args) {
        return createEntity(name, pos.getChunk(), getDefaultNBT(pos), args);
    }

    public static Entity createEntity(int type, Position pos, Object... args) {
        return createEntity(String.valueOf(type), pos.getChunk(), getDefaultNBT(pos), args);
    }

    public static Entity createEntity(String name, FullChunk chunk, CompoundTag nbt, Object... args) {
        if (knownEntities.containsKey(name)) {
            return createEntity(knownEntities.get(name), chunk, nbt, args);
        }
        EntityDefinition entityDefinition = EntityManager.get().getDefinition(name);
        if (entityDefinition != null) {
            return createEntity(entityDefinition.getImplementation(), chunk, nbt, args);
        }
        return null;
    }

    public static Entity createEntity(int type, FullChunk chunk, CompoundTag nbt, Object... args) {
        String name = String.valueOf(type);
        if (knownEntities.containsKey(name)) {
            return createEntity(knownEntities.get(name), chunk, nbt, args);
        }
        EntityDefinition entityDefinition = EntityManager.get().getDefinition(type);
        if (entityDefinition != null) {
            return createEntity(entityDefinition.getImplementation(), chunk, nbt, args);
        }
        return null;
    }

    private static Entity createEntity(Class<? extends Entity> clazz, FullChunk chunk, CompoundTag nbt, Object... args) {
        Entity entity = null;

        if (clazz == null) {
            return null;
        }

        for (Constructor constructor : clazz.getConstructors()) {
            if (entity != null) {
                break;
            }

            if (constructor.getParameterCount() != (args == null ? 2 : args.length + 2)) {
                continue;
            }

            try {
                if (args == null || args.length == 0) {
                    entity = (Entity) constructor.newInstance(chunk, nbt);
                } else {
                    Object[] objects = new Object[args.length + 2];

                    objects[0] = chunk;
                    objects[1] = nbt;
                    System.arraycopy(args, 0, objects, 2, args.length);
                    entity = (Entity) constructor.newInstance(objects);

                }
            } catch (Exception e) {
                MainLogger.getLogger().logException(e);
            }
        }

        return entity;
    }

    public static boolean registerEntity(String name, Class<? extends Entity> clazz) {
        return registerEntity(name, clazz, false);
    }

    public static boolean registerEntity(String name, Class<? extends Entity> clazz, boolean force) {
        if (clazz == null) {
            return false;
        }
        try {
            int networkId = clazz.getField("NETWORK_ID").getInt(null);
            knownEntities.put(String.valueOf(networkId), clazz);
        } catch (Exception e) {
            if (!force) {
                return false;
            }
        }

        knownEntities.put(name, clazz);
        shortNames.put(clazz.getSimpleName(), name);
        return true;
    }

    public static Map<Integer, String> getEntityRuntimeMapping() {
        return getEntityRuntimeMapping(ProtocolInfo.CURRENT_PROTOCOL);
    }

    public static Map<Integer, String> getEntityRuntimeMapping(int protocolId) {
        return Collections.unmodifiableMap(getEntityRuntimeMappingInternal(protocolId));
    }

    protected static Map<Integer, String> getEntityRuntimeMappingInternal(int protocolId) {
        if (protocolId >= ProtocolInfo.v1_20_0_23) {
            return entityRuntimeMapping589;
        } else if (protocolId >= ProtocolInfo.v1_19_0_29) {
            return entityRuntimeMapping527;
        } else if (protocolId >= ProtocolInfo.v1_17_0) {
            return entityRuntimeMapping440;
        } else if (protocolId >= ProtocolInfo.v1_16_0) {
            return entityRuntimeMapping407;
        }
        return entityRuntimeMappingOld;
    }

    private static void initEntityIdentifiers(int protocolId, byte[] bytes) {
        try {
            CompoundTag identifiers = (CompoundTag) NBTIO.readNetwork(new ByteArrayInputStream(bytes));
            entityIdentifiersMap.put(protocolId, identifiers);
            entityIdentifiersCache.put(protocolId, bytes);
        } catch (IOException e) {
            throw new RuntimeException("Unable to init entityIdentifiers", e);
        }
    }

    private static int correctEntityIdentifiersProtocol(int protocolId) {
        if (protocolId >= ProtocolInfo.v1_19_80) {
            return ProtocolInfo.v1_19_80;
        } else if (protocolId >= ProtocolInfo.v1_19_20) {
            return ProtocolInfo.v1_19_20;
        } else if (protocolId >= ProtocolInfo.v1_19_0_29) {
            return ProtocolInfo.v1_19_0;
        } else if (protocolId >= ProtocolInfo.v1_17_0) {
            return ProtocolInfo.v1_17_0;
        } else if (protocolId >= ProtocolInfo.v1_16_100) {
            return ProtocolInfo.v1_16_100;
        } else if (protocolId >= ProtocolInfo.v1_10_0) {
            return ProtocolInfo.v1_10_0;
        }
        return ProtocolInfo.v1_2_0;
    }

    public static void registerEntityIdentifier(String identifier, int entityId, CompoundTag nbtEntry, int protocolId) {
        Map<Integer, String> runtimeMapping = getEntityRuntimeMappingInternal(protocolId);
        runtimeMapping.put(entityId, identifier);

        int protocol = correctEntityIdentifiersProtocol(protocolId);
        CompoundTag nbt = entityIdentifiersMap.get(protocol);
        ListTag<CompoundTag> identifiers = nbt.getList("idlist", CompoundTag.class);
        identifiers.add(nbtEntry);
        nbt.putList(identifiers);
        updateEntityIdentifiersCache(protocol);
    }

    public static CompoundTag getEntityIdentifiers(int protocolId) {
        return entityIdentifiersMap.get(correctEntityIdentifiersProtocol(protocolId));
    }

    private static void updateEntityIdentifiersCache(int protocolId) {
        try {
            CompoundTag nbt = entityIdentifiersMap.get(protocolId);
            entityIdentifiersCache.put(protocolId, NBTIO.writeNetwork(nbt));
        } catch (IOException e) {
            throw new RuntimeException("Unable to update entityIdentifiers cache", e);
        }
    }

    public static byte[] getEntityIdentifiersCache(int protocolId) {
        return entityIdentifiersCache.get(correctEntityIdentifiersProtocol(protocolId));
    }

    /**
     * 获取指定网络id实体的标识符
     * <p>
     * Get the identifier of the specified network id entity
     *
     * @return the identifier
     */
    @Nullable
    public static Identifier getIdentifier(int networkID) {
        String str = getEntityRuntimeMapping().get(networkID);
        if (str != null) {
            return new Identifier(str);
        }
        EntityDefinition definition = EntityManager.get().getDefinition(networkID);
        if (definition != null) {
            return new Identifier(definition.getIdentifier());
        }
        return null;
    }

    public static CompoundTag getDefaultNBT(Vector3 pos) {
        return getDefaultNBT(pos, null);
    }

    public static CompoundTag getDefaultNBT(Vector3 pos, Vector3 motion) {
        Location loc = pos instanceof Location ? (Location) pos : null;

        if (loc != null) {
            return getDefaultNBT(pos, motion, (float) loc.getYaw(), (float) loc.getPitch());
        }

        return getDefaultNBT(pos, motion, 0, 0);
    }

    public static CompoundTag getDefaultNBT(Vector3 pos, Vector3 motion, float yaw, float pitch) {
        return new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", pos.x))
                        .add(new DoubleTag("", pos.y))
                        .add(new DoubleTag("", pos.z)))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("", motion != null ? motion.x : 0))
                        .add(new DoubleTag("", motion != null ? motion.y : 0))
                        .add(new DoubleTag("", motion != null ? motion.z : 0)))
                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("", yaw))
                        .add(new FloatTag("", pitch)));
    }

    public void saveNBT() {
        if (!this.isPlayer) {
            this.namedTag.putString("id", this.getSaveId());
            if (!this.getNameTag().isEmpty()) {
                this.namedTag.putString("CustomName", this.getNameTag());
                this.namedTag.putBoolean("CustomNameVisible", this.isNameTagVisible());
                this.namedTag.putBoolean("CustomNameAlwaysVisible", this.isNameTagAlwaysVisible());
            } else {
                this.namedTag.remove("CustomName");
                this.namedTag.remove("CustomNameVisible");
                this.namedTag.remove("CustomNameAlwaysVisible");
            }
            if (this.entityUniqueId == null) {
                this.entityUniqueId = UUID.randomUUID();
            }
            this.namedTag.putString("uuid", this.entityUniqueId.toString());
        }

        this.namedTag.putList(new ListTag<DoubleTag>("Pos")
                .add(new DoubleTag("0", this.x))
                .add(new DoubleTag("1", this.y))
                .add(new DoubleTag("2", this.z))
        );

        this.namedTag.putList(new ListTag<DoubleTag>("Motion")
                .add(new DoubleTag("0", this.motionX))
                .add(new DoubleTag("1", this.motionY))
                .add(new DoubleTag("2", this.motionZ))
        );

        this.namedTag.putList(new ListTag<FloatTag>("Rotation")
                .add(new FloatTag("0", (float) this.yaw))
                .add(new FloatTag("1", (float) this.pitch))
        );

        this.namedTag.putFloat("FallDistance", this.fallDistance);
        this.namedTag.putShort("Fire", this.fireTicks);
        this.namedTag.putShort("Air", this.airTicks);
        this.namedTag.putBoolean("OnGround", this.onGround);
        this.namedTag.putBoolean("Invulnerable", this.invulnerable);
        this.namedTag.putFloat("Scale", this.scale);

        if (!this.effects.isEmpty()) {
            ListTag<CompoundTag> list = new ListTag<>("ActiveEffects");
            for (Effect effect : this.effects.values()) {
                list.add(new CompoundTag(String.valueOf(effect.getId()))
                        .putByte("Id", effect.getId())
                        .putByte("Amplifier", effect.getAmplifier())
                        .putInt("Duration", effect.getDuration())
                        .putBoolean("Ambient", false)
                        .putBoolean("ShowParticles", effect.isVisible())
                );
            }

            this.namedTag.putList(list);
        } else {
            this.namedTag.remove("ActiveEffects");
        }
    }

    public String getName() {
        if (this.hasCustomName()) {
            return this.getNameTag();
        } else {
            return this.getSaveId();
        }
    }

    public final String getSaveId() {
        if(this instanceof CustomEntity) {
            EntityDefinition entityDefinition = ((CustomEntity) this).getEntityDefinition();
            return entityDefinition == null ? "" : entityDefinition.getIdentifier();
        }
        return shortNames.getOrDefault(this.getClass().getSimpleName(), "");
    }

    public void spawnTo(Player player) {
        if (!init || !initEntity) {
            this.server.getLogger().warning("(BUG) Spawned an entity that is not initialized yet: " + this.getName() + " (" + this.id + ')');
        }

        if (!this.hasSpawned.containsKey(player.getLoaderId()) && player.usedChunks.containsKey(Level.chunkHash(this.chunk.getX(), this.chunk.getZ()))) {
            player.dataPacket(createAddEntityPacket());
            this.hasSpawned.put(player.getLoaderId(), player);

            if (this.riding != null) {
                this.riding.spawnTo(player);

                SetEntityLinkPacket pkk = new SetEntityLinkPacket();
                pkk.vehicleUniqueId = this.riding.id;
                pkk.riderUniqueId = this.id;
                pkk.type = 1;
                pkk.immediate = 1;

                player.dataPacket(pkk);
            }

            if (this.server.vanillaBossBar && this instanceof EntityBoss) {
                BossEventPacket pkBoss = new BossEventPacket();
                pkBoss.bossEid = this.id;
                pkBoss.type = BossEventPacket.TYPE_SHOW;
                pkBoss.title = this.getName();
                pkBoss.healthPercent = player.protocol >= 361 ? this.health / 100 : this.health;
                player.dataPacket(pkBoss);
            }
        }
    }

    protected DataPacket createAddEntityPacket() {
        AddEntityPacket addEntity = new AddEntityPacket();
        addEntity.type = this.getNetworkId();
        addEntity.entityUniqueId = this.id;
        addEntity.entityRuntimeId = this.id;
        addEntity.yaw = (float) this.yaw;
        addEntity.headYaw = (float) this.yaw;
        addEntity.pitch = (float) this.pitch;
        addEntity.x = (float) this.x;
        addEntity.y = (float) this.y + this.getBaseOffset();
        addEntity.z = (float) this.z;
        addEntity.speedX = (float) this.motionX;
        addEntity.speedY = (float) this.motionY;
        addEntity.speedZ = (float) this.motionZ;
        addEntity.metadata = this.dataProperties.clone();

        addEntity.links = new EntityLink[this.passengers.size()];
        for (int i = 0; i < addEntity.links.length; i++) {
            addEntity.links[i] = new EntityLink(this.id, this.passengers.get(i).id, i == 0 ? EntityLink.TYPE_RIDER : TYPE_PASSENGER, false, false, 0f);
        }

        addEntity.properties = this.propertySyncData();

        return addEntity;
    }

    public Map<Integer, Player> getViewers() {
        return hasSpawned;
    }

    public void sendPotionEffects(Player player) {
        for (Effect effect : this.effects.values()) {
            MobEffectPacket pk = new MobEffectPacket();
            pk.eid = this.id;
            pk.effectId = effect.getId();
            pk.amplifier = effect.getAmplifier();
            pk.particles = effect.isVisible();
            pk.duration = effect.getDuration();
            pk.eventId = MobEffectPacket.EVENT_ADD;

            player.dataPacket(pk);
        }
    }

    public void sendData(Player player) {
        this.sendData(player, null);
    }

    public void sendData(Player player, EntityMetadata data) {
        SetEntityDataPacket pk = new SetEntityDataPacket();
        pk.eid = this.id;
        pk.metadata = data == null ? this.dataProperties.clone() : data;
        pk.syncedProperties = this.propertySyncData();

        player.dataPacket(pk);
    }

    public void sendData(Player[] players) {
        this.sendData(players, null);
    }

    public void sendData(Player[] players, EntityMetadata data) {
        SetEntityDataPacket pk = new SetEntityDataPacket();
        pk.eid = this.id;
        //pk.metadata = data == null ? this.dataProperties : data;
        pk.syncedProperties = this.propertySyncData();

        for (Player player : players) {
            if (player == this) {
                continue;
            }
            pk.metadata = data == null ? this.dataProperties.clone() : data;
            player.dataPacket(pk);
        }
        if (this.isPlayer) {
            pk.metadata = data == null ? this.dataProperties.clone() : data;
            ((Player) this).dataPacket(pk);
        }
    }

    public void despawnFrom(Player player) {
        if (this.hasSpawned.containsKey(player.getLoaderId())) {
            RemoveEntityPacket pk = new RemoveEntityPacket();
            pk.eid = this.id;
            player.dataPacket(pk);
            this.hasSpawned.remove(player.getLoaderId());
        }
    }

    public boolean attack(EntityDamageEvent source) {
        if (hasEffect(Effect.FIRE_RESISTANCE)
                && (source.getCause() == DamageCause.FIRE
                || source.getCause() == DamageCause.FIRE_TICK
                || source.getCause() == DamageCause.LAVA)) {
            return false;
        }

        server.getPluginManager().callEvent(source);
        if (source.isCancelled()) {
            return false;
        }

        if (source instanceof EntityDamageByEntityEvent damageByEntityEvent) {
            // Make fire aspect to set the target in fire before dealing any damage so the target is in fire on death even if killed by the first hit
            Enchantment[] enchantments = damageByEntityEvent.getWeaponEnchantments();
            if (enchantments != null) {
                for (Enchantment enchantment : enchantments) {
                    enchantment.doAttack(damageByEntityEvent.getDamager(), this);
                }
            }

            // Wolf targets
            if (source.getEntity() instanceof Player) {
                for (Entity entity : source.getEntity().getLevel().getNearbyEntities(source.getEntity().getBoundingBox().grow(17, 17, 17), source.getEntity())) {
                    if (entity instanceof EntityWolf wolf) {
                        if (wolf.hasOwner()) {
                            wolf.isAngryTo = damageByEntityEvent.getDamager().getId();
                            wolf.setAngry(true);
                        }
                    }
                }
            } else if (damageByEntityEvent.getDamager() instanceof Player) {
                for (Entity entity : damageByEntityEvent.getDamager().getLevel().getNearbyEntities(damageByEntityEvent.getDamager().getBoundingBox().grow(17, 17, 17), damageByEntityEvent.getDamager())) {
                    if (entity.getId() != source.getEntity().getId()) {
                        if (entity instanceof EntityWolf wolf) {
                            if (wolf.hasOwner() && wolf.isOwner(damageByEntityEvent.getDamager())) {
                                wolf.isAngryTo = source.getEntity().getId();
                                wolf.setAngry(true);
                            }
                        }
                    }
                }
            }
        }

        if (this.absorption > 0) { // Damage Absorption
            this.setAbsorption(Math.max(0, this.absorption + source.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION)));
        }
        setLastDamageCause(source);
        float newHealth = health - source.getFinalDamage();
        if (newHealth < 1 && this.isPlayer) {
            if (source.getCause() != DamageCause.VOID && source.getCause() != DamageCause.SUICIDE) {
                Player p = (Player) this;
                boolean totem = false;
                boolean isOffhand = false;
                if (p.getOffhandInventory().getItemFast(0) instanceof ItemTotem) {
                    totem = true;
                    isOffhand = true;
                } else if (p.getInventory().getItemInHandFast() instanceof ItemTotem) {
                    totem = true;
                }
                if (totem) {
                    this.getLevel().addLevelEvent(this, LevelEventPacket.EVENT_SOUND_TOTEM);
                    this.getLevel().addParticleEffect(this, ParticleEffect.TOTEM);

                    this.extinguish();
                    this.removeAllEffects(EntityPotionEffectEvent.Cause.TOTEM);
                    this.setHealth(1);

                    this.addEffect(Effect.getEffect(Effect.REGENERATION).setDuration(800).setAmplifier(1), EntityPotionEffectEvent.Cause.TOTEM);
                    this.addEffect(Effect.getEffect(Effect.FIRE_RESISTANCE).setDuration(800), EntityPotionEffectEvent.Cause.TOTEM);
                    this.addEffect(Effect.getEffect(Effect.ABSORPTION).setDuration(100).setAmplifier(1), EntityPotionEffectEvent.Cause.TOTEM);

                    EntityEventPacket pk = new EntityEventPacket();
                    pk.eid = this.getId();
                    pk.event = EntityEventPacket.CONSUME_TOTEM;
                    p.dataPacket(pk);

                    if (isOffhand) {
                        p.getOffhandInventory().clear(0);
                    } else {
                        p.getInventory().clear(p.getInventory().getHeldItemIndex());
                    }

                    source.setCancelled(true);
                    return false;
                }
            }
        }
        this.setHealth(newHealth);
        return true;
    }

    public boolean attack(float damage) {
        return this.attack(new EntityDamageEvent(this, DamageCause.CUSTOM, damage));
    }

    public void heal(EntityRegainHealthEvent source) {
        if (!this.isAlive()) {
            return;
        }
        this.server.getPluginManager().callEvent(source);
        if (source.isCancelled()) {
            return;
        }
        this.setHealth(this.health + source.getAmount());
    }

    public void heal(float amount) {
        this.heal(new EntityRegainHealthEvent(this, amount, EntityRegainHealthEvent.CAUSE_REGEN));
    }

    public float getHealth() {
        return health;
    }

    public boolean isAlive() {
        return this.health >= 1;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setHealth(float health) {
        if (this.health == health) {
            return;
        }

        if (health < 1) {
            if (this.isAlive()) {
                this.kill();
            }
        } else if (health <= this.getMaxHealth() || health < this.health) {
            this.health = health;
        } else {
            this.health = this.getMaxHealth();
        }

        setDataProperty(new IntEntityData(DATA_HEALTH, (int) this.health), this.isPlayer || this instanceof EntityRideable);
    }

    public void setLastDamageCause(EntityDamageEvent type) {
        this.lastDamageCause = type;
    }

    public EntityDamageEvent getLastDamageCause() {
        return lastDamageCause;
    }

    /**
     * 获取包含生命提升效果加成的最大生命值。
     * Get maximum health including health from health boost effect.
     *
     * @return 当前的最大生命值。
     *         current max health
     */
    public int getMaxHealth() {
        return maxHealth + (this.hasEffect(Effect.HEALTH_BOOST) ? (this.getEffect(Effect.HEALTH_BOOST).getAmplifier() + 1) << 2 : 0);
    }

    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
    }

    /**
     * 获取不包含效果加成的正常最大生命值。
     * Get normal maximum health excluding health from effects.
     *
     * @return 实际的最大生命值。
     *         real max health
     */
    public int getRealMaxHealth() {
        return maxHealth;
    }

    public boolean canCollideWith(Entity entity) {
        return !this.justCreated && this != entity && !this.noClip;
    }

    public boolean canBeSavedWithChunk() {
        return this.saveWithChunk;
    }

    /**
     * 设置实体是否可以保存到区块
     * @param saveWithChunk 是否可以保存到区块
     */
    public void setCanBeSavedWithChunk(boolean saveWithChunk) {
        this.saveWithChunk = saveWithChunk;
    }

    protected boolean checkObstruction(double x, double y, double z) {
        if (this.noClip || this.level.getCollisionCubes(this, this.boundingBox, false).length == 0) {
            return false;
        }

        int i = NukkitMath.floorDouble(x);
        int j = NukkitMath.floorDouble(y);
        int k = NukkitMath.floorDouble(z);

        double diffX = x - i;
        double diffY = y - j;
        double diffZ = z - k;

        if (!Block.transparent[this.level.getBlockIdAt(i, j, k)]) {
            boolean flag = Block.transparent[this.level.getBlockIdAt(i - 1, j, k)];
            boolean flag1 = Block.transparent[this.level.getBlockIdAt(i + 1, j, k)];
            boolean flag2 = Block.transparent[this.level.getBlockIdAt(i, j - 1, k)];
            boolean flag3 = Block.transparent[this.level.getBlockIdAt(i, j + 1, k)];
            boolean flag4 = Block.transparent[this.level.getBlockIdAt(i, j, k - 1)];
            boolean flag5 = Block.transparent[this.level.getBlockIdAt(i, j, k + 1)];

            int direction = -1;
            double limit = 9999;

            if (flag) {
                limit = diffX;
                direction = 0;
            }

            if (flag1 && 1 - diffX < limit) {
                limit = 1 - diffX;
                direction = 1;
            }

            if (flag2 && diffY < limit) {
                limit = diffY;
                direction = 2;
            }

            if (flag3 && 1 - diffY < limit) {
                limit = 1 - diffY;
                direction = 3;
            }

            if (flag4 && diffZ < limit) {
                limit = diffZ;
                direction = 4;
            }

            if (flag5 && 1 - diffZ < limit) {
                direction = 5;
            }

            double force = Utils.random.nextDouble() * 0.2 + 0.1;

            if (direction == 0) {
                this.motionX = -force;

                return true;
            }

            if (direction == 1) {
                this.motionX = force;

                return true;
            }

            if (direction == 2) {
                this.motionY = -force;

                return true;
            }

            if (direction == 3) {
                this.motionY = force;

                return true;
            }

            if (direction == 4) {
                this.motionZ = -force;

                return true;
            }

            if (direction == 5) {
                this.motionZ = force;

                return true;
            }
        }

        return false;
    }

    @Deprecated
    public boolean entityBaseTick() {
        return this.entityBaseTick(1);
    }

    /**
     * 实体基础 tick 方法，若实体存活，会在 `onUpdate` 方法中被调用。其返回结果会应用到 `onUpdate` 方法中，之后会自动调用 `updateMovement` 方法。
     * Entity base tick, called from onUpdate if the entity is alive. Result is applied to onUpdate. updateMovement is called afterward automatically.
     *
     * @param tickDiff 间隔 tick
     *                  Interval tick
     * @return 是否继续 tick
     *          Whether to continue tick
     */
    public boolean entityBaseTick(int tickDiff) {
        if (!this.isPlayer) {
            //this.blocksAround = null; // Use only when entity moves for better performance
            this.collisionBlocks = null;
        }

        this.justCreated = false;

        if (!this.isAlive()) {
            this.removeAllEffects(EntityPotionEffectEvent.Cause.DEATH);
            this.despawnFromAll();
            if (!this.isPlayer) {
                this.close();
            }
            return false;
        }
        if (riding != null && !riding.isAlive() && riding instanceof EntityRideable) {
            ((EntityRideable) riding).dismountEntity(this);
        }

        updatePassengers();

        if (!this.effects.isEmpty()) {
            for (Effect effect : this.effects.values()) {
                if (effect.canTick()) {
                    effect.applyEffect(this);
                }
                effect.setDuration(effect.getDuration() - tickDiff);

                if (effect.getDuration() <= 0) {
                    this.removeEffect(effect.getId(), EntityPotionEffectEvent.Cause.EXPIRATION);
                }
            }
        }

        boolean hasUpdate = false;

        this.checkBlockCollision();
        int minY = level.getMinBlockY() - 18;
        if (this.isPlayer && ((Player) this).protocol < ProtocolInfo.v1_18_0) {
            minY = -18;
        }
        if (this.y <= minY && this.isAlive()) {
            if (this.isPlayer) {
                if (((Player) this).getGamemode() != Player.CREATIVE) this.attack(new EntityDamageEvent(this, DamageCause.VOID, 10));
            } else {
                this.attack(new EntityDamageEvent(this, DamageCause.VOID, 10));
                hasUpdate = true;
            }
        }

        if (this.fireTicks > 0) {
            if (this.fireProof) {
                this.fireTicks -= tickDiff << 2;
                if (this.fireTicks < 0) {
                    this.fireTicks = 0;
                }
            } else {
                if (!this.hasEffect(Effect.FIRE_RESISTANCE) && ((this.fireTicks % 20) == 0 || tickDiff > 20)) {
                    if (!isInsideOfLava()){
                        this.attack(new EntityDamageEvent(this, DamageCause.FIRE_TICK, 1));
                    }
                }
                this.fireTicks -= tickDiff;
            }
            if (this.fireTicks <= 0) {
                this.extinguish();
            } else if (!this.fireProof && (!this.isPlayer || !((Player) this).isSpectator())) {
                this.setDataFlag(DATA_FLAGS, DATA_FLAG_ONFIRE, true);
                hasUpdate = true;
            }
        }

        if (this.noDamageTicks > 0) {
            this.noDamageTicks -= tickDiff;
            if (this.noDamageTicks < 0) {
                this.noDamageTicks = 0;
            }
        }

        if (this.inPortalTicks == 80 && Server.getInstance().isNetherAllowed() && this instanceof BaseEntity) {
            EntityPortalEnterEvent ev = new EntityPortalEnterEvent(this, EntityPortalEnterEvent.PortalType.NETHER);
            this.server.getPluginManager().callEvent(ev);

            if (!ev.isCancelled()) {
                if (this.getLevel().getDimension() == Level.DIMENSION_NETHER) {
                    this.switchLevel(server.getDefaultLevel());
                } else {
                    this.switchLevel(server.getNetherWorld(this.level.getName()));
                }
            }
        }

        this.age += tickDiff;
        this.ticksLived += tickDiff;

        return hasUpdate;
    }

    public void updateMovement() {
        double diffPosition = (this.x - this.lastX) * (this.x - this.lastX) + (this.y - this.lastY) * (this.y - this.lastY) + (this.z - this.lastZ) * (this.z - this.lastZ);
        double diffRotation = (this.yaw - this.lastYaw) * (this.yaw - this.lastYaw) + (this.pitch - this.lastPitch) * (this.pitch - this.lastPitch);

        double diffMotion = (this.motionX - this.lastMotionX) * (this.motionX - this.lastMotionX) + (this.motionY - this.lastMotionY) * (this.motionY - this.lastMotionY) + (this.motionZ - this.lastMotionZ) * (this.motionZ - this.lastMotionZ);

        if (diffPosition > 0.0001 || diffRotation > 1.0) { //0.2 ** 2, 1.5 ** 2
            this.addMovement(this.x, this.isPlayer ? this.y : this.y + this.getBaseOffset(), this.z, this.yaw, this.pitch, this.headYaw == 0.0 || this.isPlayer ? this.yaw : this.headYaw);

            this.lastX = this.x;
            this.lastY = this.y;
            this.lastZ = this.z;

            this.lastYaw = this.yaw;
            this.lastPitch = this.pitch;
            this.lastHeadYaw = this.headYaw;

            this.positionChanged = true;
        }else {
            this.positionChanged = false;
        }

        if (diffMotion > 0.0025 || (diffMotion > 0.0001 && this.getMotion().lengthSquared() <= 0.0001)) { //0.05 ** 2
            this.lastMotionX = this.motionX;
            this.lastMotionY = this.motionY;
            this.lastMotionZ = this.motionZ;

            this.addMotion(this.motionX, this.motionY, this.motionZ);
        }
    }

    public void addMovement(double x, double y, double z, double yaw, double pitch, double headYaw) {
        this.level.addEntityMovement(this, x, y, z, yaw, pitch, headYaw);
    }

    public void addMotion(double motionX, double motionY, double motionZ) {
        SetEntityMotionPacket pk = new SetEntityMotionPacket();
        pk.eid = this.id;
        pk.motionX = (float) motionX;
        pk.motionY = (float) motionY;
        pk.motionZ = (float) motionZ;
        for (Player p : this.hasSpawned.values()) {
            p.dataPacket(pk); // Server.broadcastPacket would only use batching for >= 1.16.100
        }
    }

    protected void broadcastMovement() {
        MoveEntityAbsolutePacket pk = new MoveEntityAbsolutePacket();
        pk.eid = this.getId();
        pk.x = this.x;
        //因为以前处理MOVE_PLAYER_PACKET的时候是y - this.getBaseOffset()
        //现在统一 MOVE_PLAYER_PACKET和PLAYER_AUTH_INPUT_PACKET 均为this.y - this.getEyeHeight()，所以这里不再需要对两种移动方式分别处理
        pk.y = this.y + this.getBaseOffset();
        pk.z = this.z;
        pk.headYaw = yaw;
        pk.pitch = pitch;
        pk.yaw = yaw;
        pk.teleport = false;
        pk.onGround = this.onGround;
        Server.broadcastPacket(hasSpawned.values().stream().filter(p -> p.protocol >= ProtocolInfo.v1_7_0).collect(Collectors.toList()), pk);
    }

    @Override
    public Vector3 getDirectionVector() {
        Vector3 vector = super.getDirectionVector();
        return this.temporalVector.setComponents(vector.x, vector.y, vector.z);
    }

    public Vector2 getDirectionPlane() {
        return (new Vector2((float) (-Math.cos(FastMath.toRadians(this.yaw) - 1.5707963267948966)), (float) (-Math.sin(FastMath.toRadians(this.yaw) - 1.5707963267948966)))).normalize();
    }

    public BlockFace getHorizontalFacing() {
        return BlockFace.fromHorizontalIndex(NukkitMath.floorDouble((this.yaw * 4.0F / 360.0F) + 0.5D) & 3);
    }

    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (!this.isAlive()) {
            ++this.deadTicks;
            if (this.deadTicks >= 10) {
                this.despawnFromAll();
                if (!this.isPlayer) {
                    this.close();
                }
            }
            return this.deadTicks < 10;
        }

        int tickDiff = currentTick - this.lastUpdate;

        if (tickDiff <= 0) {
            return false;
        }

        this.lastUpdate = currentTick;

        boolean hasUpdate = this.entityBaseTick(tickDiff);

        this.updateMovement();

        /*if (server.vanillaBB && this instanceof EntityBoss && currentTick % 100 == 0) { //TODO: Figure out why doesn't the boss bar length change
            for (Player p : this.hasSpawned.values()) {
                BossEventPacket pkBoss = new BossEventPacket();
                pkBoss.bossEid = this.id;
                pkBoss.type = BossEventPacket.TYPE_HEALTH_PERCENT;
                pkBoss.title = this.getName();
                pkBoss.healthPercent = p.protocol >= 361 ? this.health / 100 : this.health;
                p.dataPacket(pkBoss);
            }
        }*/

        return hasUpdate;
    }

    public boolean mountEntity(Entity entity) {
        return mountEntity(entity, TYPE_RIDE);
    }

    /**
     * Mount an Entity from a/into vehicle
     *
     * @param entity The target Entity
     * @return {@code true} if the mounting successful
     */
    public boolean mountEntity(Entity entity, byte mode) {
        Objects.requireNonNull(entity, "The target of the mounting entity can't be null");

        if (isPassenger(entity) || entity.riding != null && !entity.riding.dismountEntity(entity, false)) {
            return false;
        }

        // Entity entering a vehicle
        EntityVehicleEnterEvent ev = new EntityVehicleEnterEvent(entity, (EntityVehicle) this);
        server.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return false;
        }

        broadcastLinkPacket(entity, mode);

        // Add variables to entity
        entity.riding = this;
        entity.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, true);
        passengers.add(entity);

        entity.setSeatPosition(getMountedOffset(entity));
        updatePassengerPosition(entity);
        return true;
    }

    public boolean dismountEntity(Entity entity) {
        return this.dismountEntity(entity, true);
    }

    public boolean dismountEntity(Entity entity, boolean sendLinks) {
        if (this instanceof EntityVehicle) {
            // Run the events
            EntityVehicleExitEvent ev = new EntityVehicleExitEvent(entity, (EntityVehicle) this);
            server.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                int seatIndex = this.passengers.indexOf(entity);
                if (seatIndex == 0) {
                    this.broadcastLinkPacket(entity, TYPE_RIDE);
                } else if (seatIndex != -1) {
                    this.broadcastLinkPacket(entity, TYPE_PASSENGER);
                }
                return false;
            }
        }

        if (sendLinks) {
            broadcastLinkPacket(entity, TYPE_REMOVE);
        }

        // Refurbish the entity
        entity.riding = null;
        entity.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, false);
        passengers.remove(entity);

        entity.setSeatPosition(new Vector3f());
        updatePassengerPosition(entity);

        // Avoid issues with anti fly
        entity.resetFallDistance();
        return true;
    }

    protected void broadcastLinkPacket(Entity rider, byte type) {
        SetEntityLinkPacket pk = new SetEntityLinkPacket();
        pk.vehicleUniqueId = id;     // To the?
        pk.riderUniqueId = rider.id; // From who?
        pk.type = type;

        Server.broadcastPacket(this.hasSpawned.values(), pk);
    }

    public void updatePassengers() {
        if (this.passengers.isEmpty()) {
            return;
        }

        for (Entity passenger : new ArrayList<>(this.passengers)) {
            if (!passenger.isAlive()) {
                dismountEntity(passenger);
                continue;
            }

            updatePassengerPosition(passenger);
        }
    }

    protected void updatePassengerPosition(Entity passenger) {
        passenger.setPosition(this.add(passenger.getSeatPosition().asVector3()));
    }

    public void setSeatPosition(Vector3f pos) {
        this.setDataProperty(new Vector3fEntityData(DATA_RIDER_SEAT_POSITION, pos));
    }

    public Vector3f getSeatPosition() {
        return this.getDataPropertyVector3f(DATA_RIDER_SEAT_POSITION);
    }

    public Vector3f getMountedOffset(Entity entity) {
        return new Vector3f(0, getHeight() * 0.75f);
    }

    public final void scheduleUpdate() {
        if (!this.closed && !this.level.isBeingConverted) {
            this.level.updateEntities.put(this.id, this);
        }
    }

    public boolean isOnFire() {
        return this.fireTicks > 0;
    }

    public void setOnFire(int seconds) {
        if (!hasEffect(Effect.FIRE_RESISTANCE)) {
            int ticks = seconds * 20;
            if (ticks > this.fireTicks) {
                this.fireTicks = ticks;
            }
        }
    }

    public float getAbsorption() {
        return absorption;
    }

    public void setAbsorption(float absorption) {
        if (absorption != this.absorption) {
            this.absorption = absorption;
            if (this.isPlayer) ((Player) this).setAttribute(Attribute.getAttribute(Attribute.ABSORPTION).setValue(absorption));
        }
    }

    public boolean canBePushed() {
        return true;
    }

    public BlockFace getDirection() {
        double rotation = this.yaw % 360;
        if (rotation < 0) {
            rotation += 360.0;
        }
        if ((0 <= rotation && rotation < 45) || (315 <= rotation && rotation < 360)) {
            return BlockFace.SOUTH;
        } else if (45 <= rotation && rotation < 135) {
            return BlockFace.WEST;
        } else if (135 <= rotation && rotation < 225) {
            return BlockFace.NORTH;
        } else if (225 <= rotation && rotation < 315) {
            return BlockFace.EAST;
        } else {
            return null;
        }
    }

    public void extinguish() {
        this.fireTicks = 0;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_ONFIRE, false);
    }

    public boolean canTriggerWalking() {
        return true;
    }

    public void resetFallDistance() {
        this.highestPosition = this.y;
    }

    protected void updateFallState(boolean onGround) {
        if (onGround) {
            fallDistance = (float) (this.highestPosition - this.y);

            if (fallDistance > 0) {
                // check if we fell into at least 1 block of water
                if (this instanceof EntityLiving && !(this.getLevelBlock() instanceof BlockWater) && !(this instanceof EntityFlying)) {
                    this.fall(fallDistance);
                }
                this.resetFallDistance();
            }
        }
    }

    public AxisAlignedBB getBoundingBox() {
        return this.boundingBox;
    }

    public void fall(float fallDistance) {
        if (fallDistance > 0.75) {
            int block = this.level.getBlockIdAt(this.chunk, this.getFloorX(), this.getFloorY(), this.getFloorZ());
            if (Block.isWater(block)) {
                this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_SPLASH, ThreadLocalRandom.current().nextInt(600000, 800000), "minecraft:player", false, false);
                return; // TODO: Some waterlogged blocks prevent fall damage
            }
            if (!this.hasEffect(Effect.SLOW_FALLING)) {
                Block down = this.level.getBlock(this.chunk, this.getFloorX(), this.getFloorY() - 1, this.getFloorZ(), 0, true);
                int floor = down.getId();

                if (!this.noFallDamage) {
                    float damage = (float) Math.floor(fallDistance - 3 - (this.hasEffect(Effect.JUMP) ? this.getEffect(Effect.JUMP).getAmplifier() + 1 : 0));

                    if (floor == BlockID.HAY_BALE || block == BlockID.HAY_BALE) {
                        damage -= (damage * 0.8f);
                    } else if (floor == BlockID.BED_BLOCK || block == BlockID.BED_BLOCK) {
                        damage -= (damage * 0.5f);
                    } else if (floor == BlockID.SLIME_BLOCK || floor == BlockID.COBWEB || floor == BlockID.SCAFFOLDING || floor == BlockID.SWEET_BERRY_BUSH) {
                        damage = 0;
                    }

                    if (isPlayer) {
                        final int level = ((Player) this).getInventory().getBootsFast().getEnchantmentLevel(Enchantment.ID_PROTECTION_FALL);
                        if (level != 0) {
                            damage -= damage / 100 * (level * 12);
                        }
                    }

                    if (damage > 0 && (!this.isPlayer || level.getGameRules().getBoolean(GameRule.FALL_DAMAGE))) {
                        this.attack(new EntityDamageEvent(this, DamageCause.FALL, damage));
                    }
                }

                if (down.getId() == BlockID.FARMLAND) {
                    Event ev;

                    if (this.isPlayer) {
                        ev = new PlayerInteractEvent((Player) this, null, down, null, Action.PHYSICAL);
                    } else {
                        ev = new EntityInteractEvent(this, down);
                    }

                    this.server.getPluginManager().callEvent(ev);
                    if (ev.isCancelled()) {
                        return;
                    }
                    this.level.setBlock(down, Block.get(BlockID.DIRT), true, true);
                }
            }
        }
    }

    public void moveFlying(float strafe, float forward, float friction) {
        // This is special for Nukkit! :)
        float speed = strafe * strafe + forward * forward;
        if (speed >= 1.0E-4F) {
            speed = MathHelper.sqrt(speed);
            if (speed < 1.0F) {
                speed = 1.0F;
            }
            speed = friction / speed;
            strafe *= speed;
            forward *= speed;
            float nest = MathHelper.sin((float) (this.yaw * 3.1415927F / 180.0F));
            float place = MathHelper.cos((float) (this.yaw * 3.1415927F / 180.0F));
            this.motionX += strafe * place - forward * nest;
            this.motionZ += forward * place + strafe * nest;
        }
    }

    public void applyEntityCollision(Entity entity) {
        if (entity.riding != this && !entity.passengers.contains(this)) {
            double dx = entity.x - this.x;
            double dy = entity.z - this.z;
            double dz = NukkitMath.getDirection(dx, dy);

            if (dz >= 0.009999999776482582D) {
                dz = MathHelper.sqrt((float) dz);
                dx /= dz;
                dy /= dz;
                double d3 = 1.0D / dz;

                if (d3 > 1.0D) {
                    d3 = 1.0D;
                }

                dx *= d3;
                dy *= d3;
                dx *= 0.05000000074505806;
                dy *= 0.05000000074505806;
                dx *= 1F + entityCollisionReduction;
                if (this.riding == null) {
                    motionX -= dx;
                    motionZ -= dy;
                }
            }
        }
    }

    public void onStruckByLightning(Entity entity) {
        if (this.attack(new EntityDamageByEntityEvent(entity, this, DamageCause.LIGHTNING, 5))) {
            if (this.fireTicks < 160) {
                this.setOnFire(8);
            }

            if (this instanceof EntityCreeper) {
                ((EntityCreeper) this).setPowered(true);
            }
        }
    }

    public void onPushByPiston(BlockEntityPistonArm piston, BlockFace moveDirection) {
        if (this.closed){
            return;
        }

        float diff = Math.abs(piston.progress - piston.lastProgress);
        this.move(diff * moveDirection.getXOffset(), diff * moveDirection.getYOffset(), diff * moveDirection.getZOffset());
    }

    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        return onInteract(player, item);
    }

    public boolean onInteract(Player player, Item item) {
        return false;
    }

    protected boolean switchLevel(Level targetLevel) {
        if (this.closed) {
            return false;
        }

        if (this.isValid()) {
            EntityLevelChangeEvent ev = new EntityLevelChangeEvent(this, this.level, targetLevel);
            this.server.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                return false;
            }

            this.level.removeEntity(this);
            if (this.chunk != null) {
                this.chunk.removeEntity(this);
            }
            this.despawnFromAll();

            if (this.isPlayer) {
                this.preSwitchLevel();
            }
        }

        this.setLevel(targetLevel);
        this.level.addEntity(this);
        this.chunk = null;

        if (this.isPlayer) {
            this.afterSwitchLevel();
        }
        return true;
    }

    protected void preSwitchLevel() {
        // Override in Player
    }

    protected void afterSwitchLevel() {
        // Override in Player
    }

    @NotNull
    public Position getPosition() {
        return new Position(this.x, this.y, this.z, this.level);
    }

    @Override
    @NotNull
    public Location getLocation() {
        return new Location(this.x, this.y, this.z, this.yaw, this.pitch, this.headYaw, this.level);
    }

    public boolean isSubmerged() {
        double y = this.y + this.getEyeHeight();
        Block block = this.level.getBlock(this.temporalVector.setComponents(NukkitMath.floorDouble(this.x), NukkitMath.floorDouble(y), NukkitMath.floorDouble(this.z)));
        return block instanceof BlockWater || this.level.getBlock(block, 1) instanceof BlockWater;
    }

    public boolean isInsideOfWater() {
        Block block = level.getBlock(this.getFloorX(), this.getFloorY(), this.getFloorZ());
        return block.isWater() || block.getWaterloggingType() != Block.WaterloggingType.NO_WATERLOGGING && block.getLevelBlockAtLayer(1).isWater();
    }

    public boolean isInsideOfLava() {
        Block block = level.getBlock(this.getFloorX(), this.getFloorY(), this.getFloorZ());
        return block instanceof BlockLava || block.getLevelBlockAtLayer(1) instanceof BlockLava;
    }

    public boolean isInsideOfSolid() {
        double y = this.y + this.getEyeHeight();
        Block block = this.level.getBlock(
                this.temporalVector.setComponents(
                        NukkitMath.floorDouble(this.x),
                        NukkitMath.floorDouble(y),
                        NukkitMath.floorDouble(this.z))
        );

        AxisAlignedBB bb = block.getBoundingBox();

        return bb != null && block.isSolid() && !block.isTransparent() && bb.intersectsWith(this.boundingBox);
    }

    public boolean isInsideOfFire() {
        for (Block block : this.getCollisionBlocks()) {
            if (block instanceof BlockFire) {
                return true;
            }
        }

        return false;
    }

    public boolean fastMove(double dx, double dy, double dz) {
        if (!this.isPlayer) {
            this.blocksAround = null;
        }

        if (dx == 0 && dy == 0 && dz == 0) {
            return true;
        }

        AxisAlignedBB newBB = this.boundingBox.getOffsetBoundingBox(dx, dy, dz);

        if (server.getAllowFlight() || !this.level.hasCollision(this, newBB, false)) {
            this.boundingBox = newBB;
        }

        this.x = (this.boundingBox.getMinX() + this.boundingBox.getMaxX()) / 2;
        this.y = this.boundingBox.getMinY() - this.ySize;
        this.z = (this.boundingBox.getMinZ() + this.boundingBox.getMaxZ()) / 2;

        this.checkChunks();

        if ((!this.onGround || dy != 0) && !this.noClip) {
            AxisAlignedBB bb = this.boundingBox.clone();
            bb.setMinY(bb.getMinY() - 0.75);

            this.onGround = this.level.hasCollisionBlocks(this, bb);
        }
        this.isCollided = this.onGround;
        this.updateFallState(this.onGround);
        return true;
    }

    public boolean move(double dx, double dy, double dz) {
        if (dx == 0 && dz == 0 && dy == 0) {
            this.onGround = !this.getPosition().setComponents(this.down()).getLevelBlock().canPassThrough();
            return false;
        }

        if (!this.isPlayer) {
            this.blocksAround = null;
        }

        if (this.keepMovement) {
            this.boundingBox.offset(dx, dy, dz);
            this.setPosition(this.temporalVector.setComponents((this.boundingBox.getMinX() + this.boundingBox.getMaxX()) / 2, this.boundingBox.getMinY(), (this.boundingBox.getMinZ() + this.boundingBox.getMaxZ()) / 2));
            this.onGround = this.isPlayer;
            return true;
        } else {
            this.ySize *= STEP_CLIP_MULTIPLIER;

            double movX = dx;
            double movY = dy;
            double movZ = dz;

            AxisAlignedBB axisalignedbb = this.boundingBox.clone();

            AxisAlignedBB[] list = this.noClip ? AxisAlignedBB.EMPTY_ARRAY : this.level.getCollisionCubes(this, this.boundingBox.addCoord(dx, dy, dz), false);

            for (AxisAlignedBB bb : list) {
                dy = bb.calculateYOffset(this.boundingBox, dy);
            }

            this.boundingBox.offset(0, dy, 0);

            boolean fallingFlag = (this.onGround || (dy != movY && movY < 0));

            for (AxisAlignedBB bb : list) {
                dx = bb.calculateXOffset(this.boundingBox, dx);
            }

            this.boundingBox.offset(dx, 0, 0);

            for (AxisAlignedBB bb : list) {
                dz = bb.calculateZOffset(this.boundingBox, dz);
            }

            this.boundingBox.offset(0, 0, dz);

            if (this.getStepHeight() > 0 && fallingFlag && (movX != dx || movZ != dz)) {
                double cx = dx;
                double cy = dy;
                double cz = dz;
                dx = movX;
                dy = this.getStepHeight();
                dz = movZ;

                AxisAlignedBB axisalignedbb1 = this.boundingBox.clone();

                this.boundingBox.setBB(axisalignedbb);

                list = this.level.getCollisionCubes(this, this.boundingBox.addCoord(dx, dy, dz), false);

                for (AxisAlignedBB bb : list) {
                    dy = bb.calculateYOffset(this.boundingBox, dy);
                }

                this.boundingBox.offset(0, dy, 0);

                for (AxisAlignedBB bb : list) {
                    dx = bb.calculateXOffset(this.boundingBox, dx);
                }

                this.boundingBox.offset(dx, 0, 0);

                for (AxisAlignedBB bb : list) {
                    dz = bb.calculateZOffset(this.boundingBox, dz);
                }

                this.boundingBox.offset(0, 0, dz);

                double reverseDY = -dy;
                for (AxisAlignedBB bb : list) {
                    reverseDY = bb.calculateYOffset(this.boundingBox, reverseDY);
                }
                dy += reverseDY;
                this.boundingBox.offset(0, reverseDY, 0);

                if ((cx * cx + cz * cz) >= (dx * dx + dz * dz)) {
                    dx = cx;
                    dy = cy;
                    dz = cz;
                    this.boundingBox.setBB(axisalignedbb1);
                } else {
                    this.ySize += dy;
                }
            }

            this.x = (this.boundingBox.getMinX() + this.boundingBox.getMaxX()) / 2;
            this.y = this.boundingBox.getMinY() - this.ySize;
            this.z = (this.boundingBox.getMinZ() + this.boundingBox.getMaxZ()) / 2;

            this.checkChunks();

            this.checkGroundState(movX, movY, movZ, dx, dy, dz);
            this.updateFallState(this.onGround);

            if (movX != dx) {
                this.motionX = 0;
            }

            if (movY != dy) {
                this.motionY = 0;
            }

            if (movZ != dz) {
                this.motionZ = 0;
            }

            return true;
        }
    }

    protected void checkGroundState(double movX, double movY, double movZ, double dx, double dy, double dz) {
        if (this.noClip) {
            this.isCollidedVertically = false;
            this.isCollidedHorizontally = false;
            this.isCollided = false;
            this.onGround = false;
        } else {
            this.isCollidedVertically = movY != dy;
            this.isCollidedHorizontally = (movX != dx || movZ != dz);
            this.isCollided = (this.isCollidedHorizontally || this.isCollidedVertically);
            this.onGround = (movY != dy && movY < 0);
        }
    }

    public List<Block> getBlocksAround() {
        if (this.blocksAround == null) {
            int minX = NukkitMath.floorDouble(this.boundingBox.getMinX());
            int minY = NukkitMath.floorDouble(this.boundingBox.getMinY());
            int minZ = NukkitMath.floorDouble(this.boundingBox.getMinZ());
            int maxX = NukkitMath.ceilDouble(this.boundingBox.getMaxX());
            int maxY = NukkitMath.ceilDouble(this.boundingBox.getMaxY());
            int maxZ = NukkitMath.ceilDouble(this.boundingBox.getMaxZ());

            this.blocksAround = new ArrayList<>();

            try {
                if (this.level.isYInRange(minY) || this.level.isYInRange(maxY)) {
                    minY = Math.max(minY, this.level.getMinBlockY());
                    maxY = Math.min(maxY, this.level.getMaxBlockY());
                    for (int z = minZ; z <= maxZ; ++z) {
                        for (int x = minX; x <= maxX; ++x) {
                            for (int y = minY; y <= maxY; ++y) {
                                Block block = this.level.getBlock(x, y, z, false);
                                this.blocksAround.add(block);
                            }
                        }
                    }
                }
            } catch (NullPointerException e) {
                // 异步传送导致空指针 忽略结果
                return new ArrayList<>();
            }
        }

        return this.blocksAround;
    }

    public List<Block> getCollisionBlocks() {
        if (this.collisionBlocks == null) {
            this.collisionBlocks = new ArrayList<>();

            List<Block> bl = this.getBlocksAround();
            for (Block b : bl) {
                if (b.collidesWithBB(this.boundingBox, true)) {
                    this.collisionBlocks.add(b);
                }
            }
        }

        return this.collisionBlocks;
    }

    /**
     * Returns whether this entity can be moved by currents in liquids.
     *
     * @return boolean
     */
    public boolean canBeMovedByCurrents() {
        return true;
    }

    protected void checkBlockCollision() {
        if (this.noClip) {
            return;
        }

        Vector3 vector = new Vector3(0, 0, 0);
        boolean portal = false;
        boolean powderSnow = false;

        for (Block block : this.getCollisionBlocks()) {
            if (block.getId() == Block.NETHER_PORTAL) {
                portal = true;
                continue;
            }

            if (block.getId() == Block.POWDER_SNOW) {
                portal = true;
                continue;
            }

            block.onEntityCollide(this);
            block.getLevelBlockAtLayer(1).onEntityCollide(this);
            block.addVelocityToEntity(this, vector);
        }

        if (portal) {
            inPortalTicks++;
        } else {
            this.inPortalTicks = 0;
        }
        
        if (vector.lengthSquared() > 0) {
            vector = vector.normalize();
            double d = 0.014d;
            this.motionX += vector.x * d;
            this.motionY += vector.y * d;
            this.motionZ += vector.z * d;
        }
    }

    public boolean setPositionAndRotation(Vector3 pos, double yaw, double pitch) {
        return this.setPositionAndRotation(pos, yaw, pitch, yaw);
    }

    public boolean setPositionAndRotation(Vector3 pos, double yaw, double pitch, double headYaw) {
        if (this.setPosition(pos)) {
            this.setRotation(yaw, pitch, headYaw);
            return true;
        }

        return false;
    }

    public void setRotation(double yaw, double pitch) {
        this.setRotation(yaw, pitch, yaw);
    }

    public void setRotation(double yaw, double pitch, double headYaw) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.headYaw = headYaw;
        this.scheduleUpdate();
    }

    /**
     * Whether the entity can active pressure plates.
     *
     * @return triggers pressure plate
     */
    public boolean doesTriggerPressurePlate() {
        return true;
    }

    public boolean canPassThrough() {
        return true;
    }

    protected void checkChunks() {
        int cx = (int) this.x >> 4;
        int cz = (int) this.z >> 4;
        if (this.chunk == null || (this.chunk.getX() != cx) || this.chunk.getZ() != cz) {
            if (this.chunk != null) {
                this.chunk.removeEntity(this);
            }
            this.chunk = this.level.getChunk(cx, cz, true);

            if (!this.justCreated) {
                Map<Integer, Player> newChunk = this.level.getChunkPlayers(cx, cz);
                for (Player player : new ArrayList<>(this.hasSpawned.values())) {
                    if (!newChunk.containsKey(player.getLoaderId())) {
                        this.despawnFrom(player);
                    } else {
                        newChunk.remove(player.getLoaderId());
                    }
                }

                for (Player player : newChunk.values()) {
                    this.spawnTo(player);
                }
            }

            if (this.chunk == null) {
                return;
            }

            this.chunk.addEntity(this);
        }
    }

    public boolean setPosition(Vector3 pos) {
        if (this.closed) {
            return false;
        }

        // 校验坐标有效性
        // 当坐标接近int类型范围上限时，与碰撞相关的方法有可能计算出超出int表示上限的时
        // eg: Entity::getBlocksAround(), 在示例方法中，会导致服务端迅速OOM
        if (Math.abs(pos.x) > ENTITY_COORDINATES_MAX_VALUE ||
            Math.abs(pos.y) > ENTITY_COORDINATES_MAX_VALUE ||
            Math.abs(pos.z) > ENTITY_COORDINATES_MAX_VALUE) {
            server.getLogger().warning("Entity " + this.getName() + " is trying to set position to " + pos + " which is out of bounds!");
            return false;
        }

        if (pos instanceof Position) {
            Level oldLevel = this.level;
            Level newLevel = ((Position) pos).level;

            if (newLevel != null && newLevel != oldLevel) {
                if (!this.switchLevel(newLevel)) {
                    return false;
                }

                this.x = pos.x;
                this.y = pos.y;
                this.z = pos.z;

                // Dimension change
                if (this.isPlayer && newLevel.getDimension() != oldLevel.getDimension()) {
                    ((Player) this).setDimension(newLevel.getDimension());
                }

                // 切换世界后重置碰撞计算
                this.blocksAround = null;
                this.collisionBlocks = null;
            } else {
                this.x = pos.x;
                this.y = pos.y;
                this.z = pos.z;
            }
        } else {
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
        }

        this.recalculateBoundingBox(false);

        if (!this.isPlayer) {
            this.blocksAround = null;
        }

        this.checkChunks();

        return true;
    }

    public Vector3 getMotion() {
        return new Vector3(this.motionX, this.motionY, this.motionZ);
    }

    public boolean setMotion(Vector3 motion) {
        if (server.callEntityMotionEv && !this.justCreated) {
            EntityMotionEvent ev = new EntityMotionEvent(this, motion);
            this.server.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                return false;
            }
        }

        this.motionX = motion.x;
        this.motionY = motion.y;
        this.motionZ = motion.z;

        if (!this.justCreated) {
            this.updateMovement();
        }

        return true;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public void kill() {
        this.health = 0;
        this.scheduleUpdate();

        for (Entity passenger : new ArrayList<>(this.passengers)) {
            dismountEntity(passenger);
        }
    }

    public boolean teleport(Vector3 pos) {
        return this.teleport(pos, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    public boolean teleport(Vector3 pos, PlayerTeleportEvent.TeleportCause cause) {
        return this.teleport(Location.fromObject(pos, this.level, this.yaw, this.pitch, this.headYaw), cause);
    }

    public boolean teleport(Position pos) {
        return this.teleport(pos, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    public boolean teleport(Position pos, PlayerTeleportEvent.TeleportCause cause) {
        return this.teleport(Location.fromObject(pos, pos.level, this.yaw, this.pitch, this.headYaw), cause);
    }

    public boolean teleport(Location location) {
        return this.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    public boolean teleport(Location location, PlayerTeleportEvent.TeleportCause cause) {
        Location from = this.getLocation();
        Location to = location;
        if (cause != null) {
            EntityTeleportEvent ev = new EntityTeleportEvent(this, from, to, cause);
            this.server.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                return false;
            }
            to = ev.getTo();
        }

        if (this.riding != null && !this.riding.dismountEntity(this)) {
            return false;
        }

        this.ySize = 0;

        if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            this.setMotion(this.temporalVector.setComponents(0, 0, 0));
        }

        if (this.setPositionAndRotation(to, to.yaw, to.pitch, to.headYaw)) {
            this.resetFallDistance();
            this.onGround = !this.isNoClip();

            this.updateMovement();

            return true;
        }

        return false;
    }

    public UUID getUniqueId() {
        return this.entityUniqueId;
    }

    public long getId() {
        return this.id;
    }

    public void respawnToAll() {
        Collection<Player> players = new ArrayList<>(this.hasSpawned.values());
        this.hasSpawned.clear();

        for (Player player : players) {
            this.spawnTo(player);
        }
    }

    public void spawnToAll() {
        if (this.chunk == null || this.closed) {
            return;
        }

        for (Player player : this.level.getChunkPlayers(this.chunk.getX(), this.chunk.getZ()).values()) {
            if (player.isOnline()) {
                this.spawnTo(player);
            }
        }
    }

    public void despawnFromAll() {
        for (Player player : new ArrayList<>(this.hasSpawned.values())) {
            this.despawnFrom(player);
        }
    }

    public void close() {
        if (!this.closed) {
            this.closed = true;
            this.server.getPluginManager().callEvent(new EntityDespawnEvent(this));
            this.despawnFromAll();
            if (this.chunk != null) {
                this.chunk.removeEntity(this);
            }

            if (this.level != null) {
                this.level.removeEntity(this);
            }
        }
    }

    public boolean setDataProperty(EntityData data) {
        return this.setDataProperty(data, true);
    }

    public boolean setDataProperty(EntityData data, boolean send) {
        if (Objects.equals(data, this.dataProperties.get(data.getId()))) {
            return false;
        }

        this.dataProperties.put(data);
        if (send) {
            EntityMetadata metadata = new EntityMetadata();
            metadata.put(this.dataProperties.get(data.getId()));
            if (data.getId() == DATA_FLAGS2) {
                metadata.put(this.dataProperties.get(DATA_FLAGS));
            }
            this.sendData(this.hasSpawned.values().toArray(Player.EMPTY_ARRAY), metadata);
        }
        return true;
    }

    protected boolean removeDataProperty(int id) {
        return this.dataProperties.remove(id) != null;
    }

    public boolean setDataPropertyAndSendOnlyToSelf(EntityData data) {
        if (!Objects.equals(data, this.dataProperties.get(data.getId()))) {
            this.dataProperties.put(data);
            if (this.isPlayer) {
                EntityMetadata d = new EntityMetadata().put(this.dataProperties.get(data.getId()));
                SetEntityDataPacket pk = new SetEntityDataPacket();
                pk.eid = this.id;
                pk.metadata = d == null ? this.dataProperties.clone() : d;
                ((Player) this).dataPacket(pk);
            }
            return true;
        }
        return false;
    }

    public EntityMetadata getDataProperties() {
        return this.dataProperties;
    }

    public EntityData getDataProperty(int id) {
        return this.dataProperties.get(id);
    }

    public int getDataPropertyInt(int id) {
        return this.dataProperties.getInt(id);
    }

    public int getDataPropertyShort(int id) {
        return this.dataProperties.getShort(id);
    }

    public int getDataPropertyByte(int id) {
        return this.dataProperties.getByte(id);
    }

    public boolean getDataPropertyBoolean(int id) {
        return this.dataProperties.getBoolean(id);
    }

    public long getDataPropertyLong(int id) {
        return this.dataProperties.getLong(id);
    }

    public String getDataPropertyString(int id) {
        return this.dataProperties.getString(id);
    }

    public float getDataPropertyFloat(int id) {
        return this.dataProperties.getFloat(id);
    }

    public CompoundTag getDataPropertyNBT(int id) {
        return this.dataProperties.getNBT(id);
    }

    public Vector3 getDataPropertyPos(int id) {
        return this.dataProperties.getPosition(id);
    }

    public Vector3f getDataPropertyVector3f(int id) {
        return this.dataProperties.getFloatPosition(id);
    }

    public int getDataPropertyType(int id) {
        return this.dataProperties.exists(id) ? this.getDataProperty(id).getType() : -1;
    }

    public void setDataFlag(int propertyId, int id) {
        this.setDataFlag(propertyId, id, true);
    }

    public void setDataFlag(int propertyId, int id, boolean value) {
        this.setDataFlag(propertyId, id, value, true);
    }

    public void setDataFlag(int propertyId, int id, boolean value, boolean send) {
        if (this.getDataFlag(propertyId, id) != value) {
            if (propertyId == EntityHuman.DATA_PLAYER_FLAGS) {
                byte flags = (byte) this.getDataPropertyByte(propertyId);
                flags ^= 1 << id;
                this.setDataProperty(new ByteEntityData(propertyId, flags), send);
            } else {
                LongEntityData longEntityData = (LongEntityData)this.dataProperties.getOrDefault(propertyId, new LongEntityData(propertyId, 0L));
                long flags = longEntityData.getData() ^ 1L << id;
                LongEntityData newLongEntityData = new LongEntityData(propertyId, flags);
                if (propertyId == DATA_FLAGS) {
                    long data291;
                    long data223;
                    long data137;

                    int id291 = id > 46 ? id - 1 : id;
                    int id223 = id291 > 30 ? id291 - 1 : id291;
                    int id137 = (id223 >= 23 && id223 < 43) || (id223 >= 46) ? id223 - 1 : id223;

                    if (longEntityData.dataVersions != null && longEntityData.dataVersions.length == 3) {
                        data291 = longEntityData.dataVersions[2];
                        data223 = longEntityData.dataVersions[1];
                        data137 = longEntityData.dataVersions[0];
                    } else {
                        data291 = 0L;
                        data223 = 0L;
                        data137 = 0L;
                    }

                    newLongEntityData.dataVersions = new long[] {
                            data137 ^ 1L << id137,
                            data223 ^ 1L << id223,
                            data291 ^ 1L << id291
                    };
                } else if (propertyId == DATA_FLAGS_EXTENDED) {
                    int id2 = id > 46 ? id - 1 : id;
                    flags = longEntityData.dataVersions != null && longEntityData.dataVersions.length == 1 ? longEntityData.dataVersions[0] : 0L;
                    newLongEntityData.dataVersions = new long[]{flags ^ 1L << id2};
                }
                this.setDataProperty(newLongEntityData, send);
            }
        }
    }

    public void setDataFlagSelfOnly(int propertyId, int id, boolean value) {
        if (this.getDataFlag(propertyId, id) != value) {
            if (propertyId == EntityHuman.DATA_PLAYER_FLAGS) {
                byte flags = (byte) this.getDataPropertyByte(propertyId);
                flags ^= 1 << id;
                this.setDataPropertyAndSendOnlyToSelf(new ByteEntityData(propertyId, flags));
            } else {
                LongEntityData longEntityData = (LongEntityData)this.dataProperties.getOrDefault(propertyId, new LongEntityData(propertyId, 0L));
                long flags = longEntityData.getData() ^ 1L << id;
                LongEntityData newLongEntityData = new LongEntityData(propertyId, flags);
                if (propertyId == DATA_FLAGS) {
                    long data291;
                    long data223;
                    long data137;

                    int id291 = id > 46 ? id - 1 : id;
                    int id223 = id291 > 30 ? id291 - 1 : id291;
                    int id137 = id223 >= 23 && id223 < 43 || id223 >= 46 ? id223 - 1 : id223;

                    if (longEntityData.dataVersions != null && longEntityData.dataVersions.length == 3) {
                        data291 = longEntityData.dataVersions[2];
                        data223 = longEntityData.dataVersions[1];
                        data137 = longEntityData.dataVersions[0];
                    } else {
                        data291 = 0L;
                        data223 = 0L;
                        data137 = 0L;
                    }

                    newLongEntityData.dataVersions = new long[] {
                            data137 ^ 1L << id137,
                            data223 ^ 1L << id223,
                            data291 ^ 1L << id291
                    };
                } else if (propertyId == DATA_FLAGS_EXTENDED) {
                    int id2 = id > 46 ? id - 1 : id;
                    flags = longEntityData.dataVersions != null && longEntityData.dataVersions.length == 1 ? longEntityData.dataVersions[0] : 0L;
                    newLongEntityData.dataVersions = new long[]{flags ^ 1L << id2};
                }
                this.setDataPropertyAndSendOnlyToSelf(newLongEntityData);
            }
        }
    }

    public boolean getDataFlag(int propertyId, int id) {
        return (((propertyId == EntityHuman.DATA_PLAYER_FLAGS ? this.getDataPropertyByte(propertyId) & 0xff : this.getDataPropertyLong(propertyId))) & (1L << id)) > 0;
    }

    public void setGenericFlag(int propertyId, boolean value) {
        this.setDataFlag(propertyId >= 64 ? DATA_FLAGS2 : DATA_FLAGS, propertyId % 64, value);
    }

    public boolean getGenericFlag(int propertyId) {
        return this.getDataFlag(propertyId >= 64 ? DATA_FLAGS2 : DATA_FLAGS, propertyId % 64);
    }

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) {
        this.server.getEntityMetadata().setMetadata(this, metadataKey, newMetadataValue);
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) {
        return this.server.getEntityMetadata().getMetadata(this, metadataKey);
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        return this.server.getEntityMetadata().hasMetadata(this, metadataKey);
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) {
        this.server.getEntityMetadata().removeMetadata(this, metadataKey, owningPlugin);
    }

    public Server getServer() {
        return server;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Entity other = (Entity) obj;
        return this.id == other.id;
    }

    @Override
    public int hashCode() {
        return (int) (203 + this.id);
    }

    public static Entity create(Object type, Position source, Object... args) {
        FullChunk chunk = source.getLevel().getChunk((int) source.x >> 4, (int) source.z >> 4, true);
        if (!chunk.isGenerated()) {
            chunk.setGenerated();
        }
        if (!chunk.isPopulated()) {
            chunk.setPopulated();
        }

        CompoundTag nbt = new CompoundTag().putList(new ListTag<DoubleTag>("Pos").add(new DoubleTag("", source.x)).add(new DoubleTag("", source.y)).add(new DoubleTag("", source.z)))
                .putList(new ListTag<DoubleTag>("Motion").add(new DoubleTag("", 0)).add(new DoubleTag("", 0)).add(new DoubleTag("", 0)))
                .putList(new ListTag<FloatTag>("Rotation").add(new FloatTag("", source instanceof Location ? (float) ((Location) source).yaw : 0))
                        .add(new FloatTag("", source instanceof Location ? (float) ((Location) source).pitch : 0)));

        return Entity.createEntity(type.toString(), chunk, nbt, args);
    }

    public boolean isOnLadder() {
        int blockId = this.level.getBlockIdAt(chunk, this.getFloorX(), this.getFloorY(), this.getFloorZ());
        return blockId == Block.LADDER || blockId == Block.VINES || blockId == Block.COBWEB || blockId == Block.SCAFFOLDING;
    }

    /**
     * Get mounted entity y offset. Used to determine the height for heart particle spawning.
     *
     * @return entity height * 0.75
     */
    public float getMountedYOffset() {
        return getHeight() * 0.75F;
    }

    /**
     * Check whether there is blocks above the entity
     *
     * @return no blocks above
     */
    public boolean canSeeSky() {
        int px = this.getFloorX();
        int py = this.getFloorY();
        int pz = this.getFloorZ();
        for (int i = this.level.getMaxBlockY(); i >= py; i--) {
            if (level.getBlockIdAt(chunk, px, i, pz) != 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isNoClip() {
        return noClip;
    }

    public void setNoClip(boolean noClip) {
        this.noClip = noClip;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_HAS_COLLISION, noClip);
    }

    public void addTag(String tag) {
        this.namedTag.putList(this.namedTag.getList("Tags", StringTag.class).add(new StringTag("", tag)));
    }

    public void removeTag(String tag) {
        ListTag<StringTag> tags = this.namedTag.getList("Tags", StringTag.class);
        tags.remove(new StringTag("", tag));
        this.namedTag.putList(tags);
    }

    public boolean containTag(String tag) {
        return this.namedTag.getList("Tags", StringTag.class).getAll().stream().anyMatch(t -> t.data.equals(tag));
    }

    public List<StringTag> getAllTags() {
        return this.namedTag.getList("Tags", StringTag.class).getAll();
    }

    public float getFreezingEffectStrength() {
        return getDataPropertyFloat(DATA_FREEZING_EFFECT_STRENGTH);
    }

    public void setFreezingEffectStrength(float strength) {
        if (strength < 0 || strength > 1)
            throw new IllegalArgumentException("Freezing Effect Strength must be between 0 and 1");
        this.setDataProperty(new FloatEntityData(DATA_FREEZING_EFFECT_STRENGTH, strength));
    }

    public int getFreezingTicks() {
        return this.freezingTicks;
    }

    public void setFreezingTicks(int ticks) {
        if (ticks < 0) this.freezingTicks = 0;
        else if (ticks > 140) this.freezingTicks = 140;
        else this.freezingTicks = ticks;
        setFreezingEffectStrength(ticks / 140f);
    }

    public void addFreezingTicks(int increments) {
        if (freezingTicks + increments < 0) this.freezingTicks = 0;
        else if (freezingTicks + increments > 140) this.freezingTicks = 140;
        else this.freezingTicks += increments;
        setFreezingEffectStrength(this.freezingTicks / 140f);
    }

    private boolean validateAndSetIntProperty(String identifier, int value) {
        if(!intProperties.containsKey(identifier)) {
            return false;
        }
        intProperties.put(identifier, value);
        return true;
    }

    public final boolean setIntEntityProperty(String identifier, int value) {
        return validateAndSetIntProperty(identifier, value);
    }

    public final boolean setBooleanEntityProperty(String identifier, boolean value) {
        return validateAndSetIntProperty(identifier, value ? 1 : 0);
    }

    public final boolean setFloatEntityProperty(String identifier, float value) {
        if(!floatProperties.containsKey(identifier)) {
            return false;
        }
        floatProperties.put(identifier, value);
        return true;
    }

    public final boolean setEnumEntityProperty(String identifier, String value) {
        if(!intProperties.containsKey(identifier)) return false;
        List<EntityProperty> entityPropertyList = EntityProperty.getEntityProperty(this.getIdentifier().toString());

        for (EntityProperty property : entityPropertyList) {
            if(Objects.equals(property.getIdentifier(), identifier) && property instanceof EnumEntityProperty enumEntityProperty) {
                int index = enumEntityProperty.findIndex(value);

                if(index >= 0) {
                    intProperties.put(identifier, index);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public final String getEnumEntityProperty(String identifier) {
        List<EntityProperty> entityPropertyList = EntityProperty.getEntityProperty(this.getIdentifier().toString());

        for (EntityProperty property : entityPropertyList) {
            if (!identifier.equals(property.getIdentifier()) ||
                    !(property instanceof EnumEntityProperty enumProperty)) {
                continue;
            }
            return enumProperty.getEnums()[intProperties.get(identifier)];
        }
        return null;
    }

    private void initEntityProperties() {
        if(this.getIdentifier() != null) {
            initEntityProperties(this.getIdentifier().toString());
        }
    }

    private void initEntityProperties(String entityIdentifier) {
        List<EntityProperty> entityPropertyList = EntityProperty.getEntityProperty(entityIdentifier);
        if (entityPropertyList.isEmpty()) {
            return;
        }

        for (EntityProperty property : entityPropertyList) {
            final String identifier = property.getIdentifier();

            if (property instanceof FloatEntityProperty floatProperty) {
                floatProperties.put(identifier, floatProperty.getDefaultValue());
            } else if (property instanceof IntEntityProperty intProperty) {
                intProperties.put(identifier, intProperty.getDefaultValue());
            } else if (property instanceof BooleanEntityProperty booleanProperty) {
                intProperties.put(identifier, booleanProperty.getDefaultValue() ? 1 : 0);
            } else if (property instanceof EnumEntityProperty enumProperty) {
                intProperties.put(identifier, enumProperty.findIndex(enumProperty.getDefaultValue()));
            }
        }
    }

    private PropertySyncData propertySyncData() {
        Collection<Integer> intValues = intProperties.values();
        int[] intArray = new int[intValues.size()];
        int i = 0;
        for (Integer value : intValues) {
            intArray[i++] = value;
        }

        Collection<Float> floatValues = floatProperties.values();
        float[] floatArray = new float[floatValues.size()];
        i = 0;
        for (Float value : floatValues) {
            floatArray[i++] = value;
        }

        return new PropertySyncData(intArray, floatArray);
    }

    /**
     * Batch play animation on entity groups<br/>
     * This method is recommended if you need to play the same animation on a large number of entities at the same time, as it only sends packets once for each player, which greatly reduces bandwidth pressure
     * <p>
     * 在实体群上批量播放动画<br/>
     * 若你需要同时在大量实体上播放同一动画，建议使用此方法，因为此方法只会针对每个玩家发送一次包，这能极大地缓解带宽压力
     *
     * @param animation 动画对象 Animation objects
     * @param entities  需要播放动画的实体群 Group of entities that need to play animations
     * @param players   可视玩家 Visible Player
     */
    public static void playAnimationOnEntities(AnimateEntityPacket.Animation animation, Collection<Entity> entities, Collection<Player> players) {
        var pk = new AnimateEntityPacket();
        pk.parseFromAnimation(animation);
        entities.forEach(entity -> pk.getEntityRuntimeIds().add(entity.getId()));
        pk.encode();
        Server.broadcastPacket(players, pk);
    }

    /**
     * @see #playAnimationOnEntities(AnimateEntityPacket.Animation, Collection, Collection)
     */
    public static void playAnimationOnEntities(AnimateEntityPacket.Animation animation, Collection<Entity> entities) {
        var viewers = new HashSet<Player>();
        entities.forEach(entity -> {
            viewers.addAll(entity.getViewers().values());
            if (entity.isPlayer) viewers.add((Player) entity);
        });
        playAnimationOnEntities(animation, entities, viewers);
    }

    /**
     * Whether the entity can pass through barrier blocks.
     *
     * @return passes through barriers
     **/
    public boolean canPassThroughBarrier() {
        return this.passThroughBarrier;
    }

    public void setPassThroughBarrier(boolean passThroughBarrier) {
        this.passThroughBarrier = passThroughBarrier;
    }
}
