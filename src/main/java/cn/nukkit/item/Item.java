package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.inventory.Fuel;
import cn.nukkit.inventory.ItemTag;
import cn.nukkit.item.RuntimeItemMapping.RuntimeEntry;
import cn.nukkit.item.customitem.CustomItem;
import cn.nukkit.item.customitem.CustomItemDefinition;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.*;
import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@Log4j2
public class Item implements Cloneable, BlockID, ItemID, ProtocolInfo {

    public static final Item AIR_ITEM = new ItemBlock(Block.get(BlockID.AIR), null, 0);

    public static final Item[] EMPTY_ARRAY = new Item[0];

    /**
     * Groups:
     * <ol>
     *     <li>namespace (optional)</li>
     *     <li>item name (choice)</li>
     *     <li>damage (optional, for item name)</li>
     *     <li>numeric id (choice)</li>
     *     <li>damage (optional, for numeric id)</li>
     * </ol>
     */
    private static final Pattern ITEM_STRING_PATTERN = Pattern.compile(
            //       1:namespace    2:name           3:damage   4:num-id    5:damage
            "^(?:(?:([a-z_]\\w*):)?([a-z._]\\w*)(?::(-?\\d+))?|(-?\\d+)(?::(-?\\d+))?)$");

    public static final String UNKNOWN_STR = "Unknown";
    public static Class<?>[] list = null;
    public static final Map<String, Supplier<Item>> NAMESPACED_ID_ITEM = new HashMap<>();

    private static final HashMap<String, Supplier<Item>> CUSTOM_ITEMS = new HashMap<>();
    private static final HashMap<String, CustomItemDefinition> CUSTOM_ITEM_DEFINITIONS = new HashMap<>();

    protected Block block = null;
    protected final int id;
    protected int meta;
    protected boolean hasMeta = true;
    private byte[] tags = new byte[0];
    private CompoundTag cachedNBT = null;
    public int count;
    protected String name;

    public Item(int id) {
        this(id, 0, 1, UNKNOWN_STR);
    }

    public Item(int id, Integer meta) {
        this(id, meta, 1, UNKNOWN_STR);
    }

    public Item(int id, Integer meta, int count) {
        this(id, meta, count, UNKNOWN_STR);
    }

    public Item(int id, Integer meta, int count, String name) {
        //this.id = id & 0xffff;
        this.id = id;
        if (meta != null && meta >= 0) {
            this.meta = meta & 0xffff;
        } else {
            this.hasMeta = false;
        }
        this.count = count;
        this.name = name;
    }

    public boolean hasMeta() {
        return hasMeta;
    }

    public boolean canBeActivated() {
        return false;
    }

