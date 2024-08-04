package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.customblock.CustomBlockManager;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.customitem.ItemCustomTool;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.MovingObjectPosition;
import cn.nukkit.level.Position;
import cn.nukkit.level.persistence.PersistentDataContainer;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.metadata.MetadataValue;
import cn.nukkit.metadata.Metadatable;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.BlockColor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static cn.nukkit.utils.Utils.dynamic;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@Log4j2
public abstract class Block extends Position implements Metadatable, Cloneable, AxisAlignedBB, BlockID {
    public static final int MAX_BLOCK_ID = dynamic(1039);
    public static final int DATA_BITS = dynamic(6);
    public static final int ID_MASK = 0xfff; //max 4095
    public static final int DATA_SIZE = dynamic(1 << DATA_BITS);
    public static final int DATA_MASK = dynamic(DATA_SIZE - 1);

    @SuppressWarnings("rawtypes")
    public static Class[] list = null;
    public static Block[] fullList = null;
    public static int[] light = null;
    public static int[] lightFilter = null;
    public static boolean[] solid = null;
    public static double[] hardness = null;
    public static boolean[] transparent = null;
    public static boolean[] diffusesSkyLight = null;
    public static boolean[] hasMeta = null;

    private static final boolean[] usesFakeWater = new boolean[MAX_BLOCK_ID];

    public AxisAlignedBB boundingBox = null;
    public int layer = 0;

    /**
     * A commonly used block face pattern
     */
    protected static final int[] FACES2534 = {2, 5, 3, 4};

    protected Block() {}

