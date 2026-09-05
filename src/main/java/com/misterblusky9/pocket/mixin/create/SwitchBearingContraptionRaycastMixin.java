package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.create.InteractiveContraption;
import com.misterblusky9.pocket.debug.SwitchBearingDebug;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import com.simibubi.create.foundation.utility.RaycastHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(value = ContraptionHandlerClient.class, remap = false)
public abstract class SwitchBearingContraptionRaycastMixin {
    @WrapOperation(
            method = "rayTraceContraption",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;rayTraceUntil(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ljava/util/function/Predicate;)Lcom/simibubi/create/foundation/utility/RaycastHelper$PredicateTraceResult;"
            ),
            require = 1
    )
    private static RaycastHelper.PredicateTraceResult pocket$rayTraceSwitchBearingAsSingleBox(
            final Vec3 localOrigin,
            final Vec3 localTarget,
            final Predicate<BlockPos> originalPredicate,
            final Operation<RaycastHelper.PredicateTraceResult> original,
            @Local(argsOnly = true) final AbstractContraptionEntity contraptionEntity,
            @Local final MutableObject<BlockHitResult> mutableResult
    ) {
        if (!(contraptionEntity.getContraption() instanceof InteractiveContraption interactiveContraption)) {
            return original.call(localOrigin, localTarget, originalPredicate);
        }

        final AABB bounds = interactiveContraption.getInteractionBounds();
        if (bounds == null) {
            final Predicate<BlockPos> neverHit = ignored -> false;
            return original.call(localOrigin, localTarget, neverHit);
        }

        final Optional<Vec3> clipped = bounds.clip(localOrigin, localTarget);
        if (clipped.isEmpty()) {
            if (SwitchBearingDebug.ENABLED) {
                SwitchBearingDebug.info(
                        "Switch interaction box miss entityId={} bounds={} localOrigin={} localTarget={}",
                        contraptionEntity.getId(), bounds, localOrigin, localTarget
                );
            }
            final Predicate<BlockPos> neverHit = ignored -> false;
            return original.call(localOrigin, localTarget, neverHit);
        }

        final Vec3 hit = clipped.get();
        mutableResult.setValue(new BlockHitResult(
                hit,
                Direction.UP,
                BlockPos.ZERO,
                false
        ));

        if (SwitchBearingDebug.ENABLED) {
            SwitchBearingDebug.info(
                    "Switch interaction box hit entityId={} bounds={} localHit={} localOrigin={} localTarget={}",
                    contraptionEntity.getId(), bounds, hit, localOrigin, localTarget
            );
        }

        final Predicate<BlockPos> immediateHit = ignored -> true;
        return original.call(localOrigin, localTarget, immediateHit);
    }
}
