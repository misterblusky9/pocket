package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.compat.simulated.PhysicsBeamOwnerScale;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.outliner.LineOutline;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler$PhysicsBeam", remap = false)
public abstract class PhysicsBeamEndSegmentMixin {
    @Shadow(remap = false) @Final private LineOutline line;

    @Inject(method = "render", at = @At("RETURN"), remap = false, require = 1)
    private void pym$reachTheAnchor(
            final Vec3 start,
            final Vec3 end,
            final PoseStack poses,
            final SuperRenderTypeBuffer buffer,
            final Vec3 camera,
            final float partialTick,
            final CallbackInfo ci,
            @Local(index = 8) final Vec3 lastNode
    ) {
        if (lastNode == null || lastNode == start || lastNode.distanceToSqr(end) < 1.0E-12D) return;
        final double target = ((PhysicsBeamOwnerScale) this).pym$targetScale();
        if (target >= ScaleBounds.FULL || ScaleBounds.same(target, ScaleBounds.FULL)) return;
        this.line.set(lastNode, end).render(poses, buffer, camera, partialTick);
    }
}