    public static void init() {
        Block.usesFakeWater[SEAGRASS] = true;
        Block.usesFakeWater[BLOCK_KELP] = true;
        Block.usesFakeWater[415] = true;

        if (list == null) {
            list = new Class[MAX_BLOCK_ID];
            fullList = new Block[MAX_BLOCK_ID * (1 << DATA_BITS)];
            light = new int[MAX_BLOCK_ID];
            lightFilter = new int[MAX_BLOCK_ID];
            solid = new boolean[MAX_BLOCK_ID];
            hardness = new double[MAX_BLOCK_ID];
            transparent = new boolean[MAX_BLOCK_ID];
            diffusesSkyLight = new boolean[MAX_BLOCK_ID];
            hasMeta = new boolean[MAX_BLOCK_ID];

            list[AIR] = BlockAir.class; //0
            list[STONE] = BlockStone.class; //1
            list[GRASS] = BlockGrass.class; //2
            list[DIRT] = BlockDirt.class; //3
            list[COBBLESTONE] = BlockCobblestone.class; //4
            list[PLANKS] = BlockPlanks.class; //5
            list[SAPLING] = BlockSapling.class; //6
            list[BEDROCK] = BlockBedrock.class; //7
            list[WATER] = BlockWater.class; //8
            list[STILL_WATER] = BlockWaterStill.class; //9
            list[LAVA] = BlockLava.class; //10
            list[STILL_LAVA] = BlockLavaStill.class; //11
            list[SAND] = BlockSand.class; //12
            list[GRAVEL] = BlockGravel.class; //13
            list[GOLD_ORE] = BlockOreGold.class; //14
            list[IRON_ORE] = BlockOreIron.class; //15
            list[COAL_ORE] = BlockOreCoal.class; //16
            list[WOOD] = BlockWood.class; //17
            list[LEAVES] = BlockLeaves.class; //18
            list[SPONGE] = BlockSponge.class; //19
            list[GLASS] = BlockGlass.class; //20
            list[LAPIS_ORE] = BlockOreLapis.class; //21
            list[LAPIS_BLOCK] = BlockLapis.class; //22
            list[DISPENSER] = BlockDispenser.class; //23
            list[SANDSTONE] = BlockSandstone.class; //24
            list[NOTEBLOCK] = BlockNoteblock.class; //25
            list[BED_BLOCK] = BlockBed.class; //26
            list[POWERED_RAIL] = BlockRailPowered.class; //27
            list[DETECTOR_RAIL] = BlockRailDetector.class; //28
            list[STICKY_PISTON] = BlockPistonSticky.class; //29
            list[COBWEB] = BlockCobweb.class; //30
            list[TALL_GRASS] = BlockTallGrass.class; //31
            list[DEAD_BUSH] = BlockDeadBush.class; //32
            list[PISTON] = BlockPiston.class; //33
            list[PISTON_HEAD] = BlockPistonHead.class; //34
            list[WOOL] = BlockWool.class; //35
            list[DANDELION] = BlockDandelion.class; //37
            list[FLOWER] = BlockFlower.class; //38
            list[BROWN_MUSHROOM] = BlockMushroomBrown.class; //39
            list[RED_MUSHROOM] = BlockMushroomRed.class; //40
            list[GOLD_BLOCK] = BlockGold.class; //41
            list[IRON_BLOCK] = BlockIron.class; //42
            list[DOUBLE_STONE_SLAB] = BlockDoubleSlabStone.class; //43
            list[STONE_SLAB] = BlockSlabStone.class; //44
            list[BRICKS_BLOCK] = BlockBricks.class; //45
            list[TNT] = BlockTNT.class; //46
            list[BOOKSHELF] = BlockBookshelf.class; //47
            list[MOSS_STONE] = BlockMossStone.class; //48
            list[OBSIDIAN] = BlockObsidian.class; //49
            list[TORCH] = BlockTorch.class; //50
            list[FIRE] = BlockFire.class; //51
            list[MONSTER_SPAWNER] = BlockMobSpawner.class; //52
            list[WOOD_STAIRS] = BlockStairsWood.class; //53
            list[CHEST] = BlockChest.class; //54
            list[REDSTONE_WIRE] = BlockRedstoneWire.class; //55
            list[DIAMOND_ORE] = BlockOreDiamond.class; //56
            list[DIAMOND_BLOCK] = BlockDiamond.class; //57
            list[WORKBENCH] = BlockCraftingTable.class; //58
            list[WHEAT_BLOCK] = BlockWheat.class; //59
            list[FARMLAND] = BlockFarmland.class; //60
            list[FURNACE] = BlockFurnace.class; //61
            list[BURNING_FURNACE] = BlockFurnaceBurning.class; //62
            list[SIGN_POST] = BlockSignPost.class; //63
            list[WOOD_DOOR_BLOCK] = BlockDoorWood.class; //64
            list[LADDER] = BlockLadder.class; //65
            list[RAIL] = BlockRail.class; //66
            list[COBBLESTONE_STAIRS] = BlockStairsCobblestone.class; //67
            list[WALL_SIGN] = BlockWallSign.class; //68
            list[LEVER] = BlockLever.class; //69
            list[STONE_PRESSURE_PLATE] = BlockPressurePlateStone.class; //70
            list[IRON_DOOR_BLOCK] = BlockDoorIron.class; //71
            list[WOODEN_PRESSURE_PLATE] = BlockPressurePlateWood.class; //72
            list[REDSTONE_ORE] = BlockOreRedstone.class; //73
            list[GLOWING_REDSTONE_ORE] = BlockOreRedstoneGlowing.class; //74
            list[UNLIT_REDSTONE_TORCH] = BlockRedstoneTorchUnlit.class;
            list[REDSTONE_TORCH] = BlockRedstoneTorch.class; //76
            list[STONE_BUTTON] = BlockButtonStone.class; //77
            list[SNOW_LAYER] = BlockSnowLayer.class; //78
            list[ICE] = BlockIce.class; //79
            list[SNOW_BLOCK] = BlockSnow.class; //80
            list[CACTUS] = BlockCactus.class; //81
            list[CLAY_BLOCK] = BlockClay.class; //82
            list[SUGARCANE_BLOCK] = BlockSugarcane.class; //83
            list[JUKEBOX] = BlockJukebox.class; //84
            list[FENCE] = BlockFence.class; //85
            list[PUMPKIN] = BlockPumpkin.class; //86
            list[NETHERRACK] = BlockNetherrack.class; //87
            list[SOUL_SAND] = BlockSoulSand.class; //88
            list[GLOWSTONE_BLOCK] = BlockGlowstone.class; //89
            list[NETHER_PORTAL] = BlockNetherPortal.class; //90
            list[LIT_PUMPKIN] = BlockPumpkinLit.class; //91
            list[CAKE_BLOCK] = BlockCake.class; //92
            list[UNPOWERED_REPEATER] = BlockRedstoneRepeaterUnpowered.class; //93
            list[POWERED_REPEATER] = BlockRedstoneRepeaterPowered.class; //94
            list[INVISIBLE_BEDROCK] = BlockBedrockInvisible.class; //95
            list[TRAPDOOR] = BlockTrapdoor.class; //96
            list[MONSTER_EGG] = BlockMonsterEgg.class; //97
            list[STONE_BRICKS] = BlockBricksStone.class; //98
            list[BROWN_MUSHROOM_BLOCK] = BlockHugeMushroomBrown.class; //99
            list[RED_MUSHROOM_BLOCK] = BlockHugeMushroomRed.class; //100
            list[IRON_BARS] = BlockIronBars.class; //101
            list[GLASS_PANE] = BlockGlassPane.class; //102
            list[MELON_BLOCK] = BlockMelon.class; //103
            list[PUMPKIN_STEM] = BlockStemPumpkin.class; //104
            list[MELON_STEM] = BlockStemMelon.class; //105
            list[VINE] = BlockVine.class; //106
            list[FENCE_GATE] = BlockFenceGate.class; //107
            list[BRICK_STAIRS] = BlockStairsBrick.class; //108
            list[STONE_BRICK_STAIRS] = BlockStairsStoneBrick.class; //109
            list[MYCELIUM] = BlockMycelium.class; //110
            list[WATER_LILY] = BlockWaterLily.class; //111
            list[NETHER_BRICKS] = BlockBricksNether.class; //112
            list[NETHER_BRICK_FENCE] = BlockFenceNetherBrick.class; //113
            list[NETHER_BRICKS_STAIRS] = BlockStairsNetherBrick.class; //114
            list[NETHER_WART_BLOCK] = BlockNetherWart.class; //115
            list[ENCHANTING_TABLE] = BlockEnchantingTable.class; //116
            list[BREWING_STAND_BLOCK] = BlockBrewingStand.class; //117
            list[CAULDRON_BLOCK] = BlockCauldron.class; //118
            list[END_PORTAL] = BlockEndPortal.class; //119
            list[END_PORTAL_FRAME] = BlockEndPortalFrame.class; //120
            list[END_STONE] = BlockEndStone.class; //121
            list[DRAGON_EGG] = BlockDragonEgg.class; //122
            list[REDSTONE_LAMP] = BlockRedstoneLamp.class; //123
            list[LIT_REDSTONE_LAMP] = BlockRedstoneLampLit.class; //124
            list[DROPPER] = BlockDropper.class; //125
            list[ACTIVATOR_RAIL] = BlockRailActivator.class; //126
            list[COCOA] = BlockCocoa.class; //127
            list[SANDSTONE_STAIRS] = BlockStairsSandstone.class; //128
            list[EMERALD_ORE] = BlockOreEmerald.class; //129
            list[ENDER_CHEST] = BlockEnderChest.class; //130
            list[TRIPWIRE_HOOK] = BlockTripWireHook.class;
            list[TRIPWIRE] = BlockTripWire.class; //132
            list[EMERALD_BLOCK] = BlockEmerald.class; //133
            list[SPRUCE_WOOD_STAIRS] = BlockStairsSpruce.class; //134
            list[BIRCH_WOOD_STAIRS] = BlockStairsBirch.class; //135
            list[JUNGLE_WOOD_STAIRS] = BlockStairsJungle.class; //136
            list[COMMAND_BLOCK] = BlockCommandBlock.class; //137
            list[BEACON] = BlockBeacon.class; //138
            list[STONE_WALL] = BlockWall.class; //139
            list[FLOWER_POT_BLOCK] = BlockFlowerPot.class; //140
            list[CARROT_BLOCK] = BlockCarrot.class; //141
            list[POTATO_BLOCK] = BlockPotato.class; //142
            list[WOODEN_BUTTON] = BlockButtonWooden.class; //143
            list[SKULL_BLOCK] = BlockSkull.class; //144
            list[ANVIL] = BlockAnvil.class; //145
            list[TRAPPED_CHEST] = BlockTrappedChest.class; //146
            list[LIGHT_WEIGHTED_PRESSURE_PLATE] = BlockWeightedPressurePlateLight.class; //147
            list[HEAVY_WEIGHTED_PRESSURE_PLATE] = BlockWeightedPressurePlateHeavy.class; //148
            list[UNPOWERED_COMPARATOR] = BlockRedstoneComparatorUnpowered.class; //149
            list[POWERED_COMPARATOR] = BlockRedstoneComparatorPowered.class; //149
            list[DAYLIGHT_DETECTOR] = BlockDaylightDetector.class; //151
            list[REDSTONE_BLOCK] = BlockRedstone.class; //152
            list[QUARTZ_ORE] = BlockOreQuartz.class; //153
            list[HOPPER_BLOCK] = BlockHopper.class; //154
            list[QUARTZ_BLOCK] = BlockQuartz.class; //155
            list[QUARTZ_STAIRS] = BlockStairsQuartz.class; //156
            list[DOUBLE_WOOD_SLAB] = BlockDoubleSlabWood.class; //157
            list[WOOD_SLAB] = BlockSlabWood.class; //158
            list[STAINED_TERRACOTTA] = BlockTerracottaStained.class; //159
            list[STAINED_GLASS_PANE] = BlockGlassPaneStained.class; //160
            list[LEAVES2] = BlockLeaves2.class; //161
            list[WOOD2] = BlockWood2.class; //162
            list[ACACIA_WOOD_STAIRS] = BlockStairsAcacia.class; //163
            list[DARK_OAK_WOOD_STAIRS] = BlockStairsDarkOak.class; //164
            list[SLIME_BLOCK] = BlockSlime.class; //165
            list[GLOW_STICK] = BlockGlowStick.class; //166
            list[IRON_TRAPDOOR] = BlockTrapdoorIron.class; //167
            list[PRISMARINE] = BlockPrismarine.class; //168
            list[SEA_LANTERN] = BlockSeaLantern.class; //169
            list[HAY_BALE] = BlockHayBale.class; //170
            list[CARPET] = BlockCarpet.class; //171
            list[TERRACOTTA] = BlockTerracotta.class; //172
            list[COAL_BLOCK] = BlockCoal.class; //173
            list[PACKED_ICE] = BlockIcePacked.class; //174
            list[DOUBLE_PLANT] = BlockDoublePlant.class; //175
            list[STANDING_BANNER] = BlockBanner.class; //176
            list[WALL_BANNER] = BlockWallBanner.class; //177
            list[DAYLIGHT_DETECTOR_INVERTED] = BlockDaylightDetectorInverted.class; //178
            list[RED_SANDSTONE] = BlockRedSandstone.class; //179
            list[RED_SANDSTONE_STAIRS] = BlockStairsRedSandstone.class; //180
            list[DOUBLE_RED_SANDSTONE_SLAB] = BlockDoubleSlabRedSandstone.class; //181
            list[RED_SANDSTONE_SLAB] = BlockSlabRedSandstone.class; //182
            list[FENCE_GATE_SPRUCE] = BlockFenceGateSpruce.class; //183
            list[FENCE_GATE_BIRCH] = BlockFenceGateBirch.class; //184
            list[FENCE_GATE_JUNGLE] = BlockFenceGateJungle.class; //185
            list[FENCE_GATE_DARK_OAK] = BlockFenceGateDarkOak.class; //186
            list[FENCE_GATE_ACACIA] = BlockFenceGateAcacia.class; //187
            list[REPEATING_COMMAND_BLOCK] = BlockCommandBlockRepeating.class; //188
            list[CHAIN_COMMAND_BLOCK] = BlockCommandBlockChain.class; //189
            list[HARD_GLASS_PANE] = BlockHardGlassPane.class; //190
            list[HARD_STAINED_GLASS_PANE] = BlockHardGlassPaneStained.class; //191
            list[CHEMICAL_HEAT] = BlockChemicalHeat.class; //192
            list[SPRUCE_DOOR_BLOCK] = BlockDoorSpruce.class; //193
            list[BIRCH_DOOR_BLOCK] = BlockDoorBirch.class; //194
            list[JUNGLE_DOOR_BLOCK] = BlockDoorJungle.class; //195
            list[ACACIA_DOOR_BLOCK] = BlockDoorAcacia.class; //196
            list[DARK_OAK_DOOR_BLOCK] = BlockDoorDarkOak.class; //197
            list[GRASS_PATH] = BlockGrassPath.class; //198
            list[ITEM_FRAME_BLOCK] = BlockItemFrame.class; //199
            list[CHORUS_FLOWER] = BlockChorusFlower.class; //200
            list[PURPUR_BLOCK] = BlockPurpur.class; //201
            list[COLORED_TORCH_RG] = BlockColoredTorchRG.class; //202
            list[PURPUR_STAIRS] = BlockStairsPurpur.class; //203
            list[COLORED_TORCH_BP] = BlockColoredTorchBP.class; //204
            list[UNDYED_SHULKER_BOX] = BlockUndyedShulkerBox.class; //205
            list[END_BRICKS] = BlockBricksEndStone.class; //206
            list[FROSTED_ICE] = BlockIceFrosted.class; //207
            list[END_ROD] = BlockEndRod.class; //208
            list[END_GATEWAY] = BlockEndGateway.class; //209
            // 210 Allow in Education Edition
            // 211 Deny in Education Edition
            list[BORDER_BLOCK] = BlockBorder.class; //212
            list[MAGMA] = BlockMagma.class; //213
            list[BLOCK_NETHER_WART_BLOCK] = BlockNetherWartBlock.class; //214
            list[RED_NETHER_BRICK] = BlockBricksRedNether.class; //215
            list[BONE_BLOCK] = BlockBone.class; //216
            // 217 not yet in Minecraft
            list[SHULKER_BOX] = BlockShulkerBox.class; //218
            list[PURPLE_GLAZED_TERRACOTTA] = BlockTerracottaGlazedPurple.class; //219
            list[WHITE_GLAZED_TERRACOTTA] = BlockTerracottaGlazedWhite.class; //220
            list[ORANGE_GLAZED_TERRACOTTA] = BlockTerracottaGlazedOrange.class; //221
            list[MAGENTA_GLAZED_TERRACOTTA] = BlockTerracottaGlazedMagenta.class; //222
            list[LIGHT_BLUE_GLAZED_TERRACOTTA] = BlockTerracottaGlazedLightBlue.class; //223
            list[YELLOW_GLAZED_TERRACOTTA] = BlockTerracottaGlazedYellow.class; //224
            list[LIME_GLAZED_TERRACOTTA] = BlockTerracottaGlazedLime.class; //225
            list[PINK_GLAZED_TERRACOTTA] = BlockTerracottaGlazedPink.class; //226
            list[GRAY_GLAZED_TERRACOTTA] = BlockTerracottaGlazedGray.class; //227
            list[SILVER_GLAZED_TERRACOTTA] = BlockTerracottaGlazedSilver.class; //228
            list[CYAN_GLAZED_TERRACOTTA] = BlockTerracottaGlazedCyan.class; //229
            // 230 Chalkboard in Education Edition
            list[BLUE_GLAZED_TERRACOTTA] = BlockTerracottaGlazedBlue.class; //231
            list[BROWN_GLAZED_TERRACOTTA] = BlockTerracottaGlazedBrown.class; //232
            list[GREEN_GLAZED_TERRACOTTA] = BlockTerracottaGlazedGreen.class; //233
            list[RED_GLAZED_TERRACOTTA] = BlockTerracottaGlazedRed.class; //234
            list[BLACK_GLAZED_TERRACOTTA] = BlockTerracottaGlazedBlack.class; //235
            list[CONCRETE] = BlockConcrete.class; //236
            list[CONCRETE_POWDER] = BlockConcretePowder.class; //237
            list[CHEMISTRY_TABLE] = BlockChemistryTable.class; //238
            list[UNDERWATER_TORCH] = BlockUnderwaterTorch.class; //239
            list[CHORUS_PLANT] = BlockChorusPlant.class; //240
            list[STAINED_GLASS] = BlockGlassStained.class; //241
            list[CAMERA_BLOCK] = BlockCamera.class; //242
            list[PODZOL] = BlockPodzol.class; //243
            list[BEETROOT_BLOCK] = BlockBeetroot.class; //244
            list[STONECUTTER] = BlockStonecutter.class; //244
            list[GLOWING_OBSIDIAN] = BlockObsidianGlowing.class; //246
            list[NETHER_REACTOR] = BlockNetherReactor.class; //247
            list[INFO_UPDATE] = BlockInfoUpdate.class; //248
            list[INFO_UPDATE2] = BlockInfoUpdate2.class; //249
            list[PISTON_EXTENSION] = BlockPistonExtension.class; //250
            list[OBSERVER] = BlockObserver.class; //251
            list[STRUCTURE_BLOCK] = BlockStructureBlock.class; //252
            list[HARD_GLASS] = BlockHardGlass.class; //253
            list[HARD_STAINED_GLASS] = BlockHardGlassStained.class; //254
            list[RESERVED6] = BlockReserved6.class; //255
            // 256 not yet in Minecraft
            list[PRISMARINE_STAIRS] = BlockStairsPrismarine.class; //257
            list[DARK_PRISMARINE_STAIRS] = BlockStairsDarkPrismarine.class; //258
            list[PRISMARINE_BRICKS_STAIRS] = BlockStairsPrismarineBrick.class; //259
            list[STRIPPED_SPRUCE_LOG] = BlockWoodStrippedSpruce.class; //260
            list[STRIPPED_BIRCH_LOG] = BlockWoodStrippedBirch.class; //261
            list[STRIPPED_JUNGLE_LOG] = BlockWoodStrippedJungle.class; //262
            list[STRIPPED_ACACIA_LOG] = BlockWoodStrippedAcacia.class; //263
            list[STRIPPED_DARK_OAK_LOG] = BlockWoodStrippedDarkOak.class; //264
            list[STRIPPED_OAK_LOG] = BlockWoodStrippedOak.class; //265
            list[BLUE_ICE] = BlockBlueIce.class; //266

            list[SEAGRASS] = BlockSeagrass.class; //385
            list[CORAL] = BlockCoral.class; //386
            list[CORAL_BLOCK] = BlockCoralBlock.class; //387
            list[CORAL_FAN] = BlockCoralFan.class; //388
            list[CORAL_FAN_DEAD] = BlockCoralFanDead.class; //389
            list[CORAL_FAN_HANG] = BlockCoralFanHang.class; //390
            list[CORAL_FAN_HANG2] = BlockCoralFanHang2.class; //391
            list[CORAL_FAN_HANG3] = BlockCoralFanHang3.class; //392
            list[BLOCK_KELP] = BlockKelp.class; //393
            list[DRIED_KELP_BLOCK] = BlockDriedKelpBlock.class; //394
            list[ACACIA_BUTTON] = BlockButtonAcacia.class; //395
            list[BIRCH_BUTTON] = BlockButtonBirch.class; //396
            list[DARK_OAK_BUTTON] = BlockButtonDarkOak.class; //397
            list[JUNGLE_BUTTON] = BlockButtonJungle.class; //398
            list[SPRUCE_BUTTON] = BlockButtonSpruce.class; //399
            list[ACACIA_TRAPDOOR] = BlockTrapdoorAcacia.class; //400
            list[BIRCH_TRAPDOOR] = BlockTrapdoorBirch.class; //401
            list[DARK_OAK_TRAPDOOR] = BlockTrapdoorDarkOak.class; //402
            list[JUNGLE_TRAPDOOR] = BlockTrapdoorJungle.class; //403
            list[SPRUCE_TRAPDOOR] = BlockTrapdoorSpruce.class; //404
            list[ACACIA_PRESSURE_PLATE] = BlockPressurePlateAcacia.class; //405
            list[BIRCH_PRESSURE_PLATE] = BlockPressurePlateBirch.class; //406
            list[DARK_OAK_PRESSURE_PLATE] = BlockPressurePlateDarkOak.class; //407
            list[JUNGLE_PRESSURE_PLATE] = BlockPressurePlateJungle.class; //408
            list[SPRUCE_PRESSURE_PLATE] = BlockPressurePlateSpruce.class; //409
            list[CARVED_PUMPKIN] = BlockCarvedPumpkin.class; //410
            list[SEA_PICKLE] = BlockSeaPickle.class; //411
            list[CONDUIT] = BlockConduit.class; //412
            // 413 not yet in Minecraft
            list[TURTLE_EGG] = BlockTurtleEgg.class; //414
            list[BUBBLE_COLUMN] = BlockBubbleColumn.class; //415
            list[BARRIER] = BlockBarrier.class; //416
            list[STONE_SLAB3] = BlockSlabStone3.class; //417
            list[BAMBOO] = BlockBamboo.class; //418
            list[BAMBOO_SAPLING] = BlockBambooSapling.class; //419
            list[SCAFFOLDING] = BlockScaffolding.class; //420
            list[STONE_SLAB4] = BlockSlabStone4.class; //421
            list[DOUBLE_STONE_SLAB3] = BlockDoubleSlabStone3.class; //422
            list[DOUBLE_STONE_SLAB4] = BlockDoubleSlabStone4.class; //423
            list[GRANITE_STAIRS] = BlockStairsGranite.class; //424
            list[DIORITE_STAIRS] = BlockStairsDiorite.class; //425
            list[ANDESITE_STAIRS] = BlockStairsAndesite.class; //426
            list[POLISHED_GRANITE_STAIRS] = BlockStairsGranitePolished.class; //427
            list[POLISHED_DIORITE_STAIRS] = BlockStairsDioritePolished.class; //428
            list[POLISHED_ANDESITE_STAIRS] = BlockStairsAndesitePolished.class; //429
            list[MOSSY_STONE_BRICK_STAIRS] = BlockStairsMossyStoneBrick.class; //430
            list[SMOOTH_RED_SANDSTONE_STAIRS] = BlockStairsSmoothRedSandstone.class; //431
            list[SMOOTH_SANDSTONE_STAIRS] = BlockStairsSmoothSandstone.class; //432
            list[END_BRICK_STAIRS] = BlockStairsEndBrick.class; //433
            list[MOSSY_COBBLESTONE_STAIRS] = BlockStairsMossyCobblestone.class; //434
            list[NORMAL_STONE_STAIRS] = BlockStairsStone.class; //435
            list[SPRUCE_STANDING_SIGN] = BlockSpruceSignPost.class; //436
            list[SPRUCE_WALL_SIGN] = BlockSpruceWallSign.class; //437
            list[SMOOTH_STONE] = BlockSmoothStone.class; //438
            list[RED_NETHER_BRICK_STAIRS] = BlockStairsRedNetherBrick.class; //439
            list[SMOOTH_QUARTZ_STAIRS] = BlockStairsSmoothQuartz.class; //440
            list[BIRCH_STANDING_SIGN] = BlockBirchSignPost.class; //441
            list[BIRCH_WALL_SIGN] = BlockBirchWallSign.class; //442
            list[JUNGLE_STANDING_SIGN] = BlockJungleSignPost.class; //443
            list[JUNGLE_WALL_SIGN] = BlockJungleWallSign.class; //444
            list[ACACIA_STANDING_SIGN] = BlockAcaciaSignPost.class; //445
            list[ACACIA_WALL_SIGN] = BlockAcaciaWallSign.class; //446
            list[DARKOAK_STANDING_SIGN] = BlockDarkOakSignPost.class; //447
            list[DARKOAK_WALL_SIGN] = BlockDarkOakWallSign.class; //448
            list[LECTERN] = BlockLectern.class; //449
            list[GRINDSTONE] = BlockGrindstone.class; //450
            list[BLAST_FURNACE] = BlockBlastFurnace.class; //451
            list[SMOKER] = BlockSmoker.class; //453
            list[LIT_SMOKER] = BlockSmokerLit.class; //454
            list[CARTOGRAPHY_TABLE] = BlockCartographyTable.class; //455
            list[FLETCHING_TABLE] = BlockFletchingTable.class; //456
            list[SMITHING_TABLE] = BlockSmithingTable.class; //457
            list[BARREL] = BlockBarrel.class; //458

            list[BELL] = BlockBell.class; //461
            list[SWEET_BERRY_BUSH] = BlockSweetBerryBush.class; //462
            list[LANTERN] = BlockLantern.class; //463
            list[CAMPFIRE_BLOCK] = BlockCampfire.class; //464
            list[LAVA_CAULDRON] = BlockCauldronLava.class; //465
            list[WOOD_BARK] = BlockWoodBark.class; //467
            list[COMPOSTER] = BlockComposter.class; //468
            list[LIT_BLAST_FURNACE] = BlockBlastFurnaceLit.class; //469
            list[LIGHT_BLOCK] = BlockLightBlock.class; //470
            list[WITHER_ROSE] = BlockWitherRose.class; //471
            list[PISTON_HEAD_STICKY] = BlockPistonHeadSticky.class; //472
            list[BEE_NEST] = BlockBeeNest.class; //473
            list[BEEHIVE] = BlockBeehive.class; //474
            list[HONEY_BLOCK] = BlockHoneyBlock.class; //475
            list[HONEYCOMB_BLOCK] = BlockHoneycombBlock.class; //476
            list[LODESTONE] = BlockLodestone.class; //477
            list[CRIMSON_ROOTS] = BlockRootsCrimson.class; //478
            list[WARPED_ROOTS] = BlockRootsWarped.class; //479
            list[CRIMSON_STEM] = BlockStemCrimson.class; //480
            list[WARPED_STEM] = BlockStemWarped.class; //481
            list[WARPED_WART_BLOCK] = BlockWarpedWartBlock.class; //482
            list[CRIMSON_FUNGUS] = BlockFungusCrimson.class; //483
            list[WARPED_FUNGUS] = BlockFungusWarped.class; //484
            list[SHROOMLIGHT] = BlockShroomlight.class; //485

            list[CRIMSON_NYLIUM] = BlockNyliumCrimson.class; //487
            list[WARPED_NYLIUM] = BlockNyliumWarped.class; //488
            list[BASALT] = BlockBasalt.class; //489

            list[SOUL_SOIL] = BlockSoulSoil.class; //491

            list[NETHER_SPROUTS_BLOCK] = BlockNetherSprouts.class; //493
            list[TARGET] = BlockTarget.class; //494

            list[STRIPPED_CRIMSON_STEM] = BlockStemStrippedCrimson.class; //495
            list[STRIPPED_WARPED_STEM] = BlockStemStrippedWarped.class; //496
            list[CRIMSON_PLANKS] = BlockPlanksCrimson.class; //497
            list[WARPED_PLANKS] = BlockPlanksWarped.class; //498
            list[CRIMSON_DOOR_BLOCK] = BlockDoorCrimson.class; //499
            list[WARPED_DOOR_BLOCK] = BlockDoorWarped.class; //500
            list[CRIMSON_TRAPDOOR] = BlockTrapdoorCrimson.class; //501
            list[WARPED_TRAPDOOR] = BlockTrapdoorWarped.class; //502

            list[CRIMSON_STANDING_SIGN] = BlockCrimsonSignPost.class; //505
            list[WARPED_STANDING_SIGN] = BlockWarpedSignPost.class; //506
            list[CRIMSON_WALL_SIGN] = BlockCrimsonWallSign.class; //507
            list[WARPED_WALL_SIGN] = BlockWarpedWallSign.class; //508
            list[CRIMSON_STAIRS] = BlockStairsCrimson.class; //509
            list[WARPED_STAIRS] = BlockStairsWarped.class; //510
            list[CRIMSON_FENCE] = BlockFenceCrimson.class; //511
            list[WARPED_FENCE] = BlockFenceWarped.class; //512
            list[CRIMSON_FENCE_GATE] = BlockFenceGateCrimson.class; //513
            list[WARPED_FENCE_GATE] = BlockFenceGateWarped.class; //514
            list[CRIMSON_BUTTON] = BlockButtonCrimson.class; //515
            list[WARPED_BUTTON] = BlockButtonWarped.class; //516
            list[CRIMSON_PRESSURE_PLATE] = BlockPressurePlateCrimson.class; //517
            list[WARPED_PRESSURE_PLATE] = BlockPressurePlateWarped.class; //518
            list[CRIMSON_SLAB] = BlockSlabCrimson.class; //519
            list[WARPED_SLAB] = BlockSlabWarped.class; //520
            list[CRIMSON_DOUBLE_SLAB] = BlockDoubleSlabCrimson.class; //521
            list[WARPED_DOUBLE_SLAB] = BlockDoubleSlabWarped.class; //522
            list[SOUL_TORCH] = BlockSoulTorch.class; //523
            list[SOUL_LANTERN] = BlockSoulLantern.class; //524
            list[NETHERITE_BLOCK] = BlockNetheriteBlock.class; //525
            list[ANCIENT_DEBRIS] = BlockAncientDebris.class; //526
            list[RESPAWN_ANCHOR] = BlockRespawnAnchor.class; //527
            list[BLACKSTONE] = BlockBlackstone.class; //528
            list[POLISHED_BLACKSTONE_BRICKS] = BlockBricksBlackstonePolished.class; //529
            list[POLISHED_BLACKSTONE_BRICK_STAIRS] = BlockStairsBrickBlackstonePolished.class; //530
            list[BLACKSTONE_STAIRS] = BlockStairsBlackstone.class; //531
            list[BLACKSTONE_WALL] = BlockWallBlackstone.class; //532
            list[POLISHED_BLACKSTONE_BRICK_WALL] = BlockWallBrickBlackstonePolished.class; //533
            list[CHISELED_POLISHED_BLACKSTONE] = BlockBlackstonePolishedChiseled.class; //534
            list[CRACKED_POLISHED_BLACKSTONE_BRICKS] = BlockBricksBlackstonePolishedCracked.class; //535
            list[GILDED_BLACKSTONE] = BlockBlackstoneGilded.class; //536
            list[BLACKSTONE_SLAB] = BlockSlabBlackstone.class; //537
            list[BLACKSTONE_DOUBLE_SLAB] = BlockDoubleSlabBlackstone.class; //538
            list[POLISHED_BLACKSTONE_BRICK_SLAB] = BlockSlabBrickBlackstonePolished.class; //539
            list[POLISHED_BLACKSTONE_BRICK_DOUBLE_SLAB] = BlockDoubleSlabBrickBlackstonePolished.class; //540
            list[CHAIN_BLOCK] = BlockChain.class; //541
            list[TWISTING_VINES] = BlockVinesTwisting.class; //542
            list[NETHER_GOLD_ORE] = BlockOreGoldNether.class; //543
            list[CRYING_OBSIDIAN] = BlockCryingObsidian.class; //544
            list[SOUL_CAMPFIRE_BLOCK] = BlockCampfireSoul.class; //545
            list[POLISHED_BLACKSTONE] = BlockBlackstonePolished.class; //546
            list[POLISHED_BLACKSTONE_STAIRS] = BlockStairsBlackstonePolished.class; //547
            list[POLISHED_BLACKSTONE_SLAB] = BlockSlabBlackstonePolished.class; //548
            list[POLISHED_BLACKSTONE_DOUBLE_SLAB] = BlockDoubleSlabBlackstonePolished.class; //549
            list[POLISHED_BLACKSTONE_PRESSURE_PLATE] = BlockPressurePlateBlackstonePolished.class; //550
            list[POLISHED_BLACKSTONE_BUTTON] = BlockButtonBlackstonePolished.class; //551
            list[POLISHED_BLACKSTONE_WALL] = BlockWallBlackstonePolished.class; //552
            list[WARPED_HYPHAE] = BlockHyphaeWarped.class; //553
            list[CRIMSON_HYPHAE] = BlockHyphaeCrimson.class; //554
            list[STRIPPED_CRIMSON_HYPHAE] = BlockHyphaeStrippedCrimson.class; //555
            list[STRIPPED_WARPED_HYPHAE] = BlockHyphaeStrippedWarped.class; //556
            list[CHISELED_NETHER_BRICKS] = BlockBricksNetherChiseled.class; //557
            list[CRACKED_NETHER_BRICKS] = BlockBricksNetherCracked.class; //558
            list[QUARTZ_BRICKS] = BlockBricksQuartz.class; //559
            
            list[COPPER_ORE] = BlockOreCopper.class; // 566

            list[AMETHYST_BLOCK] = BlockAmethyst.class; //582

            list[DEEPSLATE] = BlockDeepslate.class; // 633

            list[GLOW_LICHEN] = BlockGlowLichen.class; //666
            list[CANDLE] = BlockCandle.class; //667
            list[WHITE_CANDLE] = BlockCandleWhite.class; //668
            list[ORANGE_CANDLE] = BlockCandleOrange.class; //669
            list[MAGENTA_CANDLE] = BlockCandleMagenta.class; //670
            list[LIGHT_BLUE_CANDLE] = BlockCandleLightBlue.class; //671
            list[YELLOW_CANDLE] = BlockCandleYellow.class; //672
            list[LIME_CANDLE] = BlockCandleLime.class; //673
            list[PINK_CANDLE] = BlockCandlePink.class; //674
            list[GRAY_CANDLE] = BlockCandleGray.class; //675
            list[LIGHT_GRAY_CANDLE] = BlockCandleLightGray.class; //676
            list[CYAN_CANDLE] = BlockCandleCyan.class; //677
            list[PURPLE_CANDLE] = BlockCandlePurple.class; //678
            list[BLUE_CANDLE] = BlockCandleBlue.class; //679
            list[BROWN_CANDLE] = BlockCandleBrown.class; //680
            list[GREEN_CANDLE] = BlockCandleGreen.class; //681
            list[RED_CANDLE] = BlockCandleRed.class; //682
            list[BLACK_CANDLE] = BlockCandleBlack.class; //683
            list[CANDLE_CAKE] = BlockCandleCake.class; //684
            list[WHITE_CANDLE_CAKE] = BlockCandleCakeWhite.class; //685
            list[ORANGE_CANDLE_CAKE] = BlockCandleCakeOrange.class; //686
            list[MAGENTA_CANDLE_CAKE] = BlockCandleCakeMagenta.class; //687
            list[LIGHT_BLUE_CANDLE_CAKE] = BlockCandleCakeLightBlue.class; //688
            list[YELLOW_CANDLE_CAKE] = BlockCandleCakeYellow.class; //689
            list[LIME_CANDLE_CAKE] = BlockCandleCakeLime.class; //690
            list[PINK_CANDLE_CAKE] = BlockCandleCakePink.class; //691
            list[GRAY_CANDLE_CAKE] = BlockCandleCakeGray.class; //692
            list[LIGHT_GRAY_CANDLE_CAKE] = BlockCandleCakeLightGray.class; //693
            list[CYAN_CANDLE_CAKE] = BlockCandleCakeCyan.class; //694
            list[PURPLE_CANDLE_CAKE] = BlockCandleCakePurple.class; //695
            list[BLUE_CANDLE_CAKE] = BlockCandleCakeBlue.class; //696
            list[BROWN_CANDLE_CAKE] = BlockCandleCakeBrown.class; //697
            list[GREEN_CANDLE_CAKE] = BlockCandleCakeGreen.class; //698
            list[RED_CANDLE_CAKE] = BlockCandleCakeRed.class; //699
            list[BLACK_CANDLE_CAKE] = BlockCandleCakeBlack.class; //700

            list[RAW_IRON_BLOCK] = BlockRawIron.class; //706
            list[RAW_COPPER_BLOCK] = BlockRawCopper.class; //707
            list[RAW_GOLD_BLOCK] = BlockRawGold.class; //708

            list[PEARLESCENT_FROGLIGHT] = BlockFrogLightPearlescent.class; //724
            list[VERDANT_FROGLIGHT] = BlockFrogLightVerdant.class; //725
            list[OCHRE_FROGLIGHT] = BlockFrogLightOchre.class; //726

            list[MANGROVE_PLANKS] = BlockPlanksMangrove.class; //741

            list[BAMBOO_PLANKS] = BlockPlanksBamboo.class; //765

            list[SUSPICIOUS_SAND] = BlockSuspiciousSand.class; // 784

            list[STRIPPED_CHERRY_LOG] = BlockLogStrippedCherry.class; //790
            list[CHERRY_LOG] = BlockCherryLog.class; //791
            list[CHERRY_PLANKS] = BlockPlanksCherry.class; //792

            list[STRIPPED_CHERRY_WOOD] = BlockWoodStrippedCherry.class; //800
            list[CHERRY_WOOD] = BlockWoodCherry.class; //801
            list[CHERRY_SAPLING] = BlockCherrySapling.class; //802
            list[CHERRY_LEAVES] = BlockCherryLeaves.class; //803

            list[DECORATED_POT] = BlockDecoratedPot.class; //806

            list[SUSPICIOUS_GRAVEL] = BlockSuspiciousGravel.class; //828

            list[COPPER_BULB] = BlockCopperBulb.class; //1031
            list[EXPOSED_COPPER_BULB] = BlockExposedCopperBulb.class; //1032
            list[WEATHERED_COPPER_BULB] = BlockWeatheredCopperBulb.class; //1033
            list[OXIDIZED_COPPER_BULB] = BlockOxidizedCopperBulb.class; //1034
            list[WAXED_COPPER_BULB] = BlockWaxedCopperBulb.class; //1035
            list[WAXED_EXPOSED_COPPER_BULB] = BlockWaxedExposedCopperBulb.class; //1036
            list[WAXED_WEATHERED_COPPER_BULB] = BlockWaxedWeatheredCopperBulb.class; //1037
            list[WAXED_OXIDIZED_COPPER_BULB] = BlockWaxedOxidizedCopperBulb.class; //1038

            for (int id = 0; id < MAX_BLOCK_ID; id++) {
                Class<?> c = list[id];
                if (c != null) {
                    Block block;
                    try {
                        block = (Block) c.newInstance();
                        try {
                            @SuppressWarnings("rawtypes")
                            Constructor constructor = c.getDeclaredConstructor(int.class);
                            constructor.setAccessible(true);
                            for (int data = 0; data < (1 << DATA_BITS); ++data) {
                                int fullId = (id << DATA_BITS) | data;
                                Block b;
                                try {
                                    b = (Block) constructor.newInstance(data);
                                    if (b.getDamage() != data) {
                                        b = new BlockUnknown(id, data);
                                    }
                                } catch (Exception e) {
                                    Server.getInstance().getLogger().error("Error while registering " + c.getName(), e);
                                    b = new BlockUnknown(id, data);
                                }
                                fullList[fullId] = b;
                            }
                            hasMeta[id] = true;
                        } catch (NoSuchMethodException ignore) {
                            for (int data = 0; data < DATA_SIZE; ++data) {
                                int fullId = (id << DATA_BITS) | data;
                                fullList[fullId] = block;
                            }
                        }
                    } catch (Exception e) {
                        Server.getInstance().getLogger().error("Error while registering " + c.getName(), e);
                        for (int data = 0; data < DATA_SIZE; ++data) {
                            fullList[(id << DATA_BITS) | data] = new BlockUnknown(id, data);
                        }
                        return;
                    }

                    solid[id] = block.isSolid();
                    transparent[id] = block.isTransparent();
                    diffusesSkyLight[id] = block.diffusesSkyLight();
                    hardness[id] = block.getHardness();
                    light[id] = block.getLightLevel();

                    if (block.isSolid()) {
                        if (block.isTransparent()) {
                            if (block instanceof BlockLiquid || block instanceof BlockIce) {
                                lightFilter[id] = 2;
                            } else {
                                lightFilter[id] = 1;
                            }
                        } else if (block instanceof BlockSlime) {
                            lightFilter[id] = 1;
                        } else if (id == CAULDRON_BLOCK) {
                            lightFilter[id] = 3;
                        } else {
                            lightFilter[id] = 15;
                        }
                    } else {
                        lightFilter[id] = 1;
                    }
                } else {
                    lightFilter[id] = 1;
                    for (int data = 0; data < DATA_SIZE; ++data) {
                        fullList[(id << DATA_BITS) | data] = new BlockUnknown(id, data);
                    }
                }
            }
        }
    }

