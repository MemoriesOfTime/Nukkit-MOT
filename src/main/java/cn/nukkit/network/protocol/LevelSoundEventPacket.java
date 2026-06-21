package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.mapping.LevelSoundEventMap;
import cn.nukkit.utils.Utils;
import lombok.ToString;

@ToString
public class LevelSoundEventPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.LEVEL_SOUND_EVENT_PACKET;
    private static final String SOUND_UNDEFINED_NAME = "undefined";

    public static final int SOUND_ITEM_USE_ON = 0;
    public static final int SOUND_HIT = 1;
    public static final int SOUND_STEP = 2;
    public static final int SOUND_FLY = 3;
    public static final int SOUND_JUMP = 4;
    public static final int SOUND_BREAK = 5;
    public static final int SOUND_PLACE = 6;
    public static final int SOUND_HEAVY_STEP = 7;
    public static final int SOUND_GALLOP = 8;
    public static final int SOUND_FALL = 9;
    public static final int SOUND_AMBIENT = 10;
    public static final int SOUND_AMBIENT_BABY = 11;
    public static final int SOUND_AMBIENT_IN_WATER = 12;
    public static final int SOUND_BREATHE = 13;
    public static final int SOUND_DEATH = 14;
    public static final int SOUND_DEATH_IN_WATER = 15;
    public static final int SOUND_DEATH_TO_ZOMBIE = 16;
    public static final int SOUND_HURT = 17;
    public static final int SOUND_HURT_IN_WATER = 18;
    public static final int SOUND_MAD = 19;
    public static final int SOUND_BOOST = 20;
    public static final int SOUND_BOW = 21;
    public static final int SOUND_SQUISH_BIG = 22;
    public static final int SOUND_SQUISH_SMALL = 23;
    public static final int SOUND_FALL_BIG = 24;
    public static final int SOUND_FALL_SMALL = 25;
    public static final int SOUND_SPLASH = 26;
    public static final int SOUND_FIZZ = 27;
    public static final int SOUND_FLAP = 28;
    public static final int SOUND_SWIM = 29;
    public static final int SOUND_DRINK = 30;
    public static final int SOUND_EAT = 31;
    public static final int SOUND_TAKEOFF = 32;
    public static final int SOUND_SHAKE = 33;
    public static final int SOUND_PLOP = 34;
    public static final int SOUND_LAND = 35;
    public static final int SOUND_SADDLE = 36;
    public static final int SOUND_ARMOR = 37;
    public static final int SOUND_MOB_ARMOR_STAND_PLACE = 38;
    public static final int SOUND_ADD_CHEST = 39;
    public static final int SOUND_THROW = 40;
    public static final int SOUND_ATTACK = 41;
    public static final int SOUND_ATTACK_NODAMAGE = 42;
    public static final int SOUND_ATTACK_STRONG = 43;
    public static final int SOUND_WARN = 44;
    public static final int SOUND_SHEAR = 45;
    public static final int SOUND_MILK = 46;
    public static final int SOUND_THUNDER = 47;
    public static final int SOUND_EXPLODE = 48;
    public static final int SOUND_FIRE = 49;
    public static final int SOUND_IGNITE = 50;
    public static final int SOUND_FUSE = 51;
    public static final int SOUND_STARE = 52;
    public static final int SOUND_SPAWN = 53;
    public static final int SOUND_SHOOT = 54;
    public static final int SOUND_BREAK_BLOCK = 55;
    public static final int SOUND_LAUNCH = 56;
    public static final int SOUND_BLAST = 57;
    public static final int SOUND_LARGE_BLAST = 58;
    public static final int SOUND_TWINKLE = 59;
    public static final int SOUND_REMEDY = 60;
    public static final int SOUND_UNFECT = 61;
    public static final int SOUND_LEVELUP = 62;
    public static final int SOUND_BOW_HIT = 63;
    public static final int SOUND_BULLET_HIT = 64;
    public static final int SOUND_EXTINGUISH_FIRE = 65;
    public static final int SOUND_ITEM_FIZZ = 66;
    public static final int SOUND_CHEST_OPEN = 67;
    public static final int SOUND_CHEST_CLOSED = 68;
    public static final int SOUND_SHULKERBOX_OPEN = 69;
    public static final int SOUND_SHULKERBOX_CLOSED = 70;
    public static final int SOUND_ENDERCHEST_OPEN = 71;
    public static final int SOUND_ENDERCHEST_CLOSED = 72;
    public static final int SOUND_POWER_ON = 73;
    public static final int SOUND_POWER_OFF = 74;
    public static final int SOUND_ATTACH = 75;
    public static final int SOUND_DETACH = 76;
    public static final int SOUND_DENY = 77;
    public static final int SOUND_TRIPOD = 78;
    public static final int SOUND_POP = 79;
    public static final int SOUND_DROP_SLOT = 80;
    public static final int SOUND_NOTE = 81;
    public static final int SOUND_THORNS = 82;
    public static final int SOUND_PISTON_IN = 83;
    public static final int SOUND_PISTON_OUT = 84;
    public static final int SOUND_PORTAL = 85;
    public static final int SOUND_WATER = 86;
    public static final int SOUND_LAVA_POP = 87;
    public static final int SOUND_LAVA = 88;
    public static final int SOUND_BURP = 89;
    public static final int SOUND_BUCKET_FILL_WATER = 90;
    public static final int SOUND_BUCKET_FILL_LAVA = 91;
    public static final int SOUND_BUCKET_EMPTY_WATER = 92;
    public static final int SOUND_BUCKET_EMPTY_LAVA = 93;
    public static final int SOUND_ARMOR_EQUIP_CHAIN = 94;
    public static final int SOUND_ARMOR_EQUIP_DIAMOND = 95;
    public static final int SOUND_ARMOR_EQUIP_GENERIC = 96;
    public static final int SOUND_ARMOR_EQUIP_GOLD = 97;
    public static final int SOUND_ARMOR_EQUIP_IRON = 98;
    public static final int SOUND_ARMOR_EQUIP_LEATHER = 99;
    public static final int SOUND_ARMOR_EQUIP_ELYTRA = 100;
    public static final int SOUND_RECORD_13 = 101;
    public static final int SOUND_RECORD_CAT = 102;
    public static final int SOUND_RECORD_BLOCKS = 103;
    public static final int SOUND_RECORD_CHIRP = 104;
    public static final int SOUND_RECORD_FAR = 105;
    public static final int SOUND_RECORD_MALL = 106;
    public static final int SOUND_RECORD_MELLOHI = 107;
    public static final int SOUND_RECORD_STAL = 108;
    public static final int SOUND_RECORD_STRAD = 109;
    public static final int SOUND_RECORD_WARD = 110;
    public static final int SOUND_RECORD_11 = 111;
    public static final int SOUND_RECORD_WAIT = 112;
    public static final int SOUND_STOP_RECORD = 113;
    public static final int SOUND_GUARDIAN_FLOP = 114;
    public static final int SOUND_ELDERGUARDIAN_CURSE = 115;
    public static final int SOUND_MOB_WARNING = 116;
    public static final int SOUND_MOB_WARNING_BABY = 117;
    public static final int SOUND_TELEPORT = 118;
    public static final int SOUND_SHULKER_OPEN = 119;
    public static final int SOUND_SHULKER_CLOSE = 120;
    public static final int SOUND_HAGGLE = 121;
    public static final int SOUND_HAGGLE_YES = 122;
    public static final int SOUND_HAGGLE_NO = 123;
    public static final int SOUND_HAGGLE_IDLE = 124;
    public static final int SOUND_CHORUSGROW = 125;
    public static final int SOUND_CHORUSDEATH = 126;
    public static final int SOUND_GLASS = 127;
    public static final int SOUND_POTION_BREWED = 128;
    public static final int SOUND_CAST_SPELL = 129;
    public static final int SOUND_PREPARE_ATTACK = 130;
    public static final int SOUND_PREPARE_SUMMON = 131;
    public static final int SOUND_PREPARE_WOLOLO = 132;
    public static final int SOUND_FANG = 133;
    public static final int SOUND_CHARGE = 134;
    public static final int SOUND_CAMERA_TAKE_PICTURE = 135;
    public static final int SOUND_LEASHKNOT_PLACE = 136;
    public static final int SOUND_LEASHKNOT_BREAK = 137;
    public static final int SOUND_GROWL = 138;
    public static final int SOUND_WHINE = 139;
    public static final int SOUND_PANT = 140;
    public static final int SOUND_PURR = 141;
    public static final int SOUND_PURREOW = 142;
    public static final int SOUND_DEATH_MIN_VOLUME = 143;
    public static final int SOUND_DEATH_MID_VOLUME = 144;
    public static final int SOUND_IMITATE_BLAZE = 145;
    public static final int SOUND_IMITATE_CAVE_SPIDER = 146;
    public static final int SOUND_IMITATE_CREEPER = 147;
    public static final int SOUND_IMITATE_ELDER_GUARDIAN = 148;
    public static final int SOUND_IMITATE_ENDER_DRAGON = 149;
    public static final int SOUND_IMITATE_ENDERMAN = 150;
    public static final int SOUND_IMITATE_ENDERMITE = 151;
    public static final int SOUND_IMITATE_EVOCATION_ILLAGER = 152;
    public static final int SOUND_IMITATE_GHAST = 153;
    public static final int SOUND_IMITATE_HUSK = 154;
    public static final int SOUND_IMITATE_ILLUSION_ILLAGER = 155;
    public static final int SOUND_IMITATE_MAGMA_CUBE = 156;
    public static final int SOUND_IMITATE_POLAR_BEAR = 157;
    public static final int SOUND_IMITATE_SHULKER = 158;
    public static final int SOUND_IMITATE_SILVERFISH = 159;
    public static final int SOUND_IMITATE_SKELETON = 160;
    public static final int SOUND_IMITATE_SLIME = 161;
    public static final int SOUND_IMITATE_SPIDER = 162;
    public static final int SOUND_IMITATE_STRAY = 163;
    public static final int SOUND_IMITATE_VEX = 164;
    public static final int SOUND_IMITATE_VINDICATION_ILLAGER = 165;
    public static final int SOUND_IMITATE_WITCH = 166;
    public static final int SOUND_IMITATE_WITHER = 167;
    public static final int SOUND_IMITATE_WITHER_SKELETON = 168;
    public static final int SOUND_IMITATE_WOLF = 169;
    public static final int SOUND_IMITATE_ZOMBIE = 170;
    public static final int SOUND_IMITATE_ZOMBIE_PIGMAN = 171;
    public static final int SOUND_IMITATE_ZOMBIE_VILLAGER = 172;
    public static final int SOUND_BLOCK_END_PORTAL_FRAME_FILL = 173;
    public static final int SOUND_BLOCK_END_PORTAL_SPAWN = 174;
    public static final int SOUND_RANDOM_ANVIL_USE = 175;
    public static final int SOUND_BOTTLE_DRAGONBREATH = 176;
    public static final int SOUND_PORTAL_TRAVEL = 177;
    public static final int SOUND_ITEM_TRIDENT_HIT = 178;
    public static final int SOUND_ITEM_TRIDENT_RETURN = 179;
    public static final int SOUND_ITEM_TRIDENT_RIPTIDE_1 = 180;
    public static final int SOUND_ITEM_TRIDENT_RIPTIDE_2 = 181;
    public static final int SOUND_ITEM_TRIDENT_RIPTIDE_3 = 182;
    public static final int SOUND_ITEM_TRIDENT_THROW = 183;
    public static final int SOUND_ITEM_TRIDENT_THUNDER = 184;
    public static final int SOUND_ITEM_TRIDENT_HIT_GROUND = 185;
    public static final int SOUND_DEFAULT = 186;
    public static final int SOUND_BLOCK_FLETCHING_TABLE_USE = 187;
    public static final int SOUND_ELEMCONSTRUCT_OPEN = 188;
    public static final int SOUND_ICEBOMB_HIT = 189;
    public static final int SOUND_BALLOONPOP = 190;
    public static final int SOUND_LT_REACTION_ICEBOMB = 191;
    public static final int SOUND_LT_REACTION_BLEACH = 192;
    public static final int SOUND_LT_REACTION_EPASTE = 193;
    public static final int SOUND_LT_REACTION_EPASTE2 = 194;
    public static final int SOUND_LT_REACTION_GLOW_STICK = 195;
    public static final int SOUND_LT_REACTION_GLOW_STICK_2 = 196;
    public static final int SOUND_LT_REACTION_LUMINOL = 197;
    public static final int SOUND_LT_REACTION_SALT = 198;
    public static final int SOUND_LT_REACTION_FERTILIZER = 199;
    public static final int SOUND_LT_REACTION_FIREBALL = 200;
    public static final int SOUND_LT_REACTION_MGSALT = 201;
    public static final int SOUND_LT_REACTION_MISCFIRE = 202;
    public static final int SOUND_LT_REACTION_FIRE = 203;
    public static final int SOUND_LT_REACTION_MISCEXPLOSION = 204;
    public static final int SOUND_LT_REACTION_MISCMYSTICAL = 205;
    public static final int SOUND_LT_REACTION_MISCMYSTICAL2 = 206;
    public static final int SOUND_LT_REACTION_PRODUCT = 207;
    public static final int SOUND_SPARKLER_USE = 208;
    public static final int SOUND_GLOWSTICK_USE = 209;
    public static final int SOUND_SPARKLER_ACTIVE = 210;
    public static final int SOUND_CONVERT_TO_DROWNED = 211;
    public static final int SOUND_BUCKET_FILL_FISH = 212;
    public static final int SOUND_BUCKET_EMPTY_FISH = 213;
    public static final int SOUND_BUBBLE_UP = 214;
    public static final int SOUND_BUBBLE_DOWN = 215;
    public static final int SOUND_BUBBLE_POP = 216;
    public static final int SOUND_BUBBLE_UPINSIDE = 217;
    public static final int SOUND_BUBBLE_DOWNINSIDE = 218;
    public static final int SOUND_HURT_BABY = 219;
    public static final int SOUND_DEATH_BABY = 220;
    public static final int SOUND_STEP_BABY = 221;
    public static final int SOUND_SPAWN_BABY = 222;
    public static final int SOUND_BORN = 223;
    public static final int SOUND_BLOCK_TURTLE_EGG_BREAK = 224;
    public static final int SOUND_BLOCK_TURTLE_EGG_CRACK = 225;
    public static final int SOUND_BLOCK_TURTLE_EGG_HATCH = 226;
    public static final int SOUND_LAY_EGG = 227;
    public static final int SOUND_BLOCK_TURTLE_EGG_ATTACK = 228;
    public static final int SOUND_BEACON_ACTIVATE = 229;
    public static final int SOUND_BEACON_AMBIENT = 230;
    public static final int SOUND_BEACON_DEACTIVATE = 231;
    public static final int SOUND_BEACON_POWER = 232;
    public static final int SOUND_CONDUIT_ACTIVATE = 233;
    public static final int SOUND_CONDUIT_AMBIENT = 234;
    public static final int SOUND_CONDUIT_ATTACK = 235;
    public static final int SOUND_CONDUIT_DEACTIVATE = 236;
    public static final int SOUND_CONDUIT_SHORT = 237;
    public static final int SOUND_SWOOP = 238;
    public static final int SOUND_BLOCK_BAMBOO_SAPLING_PLACE = 239;
    public static final int SOUND_PRESNEEZE = 240;
    public static final int SOUND_SNEEZE = 241;
    public static final int SOUND_AMBIENT_TAME = 242;
    public static final int SOUND_SCARED = 243;
    public static final int SOUND_BLOCK_SCAFFOLDING_CLIMB = 244;
    public static final int SOUND_CROSSBOW_LOADING_START = 245;
    public static final int SOUND_CROSSBOW_LOADING_MIDDLE = 246;
    public static final int SOUND_CROSSBOW_LOADING_END = 247;
    public static final int SOUND_CROSSBOW_SHOOT = 248;
    public static final int SOUND_CROSSBOW_QUICK_CHARGE_START = 249;
    public static final int SOUND_CROSSBOW_QUICK_CHARGE_MIDDLE = 250;
    public static final int SOUND_CROSSBOW_QUICK_CHARGE_END = 251;
    public static final int SOUND_AMBIENT_AGGRESSIVE = 252;
    public static final int SOUND_AMBIENT_WORRIED = 253;
    public static final int SOUND_CANT_BREED = 254;
    public static final int SOUND_ITEM_SHIELD_BLOCK = 255;
    public static final int SOUND_ITEM_BOOK_PUT = 256;
    public static final int SOUND_BLOCK_GRINDSTONE_USE = 257;
    public static final int SOUND_BLOCK_BELL_HIT = 258;
    public static final int SOUND_BLOCK_CAMPFIRE_CRACKLE = 259;
    public static final int SOUND_ROAR = 260;
    public static final int SOUND_STUN = 261;
    public static final int SOUND_BLOCK_SWEET_BERRY_BUSH_HURT = 262;
    public static final int SOUND_BLOCK_SWEET_BERRY_BUSH_PICK = 263;
    public static final int SOUND_BLOCK_CARTOGRAPHY_TABLE_USE = 264;
    public static final int SOUND_BLOCK_STONECUTTER_USE = 265;
    public static final int SOUND_BLOCK_COMPOSTER_EMPTY = 266;
    public static final int SOUND_BLOCK_COMPOSTER_FILL = 267;
    public static final int SOUND_BLOCK_COMPOSTER_FILL_SUCCESS = 268;
    public static final int SOUND_BLOCK_COMPOSTER_READY = 269;
    public static final int SOUND_BLOCK_BARREL_OPEN = 270;
    public static final int SOUND_BLOCK_BARREL_CLOSE = 271;
    public static final int SOUND_RAID_HORN = 272;
    public static final int SOUND_BLOCK_LOOM_USE = 273;
    public static final int SOUND_AMBIENT_IN_RAID = 274;
    public static final int SOUND_UI_CARTOGRAPHY_TABLE_TAKE_RESULT = 275;
    public static final int SOUND_UI_STONECUTTER_TAKE_RESULT = 276;
    public static final int SOUND_UI_LOOM_TAKE_RESULT = 277;
    public static final int SOUND_BLOCK_SMOKER_SMOKE = 278;
    public static final int SOUND_BLOCK_BLASTFURNACE_FIRE_CRACKLE = 279;
    public static final int SOUND_BLOCK_SMITHING_TABLE_USE = 280;
    public static final int SOUND_SCREECH = 281;
    public static final int SOUND_SLEEP = 282;
    public static final int SOUND_BLOCK_FURNACE_LIT = 283;
    public static final int SOUND_CONVERT_MOOSHROOM = 284;
    public static final int SOUND_MILK_SUSPICIOUSLY = 285;
    public static final int SOUND_CELEBRATE = 286;
    public static final int SOUND_JUMP_PREVENT = 287;
    public static final int SOUND_AMBIENT_POLLINATE = 288;
    public static final int SOUND_BLOCK_BEEHIVE_DRIP = 289;
    public static final int SOUND_BLOCK_BEEHIVE_ENTER = 290;
    public static final int SOUND_BLOCK_BEEHIVE_EXIT = 291;
    public static final int SOUND_BLOCK_BEEHIVE_WORK = 292;
    public static final int SOUND_BLOCK_BEEHIVE_SHEAR = 293;
    public static final int SOUND_DRINK_HONEY = 294;
    public static final int SOUND_AMBIENT_CAVE = 295;
    public static final int SOUND_RETREAT = 296;
    public static final int SOUND_CONVERTED_TO_ZOMBIFIED = 297;
    public static final int SOUND_ADMIRE = 298;
    public static final int SOUND_STEP_LAVA = 299;
    public static final int SOUND_TEMPT = 300;
    public static final int SOUND_PANIC = 301;
    public static final int SOUND_ANGRY = 302;
    public static final int SOUND_AMBIENT_WARPED_FOREST_MOOD = 303;
    public static final int SOUND_AMBIENT_SOULSAND_VALLEY_MOOD = 304;
    public static final int SOUND_AMBIENT_NETHER_WASTES_MOOD = 305;
    public static final int SOUND_RESPAWN_ANCHOR_BASALT_DELTAS_MOOD = 306;
    public static final int SOUND_AMBIENT_CRIMSON_FOREST_MOOD = 307;
    public static final int SOUND_RESPAWN_ANCHOR_CHARGE = 308;
    public static final int SOUND_RESPAWN_ANCHOR_DEPLETE = 309;
    public static final int SOUND_RESPAWN_ANCHOR_SET_SPAWN = 310;
    public static final int SOUND_RESPAWN_ANCHOR_AMBIENT = 311;
    public static final int SOUND_PARTICLE_SOUL_ESCAPE_QUIET = 312;
    public static final int SOUND_PARTICLE_SOUL_ESCAPE_LOUD = 313;
    public static final int SOUND_RECORD_PIGSTEP = 314;
    public static final int SOUND_LODESTONE_COMPASS_LINK_COMPASS_TO_LODESTONE = 315;
    public static final int SOUND_SMITHING_TABLE_USE = 316;
    public static final int SOUND_ARMOR_EQUIP_NETHERITE = 317;
    public static final int SOUND_AMBIENT_WARPED_FOREST_LOOP = 318;
    public static final int SOUND_AMBIENT_SOULSAND_VALLEY_LOOP = 319;
    public static final int SOUND_AMBIENT_NETHER_WASTES_LOOP = 320;
    public static final int SOUND_AMBIENT_BASALT_DELTAS_LOOP = 321;
    public static final int SOUND_AMBIENT_CRIMSON_FOREST_LOOP = 322;
    public static final int SOUND_AMBIENT_WARPED_FOREST_ADDITIONS = 323;
    public static final int SOUND_AMBIENT_SOULSAND_VALLEY_ADDITIONS = 324;
    public static final int SOUND_AMBIENT_NETHER_WASTES_ADDITIONS = 325;
    public static final int SOUND_AMBIENT_BASALT_DELTAS_ADDITIONS = 326;
    public static final int SOUND_AMBIENT_CRIMSON_FOREST_ADDITIONS = 327;
    public static final int SOUND_SCULK_SENSOR_POWER_ON = 328;
    public static final int SOUND_SCULK_SENSOR_POWER_OFF = 329;
    public static final int SOUND_BUCKET_FILL_POWDER_SNOW = 330;
    public static final int SOUND_BUCKET_EMPTY_POWDER_SNOW = 331;
    public static final int SOUND_POINTED_DRIPSTONE_CAULDRON_DRIP_WATER = 332;
    public static final int SOUND_POINTED_DRIPSTONE_CAULDRON_DRIP_LAVA = 333;
    public static final int SOUND_POINTED_DRIPSTONE_DRIP_WATER = 334;
    public static final int SOUND_POINTED_DRIPSTONE_DRIP_LAVA = 335;
    public static final int SOUND_CAVE_VINES_PICK_BERRIES = 336;
    public static final int SOUND_BIG_DRIPLEAF_TILT_DOWN = 337;
    public static final int SOUND_BIG_DRIPLEAF_TILT_UP = 338;
    public static final int SOUND_COPPER_WAX_ON = 339;
    public static final int SOUND_COPPER_WAX_OFF = 340;
    public static final int SOUND_SCRAPE = 341;
    public static final int SOUND_PLAYER_HURT_DROWN = 342;
    public static final int SOUND_PLAYER_HURT_ON_FIRE = 343;
    public static final int SOUND_PLAYER_HURT_FREEZE = 344;
    public static final int SOUND_USE_SPYGLASS = 345;
    public static final int SOUND_STOP_USING_SPYGLASS = 346;
    public static final int SOUND_AMETHYST_BLOCK_CHIME = 347;
    public static final int SOUND_AMBIENT_SCREAMER = 348;
    public static final int SOUND_HURT_SCREAMER = 349;
    public static final int SOUND_DEATH_SCREAMER = 350;
    public static final int SOUND_MILK_SCREAMER = 351;
    public static final int SOUND_JUMP_TO_BLOCK = 352;
    public static final int SOUND_PRE_RAM = 353;
    public static final int SOUND_PRE_RAM_SCREAMER = 354;
    public static final int SOUND_RAM_IMPACT = 355;
    public static final int SOUND_RAM_IMPACT_SCREAMER = 356;
    public static final int SOUND_SQUID_INK_SQUIRT = 357;
    public static final int SOUND_GLOW_SQUID_INK_SQUIRT = 358;
    public static final int SOUND_CONVERT_TO_STRAY = 359;
    public static final int SOUND_CAKE_ADD_CANDLE = 360;
    public static final int SOUND_EXTINGUISH_CANDLE = 361;
    public static final int SOUND_AMBIENT_CANDLE = 362;
    public static final int SOUND_BLOCK_CLICK = 363;
    public static final int SOUND_BLOCK_CLICK_FAIL = 364;
    public static final int SOUND_SCULK_CATALYST_BLOOM = 365;
    public static final int SOUND_SCULK_SHRIEKER_SHRIEK = 366;
    public static final int SOUND_WARDEN_NEARBY_CLOSE = 367;
    public static final int SOUND_WARDEN_NEARBY_CLOSER = 368;
    public static final int SOUND_WARDEN_NEARBY_CLOSEST = 369;
    public static final int SOUND_WARDEN_SLIGHTLY_ANGRY = 370;
    public static final int SOUND_RECORD_OTHERSIDE = 371;
    public static final int SOUND_TONGUE = 372;
    public static final int SOUND_CRACK_IRON_GOLEM = 373;
    public static final int SOUND_REPAIR_IRON_GOLEM = 374;
    public static final int SOUND_LISTENING = 375;
    public static final int SOUND_HEARTBEAT = 376;
    public static final int SOUND_HORN_BREAK = 377;
    public static final int SOUND_SCULK_PLACE = 378;
    public static final int SOUND_SCULK_SPREAD = 379;
    public static final int SOUND_SCULK_CHARGE = 380;
    public static final int SOUND_SCULK_SENSOR_PLACE = 381;
    public static final int SOUND_SCULK_SHRIEKER_PLACE = 382;
    public static final int SOUND_GOAT_CALL_0 = 383;
    public static final int SOUND_GOAT_CALL_1 = 384;
    public static final int SOUND_GOAT_CALL_2 = 385;
    public static final int SOUND_GOAT_CALL_3 = 386;
    public static final int SOUND_GOAT_CALL_4 = 387;
    public static final int SOUND_GOAT_CALL_5 = 388;
    public static final int SOUND_GOAT_CALL_6 = 389;
    public static final int SOUND_GOAT_CALL_7 = 390;
    public static final int SOUND_GOAT_CALL_8 = 391;
    public static final int SOUND_GOAT_CALL_9 = 392;
    public static final int SOUND_GOAT_HARMONY_0 = 393;
    public static final int SOUND_GOAT_HARMONY_1 = 394;
    public static final int SOUND_GOAT_HARMONY_2 = 395;
    public static final int SOUND_GOAT_HARMONY_3 = 396;
    public static final int SOUND_GOAT_HARMONY_4 = 397;
    public static final int SOUND_GOAT_HARMONY_5 = 398;
    public static final int SOUND_GOAT_HARMONY_6 = 399;
    public static final int SOUND_GOAT_HARMONY_7 = 400;
    public static final int SOUND_GOAT_HARMONY_8 = 401;
    public static final int SOUND_GOAT_HARMONY_9 = 402;
    public static final int SOUND_GOAT_MELODY_0 = 403;
    public static final int SOUND_GOAT_MELODY_1 = 404;
    public static final int SOUND_GOAT_MELODY_2 = 405;
    public static final int SOUND_GOAT_MELODY_3 = 406;
    public static final int SOUND_GOAT_MELODY_4 = 407;
    public static final int SOUND_GOAT_MELODY_5 = 408;
    public static final int SOUND_GOAT_MELODY_6 = 409;
    public static final int SOUND_GOAT_MELODY_7 = 410;
    public static final int SOUND_GOAT_MELODY_8 = 411;
    public static final int SOUND_GOAT_MELODY_9 = 412;
    public static final int SOUND_GOAT_BASS_0 = 413;
    public static final int SOUND_GOAT_BASS_1 = 414;
    public static final int SOUND_GOAT_BASS_2 = 415;
    public static final int SOUND_GOAT_BASS_3 = 416;
    public static final int SOUND_GOAT_BASS_4 = 417;
    public static final int SOUND_GOAT_BASS_5 = 418;
    public static final int SOUND_GOAT_BASS_6 = 419;
    public static final int SOUND_GOAT_BASS_7 = 420;
    public static final int SOUND_GOAT_BASS_8 = 421;
    public static final int SOUND_GOAT_BASS_9 = 422;

    public static final int SOUND_IMITATE_WARDEN = 426;
    public static final int SOUND_LISTENING_ANGRY = 427;
    public static final int SOUND_ITEM_GIVEN = 428;
    public static final int SOUND_ITEM_TAKEN = 429;
    public static final int SOUND_DISAPPEARED = 430;
    public static final int SOUND_REAPPEARED = 431;
    public static final int SOUND_MILK_DRINK = 432;
    public static final int SOUND_FROGSPAWN_HATCHED = 433;
    public static final int SOUND_LAY_SPAWN = 434;
    public static final int SOUND_FROGSPAWN_BREAK = 435;
    public static final int SOUND_SONIC_BOOM = 436;
    public static final int SOUND_SONIC_CHARGE = 437;
    public static final int SOUND_ITEM_THROWN = 438;
    public static final int SOUND_RECORD_5 = 439;
    public static final int SOUND_CONVERT_TO_FROG = 440;
    public static final int SOUND_RECORD_PLAYING = 441;
    public static final int SOUND_ENCHANTING_TABLE_USE = 442;
    public static final int SOUND_BUNDLE_DROP_CONTENTS = 445;
    public static final int SOUND_BUNDLE_INSERT = 446;
    public static final int SOUND_BUNDLE_REMOVE_ONE = 447;

    public static final int SOUND_PRESSURE_PLATE_CLICK_OFF = 448;
    public static final int SOUND_PRESSURE_PLATE_CLICK_ON = 449;
    public static final int SOUND_BUTTON_CLICK_OFF = 450;
    public static final int SOUND_BUTTON_CLICK_ON = 451;
    public static final int SOUND_DOOR_OPEN = 452;
    public static final int SOUND_DOOR_CLOSE = 453;
    public static final int SOUND_TRAPDOOR_OPEN = 454;
    public static final int SOUND_TRAPDOOR_CLOSE = 455;
    public static final int SOUND_FENCE_GATE_OPEN = 456;
    public static final int SOUND_FENCE_GATE_CLOSE = 457;
    public static final int SOUND_INSERT = 458;
    public static final int SOUND_PICKUP = 459;
    public static final int SOUND_INSERT_ENCHANTED = 460;
    public static final int SOUND_PICKUP_ENCHANTED = 461;
    public static final int SOUND_BRUSH = 462;
    public static final int SOUND_BRUSH_COMPLETED = 463;
    public static final int SOUND_SHATTER_DECORATED_POT = 464;
    public static final int SOUND_BREAK_DECORATED_POD = 465;
    public static final int SOUND_SNIFFER_EGG_CRACK = 466;
    public static final int SOUND_SNIFFER_EGG_HATCHED = 467;
    public static final int SOUND_WAXED_SIGN_INTERACT_FAIL = 468;
    public static final int SOUND_RECORD_RELIC = 469;
    public static final int SOUND_BUMP = 470;
    public static final int SOUND_PUMPKIN_CARVE = 471;
    public static final int SOUND_CONVERT_HUSK_TO_ZOMBIE = 472;
    public static final int SOUND_PIG_DEATH = 473;
    public static final int SOUND_HOGLIN_CONVERT_TO_ZOMBIE = 474;
    public static final int SOUND_AMBIENT_UNDERWATER_ENTER = 475;
    public static final int SOUND_AMBIENT_UNDERWATER_EXIT = 476;
    public static final int SOUND_BOTTLE_FILL = 477;
    public static final int SOUND_BOTTLE_EMPTY = 478;
    public static final int SOUND_CRAFTER_CRAFT = 479;
    public static final int SOUND_CRAFTER_FAILED = 480;
    public static final int SOUND_DECORATED_POT_INSERT = 481;
    public static final int SOUND_DECORATED_POT_INSERT_FAILED = 482;
    public static final int SOUND_CRAFTER_DISABLE_SLOT = 483;
    public static final int SOUND_TRIAL_SPAWNER_OPEN_SHUTTER = 484;
    public static final int SOUND_TRIAL_SPAWNER_EJECT_ITEM = 485;
    public static final int SOUND_TRIAL_SPAWNER_DETECT_PLAYER = 486;
    public static final int SOUND_TRIAL_SPAWNER_SPAWN_MOB = 487;
    public static final int SOUND_TRIAL_SPAWNER_CLOSE_SHUTTER = 488;
    public static final int SOUND_TRIAL_SPAWNER_AMBIENT = 489;
    public static final int SOUND_COPPER_BULB_ON = 490;
    public static final int SOUND_COPPER_BULB_OFF = 491;
    public static final int SOUND_AMBIENT_IN_AIR = 492;
    public static final int SOUND_WIND_BURST = 493;
    public static final int SOUND_IMITATE_BREEZE = 494;
    public static final int SOUND_ARMADILLO_BRUSH = 495;
    public static final int SOUND_ARMADILLO_SCUTE_DROP = 496;
    public static final int SOUND_EQUIP_WOLF = 497;
    public static final int SOUND_UNEQUIP_WOLF = 498;
    public static final int SOUND_REFLECT = 499;
    /**
     * @since v662
     */
    public static final int SOUND_VAULT_OPEN_SHUTTER = 500;
    /**
     * @since v662
     */
    public static final int SOUND_VAULT_CLOSE_SHUTTER = 501;
    /**
     * @since v662
     */
    public static final int SOUND_VAULT_EJECT_ITEM = 502;
    /**
     * @since v662
     */
    public static final int SOUND_VAULT_INSERT_ITEM = 503;
    /**
     * @since v662
     */
    public static final int SOUND_VAULT_INSERT_ITEM_FAIL = 504;
    /**
     * @since v662
     */
    public static final int SOUND_VAULT_AMBIENT = 505;
    /**
     * @since v662
     */
    public static final int SOUND_VAULT_ACTIVATE = 506;
    /**
     * @since v662
     */
    public static final int SOUND_VAULT_DEACTIVATE = 507;
    /**
     * @since v662
     */
    public static final int SOUND_HURT_REDUCED = 508;
    /**
     * @since v662
     */
    public static final int SOUND_WIND_CHARGE_BURST = 509;
    /**
     * @since v712
     */
    public static final int SOUND_IMITATE_BOGGED = 510;
    /**
     * @since v671
     */
    public static final int SOUND_ARMOR_CRACK_WOLF = 511;
    /**
     * @since v671
     */
    public static final int SOUND_ARMOR_BREAK_WOLF = 512;
    /**
     * @since v671
     */
    public static final int SOUND_ARMOR_REPAIR_WOLF = 513;
    /**
     * @since v671
     */
    public static final int SOUND_MACE_SMASH_AIR = 514;
    /**
     * @since v671
     */
    public static final int SOUND_MACE_SMASH_GROUND = 515;
    /**
     * @since v671
     */
    public static final int SOUND_MACE_SMASH_HEAVY_GROUND = 520;
    /**
     * @since v685
     */
    public static final int SOUND_TRAIL_SPAWNER_CHARGE_ACTIVATE = 516;
    /**
     * @since v685
     */
    public static final int SOUND_TRAIL_SPAWNER_AMBIENT_OMINOUS = 517;
    /**
     * @since v685
     */
    public static final int SOUND_OMINOUS_ITEM_SPAWNER_SPAWN_ITEM = 518;
    /**
     * @since v685
     */
    public static final int SOUND_OMINOUS_BOTTLE_END_USE = 519;
    /**
     * @since v685
     */
    public static final int SOUND_OMINOUS_ITEM_SPAWNER_SPAWN_ITEM_BEGIN = 521;
    /**
     * @since v685
     */
    public static final int SOUND_APPLY_EFFECT_BAD_OMEN = 523;
    /**
     * @since v685
     */
    public static final int SOUND_APPLY_EFFECT_RAID_OMEN = 524;
    /**
     * @since v685
     */
    public static final int SOUND_APPLY_EFFECT_TRIAL_OMEN = 525;
    /**
     * @since v685
     */
    public static final int SOUND_OMINOUS_ITEM_SPAWNER_ABOUT_TO_SPAWN_ITEM = 526;
    /**
     * @since v685
     */
    public static final int SOUND_RECORD_CREATOR = 527;
    /**
     * @since v685
     */
    public static final int SOUND_RECORD_CREATOR_MUSIC_BOX = 528;
    /**
     * @since v685
     */
    public static final int SOUND_RECORD_PRECIPICE = 529;
    /**
     * @since v712
     */
    public static final int SOUND_VAULT_REJECT_REWARDED_PLAYER = 530;
    /**
     * @since v729
     */
    public static final int SOUND_IMITATE_DROWNED = 531;
    /**
     * @since v729
     */
    public static final int SOUND_BUNDLE_INSERT_FAILED = 533;
    /**
     * @since v766
     */
    public static final int SOUND_IMITATE_CREAKING = 532;
    /**
     * @since v766
     */
    public static final int SOUND_SPONGE_ABSORB = 534;
    /**
     * @since v766
     */
    public static final int SOUND_BLOCK_CREAKING_HEART_TRAIL = 536;
    /**
     * @since v766
     */
    public static final int SOUND_CREAKING_HEART_SPAWN = 537;
    /**
     * @since v766
     */
    public static final int SOUND_ACTIVATE = 538;
    /**
     * @since v766
     */
    public static final int SOUND_DEACTIVATE = 539;
    /**
     * @since v766
     */
    public static final int SOUND_FREEZE = 540;
    /**
     * @since v766
     */
    public static final int SOUND_UNFREEZE = 541;
    /**
     * @since v766
     */
    public static final int SOUND_OPEN = 542;
    /**
     * @since v766
     */
    public static final int SOUND_OPEN_LONG = 543;
    /**
     * @since v766
     */
    public static final int SOUND_CLOSE = 544;
    /**
     * @since v766
     */
    public static final int SOUND_CLOSE_LONG = 545;
    /**
     * @since v800
     */
    public static final int SOUND_IMITATE_PHANTOM = 546;
    /**
     * @since v800
     */
    public static final int SOUND_IMITATE_ZOGLIN = 547;
    /**
     * @since v800
     */
    public static final int SOUND_IMITATE_GUARDIAN = 548;
    /**
     * @since v800
     */
    public static final int SOUND_IMITATE_RAVAGER = 549;
    /**
     * @since v800
     */
    public static final int SOUND_IMITATE_PILLAGER = 550;
    /**
     * @since v800
     */
    public static final int SOUND_PLACE_IN_WATER = 551;
    /**
     * @since v800
     */
    public static final int SOUND_STATE_CHANGE = 552;
    /**
     * @since v800
     */
    public static final int SOUND_IMITATE_HAPPY_GHAST = 553;
    /**
     * @since v800
     */
    public static final int SOUND_UNEQUIP_GENERIC = 554;
    /**
     * @since v818
     */
    public static final int SOUND_RECORD_TEARS = 555;
    /**
     * @since v818
     */
    public static final int SOUND_THE_END_LIGHT_FLASH = 556;
    /**
     * @since v818
     */
    public static final int SOUND_LEAD_LEASH = 557;
    /**
     * @since v818
     */
    public static final int SOUND_LEAD_UNLEASH = 558;
    /**
     * @since v818
     */
    public static final int SOUND_LEAD_BREAK = 559;
    /**
     * @since v818
     */
    public static final int SOUND_UNSADDLE = 560;
    /**
     * @since v827
     */
    public static final int SOUND_ARMOR_EQUIP_COPPER = 561;
    /**
     * @since v819
     */
    public static final int SOUND_RECORD_LAVA_CHICKEN = 562;
    private static final int SOUND_RECORD_LAVA_CHICKEN_1_21_93 = 561;
    /**
     * @since v844
     */
    public static final int SOUND_PLACE_ITEM = 563;
    /**
     * @since v844
     */
    public static final int SOUND_SINGLE_ITEM_SWAP = 564;
    /**
     * @since v844
     */
    public static final int SOUND_MULTI_ITEM_SWAP = 565;
    /**
     * @since v898
     */
    public static final int SOUND_LUNGE_1 = 566;
    /**
     * @since v898
     */
    public static final int SOUND_LUNGE_2 = 567;
    /**
     * @since v898
     */
    public static final int SOUND_LUNGE_3 = 568;
    /**
     * @since v898
     */
    public static final int SOUND_ATTACK_CRITICAL = 569;
    /**
     * @since v898
     */
    public static final int SOUND_SPEAR_ATTACK_HIT = 570;
    /**
     * @since v898
     */
    public static final int SOUND_SPEAR_ATTACK_MISS = 571;
    /**
     * @since v898
     */
    public static final int SOUND_WOODEN_SPEAR_ATTACK_HIT = 572;
    /**
     * @since v898
     */
    public static final int SOUND_WOODEN_SPEAR_ATTACK_MISS = 573;
    /**
     * @since v898
     */
    public static final int SOUND_IMITATE_PARCHED = 574;
    /**
     * @since v898
     */
    public static final int SOUND_IMITATE_CAMEL_HUSK = 575;
    /**
     * @since v898
     */
    public static final int SOUND_SPEAR_USE = 576;
    /**
     * @since v898
     */
    public static final int SOUND_WOODEN_SPEAR_USE = 577;
    /**
     * @since v924
     */
    public static final int SOUND_SADDLE_IN_WATER = 578;
    /**
     * @since v924
     */
    public static final int SOUND_STONE_SPEAR_ATTACK_HIT = 579;
    /**
     * @since v924
     */
    public static final int SOUND_IRON_SPEAR_ATTACK_HIT = 580;
    /**
     * @since v924
     */
    public static final int SOUND_COPPER_SPEAR_ATTACK_HIT = 581;
    /**
     * @since v924
     */
    public static final int SOUND_GOLDEN_SPEAR_ATTACK_HIT = 582;
    /**
     * @since v924
     */
    public static final int SOUND_DIAMOND_SPEAR_ATTACK_HIT = 583;
    /**
     * @since v924
     */
    public static final int SOUND_NETHERITE_SPEAR_ATTACK_HIT = 584;
    /**
     * @since v924
     */
    public static final int SOUND_STONE_SPEAR_ATTACK_MISS = 585;
    /**
     * @since v924
     */
    public static final int SOUND_IRON_SPEAR_ATTACK_MISS = 586;
    /**
     * @since v924
     */
    public static final int SOUND_COPPER_SPEAR_ATTACK_MISS = 587;
    /**
     * @since v924
     */
    public static final int SOUND_GOLDEN_SPEAR_ATTACK_MISS = 588;
    /**
     * @since v924
     */
    public static final int SOUND_DIAMOND_SPEAR_ATTACK_MISS = 589;
    /**
     * @since v924
     */
    public static final int SOUND_NETHERITE_SPEAR_ATTACK_MISS = 590;
    /**
     * @since v924
     */
    public static final int SOUND_STONE_SPEAR_USE = 591;
    /**
     * @since v924
     */
    public static final int SOUND_IRON_SPEAR_USE = 592;
    /**
     * @since v924
     */
    public static final int SOUND_COPPER_SPEAR_USE = 593;
    /**
     * @since v924
     */
    public static final int SOUND_GOLDEN_SPEAR_USE = 594;
    /**
     * @since v924
     */
    public static final int SOUND_DIAMOND_SPEAR_USE = 595;
    /**
     * @since v924
     */
    public static final int SOUND_NETHERITE_SPEAR_USE = 596;
    /**
     * @since v944
     */
    public static final int SOUND_PAUSE_GROWTH = 597;
    /**
     * @since v944
     */
    public static final int SOUND_RESET_GROWTH = 598;
    /**
     * @since v975
     */
    public static final int SOUND_PUSHED_BY_PLAYER = 599;
    /**
     * @since v975
     */
    public static final int SOUND_BOUNCE = 600;
    /**
     * @since v1001
     */
    public static final int SOUND_SLIME_LANDING = 601;
    /**
     * @since v1001
     */
    public static final int SOUND_ABSORB_BLOCK = 602;
    /**
     * @since v1001
     */
    public static final int SOUND_EJECT_BLOCK = 603;
    /**
     * @since v1001
     */
    public static final int SOUND_GEYSER_ERUPTION_START = 604;
    /**
     * @since v1001
     */
    public static final int SOUND_GEYSER_ERUPTION_ACTIVE = 605;
    /**
     * @since v1001
     */
    public static final int SOUND_RECORD_BOUNCE = 606;
    /**
     * @since v1001
     */
    public static final int SOUND_BUCKET_FILL_LAND_ANIMAL = 607;
    /**
     * @since v1001
     */
    public static final int SOUND_BUCKET_EMPTY_LAND_ANIMAL = 608;
    /**
     * @since v1001
     */
    public static final int SOUND_GEYSER_CONTINUOUS_ERUPTION_START = 609;
    /**
     * @since v1001
     */
    public static final int SOUND_GEYSER_CONTINUOUS_ERUPTION_ACTIVE = 610;

    public static final int SOUND_UNDEFINED = Utils.dynamic(611);

    public int sound;
    public float x;
    public float y;
    public float z;
    public int extraData = -1;
    public String entityIdentifier;
    public boolean isBabyMob;
    public boolean isGlobal;
    public long entityUniqueId = -1;
    /**
     * @since v975
     */
    public Vector3f fireAtPosition;

    @Override
    public void decode() {
        this.sound = this.protocol >= ProtocolInfo.v1_26_30 ? LevelSoundEventMap.getId(this.getString()) : getCanonicalSound((int) this.getUnsignedVarInt(), this.protocol);
        Vector3f v = this.getVector3f();
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
        this.extraData = this.getVarInt();
        this.entityIdentifier = this.getString();
        this.isBabyMob = this.getBoolean();
        this.isGlobal = this.getBoolean();
        if (this.protocol >= ProtocolInfo.v1_21_70_24) {
            this.entityUniqueId = this.getLLong();
        }
        if (this.protocol >= ProtocolInfo.v1_26_20_26) {
            this.fireAtPosition = this.getOptional(null, s -> s.getVector3f());
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_26_30) {
            this.putString(LevelSoundEventMap.getName(this.sound));
        } else {
            this.putUnsignedVarInt(getProtocolSound(this.sound, this.protocol));
        }
        this.putVector3f(this.x, this.y, this.z);
        if (this.sound == SOUND_NOTE && this.protocol < ProtocolInfo.v1_21_50) {
            // Pre-1.21.50 instrument ID order differs: swap 5↔6 (flute↔glockenspiel), 7↔8 (guitar↔chime)
            int instrumentId = this.extraData >> 8;
            int strength = this.extraData & 0xFF;
            instrumentId = switch (instrumentId) {
                case 5 -> 6;
                case 6 -> 5;
                case 7 -> 8;
                case 8 -> 7;
                default -> instrumentId;
            };
            this.putVarInt(instrumentId << 8 | strength);
        } else {
            this.putVarInt(this.extraData);
        }
        this.putString(this.entityIdentifier);
        this.putBoolean(this.isBabyMob);
        this.putBoolean(this.isGlobal);
        if (this.protocol >= ProtocolInfo.v1_21_70_24) {
            this.putLLong(this.entityUniqueId);
        }
        if (this.protocol >= ProtocolInfo.v1_26_20_26) {
            this.putOptionalNull(this.fireAtPosition, this::putVector3f);
        }
    }

    private static int getCanonicalSound(int sound, int protocol) {
        if (protocol >= ProtocolInfo.v1_21_93 && protocol < ProtocolInfo.v1_21_100
                && sound == SOUND_RECORD_LAVA_CHICKEN_1_21_93) {
            return SOUND_RECORD_LAVA_CHICKEN;
        }
        return sound;
    }

    private static int getProtocolSound(int sound, int protocol) {
        if (protocol >= ProtocolInfo.v1_21_93 && protocol < ProtocolInfo.v1_21_100
                && sound == SOUND_RECORD_LAVA_CHICKEN) {
            return SOUND_RECORD_LAVA_CHICKEN_1_21_93;
        }
        return sound;
    }

    public static String getSoundName(int sound) {
        return switch (sound) {
            case SOUND_ITEM_USE_ON -> "item.use.on";
            case SOUND_HIT -> "hit";
            case SOUND_STEP -> "step";
            case SOUND_FLY -> "fly";
            case SOUND_JUMP -> "jump";
            case SOUND_BREAK -> "break";
            case SOUND_PLACE -> "place";
            case SOUND_HEAVY_STEP -> "heavy.step";
            case SOUND_GALLOP -> "gallop";
            case SOUND_FALL -> "fall";
            case SOUND_AMBIENT -> "ambient";
            case SOUND_AMBIENT_BABY -> "ambient.baby";
            case SOUND_AMBIENT_IN_WATER -> "ambient.in.water";
            case SOUND_BREATHE -> "breathe";
            case SOUND_DEATH -> "death";
            case SOUND_DEATH_IN_WATER -> "death.in.water";
            case SOUND_DEATH_TO_ZOMBIE -> "death.to.zombie";
            case SOUND_HURT -> "hurt";
            case SOUND_HURT_IN_WATER -> "hurt.in.water";
            case SOUND_MAD -> "mad";
            case SOUND_BOOST -> "boost";
            case SOUND_BOW -> "bow";
            case SOUND_SQUISH_BIG -> "squish.big";
            case SOUND_SQUISH_SMALL -> "squish.small";
            case SOUND_FALL_BIG -> "fall.big";
            case SOUND_FALL_SMALL -> "fall.small";
            case SOUND_SPLASH -> "splash";
            case SOUND_FIZZ -> "fizz";
            case SOUND_FLAP -> "flap";
            case SOUND_SWIM -> "swim";
            case SOUND_DRINK -> "drink";
            case SOUND_EAT -> "eat";
            case SOUND_TAKEOFF -> "takeoff";
            case SOUND_SHAKE -> "shake";
            case SOUND_PLOP -> "plop";
            case SOUND_LAND -> "land";
            case SOUND_SADDLE -> "saddle";
            case SOUND_ARMOR -> "armor";
            case SOUND_MOB_ARMOR_STAND_PLACE -> "mob.armor_stand.place";
            case SOUND_ADD_CHEST -> "add.chest";
            case SOUND_THROW -> "throw";
            case SOUND_ATTACK -> "attack";
            case SOUND_ATTACK_NODAMAGE -> "attack.nodamage";
            case SOUND_ATTACK_STRONG -> "attack.strong";
            case SOUND_WARN -> "warn";
            case SOUND_SHEAR -> "shear";
            case SOUND_MILK -> "milk";
            case SOUND_THUNDER -> "thunder";
            case SOUND_EXPLODE -> "explode";
            case SOUND_FIRE -> "fire";
            case SOUND_IGNITE -> "ignite";
            case SOUND_FUSE -> "fuse";
            case SOUND_STARE -> "stare";
            case SOUND_SPAWN -> "spawn";
            case SOUND_SHOOT -> "shoot";
            case SOUND_BREAK_BLOCK -> "break.block";
            case SOUND_LAUNCH -> "launch";
            case SOUND_BLAST -> "blast";
            case SOUND_LARGE_BLAST -> "large.blast";
            case SOUND_TWINKLE -> "twinkle";
            case SOUND_REMEDY -> "remedy";
            case SOUND_UNFECT -> "unfect";
            case SOUND_LEVELUP -> "levelup";
            case SOUND_BOW_HIT -> "bow.hit";
            case SOUND_BULLET_HIT -> "bullet.hit";
            case SOUND_EXTINGUISH_FIRE -> "extinguish.fire";
            case SOUND_ITEM_FIZZ -> "item.fizz";
            case SOUND_CHEST_OPEN -> "chest.open";
            case SOUND_CHEST_CLOSED -> "chest.closed";
            case SOUND_SHULKERBOX_OPEN -> "shulkerbox.open";
            case SOUND_SHULKERBOX_CLOSED -> "shulkerbox.closed";
            case SOUND_ENDERCHEST_OPEN -> "enderchest.open";
            case SOUND_ENDERCHEST_CLOSED -> "enderchest.closed";
            case SOUND_POWER_ON -> "power.on";
            case SOUND_POWER_OFF -> "power.off";
            case SOUND_ATTACH -> "attach";
            case SOUND_DETACH -> "detach";
            case SOUND_DENY -> "deny";
            case SOUND_TRIPOD -> "tripod";
            case SOUND_POP -> "pop";
            case SOUND_DROP_SLOT -> "drop.slot";
            case SOUND_NOTE -> "note";
            case SOUND_THORNS -> "thorns";
            case SOUND_PISTON_IN -> "piston.in";
            case SOUND_PISTON_OUT -> "piston.out";
            case SOUND_PORTAL -> "portal";
            case SOUND_WATER -> "water";
            case SOUND_LAVA_POP -> "lava.pop";
            case SOUND_LAVA -> "lava";
            case SOUND_BURP -> "burp";
            case SOUND_BUCKET_FILL_WATER -> "bucket.fill.water";
            case SOUND_BUCKET_FILL_LAVA -> "bucket.fill.lava";
            case SOUND_BUCKET_EMPTY_WATER -> "bucket.empty.water";
            case SOUND_BUCKET_EMPTY_LAVA -> "bucket.empty.lava";
            case SOUND_ARMOR_EQUIP_CHAIN -> "armor.equip_chain";
            case SOUND_ARMOR_EQUIP_DIAMOND -> "armor.equip_diamond";
            case SOUND_ARMOR_EQUIP_GENERIC -> "armor.equip_generic";
            case SOUND_ARMOR_EQUIP_GOLD -> "armor.equip_gold";
            case SOUND_ARMOR_EQUIP_IRON -> "armor.equip_iron";
            case SOUND_ARMOR_EQUIP_LEATHER -> "armor.equip_leather";
            case SOUND_ARMOR_EQUIP_ELYTRA -> "armor.equip_elytra";
            case SOUND_RECORD_13 -> "record.13";
            case SOUND_RECORD_CAT -> "record.cat";
            case SOUND_RECORD_BLOCKS -> "record.blocks";
            case SOUND_RECORD_CHIRP -> "record.chirp";
            case SOUND_RECORD_FAR -> "record.far";
            case SOUND_RECORD_MALL -> "record.mall";
            case SOUND_RECORD_MELLOHI -> "record.mellohi";
            case SOUND_RECORD_STAL -> "record.stal";
            case SOUND_RECORD_STRAD -> "record.strad";
            case SOUND_RECORD_WARD -> "record.ward";
            case SOUND_RECORD_11 -> "record.11";
            case SOUND_RECORD_WAIT -> "record.wait";
            case SOUND_STOP_RECORD -> "record.null";
            case SOUND_GUARDIAN_FLOP -> "flop";
            case SOUND_ELDERGUARDIAN_CURSE -> "elderguardian.curse";
            case SOUND_MOB_WARNING -> "mob.warning";
            case SOUND_MOB_WARNING_BABY -> "mob.warning.baby";
            case SOUND_TELEPORT -> "teleport";
            case SOUND_SHULKER_OPEN -> "shulker.open";
            case SOUND_SHULKER_CLOSE -> "shulker.close";
            case SOUND_HAGGLE -> "haggle";
            case SOUND_HAGGLE_YES -> "haggle.yes";
            case SOUND_HAGGLE_NO -> "haggle.no";
            case SOUND_HAGGLE_IDLE -> "haggle.idle";
            case SOUND_CHORUSGROW -> "chorusgrow";
            case SOUND_CHORUSDEATH -> "chorusdeath";
            case SOUND_GLASS -> "glass";
            case SOUND_POTION_BREWED -> "potion.brewed";
            case SOUND_CAST_SPELL -> "cast.spell";
            case SOUND_PREPARE_ATTACK -> "prepare.attack";
            case SOUND_PREPARE_SUMMON -> "prepare.summon";
            case SOUND_PREPARE_WOLOLO -> "prepare.wololo";
            case SOUND_FANG -> "fang";
            case SOUND_CHARGE -> "charge";
            case SOUND_CAMERA_TAKE_PICTURE -> "camera.take_picture";
            case SOUND_LEASHKNOT_PLACE -> "leashknot.place";
            case SOUND_LEASHKNOT_BREAK -> "leashknot.break";
            case SOUND_GROWL -> "growl";
            case SOUND_WHINE -> "whine";
            case SOUND_PANT -> "pant";
            case SOUND_PURR -> "purr";
            case SOUND_PURREOW -> "purreow";
            case SOUND_DEATH_MIN_VOLUME -> "death.min.volume";
            case SOUND_DEATH_MID_VOLUME -> "death.mid.volume";
            case SOUND_IMITATE_BLAZE -> "imitate.blaze";
            case SOUND_IMITATE_CAVE_SPIDER -> "imitate.cave_spider";
            case SOUND_IMITATE_CREEPER -> "imitate.creeper";
            case SOUND_IMITATE_ELDER_GUARDIAN -> "imitate.elder_guardian";
            case SOUND_IMITATE_ENDER_DRAGON -> "imitate.ender_dragon";
            case SOUND_IMITATE_ENDERMAN -> "imitate.enderman";
            case SOUND_IMITATE_ENDERMITE -> "imitate.endermite";
            case SOUND_IMITATE_EVOCATION_ILLAGER -> "imitate.evocation_illager";
            case SOUND_IMITATE_GHAST -> "imitate.ghast";
            case SOUND_IMITATE_HUSK -> "imitate.husk";
            case SOUND_IMITATE_MAGMA_CUBE -> "imitate.magma_cube";
            case SOUND_IMITATE_POLAR_BEAR -> "imitate.polar_bear";
            case SOUND_IMITATE_SHULKER -> "imitate.shulker";
            case SOUND_IMITATE_SILVERFISH -> "imitate.silverfish";
            case SOUND_IMITATE_SKELETON -> "imitate.skeleton";
            case SOUND_IMITATE_SLIME -> "imitate.slime";
            case SOUND_IMITATE_SPIDER -> "imitate.spider";
            case SOUND_IMITATE_STRAY -> "imitate.stray";
            case SOUND_IMITATE_VEX -> "imitate.vex";
            case SOUND_IMITATE_VINDICATION_ILLAGER -> "imitate.vindication_illager";
            case SOUND_IMITATE_WITCH -> "imitate.witch";
            case SOUND_IMITATE_WITHER -> "imitate.wither";
            case SOUND_IMITATE_WITHER_SKELETON -> "imitate.wither_skeleton";
            case SOUND_IMITATE_WOLF -> "imitate.wolf";
            case SOUND_IMITATE_ZOMBIE -> "imitate.zombie";
            case SOUND_IMITATE_ZOMBIE_PIGMAN -> "imitate.zombie_pigman";
            case SOUND_IMITATE_ZOMBIE_VILLAGER -> "imitate.zombie_villager";
            case SOUND_BLOCK_END_PORTAL_FRAME_FILL -> "block.end_portal_frame.fill";
            case SOUND_BLOCK_END_PORTAL_SPAWN -> "block.end_portal.spawn";
            case SOUND_RANDOM_ANVIL_USE -> "random.anvil_use";
            case SOUND_BOTTLE_DRAGONBREATH -> "bottle.dragonbreath";
            case SOUND_PORTAL_TRAVEL -> "portal.travel";
            case SOUND_ITEM_TRIDENT_HIT -> "item.trident.hit";
            case SOUND_ITEM_TRIDENT_RETURN -> "item.trident.return";
            case SOUND_ITEM_TRIDENT_RIPTIDE_1 -> "item.trident.riptide_1";
            case SOUND_ITEM_TRIDENT_RIPTIDE_2 -> "item.trident.riptide_2";
            case SOUND_ITEM_TRIDENT_RIPTIDE_3 -> "item.trident.riptide_3";
            case SOUND_ITEM_TRIDENT_THROW -> "item.trident.throw";
            case SOUND_ITEM_TRIDENT_THUNDER -> "item.trident.thunder";
            case SOUND_ITEM_TRIDENT_HIT_GROUND -> "item.trident.hit_ground";
            case SOUND_DEFAULT -> "default";
            case SOUND_BLOCK_FLETCHING_TABLE_USE -> "block.fletching_table.use";
            case SOUND_ELEMCONSTRUCT_OPEN -> "elemconstruct.open";
            case SOUND_ICEBOMB_HIT -> "icebomb.hit";
            case SOUND_BALLOONPOP -> "balloonpop";
            case SOUND_LT_REACTION_ICEBOMB -> "lt.reaction.icebomb";
            case SOUND_LT_REACTION_BLEACH -> "lt.reaction.bleach";
            case SOUND_LT_REACTION_EPASTE -> "lt.reaction.epaste";
            case SOUND_LT_REACTION_EPASTE2 -> "lt.reaction.epaste2";
            case SOUND_LT_REACTION_FERTILIZER -> "lt.reaction.fertilizer";
            case SOUND_LT_REACTION_FIREBALL -> "lt.reaction.fireball";
            case SOUND_LT_REACTION_MGSALT -> "lt.reaction.mgsalt";
            case SOUND_LT_REACTION_MISCFIRE -> "lt.reaction.miscfire";
            case SOUND_LT_REACTION_FIRE -> "lt.reaction.fire";
            case SOUND_LT_REACTION_MISCEXPLOSION -> "lt.reaction.miscexplosion";
            case SOUND_LT_REACTION_MISCMYSTICAL -> "lt.reaction.miscmystical";
            case SOUND_LT_REACTION_MISCMYSTICAL2 -> "lt.reaction.miscmystical2";
            case SOUND_LT_REACTION_PRODUCT -> "lt.reaction.product";
            case SOUND_SPARKLER_USE -> "sparkler.use";
            case SOUND_GLOWSTICK_USE -> "glowstick.use";
            case SOUND_SPARKLER_ACTIVE -> "sparkler.active";
            case SOUND_CONVERT_TO_DROWNED -> "convert_to_drowned";
            case SOUND_BUCKET_FILL_FISH -> "bucket.fill.fish";
            case SOUND_BUCKET_EMPTY_FISH -> "bucket.empty.fish";
            case SOUND_BUBBLE_UP -> "bubble.up";
            case SOUND_BUBBLE_DOWN -> "bubble.down";
            case SOUND_BUBBLE_POP -> "bubble.pop";
            case SOUND_BUBBLE_UPINSIDE -> "bubble.upinside";
            case SOUND_BUBBLE_DOWNINSIDE -> "bubble.downinside";
            case SOUND_HURT_BABY -> "hurt.baby";
            case SOUND_DEATH_BABY -> "death.baby";
            case SOUND_STEP_BABY -> "step.baby";
            case SOUND_BORN -> "born";
            case SOUND_BLOCK_TURTLE_EGG_BREAK -> "block.turtle_egg.break";
            case SOUND_BLOCK_TURTLE_EGG_CRACK -> "block.turtle_egg.crack";
            case SOUND_BLOCK_TURTLE_EGG_HATCH -> "block.turtle_egg.hatch";
            case SOUND_LAY_EGG -> "lay_egg";
            case SOUND_BLOCK_TURTLE_EGG_ATTACK -> "block.turtle_egg.attack";
            case SOUND_BEACON_ACTIVATE -> "beacon.activate";
            case SOUND_BEACON_AMBIENT -> "beacon.ambient";
            case SOUND_BEACON_DEACTIVATE -> "beacon.deactivate";
            case SOUND_BEACON_POWER -> "beacon.power";
            case SOUND_CONDUIT_ACTIVATE -> "conduit.activate";
            case SOUND_CONDUIT_AMBIENT -> "conduit.ambient";
            case SOUND_CONDUIT_ATTACK -> "conduit.attack";
            case SOUND_CONDUIT_DEACTIVATE -> "conduit.deactivate";
            case SOUND_CONDUIT_SHORT -> "conduit.short";
            case SOUND_SWOOP -> "swoop";
            case SOUND_BLOCK_BAMBOO_SAPLING_PLACE -> "block.bamboo_sapling.place";
            case SOUND_PRESNEEZE -> "presneeze";
            case SOUND_SNEEZE -> "sneeze";
            case SOUND_AMBIENT_TAME -> "ambient.tame";
            case SOUND_SCARED -> "scared";
            case SOUND_BLOCK_SCAFFOLDING_CLIMB -> "block.scaffolding.climb";
            case SOUND_CROSSBOW_LOADING_START -> "crossbow.loading.start";
            case SOUND_CROSSBOW_LOADING_MIDDLE -> "crossbow.loading.middle";
            case SOUND_CROSSBOW_LOADING_END -> "crossbow.loading.end";
            case SOUND_CROSSBOW_SHOOT -> "crossbow.shoot";
            case SOUND_CROSSBOW_QUICK_CHARGE_START -> "crossbow.quick_charge.start";
            case SOUND_CROSSBOW_QUICK_CHARGE_MIDDLE -> "crossbow.quick_charge.middle";
            case SOUND_CROSSBOW_QUICK_CHARGE_END -> "crossbow.quick_charge.end";
            case SOUND_AMBIENT_AGGRESSIVE -> "ambient.aggressive";
            case SOUND_AMBIENT_WORRIED -> "ambient.worried";
            case SOUND_CANT_BREED -> "cant_breed";
            case SOUND_ITEM_SHIELD_BLOCK -> "item.shield.block";
            case SOUND_ITEM_BOOK_PUT -> "item.book.put";
            case SOUND_BLOCK_GRINDSTONE_USE -> "block.grindstone.use";
            case SOUND_BLOCK_BELL_HIT -> "block.bell.hit";
            case SOUND_BLOCK_CAMPFIRE_CRACKLE -> "block.campfire.crackle";
            case SOUND_ROAR -> "roar";
            case SOUND_STUN -> "stun";
            case SOUND_BLOCK_SWEET_BERRY_BUSH_HURT -> "block.sweet_berry_bush.hurt";
            case SOUND_BLOCK_SWEET_BERRY_BUSH_PICK -> "block.sweet_berry_bush.pick";
            case SOUND_BLOCK_CARTOGRAPHY_TABLE_USE -> "block.cartography_table.use";
            case SOUND_BLOCK_STONECUTTER_USE -> "block.stonecutter.use";
            case SOUND_BLOCK_COMPOSTER_EMPTY -> "block.composter.empty";
            case SOUND_BLOCK_COMPOSTER_FILL -> "block.composter.fill";
            case SOUND_BLOCK_COMPOSTER_FILL_SUCCESS -> "block.composter.fill_success";
            case SOUND_BLOCK_COMPOSTER_READY -> "block.composter.ready";
            case SOUND_BLOCK_BARREL_OPEN -> "block.barrel.open";
            case SOUND_BLOCK_BARREL_CLOSE -> "block.barrel.close";
            case SOUND_RAID_HORN -> "raid.horn";
            case SOUND_BLOCK_LOOM_USE -> "block.loom.use";
            case SOUND_AMBIENT_IN_RAID -> "ambient.in.raid";
            case SOUND_UI_CARTOGRAPHY_TABLE_TAKE_RESULT -> "ui.cartography_table.take_result";
            case SOUND_UI_STONECUTTER_TAKE_RESULT -> "ui.stonecutter.take_result";
            case SOUND_UI_LOOM_TAKE_RESULT -> "ui.loom.take_result";
            case SOUND_BLOCK_SMOKER_SMOKE -> "block.smoker.smoke";
            case SOUND_BLOCK_BLASTFURNACE_FIRE_CRACKLE -> "block.blastfurnace.fire_crackle";
            case SOUND_BLOCK_SMITHING_TABLE_USE -> "block.smithing_table.use";
            case SOUND_SCREECH -> "screech";
            case SOUND_SLEEP -> "sleep";
            case SOUND_BLOCK_FURNACE_LIT -> "block.furnace.lit";
            case SOUND_CONVERT_MOOSHROOM -> "convert_mooshroom";
            case SOUND_MILK_SUSPICIOUSLY -> "milk_suspiciously";
            case SOUND_CELEBRATE -> "celebrate";
            case SOUND_JUMP_PREVENT -> "jump.prevent";
            case SOUND_AMBIENT_POLLINATE -> "ambient.pollinate";
            case SOUND_BLOCK_BEEHIVE_DRIP -> "block.beehive.drip";
            case SOUND_BLOCK_BEEHIVE_ENTER -> "block.beehive.enter";
            case SOUND_BLOCK_BEEHIVE_EXIT -> "block.beehive.exit";
            case SOUND_BLOCK_BEEHIVE_WORK -> "block.beehive.work";
            case SOUND_BLOCK_BEEHIVE_SHEAR -> "block.beehive.shear";
            case SOUND_DRINK_HONEY -> "drink.honey";
            case SOUND_AMBIENT_CAVE -> "ambient.cave";
            case SOUND_RETREAT -> "retreat";
            case SOUND_CONVERTED_TO_ZOMBIFIED -> "converted_to_zombified";
            case SOUND_ADMIRE -> "admire";
            case SOUND_STEP_LAVA -> "step_lava";
            case SOUND_TEMPT -> "tempt";
            case SOUND_PANIC -> "panic";
            case SOUND_ANGRY -> "angry";
            case SOUND_AMBIENT_WARPED_FOREST_MOOD -> "ambient.warped_forest.mood";
            case SOUND_AMBIENT_SOULSAND_VALLEY_MOOD -> "ambient.soulsand_valley.mood";
            case SOUND_AMBIENT_NETHER_WASTES_MOOD -> "ambient.nether_wastes.mood";
            case SOUND_RESPAWN_ANCHOR_BASALT_DELTAS_MOOD -> "ambient.basalt_deltas.mood";
            case SOUND_AMBIENT_CRIMSON_FOREST_MOOD -> "ambient.crimson_forest.mood";
            case SOUND_RESPAWN_ANCHOR_CHARGE -> "respawn_anchor.charge";
            case SOUND_RESPAWN_ANCHOR_DEPLETE -> "respawn_anchor.deplete";
            case SOUND_RESPAWN_ANCHOR_SET_SPAWN -> "respawn_anchor.set_spawn";
            case SOUND_RESPAWN_ANCHOR_AMBIENT -> "respawn_anchor.ambient";
            case SOUND_PARTICLE_SOUL_ESCAPE_QUIET -> "particle.soul_escape.quiet";
            case SOUND_PARTICLE_SOUL_ESCAPE_LOUD -> "particle.soul_escape.loud";
            case SOUND_RECORD_PIGSTEP -> "record.pigstep";
            case SOUND_LODESTONE_COMPASS_LINK_COMPASS_TO_LODESTONE -> "lodestone_compass.link_compass_to_lodestone";
            case SOUND_SMITHING_TABLE_USE -> "smithing_table.use";
            case SOUND_ARMOR_EQUIP_NETHERITE -> "armor.equip_netherite";
            case SOUND_AMBIENT_WARPED_FOREST_LOOP -> "ambient.warped_forest.loop";
            case SOUND_AMBIENT_SOULSAND_VALLEY_LOOP -> "ambient.soulsand_valley.loop";
            case SOUND_AMBIENT_NETHER_WASTES_LOOP -> "ambient.nether_wastes.loop";
            case SOUND_AMBIENT_BASALT_DELTAS_LOOP -> "ambient.basalt_deltas.loop";
            case SOUND_AMBIENT_CRIMSON_FOREST_LOOP -> "ambient.crimson_forest.loop";
            case SOUND_AMBIENT_WARPED_FOREST_ADDITIONS -> "ambient.warped_forest.additions";
            case SOUND_AMBIENT_SOULSAND_VALLEY_ADDITIONS -> "ambient.soulsand_valley.additions";
            case SOUND_AMBIENT_NETHER_WASTES_ADDITIONS -> "ambient.nether_wastes.additions";
            case SOUND_AMBIENT_BASALT_DELTAS_ADDITIONS -> "ambient.basalt_deltas.additions";
            case SOUND_AMBIENT_CRIMSON_FOREST_ADDITIONS -> "ambient.crimson_forest.additions";
            case SOUND_SCULK_SENSOR_POWER_ON -> "power.on.sculk_sensor";
            case SOUND_SCULK_SENSOR_POWER_OFF -> "power.off.sculk_sensor";
            case SOUND_BUCKET_FILL_POWDER_SNOW -> "bucket.fill.powder_snow";
            case SOUND_BUCKET_EMPTY_POWDER_SNOW -> "bucket.empty.powder_snow";
            case SOUND_POINTED_DRIPSTONE_CAULDRON_DRIP_WATER -> "cauldron_drip.lava.pointed_dripstone";
            case SOUND_POINTED_DRIPSTONE_CAULDRON_DRIP_LAVA -> "cauldron_drip.water.pointed_dripstone";
            case SOUND_POINTED_DRIPSTONE_DRIP_WATER -> "drip.lava.pointed_dripstone";
            case SOUND_POINTED_DRIPSTONE_DRIP_LAVA -> "drip.water.pointed_dripstone";
            case SOUND_CAVE_VINES_PICK_BERRIES -> "pick_berries.cave_vines";
            case SOUND_BIG_DRIPLEAF_TILT_DOWN -> "tilt_down.big_dripleaf";
            case SOUND_BIG_DRIPLEAF_TILT_UP -> "tilt_up.big_dripleaf";
            case SOUND_COPPER_WAX_ON -> "copper.wax.on";
            case SOUND_COPPER_WAX_OFF -> "copper.wax.off";
            case SOUND_SCRAPE -> "scrape";
            case SOUND_PLAYER_HURT_DROWN -> "mob.player.hurt_drown";
            case SOUND_PLAYER_HURT_ON_FIRE -> "mob.player.hurt_on_fire";
            case SOUND_PLAYER_HURT_FREEZE -> "mob.player.hurt_freeze";
            case SOUND_USE_SPYGLASS -> "item.spyglass.use";
            case SOUND_STOP_USING_SPYGLASS -> "item.spyglass.stop_using";
            case SOUND_AMETHYST_BLOCK_CHIME -> "chime.amethyst_block";
            case SOUND_AMBIENT_SCREAMER -> "ambient.screamer";
            case SOUND_HURT_SCREAMER -> "hurt.screamer";
            case SOUND_DEATH_SCREAMER -> "death.screamer";
            case SOUND_MILK_SCREAMER -> "milk.screamer";
            case SOUND_JUMP_TO_BLOCK -> "jump_to_block";
            case SOUND_PRE_RAM -> "pre_ram";
            case SOUND_PRE_RAM_SCREAMER -> "pre_ram.screamer";
            case SOUND_RAM_IMPACT -> "ram_impact";
            case SOUND_RAM_IMPACT_SCREAMER -> "ram_impact.screamer";
            case SOUND_SQUID_INK_SQUIRT -> "squid.ink_squirt";
            case SOUND_GLOW_SQUID_INK_SQUIRT -> "glow_squid.ink_squirt";
            case SOUND_CONVERT_TO_STRAY -> "convert_to_stray";
            case SOUND_CAKE_ADD_CANDLE -> "cake.add_candle";
            case SOUND_EXTINGUISH_CANDLE -> "extinguish.candle";
            case SOUND_AMBIENT_CANDLE -> "ambient.candle";
            case SOUND_BLOCK_CLICK -> "block.click";
            case SOUND_BLOCK_CLICK_FAIL -> "block.click.fail";
            case SOUND_SCULK_CATALYST_BLOOM -> "block.sculk_catalyst.bloom";
            case SOUND_SCULK_SHRIEKER_SHRIEK -> "block.sculk_shrieker.shriek";
            case SOUND_WARDEN_NEARBY_CLOSE -> "nearby_close";
            case SOUND_WARDEN_NEARBY_CLOSER -> "nearby_closer";
            case SOUND_WARDEN_NEARBY_CLOSEST -> "nearby_closest";
            case SOUND_WARDEN_SLIGHTLY_ANGRY -> "agitated";
            case SOUND_RECORD_OTHERSIDE -> "record.otherside";
            case SOUND_TONGUE -> "tongue";
            case SOUND_CRACK_IRON_GOLEM -> "irongolem.crack";
            case SOUND_REPAIR_IRON_GOLEM -> "irongolem.repair";
            case SOUND_LISTENING -> "listening";
            case SOUND_HEARTBEAT -> "heartbeat";
            case SOUND_HORN_BREAK -> "horn_break";
            case SOUND_SCULK_SPREAD -> "block.sculk.spread";
            case SOUND_SCULK_CHARGE -> "charge.sculk";
            case SOUND_SCULK_SENSOR_PLACE -> "block.sculk_sensor.place";
            case SOUND_SCULK_SHRIEKER_PLACE -> "block.sculk_shrieker.place";
            case SOUND_GOAT_CALL_0 -> "horn_call0";
            case SOUND_GOAT_CALL_1 -> "horn_call1";
            case SOUND_GOAT_CALL_2 -> "horn_call2";
            case SOUND_GOAT_CALL_3 -> "horn_call3";
            case SOUND_GOAT_CALL_4 -> "horn_call4";
            case SOUND_GOAT_CALL_5 -> "horn_call5";
            case SOUND_GOAT_CALL_6 -> "horn_call6";
            case SOUND_GOAT_CALL_7 -> "horn_call7";
            case SOUND_IMITATE_WARDEN -> "imitate.warden";
            case SOUND_LISTENING_ANGRY -> "listening_angry";
            case SOUND_ITEM_GIVEN -> "item_given";
            case SOUND_ITEM_TAKEN -> "item_taken";
            case SOUND_DISAPPEARED -> "disappeared";
            case SOUND_REAPPEARED -> "reappeared";
            case SOUND_MILK_DRINK -> "drink.milk";
            case SOUND_FROGSPAWN_HATCHED -> "block.frog_spawn.hatch";
            case SOUND_LAY_SPAWN -> "lay_spawn";
            case SOUND_FROGSPAWN_BREAK -> "block.frog_spawn.break";
            case SOUND_SONIC_BOOM -> "sonic_boom";
            case SOUND_SONIC_CHARGE -> "sonic_charge";
            case SOUND_ITEM_THROWN -> "item_thrown";
            case SOUND_RECORD_5 -> "record.5";
            case SOUND_CONVERT_TO_FROG -> "convert_to_frog";
            case SOUND_ENCHANTING_TABLE_USE -> "block.enchanting_table.use";
            case SOUND_BUNDLE_DROP_CONTENTS -> "bundle.drop_contents";
            case SOUND_BUNDLE_INSERT -> "bundle.insert";
            case SOUND_BUNDLE_REMOVE_ONE -> "bundle.remove_one";
            case SOUND_PRESSURE_PLATE_CLICK_OFF -> "pressure_plate.click_off";
            case SOUND_PRESSURE_PLATE_CLICK_ON -> "pressure_plate.click_on";
            case SOUND_BUTTON_CLICK_OFF -> "button.click_off";
            case SOUND_BUTTON_CLICK_ON -> "button.click_on";
            case SOUND_DOOR_OPEN -> "door.open";
            case SOUND_DOOR_CLOSE -> "door.close";
            case SOUND_TRAPDOOR_OPEN -> "trapdoor.open";
            case SOUND_TRAPDOOR_CLOSE -> "trapdoor.close";
            case SOUND_FENCE_GATE_OPEN -> "fence_gate.open";
            case SOUND_FENCE_GATE_CLOSE -> "fence_gate.close";
            case SOUND_INSERT -> "insert";
            case SOUND_PICKUP -> "pickup";
            case SOUND_INSERT_ENCHANTED -> "insert_enchanted";
            case SOUND_PICKUP_ENCHANTED -> "pickup_enchanted";
            case SOUND_BRUSH -> "brush";
            case SOUND_BRUSH_COMPLETED -> "brush_completed";
            case SOUND_SHATTER_DECORATED_POT -> "shatter_pot";
            case SOUND_BREAK_DECORATED_POD -> "break_pot";
            case SOUND_SNIFFER_EGG_CRACK -> "block.sniffer_egg.crack";
            case SOUND_SNIFFER_EGG_HATCHED -> "block.sniffer_egg.hatch";
            case SOUND_WAXED_SIGN_INTERACT_FAIL -> "block.sign.waxed_interact_fail";
            case SOUND_RECORD_RELIC -> "record.relic";
            case SOUND_BUMP -> "note.bass";
            case SOUND_PUMPKIN_CARVE -> "pumpkin.carve";
            case SOUND_CONVERT_HUSK_TO_ZOMBIE -> "mob.husk.convert_to_zombie";
            case SOUND_PIG_DEATH -> "mob.pig.death";
            case SOUND_HOGLIN_CONVERT_TO_ZOMBIE -> "mob.hoglin.converted_to_zombified";
            case SOUND_AMBIENT_UNDERWATER_ENTER -> "ambient.underwater.enter";
            case SOUND_AMBIENT_UNDERWATER_EXIT -> "ambient.underwater.exit";
            case SOUND_BOTTLE_FILL -> "bottle.fill";
            case SOUND_BOTTLE_EMPTY -> "bottle.empty";
            case SOUND_CRAFTER_CRAFT -> "crafter.craft";
            case SOUND_CRAFTER_FAILED -> "crafter.fail";
            case SOUND_DECORATED_POT_INSERT -> "block.decorated_pot.insert";
            case SOUND_DECORATED_POT_INSERT_FAILED -> "block.decorated_pot.insert_fail";
            case SOUND_CRAFTER_DISABLE_SLOT -> "crafter.disable_slot";
            case SOUND_TRIAL_SPAWNER_OPEN_SHUTTER -> "trial_spawner.open_shutter";
            case SOUND_TRIAL_SPAWNER_EJECT_ITEM -> "trial_spawner.eject_item";
            case SOUND_TRIAL_SPAWNER_DETECT_PLAYER -> "trial_spawner.detect_player";
            case SOUND_TRIAL_SPAWNER_SPAWN_MOB -> "trial_spawner.spawn_mob";
            case SOUND_TRIAL_SPAWNER_CLOSE_SHUTTER -> "trial_spawner.close_shutter";
            case SOUND_TRIAL_SPAWNER_AMBIENT -> "trial_spawner.ambient";
            case SOUND_COPPER_BULB_ON -> "block.copper_bulb.turn_on";
            case SOUND_COPPER_BULB_OFF -> "block.copper_bulb.turn_off";
            case SOUND_AMBIENT_IN_AIR -> "ambient.in.air";
            case SOUND_WIND_BURST -> "breeze_wind_charge.burst";
            case SOUND_IMITATE_BREEZE -> "imitate.breeze";
            case SOUND_ARMADILLO_BRUSH -> "mob.armadillo.brush";
            case SOUND_ARMADILLO_SCUTE_DROP -> "mob.armadillo.scute_drop";
            case SOUND_EQUIP_WOLF -> "armor.equip_wolf";
            case SOUND_UNEQUIP_WOLF -> "armor.unequip_wolf";
            case SOUND_REFLECT -> "reflect";
            case SOUND_VAULT_OPEN_SHUTTER -> "vault.open_shutter";
            case SOUND_VAULT_CLOSE_SHUTTER -> "vault.close_shutter";
            case SOUND_VAULT_EJECT_ITEM -> "vault.eject_item";
            case SOUND_VAULT_INSERT_ITEM -> "vault.insert_item";
            case SOUND_VAULT_INSERT_ITEM_FAIL -> "vault.insert_item_fail";
            case SOUND_VAULT_AMBIENT -> "vault.ambient";
            case SOUND_VAULT_ACTIVATE -> "vault.activate";
            case SOUND_VAULT_DEACTIVATE -> "vault.deactivate";
            case SOUND_HURT_REDUCED -> "hurt.reduced";
            case SOUND_WIND_CHARGE_BURST -> "wind_charge.burst";
            case SOUND_IMITATE_BOGGED -> "imitate.bogged";
            case SOUND_ARMOR_CRACK_WOLF -> "armor.crack_wolf";
            case SOUND_ARMOR_BREAK_WOLF -> "armor.break_wolf";
            case SOUND_ARMOR_REPAIR_WOLF -> "armor.repair_wolf";
            case SOUND_MACE_SMASH_AIR -> "mace.smash_air";
            case SOUND_MACE_SMASH_GROUND -> "mace.smash_ground";
            case SOUND_TRAIL_SPAWNER_CHARGE_ACTIVATE -> "trial_spawner.charge_activate";
            case SOUND_TRAIL_SPAWNER_AMBIENT_OMINOUS -> "trial_spawner.ambient_ominous";
            case SOUND_OMINOUS_ITEM_SPAWNER_SPAWN_ITEM -> "ominous_item_spawner.spawn_item";
            case SOUND_OMINOUS_BOTTLE_END_USE -> "ominous_bottle.end_use";
            case SOUND_MACE_SMASH_HEAVY_GROUND -> "mace.heavy_smash_ground";
            case SOUND_OMINOUS_ITEM_SPAWNER_SPAWN_ITEM_BEGIN -> "ominous_item_spawner.spawn_item_begin";
            case SOUND_APPLY_EFFECT_BAD_OMEN -> "apply_effect.bad_omen";
            case SOUND_APPLY_EFFECT_RAID_OMEN -> "apply_effect.raid_omen";
            case SOUND_APPLY_EFFECT_TRIAL_OMEN -> "apply_effect.trial_omen";
            case SOUND_OMINOUS_ITEM_SPAWNER_ABOUT_TO_SPAWN_ITEM -> "ominous_item_spawner.about_to_spawn_item";
            case SOUND_RECORD_CREATOR -> "record.creator";
            case SOUND_RECORD_CREATOR_MUSIC_BOX -> "record.creator_music_box";
            case SOUND_RECORD_PRECIPICE -> "record.precipice";
            case SOUND_VAULT_REJECT_REWARDED_PLAYER -> "vault.reject_rewarded_player";
            case SOUND_IMITATE_DROWNED -> "imitate.drowned";
            case SOUND_IMITATE_CREAKING -> "imitate.creaking";
            case SOUND_BUNDLE_INSERT_FAILED -> "bundle.insert_fail";
            case SOUND_SPONGE_ABSORB -> "sponge.absorb";
            case SOUND_BLOCK_CREAKING_HEART_TRAIL -> "block.creaking_heart.trail";
            case SOUND_CREAKING_HEART_SPAWN -> "creaking_heart_spawn";
            case SOUND_ACTIVATE -> "activate";
            case SOUND_DEACTIVATE -> "deactivate";
            case SOUND_FREEZE -> "freeze";
            case SOUND_UNFREEZE -> "unfreeze";
            case SOUND_OPEN -> "open";
            case SOUND_OPEN_LONG -> "open_long";
            case SOUND_CLOSE -> "close";
            case SOUND_CLOSE_LONG -> "close_long";
            case SOUND_IMITATE_PHANTOM -> "imitate.phantom";
            case SOUND_IMITATE_ZOGLIN -> "imitate.zoglin";
            case SOUND_IMITATE_GUARDIAN -> "imitate.guardian";
            case SOUND_IMITATE_RAVAGER -> "imitate.ravager";
            case SOUND_IMITATE_PILLAGER -> "imitate.pillager";
            case SOUND_PLACE_IN_WATER -> "place_in_water";
            case SOUND_STATE_CHANGE -> "state_change";
            case SOUND_IMITATE_HAPPY_GHAST -> "imitate.happy_ghast";
            case SOUND_UNEQUIP_GENERIC -> "armor.unequip_generic";
            case SOUND_RECORD_TEARS -> "record.tears";
            case SOUND_THE_END_LIGHT_FLASH -> "ambient.weather.the_end_light_flash";
            case SOUND_LEAD_LEASH -> "lead.leash";
            case SOUND_LEAD_UNLEASH -> "lead.unleash";
            case SOUND_LEAD_BREAK -> "lead.break";
            case SOUND_UNSADDLE -> "unsaddle";
            case SOUND_ARMOR_EQUIP_COPPER -> "armor.equip_copper";
            case SOUND_RECORD_LAVA_CHICKEN -> "record.lava_chicken";
            case SOUND_PLACE_ITEM -> "place_item";
            case SOUND_SINGLE_ITEM_SWAP -> "single_swap";
            case SOUND_MULTI_ITEM_SWAP -> "multi_swap";
            case SOUND_LUNGE_1 -> "item.enchant.lunge1";
            case SOUND_LUNGE_2 -> "item.enchant.lunge2";
            case SOUND_LUNGE_3 -> "item.enchant.lunge3";
            case SOUND_ATTACK_CRITICAL -> "attack.critical";
            case SOUND_SPEAR_ATTACK_HIT -> "item.spear.attack_hit";
            case SOUND_SPEAR_ATTACK_MISS -> "item.spear.attack_miss";
            case SOUND_WOODEN_SPEAR_ATTACK_HIT -> "item.wooden_spear.attack_hit";
            case SOUND_WOODEN_SPEAR_ATTACK_MISS -> "item.wooden_spear.attack_miss";
            case SOUND_IMITATE_PARCHED -> "imitate.parched";
            case SOUND_IMITATE_CAMEL_HUSK -> "imitate.camel_husk";
            case SOUND_SPEAR_USE -> "item.spear.use";
            case SOUND_WOODEN_SPEAR_USE -> "item.wooden_spear.use";
            case SOUND_SADDLE_IN_WATER -> "saddle_in_water";
            case SOUND_STONE_SPEAR_ATTACK_HIT -> "item.stone_spear.attack_hit";
            case SOUND_IRON_SPEAR_ATTACK_HIT -> "item.iron_spear.attack_hit";
            case SOUND_COPPER_SPEAR_ATTACK_HIT -> "item.copper_spear.attack_hit";
            case SOUND_GOLDEN_SPEAR_ATTACK_HIT -> "item.golden_spear.attack_hit";
            case SOUND_DIAMOND_SPEAR_ATTACK_HIT -> "item.diamond_spear.attack_hit";
            case SOUND_NETHERITE_SPEAR_ATTACK_HIT -> "item.netherite_spear.attack_hit";
            case SOUND_STONE_SPEAR_ATTACK_MISS -> "item.stone_spear.attack_miss";
            case SOUND_IRON_SPEAR_ATTACK_MISS -> "item.iron_spear.attack_miss";
            case SOUND_COPPER_SPEAR_ATTACK_MISS -> "item.copper_spear.attack_miss";
            case SOUND_GOLDEN_SPEAR_ATTACK_MISS -> "item.golden_spear.attack_miss";
            case SOUND_DIAMOND_SPEAR_ATTACK_MISS -> "item.diamond_spear.attack_miss";
            case SOUND_NETHERITE_SPEAR_ATTACK_MISS -> "item.netherite_spear.attack_miss";
            case SOUND_STONE_SPEAR_USE -> "item.stone_spear.use";
            case SOUND_IRON_SPEAR_USE -> "item.iron_spear.use";
            case SOUND_COPPER_SPEAR_USE -> "item.copper_spear.use";
            case SOUND_GOLDEN_SPEAR_USE -> "item.golden_spear.use";
            case SOUND_DIAMOND_SPEAR_USE -> "item.diamond_spear.use";
            case SOUND_NETHERITE_SPEAR_USE -> "item.netherite_spear.use";
            case SOUND_PAUSE_GROWTH -> "pause_growth";
            case SOUND_RESET_GROWTH -> "reset_growth";
            case SOUND_PUSHED_BY_PLAYER -> "pushed_by_player";
            case SOUND_BOUNCE -> "bounce";
            case SOUND_SLIME_LANDING -> "slime_landing";
            case SOUND_ABSORB_BLOCK -> "absorb_block";
            case SOUND_EJECT_BLOCK -> "eject_block";
            case SOUND_GEYSER_ERUPTION_START -> "geyser_eruption_start";
            case SOUND_GEYSER_ERUPTION_ACTIVE -> "geyser_eruption_active";
            case SOUND_RECORD_BOUNCE -> "record.bounce";
            case SOUND_BUCKET_FILL_LAND_ANIMAL -> "bucket.fill.land_animal";
            case SOUND_BUCKET_EMPTY_LAND_ANIMAL -> "bucket.empty.land_animal";
            case SOUND_GEYSER_CONTINUOUS_ERUPTION_START -> "geyser_continuous_eruption_start";
            case SOUND_GEYSER_CONTINUOUS_ERUPTION_ACTIVE -> "geyser_continuous_eruption_active";
            default -> SOUND_UNDEFINED_NAME;
        };
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
