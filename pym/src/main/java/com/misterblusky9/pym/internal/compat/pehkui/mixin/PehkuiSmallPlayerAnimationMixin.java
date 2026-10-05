package com.misterblusky9.pym.internal.compat.pehkui.mixin;

import com.misterblusky9.pym.api.ScaleBounds;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import virtuoel.pehkui.util.ScaleUtils;

@Mixin(value = LivingEntity.class, priority = 900)
public abstract class PehkuiSmallPlayerAnimationMixin {
    @Inject(method = "calculateEntityAnimation(Z)V", at = @At("HEAD"), cancellable = true)
    private void pym$normalizeSmallPlayerAnimation(
            final boolean includeY,
            final CallbackInfo ci
    ) {
        final LivingEntity self = (LivingEntity) (Object) this;
        final float motionScale = ScaleUtils.getMotionScale(self);
        if (!Float.isFinite(motionScale)
                || motionScale <= ScaleBounds.EPSILON
                || Math.abs(motionScale - 1.0F) <= ScaleBounds.EPSILON) {
            return;
        }

        final float distance = (float) Mth.length(
                self.getX() - self.xo,
                includeY ? self.getY() - self.yo : 0.0D,
                self.getZ() - self.zo
        );
        final float normalizedDistance = distance / motionScale;
        self.walkAnimation.update(Math.min(normalizedDistance * 4.0F, 1.0F), 0.4F);
        ci.cancel();
    }
}