    public static Block get(int id) {
        return get(id, null);
    }

    public static Block get(int id, Integer meta) {
        if (id < 0) {
            id = 255 - id;
        }

        if (id >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            return CustomBlockManager.get().getBlock(id, 0);
        }

        int fullId = id << DATA_BITS;
        if (meta != null) {
            int iMeta = meta;
            if (iMeta <= DATA_SIZE) {
                fullId = fullId | meta;
                if (fullId >= fullList.length || fullList[fullId] == null) {
                    log.warn("Found an unknown BlockId:Meta combination: {}:{}", id, iMeta);
                    return new BlockUnknown(id, iMeta);
                }
                return fullList[fullId].clone();
            } else {
                if (fullId >= fullList.length || fullList[fullId] == null) {
                    log.warn("Found an unknown BlockId:Meta combination: {}:{}", id, iMeta);
                    return new BlockUnknown(id, iMeta);
                }
                Block block = fullList[fullId].clone();
                block.setDamage(iMeta);
                return block;
            }
        } else {
            if (fullId >= fullList.length || fullList[fullId] == null) {
                log.warn("Found an unknown BlockId:Meta combination: {}:{}", id, 0);
                return new BlockUnknown(id, 0);
            }
            return fullList[fullId].clone();
        }
    }

    public static Block get(int id, Integer meta, Position pos) {
        return get(id, meta, pos, 0);
    }

