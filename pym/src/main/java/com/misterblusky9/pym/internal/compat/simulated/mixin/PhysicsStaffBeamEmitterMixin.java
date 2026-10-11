package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.client.LineEmitter;
import com.misterblusky9.pym.internal.compat.simulated.PhysicsBeamOwnerScale;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler", remap = false)
public abstract class PhysicsStaffBeamEmitterMixin {
    @WrapOperation(
            method = "lambda$onRender$3",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/simulated_team/simulated/content/physics_staff/PhysicsStaffClientHandler$PhysicsBeam;render(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;F)V"
            ),
            remap = false,
            require = 1
    )
    private static void pym$beamFiredByHolder(
            final PhysicsStaffClientHandler.PhysicsBeam beam,
            final Vec3 start,
            final Vec3 end,
            final PoseStack poses,
            final SuperRenderTypeBuffer buffer,
            final Vec3 camera,
            final float partialTick,
            final Operation<Void> original,
            @Local final Player player,
            @Local final ClientSubLevel target
    ) {
        LineEmitter.firedBy(((PhysicsBeamLineAccessor) (Object) beam).pym$line().getParams(), player);
        ((PhysicsBeamOwnerScale) (Object) beam).pym$ownerScale(Pym.entities().renderScale(player, partialTick));
        ((PhysicsBeamOwnerScale) (Object) beam).pym$targetScale(target.renderPose(partialTick).scale().x());
        original.call(beam, start, end, poses, buffer, camera, partialTick);
    }
}
