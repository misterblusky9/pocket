package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.client.IntegratedContraptionRaycast;
import com.misterblusky9.pocket.create.InteractiveContraption;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
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
    private static boolean pocket$keepIntegratedInteractionCandidates(
            final AABB sableProjectedBounds,
            final AABB rayBounds,
            final Operation<Boolean> original,
            @Local(ordinal = 1) final AbstractContraptionEntity contraptionEntity
    ) {
        if (!(contraptionEntity.getContraption() instanceof final InteractiveContraption interactiveContraption)) {
            return original.call(sableProjectedBounds, rayBounds);
        }

        final LocalPlayer player = Minecraft.getInstance().player;
        final IntegratedContraptionRaycast.Ray ray = IntegratedContraptionRaycast.capture(player);
        if (ray != null
                && IntegratedContraptionRaycast.rayTrace(contraptionEntity, interactiveContraption, ray).isPresent()) {
            return true;
        }

        return original.call(sableProjectedBounds, rayBounds);
    }
}
