package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.entity.projectile.EntityThrownTrident;
import cn.nukkit.event.entity.EntityShootBowEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.inventory.PlayerInventory;
import java.util.List;
import java.util.function.BooleanSupplier;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * Created by PetteriM1
 */
public class ItemTrident extends ItemTool {

    public ItemTrident() {
        this(0, 1);
    }

    public ItemTrident(Integer meta) {
        this(meta, 1);
    }

    public ItemTrident(Integer meta, int count) {
        super(TRIDENT, meta, count, "Trident");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_TRIDENT;
    }
    
    @Override
    public boolean isSword() {
        return true;
    }
    
    @Override
    public int getAttackDamage() {
        return 9;
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        return true;
    }

    @Override
    public boolean canRelease() {
        return true;
    }

    @Override
    public boolean onRelease(Player player, int ticksUsed) {
        if (this.getDamage() >= this.getMaxDurability() - 1 || this.hasEnchantment(Enchantment.ID_TRIDENT_RIPTIDE)) {
            return true;
        }

        PlayerInventory inventory = player.getInventory();
        if (inventory == null) {
            return true;
        }
        Item expected = inventory.getItemInHand().clone();
        if (expected.isNull() || !expected.equalsExact(this)) {
            return true;
        }
        int heldSlot = inventory.getHeldItemIndex();
        int gameMode = player.getGamemode();
        var sourceLevel = player.getLevel();
        Vector3 sourcePosition = new Vector3(player.x, player.y, player.z);
        var sourceChunk = player.chunk;
        var sourceVersion = player.getGameVersion();
        BooleanSupplier contextIntact = () -> !player.closed && player.isAlive()
                && player.isOnline() && player.isConnected() && player.getInventory() == inventory
                && inventory.getHeldItemIndex() == heldSlot && player.getGamemode() == gameMode
                && player.getLevel() == sourceLevel && player.chunk == sourceChunk
                && player.getGameVersion().equals(sourceVersion);
        try (InventoryMutationGuard.Lease claim = InventoryMutationGuard.tryClaim(List.of(inventory))) {
            if (claim == null || !contextIntact.getAsBoolean()
                    || !inventory.getItem(heldSlot).equalsExact(expected)) {
                return true;
            }
            this.useOn(player);

            CompoundTag nbt = new CompoundTag()
                    .putByte("pickup", EntityProjectile.PICKUP_NONE)
                    .putList(new ListTag<DoubleTag>("Pos")
                            .add(new DoubleTag("", player.x))
                            .add(new DoubleTag("", player.y + player.getEyeHeight()))
                            .add(new DoubleTag("", player.z)))
                    .putList(new ListTag<DoubleTag>("Motion")
                            .add(new DoubleTag("", -Math.sin(player.yaw / 180 * Math.PI) * Math.cos(player.pitch / 180 * Math.PI)))
                            .add(new DoubleTag("", -Math.sin(player.pitch / 180 * Math.PI)))
                            .add(new DoubleTag("", Math.cos(player.yaw / 180 * Math.PI) * Math.cos(player.pitch / 180 * Math.PI))))
                    .putList(new ListTag<FloatTag>("Rotation")
                            .add(new FloatTag("", (player.yaw > 180 ? 360 : 0) - (float) player.yaw))
                            .add(new FloatTag("", (float) -player.pitch)));

            EntityThrownTrident trident = new EntityThrownTrident(player.chunk, nbt, player);
            boolean[] released = {false};
            Throwable failureInFlight = null;
            EntityShootBowEvent shot = null;
            try {
                Item thrownItem = this.clone();
                thrownItem.setCount(1);
                trident.setItem(thrownItem);
                trident.setFavoredSlot(heldSlot);
                double p = (double) ticksUsed / 20;
                double f = Math.min((p * p + p * 2) / 3, 1) * 2.5;
                shot = new EntityShootBowEvent(player, this, trident, f);
                if (f < 0.1 || ticksUsed < 5) {
                    shot.setCancelled();
                }
                Server.getInstance().getPluginManager().callEvent(shot);
                if (shot.isCancelled() || shot.getProjectile() != trident) {
                    return true;
                }
                trident.setMotion(trident.getMotion().multiply(shot.getForce()));
                ProjectileLaunchEvent launch = new ProjectileLaunchEvent(trident);
                Server.getInstance().getPluginManager().callEvent(launch);
                BooleanSupplier sourceIntact = () -> contextIntact.getAsBoolean() && !trident.closed
                        && trident.shootingEntity == player && trident.level == sourceLevel
                        && inventory.getItem(heldSlot).equalsExact(expected)
                        && trident.getItem().equalsExact(thrownItem);
                if (launch.isCancelled() || !sourceIntact.getAsBoolean()) {
                    return true;
                }
                boolean creative = player.isCreative();
                inventory.exchangeItemInHand(expected, null, !creative, sourceIntact, () -> {
                    trident.setPickupMode(creative ? EntityProjectile.PICKUP_CREATIVE : EntityProjectile.PICKUP_ANY);
                    released[0] = true;
                });
            } catch (RuntimeException | Error failure) {
                failureInFlight = failure;
                throw failure;
            } finally {
                try {
                    if (released[0]) {
                        trident.spawnToAll();
                        sourceLevel.addLevelSoundEvent(sourcePosition, LevelSoundEventPacket.SOUND_ITEM_TRIDENT_THROW);
                    } else {
                        closePreparedProjectiles(shot, trident);
                    }
                } catch (RuntimeException | Error cleanup) {
                    if (failureInFlight == null) {
                        throw cleanup;
                    }
                    if (cleanup != failureInFlight) {
                        failureInFlight.addSuppressed(cleanup);
                    }
                }
            }
        }

        return true;
    }

    private static void closePreparedProjectiles(EntityShootBowEvent shot, EntityThrownTrident original) {
        Throwable failure = null;
        EntityProjectile replacement = shot == null ? null : shot.getProjectile();
        try {
            if (replacement != null && replacement != original) {
                replacement.close();
            }
        } catch (RuntimeException | Error close) {
            failure = close;
        }
        try {
            original.close();
        } catch (RuntimeException | Error close) {
            if (failure == null) {
                failure = close;
            } else if (failure != close) {
                failure.addSuppressed(close);
            }
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_4_0;
    }
}
