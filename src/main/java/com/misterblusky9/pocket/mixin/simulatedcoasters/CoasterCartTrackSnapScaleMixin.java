package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersCartScaleContext;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersScaleLookup;
import com.misterblusky9.pocket.physics.ScaleFrame;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartTrackSnap", remap = false)
public abstract class CoasterCartTrackSnapScaleMixin {
    private static final String POCKET$IDEAL =
            "idealRepresentativeSnapCenterWorld("
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;Z)"
                    + "Lnet/minecraft/world/phys/Vec3;";


    private static final String POCKET$PRE_TICK =
            "onPrePhysicsTick("
                    + "Ldev/ryanhcode/sable/neoforge/event/ForgeSablePrePhysicsTickEvent;)V";

    private static final String POCKET$APPLY =
            "applySnap("
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;Z)V";

    @ModifyExpressionValue(
            method = POCKET$PRE_TICK,
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartTrackSnap;engagedTrackSearchDistSq(ZZ)D"
            ),
            remap = false,
            require = 1
    )
    private static double pocket$scaleTrackSearchDistance(
            final double original,
            @Local(ordinal = 0) final ServerSubLevel cart
    ) {
        final double scale = ScaleFrame.scaleOf(cart);
        return original * scale * scale;
    }

    @ModifyConstant(
            method = POCKET$PRE_TICK,
            constant = @Constant(doubleValue = 0.0484D),
            remap = false,
            require = 2
    )
    private static double pocket$scaleGluePeelReleaseDistanceSq(
            final double original,
            @Local(ordinal = 0) final ServerSubLevel cart
    ) {
        final double scale = ScaleFrame.scaleOf(cart);
        return original * scale * scale;
    }

    @Inject(method = POCKET$IDEAL, at = @At("HEAD"), remap = false, require = 1)
    private static void pocket$enterIdeal(
            final ServerLevel level,
            @Coerce final Object graphHit,
            final boolean negateEdgeTangent,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        SimulatedCoastersCartScaleContext.push(
                SimulatedCoastersScaleLookup.scaleForGraphHit(level, graphHit, null));
    }

    @ModifyConstant(
            method = POCKET$IDEAL,
            constant = @Constant(doubleValue = 0.3125D),
            remap = false,
            require = 1
    )
    private static double pocket$scaleIdealRailOffset(final double original) {
        return original * SimulatedCoastersCartScaleContext.current();
    }

    @Inject(method = POCKET$IDEAL, at = @At("RETURN"), remap = false, require = 1)
    private static void pocket$exitIdeal(
            final ServerLevel level,
            @Coerce final Object graphHit,
            final boolean negateEdgeTangent,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        SimulatedCoastersCartScaleContext.pop();
    }

    @Inject(method = POCKET$APPLY, at = @At("HEAD"), remap = false, require = 1)
    private static void pocket$enterApply(
            final ServerSubLevel cart,
            final ServerLevel level,
            final SubLevelPhysicsSystem physicsSystem,
            @Coerce final Object graphHit,
            final Vec3 currentBearingCenter,
            final Vec3 bearingPlotCenter,
            final boolean negateEdgeTangent,
            final CallbackInfo ci
    ) {
        SimulatedCoastersCartScaleContext.push(ScaleFrame.scaleOf(cart));
    }

    @ModifyConstant(
            method = POCKET$APPLY,
            constant = @Constant(doubleValue = 0.3125D),
            remap = false,
            require = 1
    )
    private static double pocket$scaleAppliedRailOffset(final double original) {
        return original * SimulatedCoastersCartScaleContext.current();
    }

    @Inject(method = POCKET$APPLY, at = @At("RETURN"), remap = false, require = 1)
    private static void pocket$exitApply(
            final ServerSubLevel cart,
            final ServerLevel level,
            final SubLevelPhysicsSystem physicsSystem,
            @Coerce final Object graphHit,
            final Vec3 currentBearingCenter,
            final Vec3 bearingPlotCenter,
            final boolean negateEdgeTangent,
            final CallbackInfo ci
    ) {
        SimulatedCoastersCartScaleContext.pop();
    }
}
