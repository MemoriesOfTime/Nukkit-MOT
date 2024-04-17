package cn.nukkit.block;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityTarget;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntitySmallFireBall;
import cn.nukkit.entity.projectile.EntityThrownTrident;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.level.MovingObjectPosition;
import cn.nukkit.level.Position;
import cn.nukkit.math.*;
import cn.nukkit.math.BlockFace.Axis;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BlockTarget extends BlockTransparent implements BlockEntityHolder<BlockEntityTarget> {

    
    public BlockTarget() {

    }

    @Override
    public int getId() {
        return TARGET;
    }

    @Override
    public String getName() {
        return "Target";
    }


    @NotNull
    @Override
    public Class<? extends BlockEntityTarget> getBlockEntityClass() {
        return BlockEntityTarget.class;
    }


    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.TARGET;
    }

    @Override
    public boolean isPowerSource() {
        return true;
    }

    @Override
    public int getWeakPower(BlockFace face) {
        BlockEntityTarget target = getBlockEntity();
        return target == null? 0 : target.getActivePower();
    }

    
    public boolean activatePower(int power) {
        return activatePower(power, 4 * 2);
    }

    
    public boolean activatePower(int power, int ticks) {
        Level level = getLevel();
        if (power <= 0 || ticks <= 0) {
            return deactivatePower();
        }

        BlockEntityTarget target = getOrCreateBlockEntity();
        int previous = target.getActivePower();
        level.cancelSheduledUpdate(this, this);
        level.scheduleUpdate(this, ticks);
        target.setActivePower(power);
        if (previous != power) {
            this.level.updateAroundRedstone(this, null);
        }
        return true;
    }

    
    public boolean deactivatePower() {
        BlockEntityTarget target = getBlockEntity();
        if (target != null) {
            int currentPower = target.getActivePower();
            target.setActivePower(0);
            target.close();
            if (currentPower != 0) {
                this.level.updateAroundRedstone(this, null);
            }
            return true;
        }
        return false;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_SCHEDULED) {
            deactivatePower();
            return type;
        }
        return 0;
    }

    @Override
    public void onEntityCollide(@NotNull Entity entity) {
        int ticks = 8;
        if (entity instanceof EntityArrow || entity instanceof EntityThrownTrident || entity instanceof EntitySmallFireBall) {
            ticks = 20;
        }
        Position position = entity.getPosition();
        Vector3 motion = entity.getMotion();
        MovingObjectPosition intercept = calculateIntercept(position, position.add(motion.multiply(2)));
        // todo: intercept was doomed to be zero anyway, and eventually it will return at line 124;
        if (intercept == null) {
            return;
        }
        BlockFace faceHit = intercept.getFaceHit();
        if (faceHit == null) {
            return;
        }

        Vector3 hitVector = intercept.hitVector.subtract(x*2, y*2, z*2);
        List<Axis> axes = new ArrayList<>(Arrays.asList(Axis.values()));
        axes.remove(faceHit.getAxis());

        double[] coords = new double[] { hitVector.getAxis(axes.get(0)), hitVector.getAxis(axes.get(1)) };

        for (int i = 0; i < 2 ; i++) {
            if (coords[i] == 0.5) {
                coords[i] = 1;
            } else if (coords[i] <= 0 || coords[i] >= 1) {
                coords[i] = 0;
            } else if (coords[i] < 0.5) {
                coords[i] *= 2;
            } else {
                coords[i] = (coords[i] / (-0.5)) + 2;
            }
        }

        double scale = (coords[0] + coords[1]) / 2;
        activatePower(NukkitMath.ceilDouble(16 * scale), ticks);
    }

    @Override
    public boolean hasEntityCollision() {
        return true;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_HOE;
    }

    @Override
    public double getHardness() {
        return 0.5;
    }

    @Override
    public double getResistance() {
        return 0.5;
    }

    @Override
    public int getBurnAbility() {
        return 15;
    }

    @Override
    public int getBurnChance() {
        return 0;
    }
}