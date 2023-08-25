package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntitySwimming;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

import java.util.Objects;
import java.util.Optional;

public abstract class EntitySwimmingMob extends EntitySwimming implements EntityMob {

    private int[] minDamage;

    private int[] maxDamage;

    private boolean canAttack = true;

    public EntitySwimmingMob(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public void setTarget(Entity target) {
        this.setTarget(target, true);
    }

    public void setTarget(Entity target, boolean attack) {
        super.setTarget(target);
        this.canAttack = attack;
    }

    public int getDamage() {
        return getDamage(null);
    }

    public int getDamage(Integer difficulty) {
        return Utils.rand(this.getMinDamage(difficulty), this.getMaxDamage(difficulty));
    }

    public int getMinDamage() {
        return getMinDamage(null);
    }

    public int getMinDamage(Integer difficulty) {
        if (difficulty == null || difficulty > 3 || difficulty < 0) {
            difficulty = Server.getInstance().getDifficulty();
        }
        return this.minDamage[difficulty];
    }

    public int getMaxDamage() {
        return getMaxDamage(null);
    }

    public int getMaxDamage(Integer difficulty) {
        if (difficulty == null || difficulty > 3 || difficulty < 0) {
            difficulty = Server.getInstance().getDifficulty();
        }
        return this.maxDamage[difficulty];
    }

    public void setDamage(int damage) {
        this.setDamage(damage, Server.getInstance().getDifficulty());
    }

    public void setDamage(int damage, int difficulty) {
        if (difficulty >= 1 && difficulty <= 3) {
            this.minDamage[difficulty] = damage;
            this.maxDamage[difficulty] = damage;
        }
    }

    public void setDamage(int[] damage) {
        if (damage.length != 4) {
            throw new IllegalArgumentException("Invalid damage array length");
        }

        if (minDamage == null || minDamage.length < 4) {
            minDamage = Utils.getEmptyDamageArray();
        }

        if (maxDamage == null || maxDamage.length < 4) {
            maxDamage = Utils.getEmptyDamageArray();
        }

        for (int i = 0; i < 4; i++) {
            this.minDamage[i] = damage[i];
            this.maxDamage[i] = damage[i];
        }
    }

    public void setMinDamage(int[] damage) {
        if (damage.length != 4) {
            throw new IllegalArgumentException("Invalid damage array length");
        }

        for (int i = 0; i < 4; i++) {
            this.setMinDamage(damage[i], i);
        }
    }

    public void setMinDamage(int damage) {
        this.setMinDamage(damage, Server.getInstance().getDifficulty());
    }

    public void setMinDamage(int damage, int difficulty) {
        if (difficulty >= 1 && difficulty <= 3) {
            this.minDamage[difficulty] = Math.min(damage, this.getMaxDamage(difficulty));
        }
    }

    public void setMaxDamage(int[] damage) {
        if (damage.length != 4) {
            throw new IllegalArgumentException("Invalid damage array length");
        }

        for (int i = 0; i < 4; i++) {
            this.setMaxDamage(damage[i], i);
        }
    }

    public void setMaxDamage(int damage) {
        this.setMaxDamage(damage, Server.getInstance().getDifficulty());
    }

    public void setMaxDamage(int damage, int difficulty) {
        if (difficulty >= 1 && difficulty <= 3) {
            this.maxDamage[difficulty] = Math.max(damage, this.getMinDamage(difficulty));
        }
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (this.server.getDifficulty() < 1) {
            this.close();
            return false;
        }

        if (!this.isAlive()) {
            if (++this.deadTicks >= 23) {
                this.close();
                return false;
            }
            return true;
        }

        int tickDiff = currentTick - this.lastUpdate;
        this.lastUpdate = currentTick;
        this.entityBaseTick(tickDiff);

        Vector3 target = this.updateMove(tickDiff);
        if (Objects.nonNull(target)) {
            Optional.ofNullable(getAttackTarget(target))
                    .ifPresent(this::attackEntity);
        }
        return true;
    }

    protected Entity getAttackTarget(Vector3 target) {
        if (isMeetAttackConditions(target)) {
            Entity entity = (Entity) target;
            if (!entity.isClosed() && (target != this.followTarget || this.canAttack)) {
                return entity;
            }
        }
        return null;
    }

    @Override
    public boolean isMeetAttackConditions(Vector3 target) {
        return this.getServer().getMobAiEnabled() &&
                (!this.isFriendly() || !(target instanceof Player)) &&
                target instanceof Entity;
    }
}