    public static void init() {
        if (list == null) {
            list = new Class[65535];
            list[LADDER] = ItemLadder.class; //65
            list[RAIL] = ItemRail.class; //66
            list[CACTUS] = ItemCactus.class; //81
            list[IRON_SHOVEL] = ItemShovelIron.class; //256
            list[IRON_PICKAXE] = ItemPickaxeIron.class; //257
            list[IRON_AXE] = ItemAxeIron.class; //258
            list[FLINT_AND_STEEL] = ItemFlintSteel.class; //259
            list[APPLE] = ItemApple.class; //260
            list[BOW] = ItemBow.class; //261
            list[ARROW] = ItemArrow.class; //262
            list[COAL] = ItemCoal.class; //263
            list[DIAMOND] = ItemDiamond.class; //264
            list[IRON_INGOT] = ItemIngotIron.class; //265
            list[GOLD_INGOT] = ItemIngotGold.class; //266
            list[IRON_SWORD] = ItemSwordIron.class; //267
            list[WOODEN_SWORD] = ItemSwordWood.class; //268
            list[WOODEN_SHOVEL] = ItemShovelWood.class; //269
            list[WOODEN_PICKAXE] = ItemPickaxeWood.class; //270
            list[WOODEN_AXE] = ItemAxeWood.class; //271
            list[STONE_SWORD] = ItemSwordStone.class; //272
            list[STONE_SHOVEL] = ItemShovelStone.class; //273
            list[STONE_PICKAXE] = ItemPickaxeStone.class; //274
            list[STONE_AXE] = ItemAxeStone.class; //275
            list[DIAMOND_SWORD] = ItemSwordDiamond.class; //276
            list[DIAMOND_SHOVEL] = ItemShovelDiamond.class; //277
            list[DIAMOND_PICKAXE] = ItemPickaxeDiamond.class; //278
            list[DIAMOND_AXE] = ItemAxeDiamond.class; //279
            list[STICK] = ItemStick.class; //280
            list[BOWL] = ItemBowl.class; //281
            list[MUSHROOM_STEW] = ItemMushroomStew.class; //282
            list[GOLD_SWORD] = ItemSwordGold.class; //283
            list[GOLD_SHOVEL] = ItemShovelGold.class; //284
            list[GOLD_PICKAXE] = ItemPickaxeGold.class; //285
            list[GOLD_AXE] = ItemAxeGold.class; //286
            list[STRING] = ItemString.class; //287
            list[FEATHER] = ItemFeather.class; //288
            list[GUNPOWDER] = ItemGunpowder.class; //289
            list[WOODEN_HOE] = ItemHoeWood.class; //290
            list[STONE_HOE] = ItemHoeStone.class; //291
            list[IRON_HOE] = ItemHoeIron.class; //292
            list[DIAMOND_HOE] = ItemHoeDiamond.class; //293
            list[GOLD_HOE] = ItemHoeGold.class; //294
            list[WHEAT_SEEDS] = ItemSeedsWheat.class; //295
            list[WHEAT] = ItemWheat.class; //296
            list[BREAD] = ItemBread.class; //297
            list[LEATHER_CAP] = ItemHelmetLeather.class; //298
            list[LEATHER_TUNIC] = ItemChestplateLeather.class; //299
            list[LEATHER_PANTS] = ItemLeggingsLeather.class; //300
            list[LEATHER_BOOTS] = ItemBootsLeather.class; //301
            list[CHAIN_HELMET] = ItemHelmetChain.class; //302
            list[CHAIN_CHESTPLATE] = ItemChestplateChain.class; //303
            list[CHAIN_LEGGINGS] = ItemLeggingsChain.class; //304
            list[CHAIN_BOOTS] = ItemBootsChain.class; //305
            list[IRON_HELMET] = ItemHelmetIron.class; //306
            list[IRON_CHESTPLATE] = ItemChestplateIron.class; //307
            list[IRON_LEGGINGS] = ItemLeggingsIron.class; //308
            list[IRON_BOOTS] = ItemBootsIron.class; //309
            list[DIAMOND_HELMET] = ItemHelmetDiamond.class; //310
            list[DIAMOND_CHESTPLATE] = ItemChestplateDiamond.class; //311
            list[DIAMOND_LEGGINGS] = ItemLeggingsDiamond.class; //312
            list[DIAMOND_BOOTS] = ItemBootsDiamond.class; //313
            list[GOLD_HELMET] = ItemHelmetGold.class; //314
            list[GOLD_CHESTPLATE] = ItemChestplateGold.class; //315
            list[GOLD_LEGGINGS] = ItemLeggingsGold.class; //316
            list[GOLD_BOOTS] = ItemBootsGold.class; //317
            list[FLINT] = ItemFlint.class; //318
            list[RAW_PORKCHOP] = ItemPorkchopRaw.class; //319
            list[COOKED_PORKCHOP] = ItemPorkchopCooked.class; //320
            list[PAINTING] = ItemPainting.class; //321
            list[GOLDEN_APPLE] = ItemAppleGold.class; //322
            list[SIGN] = ItemSign.class; //323
            list[WOODEN_DOOR] = ItemDoorWood.class; //324
            list[BUCKET] = ItemBucket.class; //325
            list[MINECART] = ItemMinecart.class; //328
            list[SADDLE] = ItemSaddle.class; //329
            list[IRON_DOOR] = ItemDoorIron.class; //330
            list[REDSTONE] = ItemRedstone.class; //331
            list[SNOWBALL] = ItemSnowball.class; //332
            list[BOAT] = ItemBoat.class; //333
            list[LEATHER] = ItemLeather.class; //334
            list[KELP] = ItemKelp.class; //335
            list[BRICK] = ItemBrick.class; //336
            list[CLAY] = ItemClay.class; //337
            list[SUGARCANE] = ItemSugarcane.class; //338
            list[PAPER] = ItemPaper.class; //339
            list[BOOK] = ItemBook.class; //340
            list[SLIMEBALL] = ItemSlimeball.class; //341
            list[MINECART_WITH_CHEST] = ItemMinecartChest.class; //342
            list[EGG] = ItemEgg.class; //344
            list[COMPASS] = ItemCompass.class; //345
            list[FISHING_ROD] = ItemFishingRod.class; //346
            list[CLOCK] = ItemClock.class; //347
            list[GLOWSTONE_DUST] = ItemGlowstoneDust.class; //348
            list[RAW_FISH] = ItemFish.class; //349
            list[COOKED_FISH] = ItemFishCooked.class; //350
            list[DYE] = ItemDye.class; //351
            list[BONE] = ItemBone.class; //352
            list[SUGAR] = ItemSugar.class; //353
            list[CAKE] = ItemCake.class; //354
            list[BED] = ItemBed.class; //355
            list[REPEATER] = ItemRedstoneRepeater.class; //356
            list[COOKIE] = ItemCookie.class; //357
            list[MAP] = ItemMap.class; //358
            list[SHEARS] = ItemShears.class; //359
            list[MELON] = ItemMelon.class; //360
            list[PUMPKIN_SEEDS] = ItemSeedsPumpkin.class; //361
            list[MELON_SEEDS] = ItemSeedsMelon.class; //362
            list[RAW_BEEF] = ItemBeefRaw.class; //363
            list[STEAK] = ItemSteak.class; //364
            list[RAW_CHICKEN] = ItemChickenRaw.class; //365
            list[COOKED_CHICKEN] = ItemChickenCooked.class; //366
            list[ROTTEN_FLESH] = ItemRottenFlesh.class; //367
            list[ENDER_PEARL] = ItemEnderPearl.class; //368
            list[BLAZE_ROD] = ItemBlazeRod.class; //369
            list[GHAST_TEAR] = ItemGhastTear.class; //370
            list[GOLD_NUGGET] = ItemNuggetGold.class; //371
            list[NETHER_WART] = ItemNetherWart.class; //372
            list[POTION] = ItemPotion.class; //373
            list[GLASS_BOTTLE] = ItemGlassBottle.class; //374
            list[SPIDER_EYE] = ItemSpiderEye.class; //375
            list[FERMENTED_SPIDER_EYE] = ItemSpiderEyeFermented.class; //376
            list[BLAZE_POWDER] = ItemBlazePowder.class; //377
            list[MAGMA_CREAM] = ItemMagmaCream.class; //378
            list[BREWING_STAND] = ItemBrewingStand.class; //379
            list[CAULDRON] = ItemCauldron.class; //380
            list[ENDER_EYE] = ItemEnderEye.class; //381
            list[GLISTERING_MELON] = ItemMelonGlistering.class; //382
            list[SPAWN_EGG] = ItemSpawnEgg.class; //383
            list[EXPERIENCE_BOTTLE] = ItemExpBottle.class; //384
            list[FIRE_CHARGE] = ItemFireCharge.class; //385
            list[BOOK_AND_QUILL] = ItemBookAndQuill.class; //386
            list[WRITTEN_BOOK] = ItemBookWritten.class; //387
            list[EMERALD] = ItemEmerald.class; //388
            list[ITEM_FRAME] = ItemItemFrame.class; //389
            list[FLOWER_POT] = ItemFlowerPot.class; //390
            list[CARROT] = ItemCarrot.class; //391
            list[POTATO] = ItemPotato.class; //392
            list[BAKED_POTATO] = ItemPotatoBaked.class; //393
            list[POISONOUS_POTATO] = ItemPotatoPoisonous.class; //394
            list[EMPTY_MAP] = ItemEmptyMap.class; //395
            list[GOLDEN_CARROT] = ItemCarrotGolden.class; //396
            list[SKULL] = ItemSkull.class; //397
            list[CARROT_ON_A_STICK] = ItemCarrotOnAStick.class; //398
            list[NETHER_STAR] = ItemNetherStar.class; //399
            list[PUMPKIN_PIE] = ItemPumpkinPie.class; //400
            list[FIREWORKS] = ItemFirework.class; //401
            list[FIREWORKSCHARGE] = ItemFireworkStar.class; //402
            list[ENCHANTED_BOOK] = ItemBookEnchanted.class; //403
            list[COMPARATOR] = ItemRedstoneComparator.class; //404
            list[NETHER_BRICK] = ItemNetherBrick.class; //405
            list[QUARTZ] = ItemQuartz.class; //406
            list[MINECART_WITH_TNT] = ItemMinecartTNT.class; //407
            list[MINECART_WITH_HOPPER] = ItemMinecartHopper.class; //408
            list[PRISMARINE_SHARD] = ItemPrismarineShard.class; //409
            list[HOPPER] = ItemHopper.class;
            list[RAW_RABBIT] = ItemRabbitRaw.class; //411
            list[COOKED_RABBIT] = ItemRabbitCooked.class; //412
            list[RABBIT_STEW] = ItemRabbitStew.class; //413
            list[RABBIT_FOOT] = ItemRabbitFoot.class; //414
            list[RABBIT_HIDE] = ItemRabbitHide.class; //415
            list[LEATHER_HORSE_ARMOR] = ItemHorseArmorLeather.class; //416
            list[IRON_HORSE_ARMOR] = ItemHorseArmorIron.class; //417
            list[GOLD_HORSE_ARMOR] = ItemHorseArmorGold.class; //418
            list[DIAMOND_HORSE_ARMOR] = ItemHorseArmorDiamond.class; //419
            list[LEAD] = ItemLead.class; //420
            list[NAME_TAG] = ItemNameTag.class; //421
            list[PRISMARINE_CRYSTALS] = ItemPrismarineCrystals.class; //422
            list[RAW_MUTTON] = ItemMuttonRaw.class; //423
            list[COOKED_MUTTON] = ItemMuttonCooked.class; //424
            list[ARMOR_STAND] = ItemArmorStand.class; //425
            list[END_CRYSTAL] = ItemEndCrystal.class; //426
            list[SPRUCE_DOOR] = ItemDoorSpruce.class; //427
            list[BIRCH_DOOR] = ItemDoorBirch.class; //428
            list[JUNGLE_DOOR] = ItemDoorJungle.class; //429
            list[ACACIA_DOOR] = ItemDoorAcacia.class; //430
            list[DARK_OAK_DOOR] = ItemDoorDarkOak.class; //431
            list[CHORUS_FRUIT] = ItemChorusFruit.class; //432
            list[POPPED_CHORUS_FRUIT] = ItemChorusFruitPopped.class; //433
            list[BANNER_PATTERN] = ItemBannerPattern.class; //434
            list[DRAGON_BREATH] = ItemDragonBreath.class; //437
            list[SPLASH_POTION] = ItemPotionSplash.class; //438
            list[LINGERING_POTION] = ItemPotionLingering.class; //441
            list[ELYTRA] = ItemElytra.class; //444
            list[SHULKER_SHELL] = ItemShulkerShell.class; //445
            list[BANNER] = ItemBanner.class; //446
            list[TOTEM] = ItemTotem.class; //450
            list[IRON_NUGGET] = ItemNuggetIron.class; //452
            list[TRIDENT] = ItemTrident.class; //455
            list[BEETROOT] = ItemBeetroot.class; //457
            list[BEETROOT_SEEDS] = ItemSeedsBeetroot.class; //458
            list[BEETROOT_SOUP] = ItemBeetrootSoup.class; //459
            list[RAW_SALMON] = ItemSalmon.class; //460
            list[CLOWNFISH] = ItemClownfish.class; //461
            list[PUFFERFISH] = ItemPufferfish.class; //462
            list[COOKED_SALMON] = ItemSalmonCooked.class; //463
            list[DRIED_KELP] = ItemDriedKelp.class; //464
            list[NAUTILUS_SHELL] = ItemNautilusShell.class; //465
            list[GOLDEN_APPLE_ENCHANTED] = ItemAppleGoldEnchanted.class; //466
            list[HEART_OF_THE_SEA] = ItemHeartOfTheSea.class; //467
            list[SCUTE] = ItemScute.class; //468
            list[TURTLE_SHELL] = ItemTurtleShell.class; //469
            list[PHANTOM_MEMBRANE] = ItemPhantomMembrane.class; //470
            list[CROSSBOW] = ItemCrossbow.class; //471
            list[SPRUCE_SIGN] = ItemSpruceSign.class; //472
            list[BIRCH_SIGN] = ItemBirchSign.class; //473
            list[JUNGLE_SIGN] = ItemJungleSign.class; //474
            list[ACACIA_SIGN] = ItemAcaciaSign.class; //475
            list[DARKOAK_SIGN] = ItemDarkOakSign.class; //476
            list[SWEET_BERRIES] = ItemSweetBerries.class; //477
            list[RECORD_11] = ItemRecord11.class; //510
            list[RECORD_CAT] = ItemRecordCat.class; //501
            list[RECORD_13] = ItemRecord13.class; //500
            list[RECORD_BLOCKS] = ItemRecordBlocks.class; //502
            list[RECORD_CHIRP] = ItemRecordChirp.class; //503
            list[RECORD_FAR] = ItemRecordFar.class; //504
            list[RECORD_WARD] = ItemRecordWard.class; //509
            list[RECORD_MALL] = ItemRecordMall.class; //505
            list[RECORD_MELLOHI] = ItemRecordMellohi.class; //506
            list[RECORD_STAL] = ItemRecordStal.class; //507
            list[RECORD_STRAD] = ItemRecordStrad.class; //508
            list[RECORD_WAIT] = ItemRecordWait.class; //511
            list[SHIELD] = ItemShield.class; //513
            list[RECORD_5] = ItemRecord5.class; //636
            list[DISC_FRAGMENT_5] = ItemDiscFragment5.class; //637
            list[OAK_CHEST_BOAT] = ItemChestBoatOak.class; //638
            list[BIRCH_CHEST_BOAT] = ItemChestBoatBirch.class; //639
            list[JUNGLE_CHEST_BOAT] = ItemChestBoatJungle.class; //640
            list[SPRUCE_CHEST_BOAT] = ItemChestBoatSpruce.class; //641
            list[ACACIA_CHEST_BOAT] = ItemChestBoatAcacia.class; //642
            list[DARK_OAK_CHEST_BOAT] = ItemChestBoatDarkOak.class; //643
            list[MANGROVE_CHEST_BOAT] = ItemChestBoatMangrove.class; //644

            list[BAMBOO_CHEST_RAFT] = ItemChestRaftBamboo.class; //648
            list[CHERRY_CHEST_BOAT] = ItemChestBoatCherry.class; //649

            list[GLOW_BERRIES] = ItemGlowBerries.class; //654
            list[RECORD_RELIC] = ItemRecordRelic.class; //701
            list[CAMPFIRE] = ItemCampfire.class; //720
            list[SUSPICIOUS_STEW] = ItemSuspiciousStew.class; //734
            list[HONEYCOMB] = ItemHoneycomb.class; //736
            list[HONEY_BOTTLE] = ItemHoneyBottle.class; //737
            list[NETHERITE_INGOT] = ItemIngotNetherite.class; //742
            list[NETHERITE_SWORD] = ItemSwordNetherite.class; //743
            list[NETHERITE_SHOVEL] = ItemShovelNetherite.class; //744
            list[NETHERITE_PICKAXE] = ItemPickaxeNetherite.class; //745
            list[NETHERITE_AXE] = ItemAxeNetherite.class; //746
            list[NETHERITE_HOE] = ItemHoeNetherite.class; //747
            list[NETHERITE_HELMET] = ItemHelmetNetherite.class; //748
            list[NETHERITE_CHESTPLATE] = ItemChestplateNetherite.class; //749
            list[NETHERITE_LEGGINGS] = ItemLeggingsNetherite.class; //750
            list[NETHERITE_BOOTS] = ItemBootsNetherite.class; //751
            list[NETHERITE_SCRAP] = ItemScrapNetherite.class; //752
            list[CRIMSON_SIGN] = ItemCrimsonSign.class; //753
            list[WARPED_SIGN] = ItemWarpedSign.class; //754
            list[CRIMSON_DOOR] = ItemDoorCrimson.class; //755
            list[WARPED_DOOR] = ItemDoorWarped.class; //756
            list[WARPED_FUNGUS_ON_A_STICK] = ItemWarpedFungusOnAStick.class; //757
            list[CHAIN] = ItemChain.class; //758
            list[RECORD_PIGSTEP] = ItemRecordPigstep.class; //759
            list[NETHER_SPROUTS] = ItemNetherSprouts.class; //760

            list[AMETHYST_SHARD] = ItemAmethystShard.class; //771
            list[SPYGLASS] = ItemSpyglass.class; //772
            list[RECORD_OTHERSIDE] = ItemRecordOtherside.class; //773

            list[SOUL_CAMPFIRE] = ItemCampfireSoul.class; //801

            for (int i = 0; i < 256; ++i) {
                if (Block.list[i] != null) {
                    list[i] = Block.list[i];
                }
            }

            registerNamespacedIdItem(ItemIngotCopper.class);
            registerNamespacedIdItem(ItemRawIron.class);
            registerNamespacedIdItem(ItemRawGold.class);
            registerNamespacedIdItem(ItemRawCopper.class);
            registerNamespacedIdItem(ItemCopperIngot.class);
            registerNamespacedIdItem(ItemNetheriteUpgradeSmithingTemplate.class);
            registerNamespacedIdItem(ItemSentryArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemDuneArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemCoastArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemWildArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemWardArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemEyeArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemVexArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemTideArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemSnoutArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemRibArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemSpireArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemSilenceArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemWayfinderArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemRaiserArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemShaperArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemHostArmorTrimSmithingTemplate.class);
            registerNamespacedIdItem(ItemAnglerPotterySherd.class);
            registerNamespacedIdItem(ItemArcherPotterySherd.class);
            registerNamespacedIdItem(ItemArmsUpPotterySherd.class);
            registerNamespacedIdItem(ItemBladePotterySherd.class);
            registerNamespacedIdItem(ItemBrewerPotterySherd.class);
            registerNamespacedIdItem(ItemBurnPotterySherd.class);
            registerNamespacedIdItem(ItemDangerPotterySherd.class);
            registerNamespacedIdItem(ItemExplorerPotterySherd.class);
            registerNamespacedIdItem(ItemFriendPotterySherd.class);
            registerNamespacedIdItem(ItemHeartPotterySherd.class);
            registerNamespacedIdItem(ItemHeartbreakPotterySherd.class);
            registerNamespacedIdItem(ItemHowlPotterySherd.class);
            registerNamespacedIdItem(ItemMinerPotterySherd.class);
            registerNamespacedIdItem(ItemMournerPotterySherd.class);
            registerNamespacedIdItem(ItemPlentyPotterySherd.class);
            registerNamespacedIdItem(ItemPrizePotterySherd.class);
            registerNamespacedIdItem(ItemSheafPotterySherd.class);
            registerNamespacedIdItem(ItemShelterPotterySherd.class);
            registerNamespacedIdItem(ItemSkullPotterySherd.class);
            registerNamespacedIdItem(ItemSnortPotterySherd.class);
            registerNamespacedIdItem(ItemBrush.class);
            registerNamespacedIdItem(ItemGoatHorn.class);
            registerNamespacedIdItem(ItemTrialKey.class);
            registerNamespacedIdItem(ItemTrialKeyOminous.class);
            registerNamespacedIdItem(ItemBreezeRod.class);
            registerNamespacedIdItem(ItemWindCharge.class);
            registerNamespacedIdItem(ItemMace.class);

            // 添加原版物品到NAMESPACED_ID_ITEM
            // Add vanilla items to NAMESPACED_ID_ITEM
            RuntimeItemMapping mapping = RuntimeItems.getMapping(ProtocolInfo.CURRENT_PROTOCOL);
            for (Object2IntMap.Entry<String> entity : mapping.getName2RuntimeId().object2IntEntrySet()) {
                try {
                    RuntimeItemMapping.LegacyEntry legacyEntry = mapping.fromRuntime(entity.getIntValue());
                    int id = legacyEntry.getLegacyId();
                    int damage = 0;
                    if (legacyEntry.isHasDamage()) {
                        damage = legacyEntry.getDamage();
                    }
                    Item item = Item.get(id, damage);
                    if (item.getId() != 0) {
                        NAMESPACED_ID_ITEM.put(entity.getKey(), () -> item);
                    }
                } catch (Exception ignored) {

                }
            }
        }

        initCreativeItems();
    }

