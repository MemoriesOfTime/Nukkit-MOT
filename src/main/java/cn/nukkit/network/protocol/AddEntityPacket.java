package cn.nukkit.network.protocol;

import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.item.*;
import cn.nukkit.entity.mob.*;
import cn.nukkit.entity.passive.*;
import cn.nukkit.entity.projectile.*;
import cn.nukkit.entity.weather.EntityLightning;
import cn.nukkit.network.protocol.types.EntityLink;
import cn.nukkit.utils.Binary;
import lombok.ToString;

import java.util.Map;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class AddEntityPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ADD_ENTITY_PACKET;

    public static void setupLegacyIdentifiers(Map<Integer, String> mapping, int protocolId) {
        mapping.put(51, "minecraft:npc");
        mapping.put(63, "minecraft:player");
        mapping.put(EntityWitherSkeleton.NETWORK_ID, "minecraft:wither_skeleton");
        mapping.put(EntityHusk.NETWORK_ID, "minecraft:husk");
        mapping.put(EntityStray.NETWORK_ID, "minecraft:stray");
        mapping.put(EntityWitch.NETWORK_ID, "minecraft:witch");
        mapping.put(EntityZombieVillager.NETWORK_ID, "minecraft:zombie_villager");
        mapping.put(EntityBlaze.NETWORK_ID, "minecraft:blaze");
        mapping.put(EntityMagmaCube.NETWORK_ID, "minecraft:magma_cube");
        mapping.put(EntityGhast.NETWORK_ID, "minecraft:ghast");
        mapping.put(EntityCaveSpider.NETWORK_ID, "minecraft:cave_spider");
        mapping.put(EntitySilverfish.NETWORK_ID, "minecraft:silverfish");
        mapping.put(EntityEnderman.NETWORK_ID, "minecraft:enderman");
        mapping.put(EntitySlime.NETWORK_ID, "minecraft:slime");
        mapping.put(EntityZombiePigman.NETWORK_ID, "minecraft:zombie_pigman");
        mapping.put(EntitySpider.NETWORK_ID, "minecraft:spider");
        mapping.put(EntitySkeleton.NETWORK_ID, "minecraft:skeleton");
        mapping.put(EntityCreeper.NETWORK_ID, "minecraft:creeper");
        mapping.put(EntityZombie.NETWORK_ID, "minecraft:zombie");
        mapping.put(EntitySkeletonHorse.NETWORK_ID, "minecraft:skeleton_horse");
        mapping.put(EntityMule.NETWORK_ID, "minecraft:mule");
        mapping.put(EntityDonkey.NETWORK_ID, "minecraft:donkey");
        mapping.put(EntityDolphin.NETWORK_ID, "minecraft:dolphin");
        mapping.put(EntityTropicalFish.NETWORK_ID, "minecraft:tropicalfish");
        mapping.put(EntityWolf.NETWORK_ID, "minecraft:wolf");
        mapping.put(EntitySquid.NETWORK_ID, "minecraft:squid");
        mapping.put(EntityDrowned.NETWORK_ID, "minecraft:drowned");
        mapping.put(EntitySheep.NETWORK_ID, "minecraft:sheep");
        mapping.put(EntityMooshroom.NETWORK_ID, "minecraft:mooshroom");
        mapping.put(EntityPanda.NETWORK_ID, "minecraft:panda");
        mapping.put(EntitySalmon.NETWORK_ID, "minecraft:salmon");
        mapping.put(EntityPig.NETWORK_ID, "minecraft:pig");
        mapping.put(EntityVillager.NETWORK_ID, "minecraft:villager");
        mapping.put(EntityCod.NETWORK_ID, "minecraft:cod");
        mapping.put(EntityPufferfish.NETWORK_ID, "minecraft:pufferfish");
        mapping.put(EntityCow.NETWORK_ID, "minecraft:cow");
        mapping.put(EntityChicken.NETWORK_ID, "minecraft:chicken");
        mapping.put(107, "minecraft:balloon");
        mapping.put(EntityLlama.NETWORK_ID, "minecraft:llama");
        mapping.put(EntityIronGolem.NETWORK_ID, "minecraft:iron_golem");
        mapping.put(EntityRabbit.NETWORK_ID, "minecraft:rabbit");
        mapping.put(EntitySnowGolem.NETWORK_ID, "minecraft:snow_golem");
        mapping.put(EntityBat.NETWORK_ID, "minecraft:bat");
        mapping.put(EntityOcelot.NETWORK_ID, "minecraft:ocelot");
        mapping.put(EntityHorse.NETWORK_ID, "minecraft:horse");
        mapping.put(EntityCat.NETWORK_ID, "minecraft:cat");
        mapping.put(EntityPolarBear.NETWORK_ID, "minecraft:polar_bear");
        mapping.put(EntityZombieHorse.NETWORK_ID, "minecraft:zombie_horse");
        mapping.put(EntityTurtle.NETWORK_ID, "minecraft:turtle");
        mapping.put(EntityParrot.NETWORK_ID, "minecraft:parrot");
        mapping.put(EntityGuardian.NETWORK_ID, "minecraft:guardian");
        mapping.put(EntityElderGuardian.NETWORK_ID, "minecraft:elder_guardian");
        mapping.put(EntityVindicator.NETWORK_ID, "minecraft:vindicator");
        mapping.put(EntityWither.NETWORK_ID, "minecraft:wither");
        mapping.put(EntityEnderDragon.NETWORK_ID, "minecraft:ender_dragon");
        mapping.put(EntityShulker.NETWORK_ID, "minecraft:shulker");
        mapping.put(EntityEndermite.NETWORK_ID, "minecraft:endermite");
        mapping.put(EntityMinecartEmpty.NETWORK_ID, "minecraft:minecart");
        mapping.put(EntityMinecartHopper.NETWORK_ID, "minecraft:hopper_minecart");
        mapping.put(EntityMinecartTNT.NETWORK_ID, "minecraft:tnt_minecart");
        mapping.put(EntityMinecartChest.NETWORK_ID, "minecraft:chest_minecart");
        mapping.put(100, "minecraft:command_block_minecart");
        mapping.put(EntityArmorStand.NETWORK_ID, "minecraft:armor_stand");
        mapping.put(EntityItem.NETWORK_ID, "minecraft:item");
        mapping.put(EntityPrimedTNT.NETWORK_ID, "minecraft:tnt");
        mapping.put(EntityFallingBlock.NETWORK_ID, "minecraft:falling_block");
        mapping.put(EntityExpBottle.NETWORK_ID, "minecraft:xp_bottle");
        mapping.put(EntityXPOrb.NETWORK_ID, "minecraft:xp_orb");
        mapping.put(70, "minecraft:eye_of_ender_signal");
        mapping.put(EntityEndCrystal.NETWORK_ID, "minecraft:ender_crystal");
        mapping.put(EntityShulkerBullet.NETWORK_ID, "minecraft:shulker_bullet");
        mapping.put(EntityFishingHook.NETWORK_ID, "minecraft:fishing_hook");
        mapping.put(EntityEnderCharge.NETWORK_ID, "minecraft:dragon_fireball");
        mapping.put(EntityArrow.NETWORK_ID, "minecraft:arrow");
        mapping.put(EntitySnowball.NETWORK_ID, "minecraft:snowball");
        mapping.put(EntityEgg.NETWORK_ID, "minecraft:egg");
        mapping.put(EntityPainting.NETWORK_ID, "minecraft:painting");
        mapping.put(EntityThrownTrident.NETWORK_ID, "minecraft:thrown_trident");
        mapping.put(EntityGhastFireBall.NETWORK_ID, "minecraft:fireball");
        mapping.put(EntityPotion.NETWORK_ID, "minecraft:splash_potion");
        mapping.put(EntityEnderPearl.NETWORK_ID, "minecraft:ender_pearl");
        mapping.put(88, "minecraft:leash_knot");
        mapping.put(EntityWitherSkull.NETWORK_ID, "minecraft:wither_skull");
        mapping.put(EntityBlueWitherSkull.NETWORK_ID, "minecraft:wither_skull_dangerous");
        mapping.put(EntityBoat.NETWORK_ID, "minecraft:boat");
        mapping.put(EntityLightning.NETWORK_ID, "minecraft:lightning_bolt");
        mapping.put(EntitySmallFireBall.NETWORK_ID, "minecraft:small_fireball");
        mapping.put(EntityLlamaSpit.NETWORK_ID, "minecraft:llama_spit");
        mapping.put(95, "minecraft:area_effect_cloud");
        mapping.put(EntityPotionLingering.NETWORK_ID, "minecraft:lingering_potion");
        mapping.put(EntityFirework.NETWORK_ID, "minecraft:fireworks_rocket");
        mapping.put(EntityEvocationFangs.NETWORK_ID, "minecraft:evocation_fang");
        mapping.put(104, "minecraft:evocation_illager");
        mapping.put(EntityVex.NETWORK_ID, "minecraft:vex");
        mapping.put(56, "minecraft:agent");
        mapping.put(106, "minecraft:ice_bomb");
        mapping.put(EntityPhantom.NETWORK_ID, "minecraft:phantom");
        mapping.put(62, "minecraft:tripod_camera");
        mapping.put(EntityPillager.NETWORK_ID, "minecraft:pillager");
        mapping.put(EntityWanderingTrader.NETWORK_ID, "minecraft:wandering_trader");
        mapping.put(EntityRavager.NETWORK_ID, "minecraft:ravager");
        mapping.put(EntityVillagerV2.NETWORK_ID, "minecraft:villager_v2");
        mapping.put(EntityZombieVillagerV2.NETWORK_ID, "minecraft:zombie_villager_v2");

        // Correct new entities for older protocols
        if (protocolId < ProtocolInfo.v1_13_0) {
            mapping.put(EntityFox.NETWORK_ID, mapping.get(EntityWolf.NETWORK_ID));
        } else {
            mapping.put(EntityFox.NETWORK_ID, "minecraft:fox");
        }

        if (protocolId < ProtocolInfo.v1_14_0) {
            mapping.put(EntityBee.NETWORK_ID, mapping.get(EntityBat.NETWORK_ID));
        } else {
            mapping.put(EntityBee.NETWORK_ID, "minecraft:bee");
        }

        if (protocolId < ProtocolInfo.v1_16_0) {
            mapping.put(EntityPiglin.NETWORK_ID, mapping.get(EntityZombiePigman.NETWORK_ID));
            mapping.put(EntityHoglin.NETWORK_ID, mapping.get(EntityPig.NETWORK_ID));
            mapping.put(EntityStrider.NETWORK_ID, mapping.get(EntityPig.NETWORK_ID));
            mapping.put(EntityZoglin.NETWORK_ID, mapping.get(EntityPig.NETWORK_ID));
            mapping.put(EntityPiglinBrute.NETWORK_ID, mapping.get(EntityZombiePigman.NETWORK_ID));
            mapping.put(EntityNPCEntity.NETWORK_ID, mapping.get(EntityVillager.NETWORK_ID));
        } else {
            mapping.put(EntityPiglin.NETWORK_ID, "minecraft:piglin");
            mapping.put(EntityHoglin.NETWORK_ID, "minecraft:hoglin");
            mapping.put(EntityStrider.NETWORK_ID, "minecraft:strider");
            mapping.put(EntityZoglin.NETWORK_ID, "minecraft:zoglin");
            mapping.put(EntityPiglinBrute.NETWORK_ID, "minecraft:piglin_brute");
            mapping.put(EntityNPCEntity.NETWORK_ID, "minecraft:npc");
        }

        if (protocolId < ProtocolInfo.v1_17_0 ) {
            mapping.put(EntityGoat.NETWORK_ID, mapping.get(EntitySheep.NETWORK_ID));
            mapping.put(EntityGlowSquid.NETWORK_ID, mapping.get(EntitySquid.NETWORK_ID));
            mapping.put(EntityAxolotl.NETWORK_ID, mapping.get(EntityTropicalFish.NETWORK_ID));
        } else {
            mapping.put(EntityGoat.NETWORK_ID, "minecraft:goat");
            mapping.put(EntityGlowSquid.NETWORK_ID, "minecraft:glow_squid");
            mapping.put(EntityAxolotl.NETWORK_ID, "minecraft:axolotl");
        }

        if (protocolId < ProtocolInfo.v1_19_0) {
            mapping.put(EntityAllay.NETWORK_ID, mapping.get(EntityParrot.NETWORK_ID));
            mapping.put(EntityWarden.NETWORK_ID, mapping.get(EntityWither.NETWORK_ID));
            mapping.put(EntityFrog.NETWORK_ID, mapping.get(EntityRabbit.NETWORK_ID));
            mapping.put(EntityTadpole.NETWORK_ID, mapping.get(EntitySalmon.NETWORK_ID));
            mapping.put(EntityChestBoat.NETWORK_ID, mapping.get(EntityBoat.NETWORK_ID));
        } else {
            mapping.put(EntityAllay.NETWORK_ID, "minecraft:allay");
            mapping.put(EntityWarden.NETWORK_ID, "minecraft:warden");
            mapping.put(EntityFrog.NETWORK_ID, "minecraft:frog");
            mapping.put(EntityTadpole.NETWORK_ID, "minecraft:tadpole");
            mapping.put(EntityChestBoat.NETWORK_ID, "minecraft:chest_boat");
        }

        if (protocolId < ProtocolInfo.v1_20_0_23) {
            mapping.put(EntityCamel.NETWORK_ID, mapping.get(EntityHorse.NETWORK_ID));
        } else {
            mapping.put(EntityCamel.NETWORK_ID, "minecraft:camel");
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public Map<Integer, String> mapping;
    public long entityUniqueId;
    public long entityRuntimeId;
    public int type;
    public String id;
    public float x;
    public float y;
    public float z;
    public float speedX = 0f;
    public float speedY = 0f;
    public float speedZ = 0f;
    public float yaw;
    public float pitch;
    public float headYaw;
    public float bodyYaw = -1;
    public EntityMetadata metadata = new EntityMetadata();
    public Attribute[] attributes = new Attribute[0];
    public EntityLink[] links = new EntityLink[0];

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityUniqueId(this.entityUniqueId);
        this.putEntityRuntimeId(this.entityRuntimeId);
        if (this.protocol < ProtocolInfo.v1_8_0) {
            this.putUnsignedVarInt(this.type);
        }else {
            this.putString(this.getIdentifier());
        }
        this.putVector3f(this.x, this.y, this.z);
        this.putVector3f(this.speedX, this.speedY, this.speedZ);
        this.putLFloat(this.pitch);
        this.putLFloat(this.yaw);
        if (protocol >= ProtocolInfo.v1_5_0) {
            this.putLFloat(this.headYaw);
            if (protocol >= ProtocolInfo.v1_19_10) {
                this.putLFloat(this.bodyYaw == -1 ? this.yaw : this.bodyYaw);
            }
        }
        this.putAttributeList(this.attributes);
        this.put(Binary.writeMetadata(protocol, this.metadata));
        if (protocol >= ProtocolInfo.v1_19_40) {
            this.putUnsignedVarInt(0); // Entity properties int
            this.putUnsignedVarInt(0); // Entity properties float
        }
        this.putUnsignedVarInt(this.links.length);
        for (EntityLink link : links) {
            putEntityLink(protocol, link);
        }
    }

    private String getIdentifier() {
        if (this.id != null) {
            return this.id;
        }

        if (this.protocol < ProtocolInfo.v1_20_0_23) {
            if (this.type == EntityCamel.NETWORK_ID) {
                return "minecraft:horse";
            }

            if (this.protocol < ProtocolInfo.v1_19_0) {
                if (this.type == EntityChestBoat.NETWORK_ID) {
                    return "minecraft:boat";
                } else if (this.type == EntityAllay.NETWORK_ID) {
                    return "minecraft:bat";
                } else if (this.type == EntityWarden.NETWORK_ID) {
                    return "minecraft:iron_golem";
                } else if (this.type == EntityTadpole.NETWORK_ID) {
                    return "minecraft:salmon";
                } else if (this.type == EntityFrog.NETWORK_ID) {
                    return "minecraft:rabbit";
                }

                if (this.protocol < ProtocolInfo.v1_17_0) {
                    if (this.type == EntityGoat.NETWORK_ID) {
                        return "minecraft:sheep";
                    }
                    if (this.type == EntityAxolotl.NETWORK_ID) {
                        return "minecraft:tropicalfish";
                    }
                    if (this.type == EntityGlowSquid.NETWORK_ID) {
                        return "minecraft:squid";
                    }

                    if (this.protocol < ProtocolInfo.v1_16_0) {
                        if (this.type == EntityPiglin.NETWORK_ID || this.type == EntityPiglinBrute.NETWORK_ID) {
                            return "minecraft:zombie_pigman";
                        } else if (this.type == EntityHoglin.NETWORK_ID || this.type == EntityStrider.NETWORK_ID || this.type == EntityZoglin.NETWORK_ID) {
                            return "minecraft:pig";
                        }

                        if (this.protocol < ProtocolInfo.v1_14_0 && this.type == EntityBee.NETWORK_ID) {
                            return "minecraft:bat";
                        }
                        if (this.protocol < ProtocolInfo.v1_13_0 && this.type == EntityFox.NETWORK_ID) {
                            return "minecraft:wolf";
                        }
                    }
                }
            }
        }

        if (this.mapping == null) {
            this.mapping = Entity.getEntityRuntimeMapping(this.protocol);
        }

        String identifier = this.mapping.get(type);
        if (identifier == null) {
            EntityDefinition entityDefinition = EntityManager.get().getDefinition(this.type);
            if (entityDefinition != null) {
                return entityDefinition.getIdentifier();
            }
            throw new IllegalStateException("Unknown entity with network id " + this.type + " protocol " + this.protocol);
        }
        return identifier;
    }
}
