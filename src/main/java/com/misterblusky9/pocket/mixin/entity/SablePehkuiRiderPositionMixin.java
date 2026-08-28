package com.misterblusky9.pocket.mixin.entity;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.entity.EntityScaleTracker;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ryanhcode.sable.mixinhelpers.entity.entity_riding_sub_level_vehicle.EntityRidingSubLevelVehicleHelper", remap = false)
public abstract class SablePehkuiRiderPositionMixin {
    @Inject(
            method = "kickRidingEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/sublevel/SubLevel;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void pocket$positionScaledPehkuiRider(
            final Entity entity,
            final Vec3 position,
            final SubLevel subLevel,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        final double scale = EntityScaleTracker.pehkuiRidingScale(entity, subLevel);
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return;

        final Entity vehicle = entity.getVehicle();
        if (vehicle == null) return;

        final Vec3 attachment = entity.getVehicleAttachmentPoint(vehicle);
        final Vec3 seat = position.add(attachment);
        cir.setReturnValue(subLevel.logicalPose().transformPosition(seat).subtract(attachment));
    }
}
