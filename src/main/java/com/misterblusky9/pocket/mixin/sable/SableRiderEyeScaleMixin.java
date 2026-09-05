package com.misterblusky9.pocket.mixin.sable;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.config.PocketServerConfig;
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
public abstract class SableRiderEyeScaleMixin {
    @Inject(
            method = "kickRidingEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/sublevel/SubLevel;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void pocket$keepFullSizePlayerEyeOffset(
            final Entity entity,
            final Vec3 position,
            final SubLevel subLevel,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        if (!(entity instanceof Player)) return;
        if (PocketServerConfig.scalePlayerInShrunkenSeat()) return;
        if (subLevel == null || subLevel.isRemoved()) return;

        final Vector3dc scale = subLevel.logicalPose().scale();
        final double sx = Math.abs(scale.x());
        final double sy = Math.abs(scale.y());
        final double sz = Math.abs(scale.z());

        if (!PocketSized.isValidScale(sx)
                || !PocketSized.isValidScale(sy)
                || !PocketSized.isValidScale(sz)) {
            return;
        }

        if (Math.abs(sx - 1.0D) <= PocketSized.EPSILON
                && Math.abs(sy - 1.0D) <= PocketSized.EPSILON
                && Math.abs(sz - 1.0D) <= PocketSized.EPSILON) {
            return;
        }

        final Vec3 eyeOffset = entity.getEyePosition().subtract(entity.position());
        final Vec3 transformedFeet = subLevel.logicalPose().transformPosition(position);

        final Vector3d rotatedEye = new Vector3d(
                eyeOffset.x,
                eyeOffset.y,
                eyeOffset.z
        );
        subLevel.logicalPose().orientation().transform(rotatedEye);

        cir.setReturnValue(transformedFeet.add(
                rotatedEye.x - eyeOffset.x,
                rotatedEye.y - eyeOffset.y,
                rotatedEye.z - eyeOffset.z
        ));
    }
}
