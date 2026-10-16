package com.misterblusky9.pym.internal.mixin.sable;

import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.mixinhelpers.entity.entity_riding_sub_level_vehicle.EntityRidingSubLevelVehicleHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityRidingSubLevelVehicleHelper.class, remap = false)
public abstract class RiderEyeScaleMixin {
    @Inject(
            method = "kickRidingEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/sublevel/SubLevel;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void pym$keepFullSizeRiderAnchor(
            Entity entity,
            Vec3 position,
            SubLevel subLevel,
            CallbackInfoReturnable<Vec3> cir
    ) {
        if (!(entity instanceof Player)) return;
        Entity vehicle = entity.getVehicle();
        if (vehicle == null) return;
        if (subLevel == null || subLevel.isRemoved()) return;

        Vector3dc scale = subLevel.logicalPose().scale();
        double sx = Math.abs(scale.x());
        double sy = Math.abs(scale.y());
        double sz = Math.abs(scale.z());

        if (!ScaleBounds.isValid(sx) || !ScaleBounds.isValid(sy) || !ScaleBounds.isValid(sz)) return;
        if (ScaleBounds.same(sx, 1.0D) && ScaleBounds.same(sy, 1.0D) && ScaleBounds.same(sz, 1.0D)) return;

        Vec3 eyeOffset = entity.getEyePosition().subtract(entity.position());
        Vec3 attachment = entity.getVehicleAttachmentPoint(vehicle);
        Vec3 transformedAttachment = subLevel.logicalPose()
                .transformPosition(position.add(attachment));

        Vector3d rotatedEyeFromAttachment = new Vector3d(
                eyeOffset.x - attachment.x,
                eyeOffset.y - attachment.y,
                eyeOffset.z - attachment.z
        );
        subLevel.logicalPose().orientation().transform(rotatedEyeFromAttachment);

        cir.setReturnValue(transformedAttachment.add(
                rotatedEyeFromAttachment.x - eyeOffset.x,
                rotatedEyeFromAttachment.y - eyeOffset.y,
                rotatedEyeFromAttachment.z - eyeOffset.z
        ));
    }
}
