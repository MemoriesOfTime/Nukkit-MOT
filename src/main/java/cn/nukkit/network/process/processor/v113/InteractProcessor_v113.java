package cn.nukkit.network.process.processor.v113;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityInteractable;
import cn.nukkit.entity.EntityRideable;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.event.entity.EntityDamageEvent.DamageModifier;
import cn.nukkit.event.player.PlayerInteractEntityEvent;
import cn.nukkit.event.player.PlayerKickEvent;
import cn.nukkit.event.player.PlayerMouseOverEntityEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Sound;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.InteractPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * v113 (1.1) 专用的 InteractPacket 处理器。
 * 1.1 客户端通过 InteractPacket 发送攻击（LEFT_CLICK）和实体交互（RIGHT_CLICK），
 * 从 1.2 开始这些操作改为通过 InventoryTransactionPacket 处理。
 *
 * @author LT_Name
 */
public class InteractProcessor_v113 extends DataPacketProcessor<InteractPacket> {

    public static final InteractProcessor_v113 INSTANCE = new InteractProcessor_v113();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull InteractPacket pk) {
        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive()) {
            return;
        }

        if (pk.target == 0 && pk.action == InteractPacket.ACTION_MOUSEOVER) {
            player.setButtonText("");
            return;
        }

        Entity targetEntity = pk.target == player.getId() ? player : player.level.getEntity(pk.target);

        if (pk.action != InteractPacket.ACTION_OPEN_INVENTORY && (targetEntity == null || !player.isAlive() || !targetEntity.isAlive())) {
            return;
        }

        if (pk.action != InteractPacket.ACTION_OPEN_INVENTORY && (targetEntity instanceof EntityItem || targetEntity instanceof EntityArrow || targetEntity instanceof EntityXPOrb)) {
            player.getServer().getLogger().warning(player.getServer().getLanguage().translateString("nukkit.player.invalidEntity", player.getName()));
            return;
        }

        switch (pk.action) {
            case InteractPacket.ACTION_MOUSEOVER:
                handleMouseOver(player, targetEntity);
                break;
            case InteractPacket.ACTION_VEHICLE_EXIT:
                handleVehicleExit(player, targetEntity);
                break;
            case InteractPacket.ACTION_LEFT_CLICK:
                handleAttack(player, targetEntity);
                break;
            case InteractPacket.ACTION_RIGHT_CLICK:
                handleInteract(player, targetEntity);
                break;
        }
    }

    private void handleMouseOver(Player player, Entity targetEntity) {
        String buttonText = "";
        if (targetEntity instanceof EntityInteractable) {
            buttonText = ((EntityInteractable) targetEntity).getInteractButtonText(player);
            if (buttonText == null) {
                buttonText = "";
            }
        }
        player.setButtonText(buttonText);
        player.getServer().getPluginManager().callEvent(new PlayerMouseOverEntityEvent(player, targetEntity));
    }

    private void handleVehicleExit(Player player, Entity targetEntity) {
        if (!(targetEntity instanceof EntityRideable) || player.riding != targetEntity) {
            return;
        }
        ((EntityRideable) player.riding).dismountEntity(player);
    }

    private void handleAttack(Player player, Entity target) {
        if (target.getId() == player.getId()) {
            player.kick(PlayerKickEvent.Reason.INVALID_PVP, "Tried to attack invalid player");
            return;
        }

        if (player.distanceSquared(target) > (player.isCreative() ? 64 : 25)) { // 8^2=64, 5^2=25
            return;
        }

        if (target instanceof Player) {
            if ((((Player) target).gamemode & 0x01) > 0) {
                return;
            }
            if (!player.getServer().pvpEnabled) {
                return;
            }
        }

        player.breakingBlock = null;
        player.setUsingItem(false);

        Item item = player.getInventory().getItemInHand();

        Enchantment[] enchantments = item.getEnchantments();

        float itemDamage = item.getAttackDamage(player);
        for (Enchantment enchantment : enchantments) {
            itemDamage += enchantment.getDamageBonus(target, player);
        }

        Map<DamageModifier, Float> damage = new EnumMap<>(DamageModifier.class);
        damage.put(DamageModifier.BASE, itemDamage);

        float knockBack = 0.3f;
        Enchantment knockBackEnchantment = item.getEnchantment(Enchantment.ID_KNOCKBACK);
        if (knockBackEnchantment != null) {
            knockBack += knockBackEnchantment.getLevel() * 0.1f;
        }

        EntityDamageByEntityEvent entityDamageByEntityEvent = new EntityDamageByEntityEvent(player, target, DamageCause.ENTITY_ATTACK, damage, knockBack, enchantments);
        entityDamageByEntityEvent.setBreakShield(item.canBreakShield());
        if (player.isSpectator()) entityDamageByEntityEvent.setCancelled();
        if ((target instanceof Player) && !player.level.getGameRules().getBoolean(GameRule.PVP)) {
            entityDamageByEntityEvent.setCancelled();
        }

        if (!target.attack(entityDamageByEntityEvent)) {
            if (item.isTool() && !player.isCreative()) {
                player.getInventory().sendContents(player);
            }
            return;
        }

        for (Enchantment enchantment : item.getEnchantments()) {
            enchantment.doPostAttack(player, target);
        }

        if (item.isTool() && !player.isCreative()) {
            if (item.useOn(target) && item.getDamage() >= item.getMaxDurability()) {
                player.level.addSoundToViewers(player, Sound.RANDOM_BREAK);
                player.level.addParticle(new ItemBreakParticle(player, item));
                player.getInventory().setItemInHand(Item.get(0));
            } else {
                if (item.getId() == 0 || player.getInventory().getItemInHandFast().getId() == item.getId()) {
                    player.getInventory().setItemInHand(item);
                } else {
                    player.getServer().getLogger().debug("Tried to set item " + item.getId() + " but " + player.getName() + " had item " + player.getInventory().getItemInHandFast().getId() + " in their hand slot");
                }
            }
        }
    }

    private void handleInteract(Player player, Entity target) {
        if (player.distanceSquared(target) > 256) {
            player.getServer().getLogger().debug(player.getName() + ": target entity is too far away");
            return;
        }

        player.breakingBlock = null;
        player.setUsingItem(false);

        Item item = player.getInventory().getItemInHand();

        PlayerInteractEntityEvent playerInteractEntityEvent = new PlayerInteractEntityEvent(player, target, item, new Vector3(0, 0, 0));
        if (player.isSpectator()) playerInteractEntityEvent.setCancelled();
        player.getServer().getPluginManager().callEvent(playerInteractEntityEvent);

        if (playerInteractEntityEvent.isCancelled()) {
            return;
        }

        if (target.onInteract(player, item, new Vector3(0, 0, 0)) && (player.isSurvival() || player.isAdventure())) {
            if (item.isTool()) {
                if (item.useOn(target) && item.getDamage() >= item.getMaxDurability()) {
                    player.level.addSoundToViewers(player, Sound.RANDOM_BREAK);
                    player.level.addParticle(new ItemBreakParticle(player, item));
                    item = new ItemBlock(Block.get(BlockID.AIR));
                }
            } else {
                if (item.count > 1) {
                    item.count--;
                } else {
                    item = new ItemBlock(Block.get(BlockID.AIR));
                }
            }

            if (item.getId() == 0 || player.getInventory().getItemInHandFast().getId() == item.getId()) {
                player.getInventory().setItemInHand(item);
            } else {
                player.getServer().getLogger().debug("Tried to set item " + item.getId() + " but " + player.getName() + " had item " + player.getInventory().getItemInHandFast().getId() + " in their hand slot");
            }
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(InteractPacket.NETWORK_ID);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return InteractPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol < ProtocolInfo.v1_2_0;
    }
}
