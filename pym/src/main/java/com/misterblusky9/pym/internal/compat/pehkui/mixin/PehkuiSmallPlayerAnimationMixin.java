package com.misterblusky9.pym.internal.compat.pehkui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.misterblusky9.pym.api.ScaleBounds;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import virtuoel.pehkui.util.ScaleUtils;

@Mixin(value = LivingEntity.class, priority = 900)
public abstract class PehkuiSmallPlayerAnimationMixin {
    @Inject(method = "calculateEntityAnimation(Z)V", at = @At("HEAD"), cancellable = true)
    private void pym$normalizeSmallPlayerAnimation(
            boolean includeY,
            CallbackInfo ci
    ) {
        LivingEntity self = (LivingEntity) (Object) this;
        float motionScale = ScaleUtils.getMotionScale(self);
        if (!Float.isFinite(motionScale)
                || motionScale <= ScaleBounds.EPSILON
                || Math.abs(motionScale - 1.0F) <= ScaleBounds.EPSILON) {
            return;
        }

        float distance = (float) Mth.length(
                self.getX() - self.xo,
                includeY ? self.getY() - self.yo : 0.0D,
                self.getZ() - self.zo
        );
        float normalizedDistance = distance / motionScale;
        self.walkAnimation.update(Math.min(normalizedDistance * 4.0F, 1.0F), 0.4F);
        ci.cancel();
    }

    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "CONSTANT", args = "floatValue=0.0025000002F"),
            require = 1
    )
    private float pym$scaleBodyTurnThreshold(float threshold) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player)) return threshold;

        float motionScale = ScaleUtils.getMotionScale(self);
        if (!Float.isFinite(motionScale) || motionScale <= 0.0F || motionScale >= 1.0F) {
            return threshold;
        }
        return Math.max(Float.MIN_VALUE, threshold * motionScale * motionScale);
    }
}