    public static Block get(int id, Integer meta, Position pos, int layer) {
        if (id < 0) {
            id = 255 - id;
        }

        if (id >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            return CustomBlockManager.get().getBlock(id, 0);
        }

        Block block;
        int fullId = id << DATA_BITS;
        if (meta != null && meta > DATA_SIZE) {
            if (fullId >= fullList.length || fullList[fullId] == null) {
                log.warn("Found an unknown BlockId:Meta combination: {}:{}", id, meta);
                return new BlockUnknown(id, meta);
            }
            block = fullList[fullId].clone();
            block.setDamage(meta);
        } else {
            meta = meta == null ? 0 : meta;
            fullId = fullId | meta;
            if (fullId >= fullList.length || fullList[fullId] == null) {
                log.warn("Found an unknown BlockId:Meta combination: {}:{}", id, meta);
                return new BlockUnknown(id, meta);
            }
            block = fullList[fullId].clone();
        }

        if (pos != null) {
            block.x = pos.x;
            block.y = pos.y;
            block.z = pos.z;
            block.level = pos.level;
            block.layer = layer;
        }
        return block;
    }

    public static Block get(int id, int data) {
        if (id < 0) {
            id = 255 - id;
        }

        if (id >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            return CustomBlockManager.get().getBlock(id, 0);
        }

        int fullId = id << DATA_BITS;
        if (fullId >= fullList.length) {
            log.warn("Found an unknown BlockId:Meta combination: {}:{}", id, data);
            return new BlockUnknown(id, data);
        }
        if (data < DATA_SIZE) {
            fullId = fullId | data;
            if (fullList[fullId] == null) {
                log.warn("Found an unknown BlockId:Meta combination: {}:{}", id, data);
                return new BlockUnknown(id, data);
            }
            return fullList[fullId].clone();
        } else {
            Block block = fullList[fullId].clone();
            block.setDamage(data);
            return block;
        }
    }