    private static final List<Item> creative137 = new ObjectArrayList<>();
    private static final List<Item> creative274 = new ObjectArrayList<>();
    private static final List<Item> creative291 = new ObjectArrayList<>();
    private static final List<Item> creative313 = new ObjectArrayList<>();
    private static final List<Item> creative332 = new ObjectArrayList<>();
    private static final List<Item> creative340 = new ObjectArrayList<>();
    private static final List<Item> creative354 = new ObjectArrayList<>();
    private static final List<Item> creative389 = new ObjectArrayList<>();
    private static final List<Item> creative407 = new ObjectArrayList<>();
    private static final List<Item> creative440 = new ObjectArrayList<>();
    private static final List<Item> creative448 = new ObjectArrayList<>();
    private static final List<Item> creative465 = new ObjectArrayList<>();
    private static final List<Item> creative471 = new ObjectArrayList<>();
    private static final List<Item> creative475 = new ObjectArrayList<>();
    private static final List<Item> creative486 = new ObjectArrayList<>();
    private static final List<Item> creative503 = new ObjectArrayList<>();
    private static final List<Item> creative527 = new ObjectArrayList<>();
    private static final List<Item> creative534 = new ObjectArrayList<>();
    private static final List<Item> creative544 = new ObjectArrayList<>();
    private static final List<Item> creative560 = new ObjectArrayList<>();
    private static final List<Item> creative567 = new ObjectArrayList<>();
    private static final List<Item> creative575 = new ObjectArrayList<>();
    private static final List<Item> creative582 = new ObjectArrayList<>();
    private static final List<Item> creative589 = new ObjectArrayList<>();
    private static final List<Item> creative594 = new ObjectArrayList<>();
    private static final List<Item> creative618 = new ObjectArrayList<>();
    private static final List<Item> creative622 = new ObjectArrayList<>();
    private static final List<Item> creative630 = new ObjectArrayList<>();
    private static final List<Item> creative649 = new ObjectArrayList<>();
    private static final List<Item> creative662 = new ObjectArrayList<>();
    private static final List<Item> creative671 = new ObjectArrayList<>();
    private static final List<Item> creative685 = new ObjectArrayList<>();

