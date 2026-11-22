package com.misterblusky9.pym.internal.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.schematics.client.SchematicTransformation;
import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

@Mixin(value = SchematicToolBase.class, remap = false, priority = 1500)
public abstract class SchematicToolAimMixin {
    @WrapOperation(
            method = "updateTargetPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;rayTraceUntil("
                            + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ljava/util/function/Predicate;)"
                            + "Lcom/simibubi/create/foundation/utility/RaycastHelper$PredicateTraceResult;"
            ),
            remap = false,
            require = 1
    )
    private RaycastHelper.PredicateTraceResult pym$aimThroughPose(
            Vec3 start, Vec3 end, Predicate<BlockPos> inside, Operation<RaycastHelper.PredicateTraceResult> original,
            @Local LocalPlayer player, @Local SchematicTransformation transformation) {
        ClientSubLevel subLevel = Sable.HELPER.getContainingClient(transformation.getAnchor());
        if (subLevel == null) return original.call(start, end, inside);
        Pose3dc pose = subLevel.renderPose();
        Vec3 eye = player.getEyePosition();
        Vec3 far = RaycastHelper.getTraceTarget(player, 70.0D, eye);
        return RaycastHelper.rayTraceUntil(
                transformation.toLocalSpace(pose.transformPositionInverse(eye)),
                transformation.toLocalSpace(pose.transformPositionInverse(far)), inside);
    }
}