    public static Block get(int fullId, Level level, int x, int y, int z) {
        return get(fullId, level, x, y, z, 0);
    }

    public static Block get(int fullId, Level level, int x, int y, int z, int layer) {
        int id = fullId << DATA_BITS;

        if (id >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            return CustomBlockManager.get().getBlock(id, 0);
        }

        if (fullId >= fullList.length || fullList[fullId] == null) {
            int meta = fullId & DATA_BITS;
            log.warn("Found an unknown BlockId:Meta combination: {}:{}", id, meta);
            return new BlockUnknown(id, meta);
        }
        Block block = fullList[fullId].clone();
        block.x = x;
        block.y = y;
        block.z = z;
        block.level = level;
        //block.layer = layer;
        return block;
    }

    public static Block get(int id, int meta, Level level, int x, int y, int z) {
        return get(id, meta, level, x, y, z, 0);
    }

    public static Block get(int id, int meta, Level level, int x, int y, int z, int layer) {
        if (id >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            return CustomBlockManager.get().getBlock(id, 0);
        }

        Block block;
        if (meta <= DATA_SIZE) {
            block = fullList[id << DATA_BITS | meta].clone();
        } else {
            block = fullList[id << DATA_BITS].clone();
            block.setDamage(meta);
        }
        block.x = x;
        block.y = y;
        block.z = z;
        block.level = level;
        //block.layer = layer;
        return block;
    }