    private static void initCreativeItems() {
        Server.getInstance().getLogger().debug("Loading creative items...");
        clearCreativeItems();

        // Creative inventory for oldest versions
        registerCreativeItems(v1_5_0);
        registerCreativeItems(v1_7_0);
        registerCreativeItems(v1_8_0);
        registerCreativeItems(v1_9_0);
        registerCreativeItems(v1_10_0);
        registerCreativeItems(v1_11_0);
        registerCreativeItems(v1_14_0);
        registerCreativeItems(v1_16_0);

        // New creative items mapping
        registerCreativeItemsNew(ProtocolInfo.v1_17_0, ProtocolInfo.v1_17_0, creative440);
        registerCreativeItemsNew(ProtocolInfo.v1_17_10, ProtocolInfo.v1_17_10, creative448);
        registerCreativeItemsNew(ProtocolInfo.v1_17_30, ProtocolInfo.v1_17_30, creative465);
        registerCreativeItemsNew(ProtocolInfo.v1_17_30, ProtocolInfo.v1_17_40, creative471);
        registerCreativeItemsNew(ProtocolInfo.v1_18_0, ProtocolInfo.v1_18_0, creative475);
        registerCreativeItemsNew(ProtocolInfo.v1_18_10, ProtocolInfo.v1_18_10, creative486);
        registerCreativeItemsNew(ProtocolInfo.v1_18_30, ProtocolInfo.v1_18_30, creative503);
        registerCreativeItemsNew(ProtocolInfo.v1_19_0, ProtocolInfo.v1_19_0, creative527);
        registerCreativeItemsNew(ProtocolInfo.v1_19_0, ProtocolInfo.v1_19_10, creative534);
        registerCreativeItemsNew(ProtocolInfo.v1_19_20, ProtocolInfo.v1_19_20, creative544);
        registerCreativeItemsNew(ProtocolInfo.v1_19_50, ProtocolInfo.v1_19_50, creative560);
        registerCreativeItemsNew(ProtocolInfo.v1_19_60, ProtocolInfo.v1_19_60, creative567);
        registerCreativeItemsNew(ProtocolInfo.v1_19_70, ProtocolInfo.v1_19_70, creative575);
        registerCreativeItemsNew(ProtocolInfo.v1_19_80, ProtocolInfo.v1_19_80, creative582);
        registerCreativeItemsNew(ProtocolInfo.v1_20_0, ProtocolInfo.v1_20_0, creative589);
        registerCreativeItemsNew(ProtocolInfo.v1_20_10, ProtocolInfo.v1_20_10, creative594);
        registerCreativeItemsNew(ProtocolInfo.v1_20_30, ProtocolInfo.v1_20_30, creative618);
        registerCreativeItemsNew(ProtocolInfo.v1_20_40, ProtocolInfo.v1_20_40, creative622);
        registerCreativeItemsNew(ProtocolInfo.v1_20_50, ProtocolInfo.v1_20_50, creative630);
        registerCreativeItemsNew(ProtocolInfo.v1_20_60, ProtocolInfo.v1_20_60, creative649);
        registerCreativeItemsNew(ProtocolInfo.v1_20_70, ProtocolInfo.v1_20_70, creative662);
        registerCreativeItemsNew(ProtocolInfo.v1_20_80, ProtocolInfo.v1_20_80, creative671);
        registerCreativeItemsNew(ProtocolInfo.v1_21_0, ProtocolInfo.v1_21_0, creative685);
        //TODO Multiversion 添加新版本支持时修改这里
    }

