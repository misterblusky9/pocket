package com.misterblusky9.pym.internal.compat.create_submarine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.create_submarine.DeepSeasScale;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.UUID;

@Mixin(targets = "com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker", remap = false)
public abstract class CompartmentBoundsScaleMixin {
    @WrapOperation(
            method = "refreshBounds",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
            remap = false
    )
    private static Object pym$plotBoundsInWorldUnits(
            Map<Object, Object> map,
            Object key,
            Object value,
            Operation<Object> original,
            @Local(argsOnly = true) SubLevelAccess sub
    ) {
        return original.call(map, key, DeepSeasScale.scaleBox(value, DeepSeasScale.of(sub)));
    }

    @WrapOperation(
            method = "updateAABB",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
            remap = false
    )
    private static Object pym$hullBoundsInWorldUnits(
            Map<Object, Object> map,
            Object key,
            Object value,
            Operation<Object> original,
            @Local(argsOnly = true) UUID id
    ) {
        return original.call(map, key, DeepSeasScale.scaleBox(value, DeepSeasScale.of(id)));
    }
}
