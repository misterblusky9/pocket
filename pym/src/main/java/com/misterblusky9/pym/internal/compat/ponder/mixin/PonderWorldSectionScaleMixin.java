package com.misterblusky9.pym.internal.compat.ponder.mixin;

import com.misterblusky9.pym.internal.compat.ponder.PonderSectionScaleAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.VecHelper;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorldSectionElementImpl.class, remap = false)
public abstract class PonderWorldSectionScaleMixin implements PonderSectionScaleAccess {
    @Unique private Vec3 pym$pivot = Vec3.ZERO;
    @Unique private double pym$previousScale = 1.0D;
    @Unique private double pym$currentScale = 1.0D;

    @Shadow
    private Vec3 reverseTransformVec(Vec3 input) {
        throw new AssertionError();
    }

    @Inject(method = "reset", at = @At("TAIL"))
    private void pym$resetScale(PonderScene scene, CallbackInfo ci) {
        pym$pivot = Vec3.ZERO;
        pym$previousScale = 1.0D;
        pym$currentScale = 1.0D;
    }

    @Override
    public void pym$setPivot(Vec3 pivot) {
        pym$pivot = pivot;
    }

    @Override
    public double pym$scale() {
        return pym$currentScale;
    }

    @Override
    public void pym$setScale(double scale, boolean immediate) {
        pym$currentScale = scale;
        if (immediate) pym$previousScale = scale;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void pym$advance(PonderScene scene, CallbackInfo ci) {
        pym$previousScale = pym$currentScale;
    }

    @Inject(method = "transformMS", at = @At("TAIL"))
    private void pym$applyScale(PoseStack stack, float partialTicks, CallbackInfo ci) {
        float scale = (float) Mth.lerp(partialTicks, pym$previousScale, pym$currentScale);
        if (scale == 1.0F) return;
        stack.translate(pym$pivot.x, pym$pivot.y, pym$pivot.z);
        stack.scale(scale, scale, scale);
        stack.translate(-pym$pivot.x, -pym$pivot.y, -pym$pivot.z);
    }

    @Inject(method = "reverseTransformVec", at = @At("RETURN"), cancellable = true)
    private void pym$reverseScale(Vec3 input, CallbackInfoReturnable<Vec3> cir) {
        double scale = Mth.lerp(AnimationTickHolder.getPartialTicks(), pym$previousScale, pym$currentScale);
        if (scale == 1.0D) return;
        Vec3 transformed = cir.getReturnValue();
        cir.setReturnValue(pym$pivot.add(transformed.subtract(pym$pivot).scale(1.0D / scale)));
    }

    @Inject(method = "rayTrace", at = @At("RETURN"), cancellable = true)
    private void pym$adjustPicking(
            PonderLevel level,
            Vec3 source,
            Vec3 target,
            CallbackInfoReturnable<Pair<Vec3, BlockHitResult>> cir
    ) {
        double scale = Mth.lerp(AnimationTickHolder.getPartialTicks(), pym$previousScale, pym$currentScale);
        if (scale == 1.0D) return;
        Pair<Vec3, BlockHitResult> hit = cir.getReturnValue();
        if (hit == null || hit.getSecond().getType() != HitResult.Type.BLOCK) return;
        Vec3 localTarget = reverseTransformVec(target);
        double rayLength = localTarget.distanceTo(reverseTransformVec(source));
        if (rayLength == 0.0D) return;
        double distance = localTarget.distanceTo(hit.getSecond().getLocation());
        float fraction = (float) Mth.clamp(distance / rayLength, 0.0D, 1.0D);
        cir.setReturnValue(Pair.of(VecHelper.lerp(fraction, target, source), hit.getSecond()));
    }
}