    public static int getBlockLight(int blockId) {
        if (blockId >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            return light[0]; // TODO: just temporary
        }
        return light[blockId];
    }

    public static int getBlockLightFilter(int blockId) {
        if (blockId >= CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID) {
            return lightFilter[0]; // TODO: just temporary
        }
        return lightFilter[blockId];
    }

    public static Block fromFullId(int fullId) {
        return get(fullId >> DATA_BITS, fullId & DATA_MASK);
    }

    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        return this.canPlaceOn(block.down(), target) && this.getLevel().setBlock(this, this, true, true);
    }

    public boolean canPlaceOn(Block floor, Position pos) {
        return this.canBePlaced();
    }

    public boolean canHarvestWithHand() {
        return true;
    }

    public boolean isBreakable(Item item) {
        return true;
    }

    public int tickRate() {
        return 10;
    }

    public boolean onBreak(Item item, Player player) {
        return this.onBreak(item);
    }

    public boolean onBreak(Item item) {
        return this.getLevel().setBlock(this, Block.get(BlockID.AIR), true, true);
    }

    public int onUpdate(int type) {
        return 0;
    }

    public void onNeighborChange(@NotNull BlockFace side) {

    }


    /**
     * 当玩家使用与左键或者右键方块时会触发，常被用于处理例如物品展示框左键掉落物品这种逻辑<br>
     * 触发点在{@link Player}的onBlockBreakStart中
     * <p>
     * It will be triggered when the player uses the left or right click on the block, which is often used to deal with logic such as left button dropping items in the item frame<br>
     * The trigger point is in the onBlockBreakStart of {@link Player}
     *
     * @param player the player
     * @param action the action
     * @return 状态值，返回值不为0代表这是一个touch操作而不是一个挖掘方块的操作<br>Status value, if the return value is not 0, it means that this is a touch operation rather than a mining block operation
     */
    public int onTouch(@Nullable Player player, PlayerInteractEvent.Action action) {
        this.onUpdate(Level.BLOCK_UPDATE_TOUCH);
        return 0;
    }

    public boolean onActivate(Item item) {
        return this.onActivate(item, null);
    }

    public boolean onActivate(Item item, Player player) {
        return false;
    }

    /**
     * @return 是否可以被灵魂疾行附魔加速<br>Whether it can be accelerated by the soul speed enchantment
     */
    public boolean isSoulSpeedCompatible() {
        return false;
    }

    public double getHardness() {
        return 10;
    }

    public double getResistance() {
        return 1;
    }

    public int getBurnChance() {
        return 0;
    }

    public int getBurnAbility() {
        return 0;
    }

    public int getToolType() {
        return ItemTool.TYPE_NONE;
    }

    public int getToolTier() {
        return 0;
    }

    public double getFrictionFactor() {
        return 0.6;
    }

    public int getLightLevel() {
        return 0;
    }

    public boolean canBePlaced() {
        return true;
    }

    public boolean canBeReplaced() {
        return false;
    }

    public boolean isTransparent() {
        return false;
    }

    public boolean isSolid() {
        return true;
    }

    public boolean diffusesSkyLight() {
        return false;
    }

    public int getWaterloggingLevel() {
        return 0;
    }

    public final boolean canWaterloggingFlowInto() {
        return this.canBeFlowedInto() || this.getWaterloggingLevel() > 1;
    }

    public boolean canBeFlowedInto() {
        return false;
    }

    public boolean canBeActivated() {
        return false;
    }

    public boolean hasEntityCollision() {
        return false;
    }

    public boolean canPassThrough() {
        return false;
    }

    public boolean canBePushed() {
        return true;
    }

    public boolean canBePulled() {
        return true;
    }

    public boolean breaksWhenMoved() {
        return false;
    }

    public boolean sticksToPiston() {
        return true;
    }

    public boolean hasComparatorInputOverride() {
        return false;
    }

    public int getComparatorInputOverride() {
        return 0;
    }

    public boolean canHarvest(Item item) {
        return this.getToolTier() == 0 || this.getToolType() == 0 || correctTool0(this.getToolType(), item, this.getId()) && item.getTier() >= this.getToolTier();
    }

    public boolean canBeClimbed() {
        return false;
    }

    public BlockColor getColor() {
        return BlockColor.VOID_BLOCK_COLOR;
    }

    public abstract String getName();

    public abstract int getId();

    public int getItemId() {
        int id = getId();

        if (id > 255) {
            return 255 - id;
        }

        return id;
    }

    /**
     * The full id is a combination of the id and data.
     * @return full id
     */
    public int getFullId() {
        return getId() << DATA_BITS;
    }

    public void addVelocityToEntity(Entity entity, Vector3 vector) {

    }

    public int getDamage() {
        return 0;
    }

    public void setDamage(int meta) {
    }

    public final void setDamage(Integer meta) {
        setDamage((meta == null ? 0 : meta & 0x0f));
    }

    final public void position(Position v) {
        this.x = (int) v.x;
        this.y = (int) v.y;
        this.z = (int) v.z;
        this.level = v.level;
        this.boundingBox = null;
    }

    /**
     * 是否直接掉落方块物品
     * Whether to drop block items directly
     *
     * @param player 玩家
     * @return true - 直接掉落方块物品, false - 通过getDrops方法获取掉落物品
     *         true - Drop block items directly, false - Get dropped items through the getDrops method
     */
    public boolean isDropOriginal(Player player) {
        return false;
    }

    public Item[] getDrops(Item item) {
        if (this.getId() < 0 || this.getId() > list.length) {
            return Item.EMPTY_ARRAY;
        }

        if (this.canHarvestWithHand() || this.canHarvest(item)) {
            return new Item[]{this.toItem()};
        }
        return Item.EMPTY_ARRAY;
    }

    public Item[] getDrops(@Nullable Player player, Item item) {
        if (player != null && !player.isSurvival() && !player.isAdventure()) {
            return Item.EMPTY_ARRAY;
        }

        // 几乎所有方块都是重写getDrops(Item)方法，所以我们需要调用这个方法
        return this.getDrops(item);
    }

    private double toolBreakTimeBonus0(Item item) {
        if (item instanceof ItemCustomTool itemCustomTool && itemCustomTool.getSpeed() != null) {
            return customToolBreakTimeBonus(customToolType(item), itemCustomTool.getSpeed());
        }
        return toolBreakTimeBonus0(toolType0(item, getId()), item.getTier(), this.getId() == BlockID.WOOL, this.getId() == BlockID.COBWEB);
    }

    private double customToolBreakTimeBonus(int toolType, @org.jetbrains.annotations.Nullable Integer speed) {
        if (speed != null) return speed;
        else if (toolType == ItemTool.TYPE_SWORD) {
            if (this instanceof BlockCobweb) {
                return 15.0;
            } else if (this instanceof BlockBamboo) {
                return 30.0;
            } else return 1.0;
        } else if (toolType == ItemTool.TYPE_SHEARS) {
            if (this instanceof BlockWool || this instanceof BlockLeaves) {
                return 5.0;
            } else if (this instanceof BlockCobweb) {
                return 15.0;
            } else return 1.0;
        } else if (toolType == ItemTool.TYPE_NONE) return 1.0;
        return 0;
    }

    private int customToolType(Item item) {
        if (this instanceof BlockLeaves && item.isHoe()) return ItemTool.TYPE_SHEARS;
        if (item.isSword()) return ItemTool.TYPE_SWORD;
        if (item.isShovel()) return ItemTool.TYPE_SHOVEL;
        if (item.isPickaxe()) return ItemTool.TYPE_PICKAXE;
        if (item.isAxe()) return ItemTool.TYPE_AXE;
        if (item.isHoe()) return ItemTool.TYPE_HOE;
        if (item.isShears()) return ItemTool.TYPE_SHEARS;
        return ItemTool.TYPE_NONE;
    }

    private static double toolBreakTimeBonus0(int toolType, int toolTier, boolean isWoolBlock, boolean isCobweb) {
        if (toolType == ItemTool.TYPE_SWORD) return isCobweb ? 15.0 : 1.0;
        if (toolType == ItemTool.TYPE_SHEARS) return isWoolBlock ? 5.0 : 15.0;
        if (toolType == ItemTool.TYPE_NONE) return 1.0;
        switch (toolTier) {
            case ItemTool.TIER_WOODEN:
                return 2.0;
            case ItemTool.TIER_STONE:
                return 4.0;
            case ItemTool.TIER_IRON:
                return 6.0;
            case ItemTool.TIER_DIAMOND:
                return 8.0;
            case ItemTool.TIER_NETHERITE:
                return 9.0;
            case ItemTool.TIER_GOLD:
                return 12.0;
            default:
                return 1.0;
        }
    }

    private static double speedBonusByEfficiencyLore0(int efficiencyLoreLevel) {
        if (efficiencyLoreLevel == 0) return 0;
        return efficiencyLoreLevel * efficiencyLoreLevel + 1;
    }

    private static double speedRateByHasteLore0(int hasteLoreLevel) {
        return 1.0 + (0.2 * hasteLoreLevel);
    }

    private static int toolType0(Item item, int blockId) {
        if((blockId == LEAVES && item.isHoe()) || (blockId == LEAVES2 && item.isHoe())) return ItemTool.TYPE_SHEARS;
        if (item.isSword()) return ItemTool.TYPE_SWORD;
        if (item.isShovel()) return ItemTool.TYPE_SHOVEL;
        if (item.isPickaxe()) return ItemTool.TYPE_PICKAXE;
        if (item.isAxe()) return ItemTool.TYPE_AXE;
        if (item.isHoe()) return ItemTool.TYPE_HOE;
        if (item.isShears()) return ItemTool.TYPE_SHEARS;
        return ItemTool.TYPE_NONE;
    }

    private static boolean correctTool0(int blockToolType, Item item, int blockId) {
        if (item.isShears() && (blockId == COBWEB || blockId == LEAVES || blockId == LEAVES2)){
            return true;
        }

        if((blockId == LEAVES && item.isHoe()) ||
                (blockId == LEAVES2 && item.isHoe())){
            return (blockToolType == ItemTool.TYPE_SHEARS && item.isHoe());
        }

        return (blockToolType == ItemTool.TYPE_SWORD && item.isSword()) ||
                (blockToolType == ItemTool.TYPE_SHOVEL && item.isShovel()) ||
                (blockToolType == ItemTool.TYPE_PICKAXE && item.isPickaxe()) ||
                (blockToolType == ItemTool.TYPE_AXE && item.isAxe()) ||
                (blockToolType == ItemTool.TYPE_HOE && item.isHoe()) ||
                (blockToolType == ItemTool.TYPE_SHEARS && item.isShears()) ||
                blockToolType == ItemTool.TYPE_NONE;
    }

    private static double breakTime0(double blockHardness, boolean correctTool, boolean canHarvestWithHand,
                                     int blockId, int toolType, int toolTier, int efficiencyLoreLevel, int hasteEffectLevel,
                                     boolean insideOfWaterWithoutAquaAffinity, boolean outOfWaterButNotOnGround) {
        double baseTime = ((correctTool || canHarvestWithHand) ? 1.5 : 5.0) * blockHardness;
        double speed = 1.0 / baseTime;
        boolean isWoolBlock = blockId == Block.WOOL, isCobweb = blockId == Block.COBWEB;
        if (correctTool) speed *= toolBreakTimeBonus0(toolType, toolTier, isWoolBlock, isCobweb);
        speed += correctTool ? speedBonusByEfficiencyLore0(efficiencyLoreLevel) : 0;
        speed *= speedRateByHasteLore0(hasteEffectLevel);
        if (insideOfWaterWithoutAquaAffinity || outOfWaterButNotOnGround) speed *= 0.25;
        return 1.0 / speed;
    }

    public double calculateBreakTime(@Nonnull Item item) {
        return calculateBreakTime(item, null);
    }

    public double calculateBreakTime(@Nonnull Item item, Player player) {
        Objects.requireNonNull(item, "Block#calculateBreakTime(): Item can not be null");
        double seconds = 0;
        double blockHardness = this.getHardness();
        boolean canHarvest = this.canHarvest(item);

        if (canHarvest) {
            seconds = blockHardness * 1.5;
        } else {
            seconds = blockHardness * 5;
        }

        double speedMultiplier = 1;
        int hasteEffectLevel = 0;
        int miningFatigueLevel = 0;
        if (player != null) {
            hasteEffectLevel = Optional.ofNullable(player.getEffect(Effect.HASTE))
                    .map(Effect::getAmplifier).orElse(0);
            miningFatigueLevel = Optional.ofNullable(player.getEffect(Effect.MINING_FATIGUE))
                    .map(Effect::getAmplifier).orElse(0);
        }


        int blockId = this.getId();

        if (blockId == BAMBOO && item.isSword()) {
            return 0; //用剑挖竹子时瞬间破坏
        }

        if (correctTool0(this.getToolType(), item, blockId)) {
            speedMultiplier = toolBreakTimeBonus0(item);
            int efficiencyLevel = Optional.ofNullable(item.getEnchantment(Enchantment.ID_EFFICIENCY))
                    .map(Enchantment::getLevel).orElse(0);

            if (canHarvest && efficiencyLevel > 0) {
                speedMultiplier += efficiencyLevel ^ 2 + 1;
            }

            if (hasteEffectLevel > 0) {
                speedMultiplier *= 1 + (0.2 * hasteEffectLevel);
            }
        }

        if (miningFatigueLevel > 0) {
            speedMultiplier /= 3 ^ miningFatigueLevel;
        }

        seconds /= speedMultiplier;

        if (player != null && !player.isOnGround()) {
            seconds *= 5;
        }
        return seconds;
    }

    public double getBreakTime(@Nonnull Item item, Player player) {
        return calculateBreakTime(item, player);
        /*Objects.requireNonNull(item, "getBreakTime: Item can not be null");
        Objects.requireNonNull(player, "getBreakTime: Player can not be null");
        double blockHardness = getHardness();

        if (blockHardness == 0) {
            return 0;
        }

        int blockId = getId();
        boolean correctTool = correctTool0(getToolType(), item, blockId);
        boolean canHarvestWithHand = canHarvestWithHand();
        int itemToolType = toolType0(item, blockId);
        int itemTier = item.getTier();
        int efficiencyLoreLevel = Optional.ofNullable(item.getEnchantment(Enchantment.ID_EFFICIENCY))
                .map(Enchantment::getLevel).orElse(0);
        int hasteEffectLevel = Optional.ofNullable(player.getEffect(Effect.HASTE))
                .map(Effect::getAmplifier).orElse(0);
        boolean submerged = player.isInsideOfWater();
        boolean insideOfWaterWithoutAquaAffinity = submerged &&
                Optional.ofNullable(player.getInventory().getHelmet().getEnchantment(Enchantment.ID_WATER_WORKER))
                        .map(Enchantment::getLevel).map(l -> l >= 1).orElse(false);
        boolean outOfWaterButNotOnGround = !player.isOnGround() && !submerged;
        return breakTime0(blockHardness, correctTool, canHarvestWithHand, blockId, itemToolType, itemTier,
                efficiencyLoreLevel, hasteEffectLevel, insideOfWaterWithoutAquaAffinity, outOfWaterButNotOnGround);*/
    }

    public boolean canBeBrokenWith(Item item) {
        return this.getHardness() != -1;
    }

    @Override
    public Block getSide(BlockFace face) {
        return this.getSide(face, 1);
    }

    @Override
    public Block getSide(BlockFace face, int step) {
        return this.getSideAtLayer(layer, face, step);
    }

    public Block getSideAtLayer(int layer, BlockFace face) {
        if (this.isValid()) {
            return this.getLevel().getBlock((int) x + face.getXOffset(), (int) y + face.getYOffset(), (int) z + face.getZOffset(), layer);
        }
        return this.getSide(face, 1);
    }

    public Block getSideAtLayer(int layer, BlockFace face, int step) {
        if (this.isValid()) {
            if (step == 1) {
                return this.getLevel().getBlock((int) x + face.getXOffset(), (int) y + face.getYOffset(), (int) z + face.getZOffset(), layer);
            } else {
                return this.getLevel().getBlock((int) x + face.getXOffset() * step, (int) y + face.getYOffset() * step, (int) z + face.getZOffset() * step, layer);
            }
        }
        Block block = Block.get(Item.AIR, 0);
        block.x = (int) x + face.getXOffset() * step;
        block.y = (int) y + face.getYOffset() * step;
        block.z = (int) z + face.getZOffset() * step;
        block.layer = layer;
        return block;
    }

    @Override
    public Block up() {
        return up(1);
    }

    @Override
    public Block up(int step) {
        return getSide(BlockFace.UP, step);
    }

    public Block up(int step, int layer) {
        return this.getSideAtLayer(layer, BlockFace.UP, step);
    }

    @Override
    public Block down() {
        return down(1);
    }

    @Override
    public Block down(int step) {
        return getSide(BlockFace.DOWN, step);
    }

    public Block down(int step, int layer) {
        return this.getSideAtLayer(layer, BlockFace.DOWN, step);
    }

    @Override
    public Block north() {
        return north(1);
    }

    @Override
    public Block north(int step) {
        return getSide(BlockFace.NORTH, step);
    }

    public Block north(int step, int layer) {
        return this.getSideAtLayer(layer, BlockFace.NORTH, step);
    }

    @Override
    public Block south() {
        return south(1);
    }

    @Override
    public Block south(int step) {
        return getSide(BlockFace.SOUTH, step);
    }

    public Block south(int step, int layer) {
        return this.getSideAtLayer(layer, BlockFace.SOUTH, step);
    }

    @Override
    public Block east() {
        return east(1);
    }

    @Override
    public Block east(int step) {
        return getSide(BlockFace.EAST, step);
    }

    public Block east(int step, int layer) {
        return this.getSideAtLayer(layer, BlockFace.EAST, step);
    }

    @Override
    public Block west() {
        return west(1);
    }

    @Override
    public Block west(int step) {
        return getSide(BlockFace.WEST, step);
    }

    public Block west(int step, int layer) {
        return this.getSideAtLayer(layer, BlockFace.WEST, step);
    }

    @Override
    public String toString() {
        return "Block[" + this.getName() + "] (" + this.getId() + ':' + this.getDamage() + ')';
    }

    public boolean collidesWithBB(AxisAlignedBB bb) {
        return collidesWithBB(bb, false);
    }

    public boolean collidesWithBB(AxisAlignedBB bb, boolean collisionBB) {
        AxisAlignedBB bb1 = collisionBB ? this.getCollisionBoundingBox() : this.getBoundingBox();
        return bb1 != null && bb.intersectsWith(bb1);
    }

    public void onEntityCollide(Entity entity) {
    }

    public AxisAlignedBB getBoundingBox() {
        return this.recalculateBoundingBox();
    }

    public AxisAlignedBB getCollisionBoundingBox() {
        return this.recalculateCollisionBoundingBox();
    }

    protected AxisAlignedBB recalculateBoundingBox() {
        return this;
        //return new AxisAlignedBB(this.x, this.y, this.z, this.x + 1.0D, this.y + 1.0D, this.z + 1.0D);
    }

    @Override
    public double getMinX() {
        return this.x;
    }

    @Override
    public double getMinY() {
        return this.y;
    }

    @Override
    public double getMinZ() {
        return this.z;
    }

    @Override
    public double getMaxX() {
        return this.x + 1;
    }

    @Override
    public double getMaxY() {
        return this.y + 1;
    }

    @Override
    public double getMaxZ() {
        return this.z + 1;
    }

    protected AxisAlignedBB recalculateCollisionBoundingBox() {
        return getBoundingBox();
    }

    @Override
    public MovingObjectPosition calculateIntercept(Vector3 pos1, Vector3 pos2) {
        AxisAlignedBB bb = this.getBoundingBox();
        if (bb == null) {
            return null;
        }

        Vector3 v1 = pos1.getIntermediateWithXValue(pos2, bb.getMinX());
        Vector3 v2 = pos1.getIntermediateWithXValue(pos2, bb.getMaxX());
        Vector3 v3 = pos1.getIntermediateWithYValue(pos2, bb.getMinY());
        Vector3 v4 = pos1.getIntermediateWithYValue(pos2, bb.getMaxY());
        Vector3 v5 = pos1.getIntermediateWithZValue(pos2, bb.getMinZ());
        Vector3 v6 = pos1.getIntermediateWithZValue(pos2, bb.getMaxZ());

        if (v1 != null && !bb.isVectorInYZ(v1)) {
            v1 = null;
        }

        if (v2 != null && !bb.isVectorInYZ(v2)) {
            v2 = null;
        }

        if (v3 != null && !bb.isVectorInXZ(v3)) {
            v3 = null;
        }

        if (v4 != null && !bb.isVectorInXZ(v4)) {
            v4 = null;
        }

        if (v5 != null && !bb.isVectorInXY(v5)) {
            v5 = null;
        }

        if (v6 != null && !bb.isVectorInXY(v6)) {
            v6 = null;
        }

        Vector3 vector = v1;

        if (v2 != null && (vector == null || pos1.distanceSquared(v2) < pos1.distanceSquared(vector))) {
            vector = v2;
        }

        if (v3 != null && (vector == null || pos1.distanceSquared(v3) < pos1.distanceSquared(vector))) {
            vector = v3;
        }

        if (v4 != null && (vector == null || pos1.distanceSquared(v4) < pos1.distanceSquared(vector))) {
            vector = v4;
        }

        if (v5 != null && (vector == null || pos1.distanceSquared(v5) < pos1.distanceSquared(vector))) {
            vector = v5;
        }

        if (v6 != null && (vector == null || pos1.distanceSquared(v6) < pos1.distanceSquared(vector))) {
            vector = v6;
        }

        if (vector == null) {
            return null;
        }

        int f = -1;

        if (vector == v1) {
            f = 4;
        } else if (vector == v2) {
            f = 5;
        } else if (vector == v3) {
            f = 0;
        } else if (vector == v4) {
            f = 1;
        } else if (vector == v5) {
            f = 2;
        } else if (vector == v6) {
            f = 3;
        }

        return MovingObjectPosition.fromBlock((int) this.x, (int) this.y, (int) this.z, f, vector.add(this.x, this.y, this.z));
    }

    public String getSaveId() {
        String name = getClass().getName();
        return name.substring(16);
    }

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) throws Exception {
        if (this.getLevel() != null) {
            this.getLevel().getBlockMetadata().setMetadata(this, metadataKey, newMetadataValue);
        }
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) throws Exception {
        if (this.getLevel() != null) {
            return this.getLevel().getBlockMetadata().getMetadata(this, metadataKey);

        }
        return null;
    }

    @Override
    public boolean hasMetadata(String metadataKey) throws Exception {
        return this.getLevel() != null && this.getLevel().getBlockMetadata().hasMetadata(this, metadataKey);
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) throws Exception {
        if (this.getLevel() != null) {
            this.getLevel().getBlockMetadata().removeMetadata(this, metadataKey, owningPlugin);
        }
    }

    @NotNull
    public final Block getBlock() {
        return clone();
    }

    @Override
    public Block clone() {
        return (Block) super.clone();
    }

    public int getWeakPower(BlockFace face) {
        return 0;
    }

    public int getStrongPower(BlockFace side) {
        return 0;
    }

    public boolean isPowerSource() {
        return false;
    }

    public String getLocationHash() {
        return this.getFloorX() + ":" + this.getFloorY() + ':' + this.getFloorZ();
    }

    public int getDropExp() {
        return 0;
    }

    public boolean isNormalBlock() {
        return !isTransparent() && isSolid() && !isPowerSource();
    }

    public static boolean equals(Block b1, Block b2) {
        return equals(b1, b2, true);
    }

    public static boolean equals(Block b1, Block b2, boolean checkDamage) {
        return b1 != null && b2 != null && b1.getId() == b2.getId() && (!checkDamage || b1.getDamage() == b2.getDamage());
    }

    @Override
    public int hashCode() {
        return  ((int) x ^ ((int) z << 12)) ^ ((int) (y + 64)/*这里不删除+64，为以后支持384世界高度准备*/ << 23);
    }

    public Item toItem() {
        return new ItemBlock(this, this.getDamage(), 1);
    }

    public Optional<Block> firstInLayers(Predicate<Block> condition) {
        return firstInLayers(0, condition);
    }

    public Optional<Block> firstInLayers(int startingLayer, Predicate<Block> condition) {
        int maximumLayer = this.level.getProvider().getMaximumLayer();
        for (int layer = startingLayer; layer <= maximumLayer; layer++) {
            Block block = this.getLevelBlockAtLayer(layer);
            if (condition.test(block)) {
                return Optional.of(block);
            }
        }

        return Optional.empty();
    }

    public boolean canSilkTouch() {
        return false;
    }

    public boolean isAir() {
        return false;
    }

    public boolean isLiquid() {
        return false;
    }

    public boolean isLiquidSource() {
        return false;
    }

    public boolean isWater() {
        return false;
    }

    public boolean isWaterSource() {
        return false;
    }

    protected static boolean canStayOnFullSolid(Block down) {
        if (down.isTransparent()) {
            switch (down.getId()) {
                case BEACON:
                case ICE:
                case GLASS:
                case STAINED_GLASS:
                case HARD_GLASS:
                case HARD_STAINED_GLASS:
                case SCAFFOLDING:
                case BARRIER:
                case GLOWSTONE:
                case SEA_LANTERN:
                case HOPPER_BLOCK:
                    return true;
            }
            return false;
        }
        return true;
    }

    /**
     * 被爆炸破坏时必定掉落<br>
     * Drop when destroyed by explosion
     *
     * @return 是否必定掉落<br>Whether to drop
     */
    public boolean alwaysDropsOnExplosion() {
        return false;
    }

    @Deprecated
    public static boolean hasWater(int id) {
        return id == WATER || id == STILL_WATER || usesFakeWater[id];
    }

    @Deprecated
    public static boolean usesFakeWater(int id) {
        return usesFakeWater[id];
    }

    public boolean isSuspiciousBlock() {
        return false;
    }

    public PersistentDataContainer getPersistentDataContainer() {
        if (!this.isValid()) {
            throw new IllegalStateException("Block does not have valid level");
        }
        return this.level.getPersistentDataContainer(this);
    }

    @SuppressWarnings("unused")
    public boolean hasPersistentDataContainer() {
        if (!this.isValid()) {
            throw new IllegalStateException("Block does not have valid level");
        }
        return this.level.hasPersistentDataContainer(this);
    }
}
