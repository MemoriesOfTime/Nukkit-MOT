package cn.nukkit.block;

import static cn.nukkit.block.Block.list;
import static cn.nukkit.block.BlockID.*;

public class Blocks {

    static {
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
        list[STRUCTURE_VOID] = BlockStructureVoid.class; //217
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
        list[LOOM] = BlockLoom.class; //459

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
        list[WEEPING_VINES] = BlockWeepingVines.class; //486
        list[CRIMSON_NYLIUM] = BlockNyliumCrimson.class; //487
        list[WARPED_NYLIUM] = BlockNyliumWarped.class; //488
        list[BASALT] = BlockBasalt.class; //489
        list[POLISHED_BASALT] = BlockPolishedBasalt.class; //490
        list[SOUL_SOIL] = BlockSoulSoil.class; //491
        list[SOUL_FIRE] = BlockSoulFire.class; //492
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

        list[POWDER_SNOW] = BlockPowderSnow.class; // 561
        list[SCULK_SENSOR] = BlockSculkSensor.class; // 562
        list[POINTED_DRIPSTONE] = BlockPointedDripstone.class; // 563

        list[COPPER_ORE] = BlockOreCopper.class; //566
        list[LIGHTNING_ROD] = BlockLightningRod.class; //567

        list[DRIPSTONE_BLOCK] = BlockDripstone.class; //572
        list[ROOTED_DIRT] = BlockDirtRooted.class; //573
        list[HANGING_ROOTS] = BlockRootsHanging.class; //574
        list[MOSS_BLOCK] = BlockMoss.class; //575
        list[SPORE_BLOSSOM] = BlockSporeBlossom.class; //576
        list[CAVE_VINES] = BlockCaveVines.class; //577
        list[BIG_DRIPLEAF] = BlockDripleafBig.class; //578

        list[AZALEA_LEAVES] = BlockAzaleaLeaves.class; //589
        list[AZALEA_LEAVES_FLOWERED] = BlockAzaleaLeavesFlowered.class; //580
        list[CALCITE] = BlockCalcite.class; //581
        list[AMETHYST_BLOCK] = BlockAmethyst.class; //582

        list[BUDDING_AMETHYST] = BlockBuddingAmethyst.class; //583
        list[AMETHYST_CLUSTER] = BlockAmethystCluster.class; //584
        list[LARGE_AMETHYST_BUD] = BlockAmethystBudLarge.class; //585
        list[MEDIUM_AMETHYST_BUD] = BlockAmethystBudMedium.class; //586
        list[SMALL_AMETHYST_BUD] = BlockAmethystBudSmall.class; //587
        list[TUFF] = BlockTuff.class; //588
        list[TINTED_GLASS] = BlockGlassTinted.class; //589
        list[MOSS_CARPET] = BlockMossCarpet.class; //590
        list[SMALL_DRIPLEAF] = BlockDripleafSmall.class; //591
        list[AZALEA] = BlockAzalea.class; //592
        list[FLOWERING_AZALEA] = BlockAzaleaFlowering.class; //593

        list[GLOW_FRAME] = BlockItemFrameGlow.class; //594
        list[COPPER_BLOCK] = BlockCopper.class; //595
        list[EXPOSED_COPPER] = BlockCopperExposed.class; //596
        list[WEATHERED_COPPER] = BlockCopperWeathered.class; //597
        list[OXIDIZED_COPPER] = BlockCopperOxidized.class; //598
        list[WAXED_COPPER] = BlockCopperWaxed.class; //599
        list[WAXED_EXPOSED_COPPER] = BlockCopperExposedWaxed.class; //600
        list[WAXED_WEATHERED_COPPER] = BlockCopperWeatheredWaxed.class; //601
        list[CUT_COPPER] = BlockCopperCut.class; //602
        list[EXPOSED_CUT_COPPER] = BlockCopperCutExposed.class; //603
        list[WEATHERED_CUT_COPPER] = BlockCopperCutWeathered.class; //604
        list[OXIDIZED_CUT_COPPER] = BlockCopperCutOxidized.class; //605
        list[WAXED_CUT_COPPER] = BlockCopperCutWaxed.class; //606
        list[WAXED_EXPOSED_CUT_COPPER] = BlockCopperCutExposedWaxed.class; //607
        list[WAXED_WEATHERED_CUT_COPPER] = BlockCopperCutWeatheredWaxed.class; //608

        list[CAVE_VINES_BODY_WITH_BERRIES] = BlockCaveVinesBerriesBody.class; //630
        list[CAVE_VINES_HEAD_WITH_BERRIES] = BlockCaveVinesBerriesHead.class; //631

        list[SMOOTH_BASALT] = BlockBasaltSmooth.class; //632
        list[DEEPSLATE] = BlockDeepslate.class; //633
        list[COBBLED_DEEPSLATE] = BlockDeepslateCobbled.class; //634
        list[POLISHED_DEEPSLATE] = BlockDeepslatePolished.class; //638
        list[DEEPSLATE_TILES] = BlockTilesDeepslate.class; //642
        list[DEEPSLATE_BRICKS] = BlockBricksDeepslate.class; //646
        list[CHISELED_DEEPSLATE] = BlockDeepslateChiseled.class; //650
        list[DEEPSLATE_LAPIS_ORE] = BlockDeepslateLapisOre.class; // 655
        list[DEEPSLATE_IRON_ORE] = BlockDeepslateIronOre.class; // 656
        list[DEEPSLATE_GOLD_ORE] = BlockDeepslateGoldOre.class; // 657
        list[DEEPSLATE_REDSTONE_ORE] = BlockDeepslateRedstoneOre.class; // 658
        list[LIT_DEEPSLATE_REDSTONE_ORE] = BlockLitDeepslateRedstoneOre.class; // 659

        list[DEEPSLATE_DIAMOND_ORE] = BlockDeepslateDiamondOre.class; // 660
        list[DEEPSLATE_COAL_ORE] = BlockDeepslateCoalOre.class; // 661
        list[DEEPSLATE_EMERALD_ORE] = BlockDeepslateEmeraldOre.class; // 662
        list[DEEPSLATE_COPPER_ORE] = BlockDeepslateCopperOre.class; // 663
        list[CRACKED_DEEPSLATE_TILES] = BlockTilesDeepslateCracked.class; //664
        list[CRACKED_DEEPSLATE_BRICKS] = BlockBricksDeepslateCracked.class; //665

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
        list[WAXED_OXIDIZED_COPPER] = BlockCopperOxidizedWaxed.class; //701
        list[WAXED_OXIDIZED_CUT_COPPER] = BlockCopperCutOxidizedWaxed.class; //702

        list[RAW_IRON_BLOCK] = BlockRawIron.class; //706
        list[RAW_COPPER_BLOCK] = BlockRawCopper.class; //707
        list[RAW_GOLD_BLOCK] = BlockRawGold.class; //708
        list[INFESTED_DEEPSLATE] = BlockInfestedDeepslate.class; //709

        list[PEARLESCENT_FROGLIGHT] = BlockFrogLightPearlescent.class; //724
        list[VERDANT_FROGLIGHT] = BlockFrogLightVerdant.class; //725
        list[OCHRE_FROGLIGHT] = BlockFrogLightOchre.class; //726

        list[MUD] = BlockMud.class; //728

        list[MUD_BRICKS] = BlockMudBricks.class; //730

        list[PACKED_MUD] = BlockPackedMud.class; //732

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
    }

    static void init() {
        // Init
    }
}
