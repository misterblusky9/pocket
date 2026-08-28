package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
                    target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
            ),
            require = 1
    )
    private static AABB pocket$useScaledContraptionInteractionBounds(
            final AbstractContraptionEntity contraptionEntity,
            final Operation<AABB> original
    ) {
        final AABB originalBounds = original.call(contraptionEntity);
        final SubLevel subLevel = Sable.HELPER.getContaining(contraptionEntity);
        if (subLevel == null || subLevel.isRemoved()) {
            return originalBounds;
        }

        final Vector3dc scale = subLevel.logicalPose().scale();
        if (!PocketSized.isValidScale(scale.x())
                || Math.abs(scale.x() - scale.y()) > PocketSized.EPSILON
                || Math.abs(scale.x() - scale.z()) > PocketSized.EPSILON
                || Math.abs(scale.x() - 1.0D) <= PocketSized.EPSILON) {
            return originalBounds;
        }

        final BoundingBox3d worldBounds = new BoundingBox3d(originalBounds);
        worldBounds.transform(subLevel.logicalPose(), worldBounds);
        return worldBounds.toMojang();
    }
}
