package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author glorydark
 */
public class ItemMace extends StringItemToolBase {

    /**
     * 触发下落猛击的最小落距 / Min fall distance to trigger a smash attack.
     */
    private static final float SMASH_TRIGGER_FALL_DISTANCE = 1.5f;

    /**
     * 触发重型地面猛击音效的总伤害阈值（基础伤害 + 下落加成）。
     * <p>
     * Total damage (base + fall bonus, see {@link #getAttackDamage(Entity)})
     * to play the heavy smash ground sound.
     */
    private static final int HEAVY_SMASH_DAMAGE_THRESHOLD = 16;

    public ItemMace() {
        super("minecraft:mace", "Mace");
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_0;
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_MACE;
    }

    @Override
    public int getAttackDamage() {
        return 5;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_DIAMOND;
    }

    @Override
    public boolean isMace() {
        return true;
    }

    @Override
    public int getAttackDamage(Entity entity) {
        int damage = 6;
        int height = NukkitMath.floorDouble(entity.highestPosition - entity.y);
        if (height < 1.5f) return damage;
        for (int i = 0; i <= height; i++) {
            if (i < 3) damage+=4;
            else if (i < 8) damage+=2;
            else damage++;
        }
        return damage;
    }

    /**
     * 命中实体后播放下落猛击音效与地面灰尘粒子。规则对齐 Allay 的
     * {@code applySmashEffects}：落距 {@code <= 1.5} 不播放；空中播放
     * {@code smash_air}；着地时播放灰尘粒子，并按总伤害选择
     * {@code smash_ground}（{@code < 16}）或 {@code heavy_smash_ground}（{@code >= 16}）。
     * <p>
     * Plays smash sounds and ground dust particle after a hit, mirroring Allay's
     * {@code applySmashEffects}: nothing when fall {@code <= 1.5}; {@code smash_air}
     * while airborne; on ground emit dust plus {@code smash_ground}
     * (damage {@code < 16}) or {@code heavy_smash_ground} (damage {@code >= 16}).
     * <p>
     * 注意：{@code fallDistance} 必须在 {@code target.attack()} 之前由调用方计算并传入，
     * 因为 Wind Burst 附魔会在攻击内部重置 {@code highestPosition}。
     * <p>
     * Note: {@code fallDistance} must be computed by the caller before
     * {@code target.attack()}, since Wind Burst resets {@code highestPosition} mid-attack.
     *
     * @param attacker      发起攻击的实体 / attacking entity
     * @param victim        被攻击实体，音效与粒子以其位置为中心 / victim, center of sound and particles
     * @param fallDistance  攻击瞬间的下落距离 / fall distance at the moment of attack
     * @param damage        总伤害（含附魔加成），用于区分普通/重型地面猛击 /
     *                      total damage (incl. enchant bonus) to pick normal vs heavy ground smash
     */
    public void onPostAttack(Entity attacker, Entity victim, float fallDistance, float damage) {
        if (fallDistance <= SMASH_TRIGGER_FALL_DISTANCE) {
            return;
        }

        Level level = victim.getLevel();
        if (level == null) {
            return;
        }

        Vector3 pos = victim.getPosition();
        int sound;
        if (attacker.isOnGround()) {
            level.addLevelEvent(pos, LevelEventPacket.PARTICLE_SMASH_ATTACK_GROUND_DUST);
            sound = damage >= HEAVY_SMASH_DAMAGE_THRESHOLD
                    ? LevelSoundEventPacket.SOUND_MACE_SMASH_HEAVY_GROUND
                    : LevelSoundEventPacket.SOUND_MACE_SMASH_GROUND;
        } else {
            sound = LevelSoundEventPacket.SOUND_MACE_SMASH_AIR;
        }
        level.addLevelSoundEvent(pos, sound);
    }
}
