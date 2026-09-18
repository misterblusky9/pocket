package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, priority = 900)
public abstract class PehkuiSmallPlayerAnimationMixin {
    @Inject(method = "calculateEntityAnimation(Z)V", at = @At("HEAD"), cancellable = true)
    private void pocket$normalizeSmallPlayerAnimation(
            final boolean includeY,
            final CallbackInfo ci
    ) {
        final LivingEntity self = (LivingEntity) (Object) this;
        final float motionScale = PehkuiScaleBridge.motionScale(self);
        if (!Float.isFinite(motionScale)
                || motionScale <= PocketSized.EPSILON
                || Math.abs(motionScale - 1.0F) <= PocketSized.EPSILON) {
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
