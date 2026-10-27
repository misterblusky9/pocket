package com.misterblusky9.pym.internal.compat.simulated;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ResizeResult;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.debug.PymTrace;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DisassemblyScaleAlignment {
    public static final int EXPANSION_BUDGET_TICKS = 100;

    private static final Set<UUID> REFUSED = ConcurrentHashMap.newKeySet();

    public static UUID begin(BlockEntity assembler) {
        ServerSubLevel subLevel = containing(assembler);
        if (subLevel == null || !Pym.scale().isScaled(subLevel)) return null;

        request(subLevel, assembler.getBlockPos());
        return subLevel.getUniqueId();
    }

    public static boolean align(BlockEntity assembler) {
        ServerSubLevel subLevel = containing(assembler);
        if (subLevel == null) return true;

        if (Pym.scale().isAt(subLevel, ScaleBounds.FULL)) {
            return true;
        }

        request(subLevel, assembler.getBlockPos());
        return false;
    }

    public static boolean growing(BlockEntity assembler) {
        ServerSubLevel subLevel = containing(assembler);
        return subLevel != null && Pym.scale().of(subLevel) < ScaleBounds.FULL - ScaleBounds.EPSILON;
    }

    public static void slackMotors(FreeConstraintHandle constraint) {
        if (constraint == null || !constraint.isValid()) return;
        for (ConstraintJointAxis axis : ConstraintJointAxis.ALL) constraint.setMotor(axis, 0.0D, 0.0D, 0.0D, false, 0.0D);
    }

    public static void restoreMotors(FreeConstraintHandle constraint) {
        if (constraint == null || !constraint.isValid()) return;
        constraint.setMotor(ConstraintJointAxis.ANGULAR_X, 0.0D, 13000.0D, 1000.0D, false, 0.0D);
        constraint.setMotor(ConstraintJointAxis.ANGULAR_Z, 0.0D, 13000.0D, 1000.0D, false, 0.0D);
        constraint.setMotor(ConstraintJointAxis.ANGULAR_Y, 0.0D, 13000.0D, 1000.0D, false, 0.0D);
        constraint.setMotor(ConstraintJointAxis.LINEAR_X, 0.0D, 1.0E-6D, 50.0D, false, 0.0D);
        constraint.setMotor(ConstraintJointAxis.LINEAR_Y, 0.0D, 1.0E-6D, 50.0D, false, 0.0D);
        constraint.setMotor(ConstraintJointAxis.LINEAR_Z, 0.0D, 1.0E-6D, 50.0D, false, 0.0D);
    }

    public static void end(UUID subLevelId) {
        if (subLevelId != null) REFUSED.remove(subLevelId);
        Pym.resize().release(subLevelId);
    }

    private static void request(ServerSubLevel subLevel, BlockPos anchor) {
        ResizeResult result = Pym.resize().request(subLevel)
                .scaleTo(ScaleBounds.FULL)
                .anchor(new Vector3d(anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D))
                .submit();
        if (result.accepted()) {
            REFUSED.remove(subLevel.getUniqueId());
        } else if (REFUSED.add(subLevel.getUniqueId())) {
            PymTrace.logger().info("Physics assembler at {} is waiting for sublevel {} to return to 1x: {}",
                    anchor, subLevel.getUniqueId(), result.describe());
        }
    }

    private static ServerSubLevel containing(BlockEntity assembler) {
        if (assembler == null) return null;

        SubLevel found = Sable.HELPER.getContaining(assembler);
        return found instanceof ServerSubLevel subLevel && !subLevel.isRemoved()
                ? subLevel
                : null;
    }

    private DisassemblyScaleAlignment() {}
}
