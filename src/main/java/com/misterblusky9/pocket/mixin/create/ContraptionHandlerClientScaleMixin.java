package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.PocketSized;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ContraptionHandlerClient.class, remap = false)
public abstract class ContraptionHandlerClientScaleMixin {
    @WrapOperation(
            method = "rightClickingOnContraptionsGetsHandledLocally",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;intersects(Lnet/minecraft/world/phys/AABB;)Z"
            ),
            require = 1
    )
    private static boolean pocket$testAuthoritativeScaledInteractionBounds(
            final AABB sableProjectedBounds,
            final AABB rayBounds,
            final Operation<Boolean> original,
            @Local(ordinal = 1) final AbstractContraptionEntity contraptionEntity
    ) {
        final SubLevel subLevel = Sable.HELPER.getContaining(contraptionEntity);
        if (subLevel == null || subLevel.isRemoved()) {
            return original.call(sableProjectedBounds, rayBounds);
        }

        final Vector3dc scale = subLevel.logicalPose().scale();
        if (!PocketSized.isValidScale(scale.x())
                || Math.abs(scale.x() - scale.y()) > PocketSized.EPSILON
                || Math.abs(scale.x() - scale.z()) > PocketSized.EPSILON
                || Math.abs(scale.x() - 1.0D) <= PocketSized.EPSILON) {
            return original.call(sableProjectedBounds, rayBounds);
        }

        final BoundingBox3d worldBounds = new BoundingBox3d(contraptionEntity.getBoundingBox());
        worldBounds.transform(subLevel.logicalPose(), worldBounds);
        return worldBounds.toMojang().intersects(rayBounds);
    }
}