    private static void registerCreativeItems(int protocol) {
        for (Map map : new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("creativeitems" + protocol + ".json")).getMapList("items")) {
            try {
                Item item = fromJson(map);
                if (Utils.hasItemOrBlock(item.getId())) { //只添加nk内部已实现的物品/方块
                    addCreativeItem(protocol, item);
                }
            } catch (Exception e) {
                MainLogger.getLogger().logException(e);
            }
        }
    }

    private static void registerCreativeItemsNew(int protocol, int blockPaletteProtocol, List<Item> creativeItems) {
        JsonArray itemsArray;
        String file;
        if (protocol >= ProtocolInfo.v1_21_0) {
            file = "CreativeItems/creative_items_" + protocol + ".json";
        } else {
            file = "creativeitems" + protocol + ".json";
        }
        try (InputStream stream = Server.class.getClassLoader().getResourceAsStream(file)) {
            itemsArray = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("items");
        } catch (Exception e) {
            throw new AssertionError("Error loading required block states!", e);
        }

        for (JsonElement element : itemsArray) {
            Item item = RuntimeItems.getMapping(protocol).parseCreativeItem(element.getAsJsonObject(), true, blockPaletteProtocol);
            if (item != null && !item.getName().equals(UNKNOWN_STR)) {
                // Add only implemented items
                creativeItems.add(item.clone());
            }
        }
    }

    public static void clearCreativeItems() {
        Item.creative137.clear();
        Item.creative274.clear();
        Item.creative291.clear();
        Item.creative313.clear();
        Item.creative332.clear();
        Item.creative340.clear();
        Item.creative354.clear();
        Item.creative389.clear();
        Item.creative407.clear();
        Item.creative440.clear();
        Item.creative448.clear();
        Item.creative465.clear();
        Item.creative471.clear();
        Item.creative475.clear();
        Item.creative486.clear();
        Item.creative503.clear();
        Item.creative527.clear();
        Item.creative534.clear();
        Item.creative544.clear();
        Item.creative560.clear();
        Item.creative567.clear();
        Item.creative575.clear();
        Item.creative582.clear();
        Item.creative589.clear();
        Item.creative594.clear();
        Item.creative618.clear();
        Item.creative622.clear();
        Item.creative630.clear();
        Item.creative649.clear();
        Item.creative662.clear();
        Item.creative671.clear();
        Item.creative685.clear();
        //TODO Multiversion 添加新版本支持时修改这里
    }

    public static ArrayList<Item> getCreativeItems() {
        Server.mvw("Item#getCreativeItems()");
        return getCreativeItems(CURRENT_PROTOCOL);
    }

    public static ArrayList<Item> getCreativeItems(int protocol) {
        switch (protocol) {
            case v1_1_0: //TODO check
            case v1_2_0:
            case v1_2_5_11:
            case v1_2_5:
            case v1_2_6:
            case v1_2_7:
            case v1_2_10:
            case v1_2_13:
            case v1_2_13_11:
            case v1_4_0:
                return new ArrayList<>(Item.creative137);
            case v1_5_0:
                return new ArrayList<>(Item.creative274);
            case v1_6_0_5:
            case v1_6_0:
            case v1_7_0:
                return new ArrayList<>(Item.creative291);
            case v1_8_0:
                return new ArrayList<>(Item.creative313);
            case v1_9_0:
                return new ArrayList<>(Item.creative332);
            case v1_10_0:
                return new ArrayList<>(Item.creative340);
            case v1_11_0:
            case v1_12_0:
            case v1_13_0:
                return new ArrayList<>(Item.creative354);
            case v1_14_0:
            case v1_14_60:
                return new ArrayList<>(Item.creative389);
            case v1_16_0:
            case v1_16_20:
            case v1_16_100_0:
            case v1_16_100_51:
            case v1_16_100_52:
            case v1_16_100:
            case v1_16_200_51:
            case v1_16_200:
            case v1_16_210_50:
            case v1_16_210_53:
            case v1_16_210:
            case v1_16_220:
            case v1_16_230_50:
            case v1_16_230:
            case v1_16_230_54:
                return new ArrayList<>(Item.creative407);
            case v1_17_0:
                return new ArrayList<>(Item.creative440);
            case v1_17_10:
            case v1_17_20_20:
                return new ArrayList<>(Item.creative448);
            case v1_17_30:
                return new ArrayList<>(Item.creative465);
            case v1_17_40:
                return new ArrayList<>(Item.creative471);
            case v1_18_0:
                return new ArrayList<>(Item.creative475);
            case v1_18_10_26:
            case v1_18_10:
                return new ArrayList<>(Item.creative486);
            case v1_18_30:
                return new ArrayList<>(Item.creative503);
            case v1_19_0_29:
            case v1_19_0_31:
            case v1_19_0:
                return new ArrayList<>(Item.creative527);
            case v1_19_10:
                return new ArrayList<>(Item.creative534);
            case v1_19_20:
            case v1_19_21:
            case v1_19_30_23:
            case v1_19_30:
            case v1_19_40:
                return new ArrayList<>(Item.creative544);
            case v1_19_50:
                return new ArrayList<>(Item.creative560);
            case v1_19_60:
            case v1_19_63:
                return new ArrayList<>(Item.creative567);
            case v1_19_70_24:
            case v1_19_70:
                return new ArrayList<>(Item.creative575);
            case v1_19_80:
                return new ArrayList<>(Item.creative582);
            case v1_20_0_23:
            case v1_20_0:
                return new ArrayList<>(Item.creative589);
            case v1_20_10_21:
            case v1_20_10:
                return new ArrayList<>(Item.creative594);
            case v1_20_30_24:
            case v1_20_30:
                return new ArrayList<>(Item.creative618);
            case v1_20_40:
                return new ArrayList<>(Item.creative622);
            case v1_20_50:
                return new ArrayList<>(Item.creative630);
            case v1_20_60:
                return new ArrayList<>(Item.creative649);
            case v1_20_70:
                return new ArrayList<>(Item.creative662);
            case v1_20_80:
                return new ArrayList<>(Item.creative671);
            case v1_21_0:
                return new ArrayList<>(Item.creative685);
            // TODO Multiversion
            default:
                throw new IllegalArgumentException("Tried to get creative items for unsupported protocol version: " + protocol);
        }
    }

    public static void addCreativeItem(Item item) {
        Server.mvw("Item#addCreativeItem(Item)");
        addCreativeItem(v1_21_0, item);
    }

    public static void addCreativeItem(int protocol, Item item) {
        switch (protocol) { // NOTE: Not all versions are supposed to be here
            case v1_2_0 -> Item.creative137.add(item.clone());
            case v1_5_0 -> Item.creative274.add(item.clone());
            case v1_7_0 -> Item.creative291.add(item.clone());
            case v1_8_0 -> Item.creative313.add(item.clone());
            case v1_9_0 -> Item.creative332.add(item.clone());
            case v1_10_0 -> Item.creative340.add(item.clone());
            case v1_11_0 -> Item.creative354.add(item.clone());
            case v1_14_0 -> Item.creative389.add(item.clone());
            case v1_16_0 -> Item.creative407.add(item.clone());
            case v1_17_0 -> Item.creative440.add(item.clone());
            case v1_17_10 -> Item.creative448.add(item.clone());
            case v1_17_30 -> Item.creative465.add(item.clone());
            case v1_17_40 -> Item.creative471.add(item.clone());
            case v1_18_10 -> Item.creative486.add(item.clone());
            case v1_18_0 -> Item.creative475.add(item.clone());
            case v1_18_30 -> Item.creative503.add(item.clone());
            case v1_19_0 -> Item.creative527.add(item.clone());
            case v1_19_10 -> Item.creative534.add(item.clone());
            case v1_19_20 -> Item.creative544.add(item.clone());
            case v1_19_50 -> Item.creative560.add(item.clone());
            case v1_19_60 -> Item.creative567.add(item.clone());
            case v1_19_70 -> Item.creative575.add(item.clone());
            case v1_19_80 -> Item.creative582.add(item.clone());
            case v1_20_0 -> Item.creative589.add(item.clone());
            case v1_20_10 -> Item.creative594.add(item.clone());
            case v1_20_30 -> Item.creative618.add(item.clone());
            case v1_20_40 -> Item.creative622.add(item.clone());
            case v1_20_50 -> Item.creative630.add(item.clone());
            case v1_20_60 -> Item.creative649.add(item.clone());
            case v1_20_70 -> Item.creative662.add(item.clone());
            case v1_20_80 -> Item.creative671.add(item.clone());
            case v1_21_0 -> Item.creative685.add(item.clone());
            // TODO Multiversion
            default -> throw new IllegalArgumentException("Tried to register creative items for unsupported protocol version: " + protocol);
        }
    }

    public static void removeCreativeItem(Item item) {
        Server.mvw("Item#removeCreativeItem(Item)");
        removeCreativeItem(ProtocolInfo.CURRENT_PROTOCOL, item);
    }

    public static void removeCreativeItem(int protocol, Item item) {
        int index = getCreativeItemIndex(protocol, item);
        if (index != -1) {
            Item.getCreativeItems(protocol).remove(index);
        }
    }

    public static boolean isCreativeItem(Item item) {
        Server.mvw("Item#isCreativeItem(Item)");
        return isCreativeItem(ProtocolInfo.CURRENT_PROTOCOL, item);
    }

    public static boolean isCreativeItem(int protocol, Item item) {
        for (Item aCreative : Item.getCreativeItems(protocol)) {
            if (item.equals(aCreative, !item.isTool())) {
                return true;
            }
        }
        return false;
    }

    public static Item getCreativeItem(int index) {
        Server.mvw("Item#getCreativeItemIndex(int)");
        return Item.getCreativeItem(ProtocolInfo.CURRENT_PROTOCOL, index);
    }

    public static Item getCreativeItem(int protocol, int index) {
        return (index >= 0 && index < Item.getCreativeItems(protocol).size()) ? Item.getCreativeItems(protocol).get(index) : null;
    }

    public static int getCreativeItemIndex(Item item) {
        Server.mvw("Item#getCreativeItemIndex(Item)");
        return getCreativeItemIndex(ProtocolInfo.CURRENT_PROTOCOL, item);
    }

    public static int getCreativeItemIndex(int protocol, Item item) {
        for (int i = 0; i < Item.getCreativeItems(protocol).size(); i++) {
            if (item.equals(Item.getCreativeItems(protocol).get(i), !item.isTool())) {
                return i;
            }
        }
        return -1;
    }

    @SneakyThrows
    public static void registerNamespacedIdItem(@NotNull Class<? extends StringItem> item) {
        Constructor<? extends StringItem> declaredConstructor = item.getDeclaredConstructor();
        var Item = declaredConstructor.newInstance();
        registerNamespacedIdItem(Item.getNamespaceId(), stringItemSupplier(declaredConstructor));
    }

    public static void registerNamespacedIdItem(@NotNull String namespacedId, @NotNull Constructor<? extends Item> constructor) {
        Preconditions.checkNotNull(namespacedId, "namespacedId is null");
        Preconditions.checkNotNull(constructor, "constructor is null");
        NAMESPACED_ID_ITEM.put(namespacedId.toLowerCase(Locale.ENGLISH), itemSupplier(constructor));
    }

    public static void registerNamespacedIdItem(@NotNull String namespacedId, @NotNull Supplier<Item> constructor) {
        Preconditions.checkNotNull(namespacedId, "namespacedId is null");
        Preconditions.checkNotNull(constructor, "constructor is null");
        NAMESPACED_ID_ITEM.put(namespacedId.toLowerCase(Locale.ENGLISH), constructor);
    }

    @NotNull
    private static Supplier<Item> itemSupplier(@NotNull Constructor<? extends Item> constructor) {
        return () -> {
            try {
                return constructor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new UnsupportedOperationException(e);
            }
        };
    }

    @NotNull
    private static Supplier<Item> stringItemSupplier(@NotNull Constructor<? extends StringItem> constructor) {
        return () -> {
            try {
                return (Item) constructor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new UnsupportedOperationException(e);
            }
        };
    }

    public static OK<?> registerCustomItem(@NotNull List<Class<? extends CustomItem>> itemClassList) {
        for (Class<? extends CustomItem> itemClass : itemClassList) {
            OK<?> result = registerCustomItem(itemClass);
            if (!result.ok()) {
                return result;
            }
        }
        return new OK<>(true);
    }

    public static OK<?> registerCustomItem(@NotNull Class<? extends CustomItem> clazz) {
        return registerCustomItem(clazz, true);
    }

    public static OK<?> registerCustomItem(@NotNull Class<? extends CustomItem> clazz, boolean addCreativeItem) {
        if (!Server.getInstance().enableExperimentMode) {
            Server.getInstance().getLogger().warning("The server does not have the experiment mode feature enabled. Unable to register the custom item!");
            return new OK<>(false, "The server does not have the experiment mode feature enabled. Unable to register the custom item!");
        }

        CustomItem customItem;
        Supplier<Item> supplier;

        try {
            var method = clazz.getDeclaredConstructor();
            method.setAccessible(true);
            customItem = method.newInstance();
            supplier = () -> {
                try {
                    return (Item) method.newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new UnsupportedOperationException(e);
                }
            };
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            return new OK<>(false, e);
        }

        if (CUSTOM_ITEMS.containsKey(customItem.getNamespaceId())) {
            return new OK<>(false, "The custom item with the namespace ID \"" + customItem.getNamespaceId() + "\" is already registered!");
        }
        CUSTOM_ITEMS.put(customItem.getNamespaceId(), supplier);
        CustomItemDefinition customDef = customItem.getDefinition();
        CUSTOM_ITEM_DEFINITIONS.put(customItem.getNamespaceId(), customDef);
        registerNamespacedIdItem(customItem.getNamespaceId(), supplier);

        // 在服务端注册自定义物品的tag
        if (customDef.getNbt(ProtocolInfo.CURRENT_PROTOCOL).get("components") instanceof CompoundTag componentTag) {
            var tagList = componentTag.getList("item_tags", StringTag.class);
            if (!tagList.isEmpty()) {
                ItemTag.registerItemTag(customItem.getNamespaceId(), tagList.getAll().stream().map(tag -> tag.data).collect(Collectors.toSet()));
            }
        }

        registerCustomItem(customItem, v1_16_100, addCreativeItem, v1_16_0);
        registerCustomItem(customItem, v1_17_0, addCreativeItem, v1_17_0);
        registerCustomItem(customItem, v1_17_10, addCreativeItem, v1_17_10, v1_17_30, v1_17_40);
        registerCustomItem(customItem, v1_18_0, addCreativeItem, v1_18_0);
        registerCustomItem(customItem, v1_18_10, addCreativeItem, v1_18_10);
        registerCustomItem(customItem, v1_18_30, addCreativeItem, v1_18_30);
        registerCustomItem(customItem, v1_19_0, addCreativeItem, v1_19_0);
        registerCustomItem(customItem, v1_19_10, addCreativeItem, v1_19_10, v1_19_20);
        registerCustomItem(customItem, v1_19_50, addCreativeItem, v1_19_50);
        registerCustomItem(customItem, v1_19_60, addCreativeItem, v1_19_60);
        registerCustomItem(customItem, v1_19_70, addCreativeItem, v1_19_70);
        registerCustomItem(customItem, v1_19_80, addCreativeItem, v1_19_80);
        registerCustomItem(customItem, v1_20_0, addCreativeItem, v1_20_0);
        registerCustomItem(customItem, v1_20_10, addCreativeItem, v1_20_10);
        registerCustomItem(customItem, v1_20_30, addCreativeItem, v1_20_30);
        registerCustomItem(customItem, v1_20_40, addCreativeItem, v1_20_40);
        registerCustomItem(customItem, v1_20_50, addCreativeItem, v1_20_50);
        registerCustomItem(customItem, v1_20_60, addCreativeItem, v1_20_60);
        registerCustomItem(customItem, v1_20_70, addCreativeItem, v1_20_70);
        registerCustomItem(customItem, v1_20_80, addCreativeItem, v1_20_80);
        registerCustomItem(customItem, v1_21_0, addCreativeItem, v1_21_0);
        //TODO Multiversion 添加新版本支持时修改这里

        return new OK<Void>(true);
    }

    private static void registerCustomItem(CustomItem item, int protocol,  boolean addCreativeItem, int... creativeProtocols) {
        if (RuntimeItems.getMapping(protocol).registerCustomItem(item) && addCreativeItem) {
            for (int creativeProtocol : creativeProtocols) {
                addCreativeItem(creativeProtocol, (Item) item);
            }
        }
    }

    public static void deleteCustomItem(String namespaceId) {
        if (CUSTOM_ITEMS.containsKey(namespaceId)) {
            Item customItem = fromString(namespaceId);
            CUSTOM_ITEMS.remove(namespaceId);
            CUSTOM_ITEM_DEFINITIONS.remove(namespaceId);

            deleteCustomItem(customItem, v1_16_100, v1_16_0);
            deleteCustomItem(customItem, v1_17_0, v1_17_0);
            deleteCustomItem(customItem, v1_17_10, v1_17_10, v1_17_30, v1_17_40);
            deleteCustomItem(customItem, v1_18_0, v1_18_0);
            deleteCustomItem(customItem, v1_18_10, v1_18_10);
            deleteCustomItem(customItem, v1_18_30, v1_18_30);
            deleteCustomItem(customItem, v1_19_0, v1_19_0);
            deleteCustomItem(customItem, v1_19_10, v1_19_10, v1_19_20);
            deleteCustomItem(customItem, v1_19_50, v1_19_50);
            deleteCustomItem(customItem, v1_19_60, v1_19_60);
            deleteCustomItem(customItem, v1_19_70, v1_19_70);
            deleteCustomItem(customItem, v1_19_80, v1_19_80);
            deleteCustomItem(customItem, v1_20_0, v1_20_0);
            deleteCustomItem(customItem, v1_20_10, v1_20_10);
            deleteCustomItem(customItem, v1_20_30, v1_20_30);
            deleteCustomItem(customItem, v1_20_40, v1_20_40);
            deleteCustomItem(customItem, v1_20_50, v1_20_50);
            deleteCustomItem(customItem, v1_20_60, v1_20_60);
            deleteCustomItem(customItem, v1_20_70, v1_20_70);
            deleteCustomItem(customItem, v1_20_80, v1_20_80);
            deleteCustomItem(customItem, v1_21_0, v1_21_0);
            //TODO Multiversion 添加新版本支持时修改这里
        }
    }

    private static void deleteCustomItem(Item item, int protocol, int... creativeProtocols) {
        RuntimeItems.getMapping(protocol).deleteCustomItem((CustomItem) item);
        for (int creativeProtocol : creativeProtocols) {
            removeCreativeItem(creativeProtocol, item);
        }
    }

    public static HashMap<String, Supplier<? extends Item>> getCustomItems() {
        return new HashMap<>(CUSTOM_ITEMS);
    }

    public static HashMap<String, CustomItemDefinition> getCustomItemDefinition() {
        return new HashMap<>(CUSTOM_ITEM_DEFINITIONS);
    }

    public static Item get(int id) {
        return get(id, 0);
    }

    public static Item get(int id, Integer meta) {
        return get(id, meta, 1);
    }

    public static Item get(int id, Integer meta, int count) {
        return get(id, meta, count, new byte[0]);
    }

    public static Item get(int id, Integer meta, int count, byte[] tags) {
        try {
            Class<?> c = null;
            if (id < 0) {
                int blockId = 255 - id;
                c = Block.list[blockId];
            } else {
                c = list[id];
            }
            Item item;

            if (c == null) {
                item = new Item(id, meta, count);
            } else if (id < 256 && id != 166) {
                if (meta >= 0) {
                    item = new ItemBlock(Block.get(id, meta), meta, count);
                } else {
                    item = new ItemBlock(Block.get(id), meta, count);
                }
            } else {
                item = ((Item) c.getConstructor(Integer.class, int.class).newInstance(meta, count));
            }

            if (tags.length != 0) {
                item.setCompoundTag(tags);
            }

            return item;
        } catch (Exception e) {
            return new Item(id, meta, count).setCompoundTag(tags);
        }
    }

    public static Item fromString(String str) {
        String normalized = str.trim().replace(' ', '_').toLowerCase();
        Matcher matcher = ITEM_STRING_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return AIR_ITEM.clone();
        }

        String name = matcher.group(2);
        OptionalInt meta = OptionalInt.empty();
        String metaGroup;
        if (name != null) {
            metaGroup = matcher.group(3);
        } else {
            metaGroup = matcher.group(5);
        }
        if (metaGroup != null) {
            meta = OptionalInt.of(Short.parseShort(metaGroup));
        }

        String numericIdGroup = matcher.group(4);
        if (name != null) {
            String namespaceGroup = matcher.group(1);
            String namespacedId;
            if (namespaceGroup != null) {
                namespacedId = namespaceGroup + ":" + name;
            } else {
                namespacedId = "minecraft:" + name;
            }
            if ("minecraft:air".equals(namespacedId)) {
                return Item.AIR_ITEM.clone();
            }

            Supplier<Item> constructor = NAMESPACED_ID_ITEM.get(namespacedId);
            if (constructor != null) {
                try {
                    Item item = constructor.get();
                    if (meta.isPresent()) {
                        item.setDamage(meta.getAsInt());
                    }
                    // Avoid the upcoming changes to the original item object
                    return item.clone();
                } catch (Exception e) {
                    log.warn("Could not create a new instance of {} using the namespaced id {}", constructor, namespacedId, e);
                }
            }

            //common item
            int id = RuntimeItems.getLegacyIdFromLegacyString(namespacedId);
            if (id > 0) {
                return get(id, meta.orElse(0));
            } else if (namespaceGroup != null && !namespaceGroup.equals("minecraft:")) {
                return Item.AIR_ITEM.clone();
            }
        } else if (numericIdGroup != null) {
            int id = Integer.parseInt(numericIdGroup);
            return get(id, meta.orElse(0));
        }

        if (name == null) {
            return Item.AIR_ITEM.clone();
        }

        int id = 0;
        try {
            id = BlockID.class.getField(name.toUpperCase()).getInt(null);
            if (id > 255) {
                id = 255 - id;
            }
        } catch (Exception ignore) {
            try {
                id = ItemID.class.getField(name.toUpperCase()).getInt(null);
            } catch (Exception ignore1) {
            }
        }
        return get(id, meta.orElse(0));
    }

    public static Item fromJson(Map<String, Object> data) {
        return fromJson(data, false);
    }

    public static Item fromJson(Map<String, Object> data, boolean ignoreUnsupported) {
        String nbt = (String) data.get("nbt_b64");
        byte[] nbtBytes;
        if (nbt != null) {
            nbtBytes = Base64.getDecoder().decode(nbt);
        } else { // Support old format for backwards compatibility
            nbt = (String) data.getOrDefault("nbt_hex", null);
            if (nbt == null) {
                nbtBytes = new byte[0];
            } else {
                nbtBytes = Utils.parseHexBinary(nbt);
            }
        }

        Object id1 = data.get("id");
        if (ignoreUnsupported && !Utils.hasItemOrBlock(id1)) {
            return null;
        }
        Item item = fromString(id1 + ":" + data.getOrDefault("damage", 0));
        item.setCount(Utils.toInt(data.getOrDefault("count", 1)));
        item.setCompoundTag(nbtBytes);
        return item;
    }

    public static Item fromJsonOld(Map<String, Object> data) {
        String nbt = (String) data.getOrDefault("nbt_hex", "");
        return get(Utils.toInt(data.get("id")), Utils.toInt(data.getOrDefault("damage", 0)), Utils.toInt(data.getOrDefault("count", 1)), nbt.isEmpty() ? new byte[0] : Utils.parseHexBinary(nbt));
    }

    public static Item[] fromStringMultiple(String str) {
        String[] b = str.split(",");
        Item[] items = new Item[b.length - 1];
        for (int i = 0; i < b.length; i++) {
            items[i] = fromString(b[i]);
        }
        return items;
    }

    public Item setCompoundTag(CompoundTag tag) {
        this.setNamedTag(tag);
        return this;
    }

    public Item setCompoundTag(byte[] tags) {
        this.tags = tags == null ? new byte[0] : tags;
        this.cachedNBT = null;
        return this;
    }

    public byte[] getCompoundTag() {
        return tags;
    }

    public boolean hasCompoundTag() {
        return this.tags != null && this.tags.length > 0;
    }

    public boolean hasCustomBlockData() {
        if (!this.hasCompoundTag()) {
            return false;
        }

        CompoundTag tag = this.getNamedTag();
        return tag.contains("BlockEntityTag") && tag.get("BlockEntityTag") instanceof CompoundTag;

    }

    public Item clearCustomBlockData() {
        if (!this.hasCompoundTag()) {
            return this;
        }
        CompoundTag tag = this.getNamedTag();

        if (tag.contains("BlockEntityTag") && tag.get("BlockEntityTag") instanceof CompoundTag) {
            tag.remove("BlockEntityTag");
            this.setNamedTag(tag);
        }

        return this;
    }

    public Item setCustomBlockData(CompoundTag compoundTag) {
        CompoundTag tags = compoundTag.copy();
        tags.setName("BlockEntityTag");

        CompoundTag tag;
        if (!this.hasCompoundTag()) {
            tag = new CompoundTag();
        } else {
            tag = this.getNamedTag();
        }

        tag.putCompound("BlockEntityTag", tags);
        this.setNamedTag(tag);

        return this;
    }

    public CompoundTag getCustomBlockData() {
        if (!this.hasCompoundTag()) {
            return null;
        }

        CompoundTag tag = this.getNamedTag();

        if (tag.contains("BlockEntityTag")) {
            Tag bet = tag.get("BlockEntityTag");
            if (bet instanceof CompoundTag) {
                return (CompoundTag) bet;
            }
        }

        return null;
    }

    public boolean hasEnchantments() {
        if (!this.hasCompoundTag()) {
            return false;
        }

        CompoundTag tag = this.getNamedTag();

        if (tag.contains("ench")) {
            Tag enchTag = tag.get("ench");
            return enchTag instanceof ListTag;
        }

        return false;
    }

    /**
     * 通过附魔id来查找对应附魔的等级
     * <p>
     * Find the enchantment level by the enchantment id.
     *
     * @param id The enchantment ID from {@link Enchantment} constants.
     * @return {@code 0} if the item don't have that enchantment or the current level of the given enchantment.
     */
    public int getEnchantmentLevel(int id) {
        Enchantment enchantment = this.getEnchantment(id);
        return enchantment == null ? 0 : enchantment.getLevel();
    }

    public Enchantment getEnchantment(int id) {
        return getEnchantment((short) (id & 0xffff));
    }

    public Enchantment getEnchantment(short id) {
        if (!this.hasEnchantments()) {
            return null;
        }

        for (CompoundTag entry : this.getNamedTag().getList("ench", CompoundTag.class).getAll()) {
            if (entry.getShort("id") == id) {
                Enchantment e = Enchantment.getEnchantment(entry.getShort("id"));
                if (e != null) {
                    e.setLevel(entry.getShort("lvl"));
                    return e;
                }
            }
        }

        return null;
    }

    public void addEnchantment(Enchantment... enchantments) {
        CompoundTag tag;
        if (!this.hasCompoundTag()) {
            tag = new CompoundTag();
        } else {
            tag = this.getNamedTag();
        }

        ListTag<CompoundTag> ench;
        if (!tag.contains("ench")) {
            ench = new ListTag<>("ench");
            tag.putList(ench);
        } else {
            ench = tag.getList("ench", CompoundTag.class);
        }

        for (Enchantment enchantment : enchantments) {
            boolean found = false;

            for (int k = 0; k < ench.size(); k++) {
                CompoundTag entry = ench.get(k);
                if (entry.getShort("id") == enchantment.getId()) {
                    ench.add(k, new CompoundTag()
                            .putShort("id", enchantment.getId())
                            .putShort("lvl", enchantment.getLevel())
                    );
                    found = true;
                    break;
                }
            }

            if (!found) {
                ench.add(new CompoundTag()
                        .putShort("id", enchantment.getId())
                        .putShort("lvl", enchantment.getLevel())
                );
            }
        }

        this.setNamedTag(tag);
    }

    public Enchantment[] getEnchantments() {
        if (!this.hasEnchantments()) {
            return Enchantment.EMPTY_ARRAY;
        }

        List<Enchantment> enchantments = new ArrayList<>();

        ListTag<CompoundTag> ench = this.getNamedTag().getList("ench", CompoundTag.class);
        for (CompoundTag entry : ench.getAll()) {
            Enchantment e = Enchantment.getEnchantment(entry.getShort("id"));
            if (e != null) {
                e.setLevel(entry.getShort("lvl"));
                enchantments.add(e);
            }
        }

        return enchantments.toArray(Enchantment.EMPTY_ARRAY);
    }

    public boolean hasEnchantment(int id) {
        Enchantment e = this.getEnchantment(id);
        return e != null && e.getLevel() > 0;
    }

    public boolean hasEnchantment(short id) {
        return this.getEnchantment(id) != null;
    }

    public boolean hasCustomName() {
        if (!this.hasCompoundTag()) {
            return false;
        }

        CompoundTag tag = this.getNamedTag();
        if (tag.contains("display")) {
            Tag tag1 = tag.get("display");
            return tag1 instanceof CompoundTag && ((CompoundTag) tag1).contains("Name") && ((CompoundTag) tag1).get("Name") instanceof StringTag;
        }

        return false;
    }

    public String getCustomName() {
        if (!this.hasCompoundTag()) {
            return "";
        }

        CompoundTag tag = this.getNamedTag();
        if (tag.contains("display")) {
            Tag tag1 = tag.get("display");
            if (tag1 instanceof CompoundTag && ((CompoundTag) tag1).contains("Name") && ((CompoundTag) tag1).get("Name") instanceof StringTag) {
                return ((CompoundTag) tag1).getString("Name");
            }
        }

        return "";
    }

    public Item setCustomName(String name) {
        if (name == null || name.isEmpty()) {
            this.clearCustomName();
            return this;
        }

        if (name.length() > 100) {
            name = name.substring(0, 100);
        }

        CompoundTag tag;
        if (!this.hasCompoundTag()) {
            tag = new CompoundTag();
        } else {
            tag = this.getNamedTag();
        }
        if (tag.contains("display") && tag.get("display") instanceof CompoundTag) {
            tag.getCompound("display").putString("Name", name);
        } else {
            tag.putCompound("display", new CompoundTag("display")
                    .putString("Name", name)
            );
        }
        this.setNamedTag(tag);
        return this;
    }

    public Item clearCustomName() {
        if (!this.hasCompoundTag()) {
            return this;
        }

        CompoundTag tag = this.getNamedTag();

        if (tag.contains("display") && tag.get("display") instanceof CompoundTag) {
            tag.getCompound("display").remove("Name");
            if (tag.getCompound("display").isEmpty()) {
                tag.remove("display");
            }

            this.setNamedTag(tag);
        }

        return this;
    }

    public String[] getLore() {
        Tag tag = this.getNamedTagEntry("display");
        ArrayList<String> lines = new ArrayList<>();

        if (tag instanceof CompoundTag) {
            CompoundTag nbt = (CompoundTag) tag;
            ListTag<StringTag> lore = nbt.getList("Lore", StringTag.class);

            if (lore.size() > 0) {
                for (StringTag stringTag : lore.getAll()) {
                    lines.add(stringTag.data);
                }
            }
        }

        return lines.toArray(new String[0]);
    }

    public Item setLore(String... lines) {
        CompoundTag tag;
        if (!this.hasCompoundTag()) {
            tag = new CompoundTag();
        } else {
            tag = this.getNamedTag();
        }
        ListTag<StringTag> lore = new ListTag<>("Lore");

        for (String line : lines) {
            lore.add(new StringTag("", line));
        }

        if (!tag.contains("display")) {
            tag.putCompound("display", new CompoundTag("display").putList(lore));
        } else {
            tag.getCompound("display").putList(lore);
        }

        this.setNamedTag(tag);
        return this;
    }

    public Tag getNamedTagEntry(String name) {
        CompoundTag tag = this.getNamedTag();
        if (tag != null) {
            return tag.contains(name) ? tag.get(name) : null;
        }

        return null;
    }

    public CompoundTag getNamedTag() {
        if (!this.hasCompoundTag()) {
            return null;
        }

        if (this.cachedNBT == null) {
            this.cachedNBT = parseCompoundTag(this.tags);
        }

        this.cachedNBT.setName("");

        return this.cachedNBT;
    }

    public CompoundTag getOrCreateNamedTag() {
        if (!this.hasCompoundTag()) {
            return new CompoundTag();
        }
        return this.getNamedTag();
    }

    public Item setNamedTag(CompoundTag tag) {
        if (tag.isEmpty()) {
            return this.clearNamedTag();
        }
        tag.setName(null);

        this.cachedNBT = tag;
        this.tags = writeCompoundTag(tag);

        return this;
    }

    public Item clearNamedTag() {
        return this.setCompoundTag(new byte[0]);
    }

    public static CompoundTag parseCompoundTag(byte[] tag) {
        try {
            return NBTIO.read(tag, ByteOrder.LITTLE_ENDIAN);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] writeCompoundTag(CompoundTag tag) {
        try {
            tag.setName("");
            return NBTIO.write(tag, ByteOrder.LITTLE_ENDIAN);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public boolean isNull() {
        return this.count <= 0 || this.id == AIR;
    }

    final public String getName() {
        return this.hasCustomName() ? this.getCustomName() : this.name;
    }

    @NotNull
    final public String getDisplayName() {
        return this.hasCustomName() ? this.getCustomName() : this.name == null ? StringItem.createItemName(getNamespaceId()) : name;
    }

    final public boolean canBePlaced() {
        return ((this.block != null) && this.block.canBePlaced());
    }

    @NotNull
    public Block getBlock() {
        if (this.block != null) {
            return this.block.clone();
        } else {
            return Block.get(BlockID.AIR);
        }
    }

    public Block getBlockUnsafe() {
        return this.block;
    }

    public int getBlockId() {
        return block == null ? 0 : block.getId();
    }

    public int getId() {
        return id;
    }

    public int getDamage() {
        return meta == 0xffff ? 0 : meta;
    }

    public void setDamage(Integer meta) {
        if (meta != null) {
            this.meta = meta & 0xffff;
        } else {
            this.hasMeta = false;
        }
    }

    public int getMaxStackSize() {
        return 64;
    }

    final public Short getFuelTime() {
        if (!Fuel.duration.containsKey(id)) {
            return null;
        }
        if (this.id != BUCKET || this.meta == 10) {
            return Fuel.duration.get(this.id);
        }
        return null;
    }

    public boolean useOn(Entity entity) {
        return false;
    }

    public boolean useOn(Block block) {
        return false;
    }

    public boolean isTool() {
        return false;
    }

    public int getMaxDurability() {
        return -1;
    }

    public int getTier() {
        return 0;
    }

    public boolean isPickaxe() {
        return false;
    }

    public boolean isAxe() {
        return false;
    }

    public boolean isSword() {
        return false;
    }

    public boolean isShovel() {
        return false;
    }

    public boolean isHoe() {
        return false;
    }

    public boolean isShears() {
        return false;
    }

    public boolean isArmor() {
        return false;
    }

    public boolean isHelmet() {
        return false;
    }

    public boolean canBePutInHelmetSlot() {
        return false;
    }

    public boolean isChestplate() {
        return false;
    }

    public boolean isLeggings() {
        return false;
    }

    public boolean isBoots() {
        return false;
    }

    public int getEnchantAbility() {
        return 0;
    }

    public int getAttackDamage() {
        return 1;
    }

    public int getArmorPoints() {
        return 0;
    }

    public int getToughness() {
        return 0;
    }

    public boolean isUnbreakable() {
        return false;
    }

    public boolean canBreakShield() {
        return false;
    }

    public boolean onUse(Player player, int ticksUsed) {
        return false;
    }

    public boolean onRelease(Player player, int ticksUsed) {
        return false;
    }

    @Override
    final public String toString() {
        return "Item " + this.name + " (" + (this instanceof StringItem ? this.getNamespaceId() : this.id) + ':' + (!this.hasMeta ? "?" : this.meta) + ")x" + this.count + (this.hasCompoundTag() ? " tags:0x" + Binary.bytesToHexString(this.getCompoundTag()) : "");
    }

    public boolean onActivate(Level level, Player player, Block block, Block target, BlockFace face, double fx, double fy, double fz) {
        return false;
    }

    /**
     * Called when a player uses the item on air, for example throwing a projectile.
     * Returns whether the item was changed, for example count decrease or durability change.
     *
     * @param player player
     * @param directionVector direction
     * @return item changed
     */
    public boolean onClickAir(Player player, Vector3 directionVector) {
        return false;
    }

    public boolean canRelease() {
        return false;
    }

    public final Item decrement(int amount) {
        return increment(-amount);
    }

    public final Item increment(int amount) {
        if (count + amount <= 0) {
            return get(0);
        }
        Item cloned = clone();
        cloned.count += amount;
        return cloned;
    }

    @Override
    public final boolean equals(Object item) {
        return item instanceof Item && this.equals((Item) item, true);
    }

    public final boolean equals(Item item, boolean checkDamage) {
        return equals(item, checkDamage, true);
    }

    public final boolean equals(Item item, boolean checkDamage, boolean checkCompound) {
        if (this.id == STRING_IDENTIFIED_ITEM && item.id == STRING_IDENTIFIED_ITEM) {
            if (!this.getNamespaceId(ProtocolInfo.CURRENT_PROTOCOL).equals(item.getNamespaceId(ProtocolInfo.CURRENT_PROTOCOL))) {
                return false;
            }
        } else if (this.id != item.id) {
            return false;
        }
        if (!checkDamage || this.meta == item.meta) {
            if (checkCompound) {
                if (Arrays.equals(this.getCompoundTag(), item.getCompoundTag())) {
                    return true;
                } else if (this.hasCompoundTag() && item.hasCompoundTag()) {
                    return this.getNamedTag().equals(item.getNamedTag());
                }
            } else {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns whether the specified item stack has the same ID, damage, NBT and count as this item stack.
     *
     * @param other item
     * @return equal
     */
    public final boolean equalsExact(Item other) {
        return this.equals(other, true, true) && this.count == other.count;
    }

    public final boolean equalsFast(Item other) {
        if (this.id == STRING_IDENTIFIED_ITEM && other.id == STRING_IDENTIFIED_ITEM) {
            if (!this.getNamespaceId().equals(other.getNamespaceId())) {
                return false;
            }
        }
        return other != null && other.id == this.id && other.meta == this.meta;
    }

    public final boolean deepEquals(Item item) {
        return equals(item, true);
    }

    public final boolean deepEquals(Item item, boolean checkDamage) {
        return equals(item, checkDamage, true);
    }

    public final boolean deepEquals(Item item, boolean checkDamage, boolean checkCompound) {
        return equals(item, checkDamage, checkCompound);
    }

    public int getRepairCost() {
        if (this.hasCompoundTag()) {
            CompoundTag tag = this.getNamedTag();
            if (tag.contains("RepairCost")) {
                Tag repairCost = tag.get("RepairCost");
                if (repairCost instanceof IntTag) {
                    return ((IntTag) repairCost).data;
                }
            }
        }
        return 0;
    }

    public Item setRepairCost(int cost) {
        if (cost <= 0 && this.hasCompoundTag()) {
            return this.setNamedTag(this.getNamedTag().remove("RepairCost"));
        }

        CompoundTag tag;
        if (!this.hasCompoundTag()) {
            tag = new CompoundTag();
        } else {
            tag = this.getNamedTag();
        }
        return this.setNamedTag(tag.putInt("RepairCost", cost));
    }

    @Override
    public Item clone() {
        try {
            Item item = (Item) super.clone();
            item.tags = this.tags.clone();
            item.cachedNBT = null;
            return item;
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    @Deprecated
    public final RuntimeEntry getRuntimeEntry() {
        Server.mvw("Item#getRuntimeEntry()");
        return this.getRuntimeEntry(ProtocolInfo.CURRENT_PROTOCOL);
    }

    @Deprecated
    public final RuntimeEntry getRuntimeEntry(int protocolId) {
        return RuntimeItems.getMapping(protocolId).toRuntime(this.getId(), this.getDamage());
    }

    public final int getNetworkId() {
        Server.mvw("Item#getNetworkId()");
        return this.getNetworkId(ProtocolInfo.CURRENT_PROTOCOL);
    }

    public final int getNetworkId(int protocolId) {
        if (protocolId < ProtocolInfo.v1_16_100) {
            return getId();
        }
        return RuntimeItems.getMapping(protocolId).getNetworkId(this);
    }

    public String getNamespaceId() {
        return this.getNamespaceId(ProtocolInfo.CURRENT_PROTOCOL);
    }

    public String getNamespaceId(int protocolId) {
        if (this.getId() == 0) {
            return "minecraft:air";
        }
        RuntimeItemMapping runtimeMapping = RuntimeItems.getMapping(protocolId);
        return runtimeMapping.getNamespacedIdByNetworkId(this.getNetworkId(protocolId));
    }

    /**
     * 返回物品是否支持指定版本
     * <p>
     * Returns whether the item supports the specified version
     *
     * @param protocolId 协议版本 protocol version
     * @return 是否支持 whether supported
     */
    public boolean isSupportedOn(int protocolId) {
        return true;
    }

    /**
     * 设置物品锁定在玩家的物品栏的模式
     * @param mode lock mode
     */
    public void setItemLockMode(ItemLockMode mode) {
        CompoundTag tag = getOrCreateNamedTag();
        if (mode == ItemLockMode.NONE) {
            tag.remove("minecraft:item_lock");
        } else {
            tag.putByte("minecraft:item_lock", mode.ordinal());
        }
        this.setCompoundTag(tag);
    }

    /**
     * 获取物品锁定在玩家的物品栏的模式
     * <p>
     * Get items locked mode in the player's item inventory
     *
     * @return lock mode
     */
    public ItemLockMode getItemLockMode() {
        CompoundTag tag = getOrCreateNamedTag();
        if (tag.contains("minecraft:item_lock")) {
            return ItemLockMode.values()[tag.getByte("minecraft:item_lock")];
        }
        return ItemLockMode.NONE;
    }

    public enum ItemLockMode {
        NONE,//only used in server
        LOCK_IN_SLOT,
        LOCK_IN_INVENTORY
    }
}
