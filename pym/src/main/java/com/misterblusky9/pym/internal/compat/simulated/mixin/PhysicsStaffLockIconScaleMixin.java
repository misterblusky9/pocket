package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffRenderHandler", remap = false)
public abstract class PhysicsStaffLockIconScaleMixin {
    @Unique
    private static final String ADD_VERTEX =
            "Lcom/mojang/blaze3d/vertex/VertexConsumer;addVertex(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;";

    @ModifyArg(method = "renderAllLocks", at = @At(value = "INVOKE", target = ADD_VERTEX), index = 1, remap = false, require = 1)
    private static float pym$lockIconX(final float x, @Local final ClientSubLevel subLevel) {
        return x * pym$iconScale(subLevel);
    }

    @ModifyArg(method = "renderAllLocks", at = @At(value = "INVOKE", target = ADD_VERTEX), index = 2, remap = false, require = 1)
    private static float pym$lockIconY(final float y, @Local final ClientSubLevel subLevel) {
        return y * pym$iconScale(subLevel);
    }

    @Unique
    private static float pym$iconScale(final ClientSubLevel subLevel) {
        if (subLevel == null) return 1.0F;
        final double scale = subLevel.renderPose().scale().x();
        return ScaleBounds.isValid(scale) ? (float) scale : 1.0F;
    }
}
