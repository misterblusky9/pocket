package com.misterblusky9.pym.internal.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.create.SchematicGrid;
import com.misterblusky9.pym.internal.compat.create.SchematicToolScale;
import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = SchematicToolBase.class, remap = false)
public abstract class SchematicToolScaleMixin {
    @WrapOperation(
            method = "updateTargetPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;containing(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;",
                    ordinal = 0
            ),
            remap = false,
            require = 1
    )
    private BlockPos pym$rememberFreeAim(Position point, Operation<BlockPos> original) {
        SchematicGrid.aim(point instanceof Vec3 vec ? vec : new Vec3(point.x(), point.y(), point.z()));
        return original.call(point);
    }

    @WrapOperation(
            method = "updateTargetPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;rayTraceRange("
                            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;D)"
                            + "Lnet/minecraft/world/phys/BlockHitResult;"
            ),
            remap = false,
            require = 1
    )
    private BlockHitResult pym$rememberBlockAim(Level level, Player player, double range, Operation<BlockHitResult> original) {
        BlockHitResult trace = original.call(level, player, SchematicToolScale.distance(range));
        SchematicGrid.aim(trace == null ? null : trace.getLocation());
        return trace;
    }

    @WrapOperation(
            method = "updateSelection",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;atLowerCornerOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;",
                    ordinal = 0
            ),
            remap = false,
            require = 1
    )
    private Vec3 pym$chaseFineAim(Vec3i selected, Operation<Vec3> original) {
        return selected instanceof BlockPos pos && SchematicGrid.ratio(pos) != 1.0D
                ? SchematicGrid.snapped(pos)
                : original.call(selected);
    }

    @ModifyArg(
            method = "updateTargetPos",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"),
            remap = false,
            require = 1
    )
    private double pym$scaleFreeAimRange(double range) {
        return SchematicToolScale.distance(range);
    }

    @ModifyArg(
            method = "updateTargetPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;getTraceTarget("
                            + "Lnet/minecraft/world/entity/player/Player;DLnet/minecraft/world/phys/Vec3;)"
                            + "Lnet/minecraft/world/phys/Vec3;"
            ),
            index = 1,
            remap = false,
            require = 1
    )
    private double pym$scaleSchematicReach(double range) {
        return SchematicToolScale.distance(range);
    }

    @ModifyArg(
            method = "renderOnSchematic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/outliner/Outline$OutlineParams;lineWidth(F)"
                            + "Lnet/createmod/catnip/outliner/Outline$OutlineParams;"
            ),
            remap = false,
            require = 1
    )
    private float pym$scaleSchematicLine(float width) {
        return SchematicToolScale.lineWidth(width);
    }
}
