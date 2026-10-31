package com.misterblusky9.pym.internal.mixin.entity;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.entity.EntityScaleTracker;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AbstractArrow.class, priority = 1500)
public abstract class ArrowImpactScaleMixin {
    @TargetHandler(
            mixin = "dev.ryanhcode.sable.mixin.entity.arrows_hit_blocks.AbstractArrowMixin",
            name = "sable$setDeltaMovement"
    )
    @WrapOperation(
            method = "@MixinSquared:Handler",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;applyImpulseAtPoint(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V"
            ),
            remap = false
    )
    private void pym$scaleArrowImpulse(
            RigidBodyHandle handle,
            Vec3 position,
            Vec3 impulse,
            Operation<Void> original
    ) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        double length = impulse.length();
        double speed = arrow.getDeltaMovement().length();
        double scale = EntityScaleTracker.factor(arrow);

        if (length < 1.0E-9D || !Double.isFinite(scale) || scale <= 0.0D) {
            original.call(handle, position, impulse);
            return;
        }

        original.call(handle, position, impulse.scale(speed / length * scale * scale * scale));
    }
}
