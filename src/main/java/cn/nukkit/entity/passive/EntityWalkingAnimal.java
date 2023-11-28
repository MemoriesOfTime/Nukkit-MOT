package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.EntityWalking;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;
import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

public abstract class EntityWalkingAnimal extends EntityWalking implements EntityAnimal {

    private int panicTicks = 0;

    /**
     * todo 当玩家靠近友好生物时，看向玩家
     */
    @Getter
    @Setter
    private int stayLookAt = 0;
    @Getter
    @Setter
    private int startLookAt = 0;

    public EntityWalkingAnimal(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (!this.isAlive()) {
            if (++this.deadTicks >= 23) {
                this.close();
                return false;
            }
            return true;
        }

        if (this.panicTicks > 0) {
            this.panicTicks--;
            if (panicTicks == 0) {
                this.doPanic(false);
            }
        }

        int tickDiff = currentTick - this.lastUpdate;
        this.lastUpdate = currentTick;
        this.entityBaseTick(tickDiff);

        // fix look at
        Optional.ofNullable(this.updateMove(tickDiff))
                .ifPresent(this::lookAt);
        return true;
    }

    public int getPanicTicks() {
        return this.panicTicks;
    }

    public void doPanic(boolean panic) {
        if (panic) {
            int time = Utils.rand(60, 100);
            this.panicTicks = time;
            this.stayTime = 0;
            this.moveTime = time;
            this.moveMultiplier = 1.8f;
        } else {
            this.panicTicks = 0;
            this.moveMultiplier = 1.0f;
        }
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        boolean result = super.attack(ev);

        if (result && !ev.isCancelled()) {
            this.doPanic(true);
        }

        return result;
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (!this.isInLove() && creature instanceof Player player) {
            return player.isAlive() && !player.closed && this.isFeedItem(player.getInventory().getItemInHandFast()) && distance <= 49;
        }
        return super.targetOption(creature, distance);
    }

    @Override
    public boolean canTarget(Entity entity) {
        return this.panicTicks == 0
                && (this.isInLove() || entity instanceof Player)
                && entity.canBeFollowed();
    }

    public boolean isFeedItem(Item item) {
        return false;
    }

}
