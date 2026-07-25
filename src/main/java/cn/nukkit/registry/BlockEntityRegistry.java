package cn.nukkit.registry;

import cn.nukkit.blockentity.*;

public class BlockEntityRegistry implements IRegistry {

    /**
     * Register all default block entities
     */
    @Override
    public void init() {
        BlockEntity.registerBlockEntity(BlockEntity.FURNACE, BlockEntityFurnace.class);
        BlockEntity.registerBlockEntity(BlockEntity.BLAST_FURNACE, BlockEntityBlastFurnace.class);
        BlockEntity.registerBlockEntity(BlockEntity.SMOKER, BlockEntitySmoker.class);
        BlockEntity.registerBlockEntity(BlockEntity.CHEST, BlockEntityChest.class);
        BlockEntity.registerBlockEntity(BlockEntity.SIGN, BlockEntitySign.class);
        BlockEntity.registerBlockEntity(BlockEntity.HANGING_SIGN, BlockEntityHangingSign.class);
        BlockEntity.registerBlockEntity(BlockEntity.ENCHANT_TABLE, BlockEntityEnchantTable.class);
        BlockEntity.registerBlockEntity(BlockEntity.SKULL, BlockEntitySkull.class);
        BlockEntity.registerBlockEntity(BlockEntity.FLOWER_POT, BlockEntityFlowerPot.class);
        BlockEntity.registerBlockEntity(BlockEntity.BREWING_STAND, BlockEntityBrewingStand.class);
        BlockEntity.registerBlockEntity(BlockEntity.ITEM_FRAME, BlockEntityItemFrame.class);
        BlockEntity.registerBlockEntity(BlockEntity.GLOW_ITEM_FRAME, BlockEntityItemFrameGlow.class);
        BlockEntity.registerBlockEntity(BlockEntity.CAULDRON, BlockEntityCauldron.class);
        BlockEntity.registerBlockEntity(BlockEntity.ENDER_CHEST, BlockEntityEnderChest.class);
        BlockEntity.registerBlockEntity(BlockEntity.BEACON, BlockEntityBeacon.class);
        BlockEntity.registerBlockEntity(BlockEntity.PISTON_ARM, BlockEntityPistonArm.class);
        BlockEntity.registerBlockEntity(BlockEntity.COMPARATOR, BlockEntityComparator.class);
        BlockEntity.registerBlockEntity(BlockEntity.HOPPER, BlockEntityHopper.class);
        BlockEntity.registerBlockEntity(BlockEntity.BED, BlockEntityBed.class);
        BlockEntity.registerBlockEntity(BlockEntity.JUKEBOX, BlockEntityJukebox.class);
        BlockEntity.registerBlockEntity(BlockEntity.SHULKER_BOX, BlockEntityShulkerBox.class);
        BlockEntity.registerBlockEntity(BlockEntity.BANNER, BlockEntityBanner.class);
        BlockEntity.registerBlockEntity(BlockEntity.DROPPER, BlockEntityDropper.class);
        BlockEntity.registerBlockEntity(BlockEntity.DISPENSER, BlockEntityDispenser.class);
        BlockEntity.registerBlockEntity(BlockEntity.MOB_SPAWNER, BlockEntitySpawner.class);
        BlockEntity.registerBlockEntity(BlockEntity.MUSIC, BlockEntityMusic.class);
        BlockEntity.registerBlockEntity(BlockEntity.LECTERN, BlockEntityLectern.class);
        BlockEntity.registerBlockEntity(BlockEntity.BEEHIVE, BlockEntityBeehive.class);
        BlockEntity.registerBlockEntity(BlockEntity.CAMPFIRE, BlockEntityCampfire.class);
        BlockEntity.registerBlockEntity(BlockEntity.BELL, BlockEntityBell.class);
        BlockEntity.registerBlockEntity(BlockEntity.BARREL, BlockEntityBarrel.class);
        BlockEntity.registerBlockEntity(BlockEntity.MOVING_BLOCK, BlockEntityMovingBlock.class);
        BlockEntity.registerBlockEntity(BlockEntity.END_GATEWAY, BlockEntityEndGateway.class);
        BlockEntity.registerBlockEntity(BlockEntity.DECORATED_POT, BlockEntityDecoratedPot.class);
        BlockEntity.registerBlockEntity(BlockEntity.TARGET, BlockEntityTarget.class);
        BlockEntity.registerBlockEntity(BlockEntity.BRUSHABLE_BLOCK, BlockEntityBrushableBlock.class);
        BlockEntity.registerBlockEntity(BlockEntity.CONDUIT, BlockEntityConduit.class);
        BlockEntity.registerBlockEntity(BlockEntity.POTENT_SULFUR, BlockEntityPotentSulfur.class);
        BlockEntity.registerBlockEntity(BlockEntity.CHISELED_BOOKSHELF, BlockEntityChiseledBookshelf.class);
        BlockEntity.registerBlockEntity(BlockEntity.CRAFTER, BlockEntityCrafter.class);
        BlockEntity.registerBlockEntity(BlockEntity.SHELF, BlockEntityShelf.class);
        BlockEntity.registerBlockEntity(BlockEntity.COPPER_GOLEM_STATUE, BlockEntityCopperGolemStatue.class);
        BlockEntity.registerBlockEntity(BlockEntity.CREAKING_HEART, BlockEntityCreakingHeart.class);
        BlockEntity.registerBlockEntity(BlockEntity.COMMAND_BLOCK, BlockEntityCommandBlock.class);
        BlockEntity.registerBlockEntity(BlockEntity.SCULK_SENSOR, BlockEntitySculkSensor.class);
        BlockEntity.registerBlockEntity(BlockEntity.CALIBRATED_SCULK_SENSOR, BlockEntityCalibratedSculkSensor.class);
        BlockEntity.registerBlockEntity(BlockEntity.SCULK_SHRIEKER, BlockEntitySculkShrieker.class);

        // Persistent container, not on vanilla
        BlockEntity.registerBlockEntity(BlockEntity.PERSISTENT_CONTAINER, PersistentDataContainerBlockEntity.class);
    }
}
