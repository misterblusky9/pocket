package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.misterblusky9.pym.api.Pym;

import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterCartScaleContext;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterScaleLookup;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterPlacementScaleContext;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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

import java.util.List;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartTrackSnap", remap = false)
public abstract class CoasterCartTrackSnapScaleMixin {
    private static final String PYM$IDEAL =
            "idealRepresentativeSnapCenterWorld("
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;Z)"
                    + "Lnet/minecraft/world/phys/Vec3;";


    private static final String PYM$PRE_TICK =
            "onPrePhysicsTick("
                    + "Ldev/ryanhcode/sable/neoforge/event/ForgeSablePrePhysicsTickEvent;)V";

    private static final String PYM$APPLY =
            "applySnap("
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;Z)V";

    private static final String PYM$OPEN_END =
            "shouldDisengageAtOpenEndHit("
                    + "Lnet/minecraft/world/level/Level;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathGraph;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;"
                    + "Ljava/util/List;)Z";

    @ModifyConstant(
            method = PYM$PRE_TICK,
            constant = @Constant(doubleValue = 0.0484D),
            remap = false,
            require = 2
    )
    private static double pym$scaleGluePeelReleaseDistanceSq(
            final double original,
            @Local(ordinal = 0) final ServerSubLevel cart
    ) {
        final double scale = Pym.scale().ofBody(cart);
        return original * scale * scale;
    }

    @Inject(method = PYM$IDEAL, at = @At("HEAD"), remap = false, require = 1)
    private static void pym$enterIdeal(
            final ServerLevel level,
            @Coerce final Object graphHit,
            final boolean negateEdgeTangent,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        CoasterCartScaleContext.push(
                CoasterScaleLookup.scaleForGraphHit(
                        level,
                        graphHit,
                        null,
                        CoasterPlacementScaleContext.remembered()
                ));
    }

    @ModifyConstant(
            method = PYM$IDEAL,
            constant = @Constant(doubleValue = 0.3125D),
            remap = false,
            require = 1
    )
    private static double pym$scaleIdealRailOffset(final double original) {
        return original * CoasterCartScaleContext.current();
    }

    @Inject(method = PYM$IDEAL, at = @At("RETURN"), remap = false, require = 1)
    private static void pym$exitIdeal(
            final ServerLevel level,
            @Coerce final Object graphHit,
            final boolean negateEdgeTangent,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        CoasterCartScaleContext.pop();
    }

    @Inject(method = PYM$APPLY, at = @At("HEAD"), remap = false, require = 1)
    private static void pym$enterApply(
            final ServerSubLevel cart,
            final ServerLevel level,
            final SubLevelPhysicsSystem physicsSystem,
            @Coerce final Object graphHit,
            final Vec3 currentBearingCenter,
            final Vec3 bearingPlotCenter,
            final boolean negateEdgeTangent,
            final CallbackInfo ci
    ) {
        CoasterCartScaleContext.push(
                CoasterScaleLookup.scaleForGraphHit(
                        level,
                        graphHit,
                        null,
                        CoasterPlacementScaleContext.placementOr(Pym.scale().ofBody(cart))
                ));
    }

    @ModifyConstant(
            method = PYM$APPLY,
            constant = @Constant(doubleValue = 0.3125D),
            remap = false,
            require = 1
    )
    private static double pym$scaleAppliedRailOffset(final double original) {
        return original * CoasterCartScaleContext.current();
    }

    @Inject(method = PYM$APPLY, at = @At("RETURN"), remap = false, require = 1)
    private static void pym$exitApply(
            final ServerSubLevel cart,
            final ServerLevel level,
            final SubLevelPhysicsSystem physicsSystem,
            @Coerce final Object graphHit,
            final Vec3 currentBearingCenter,
            final Vec3 bearingPlotCenter,
            final boolean negateEdgeTangent,
            final CallbackInfo ci
    ) {
        CoasterCartScaleContext.pop();
    }

    @Inject(method = PYM$OPEN_END, at = @At("HEAD"), remap = false, require = 1)
    private static void pym$enterOpenEnd(
            final Level level,
            @Coerce final Object graph,
            @Coerce final Object graphHit,
            final List<?> openEnds,
            final CallbackInfoReturnable<Boolean> cir
    ) {
        CoasterCartScaleContext.push(
                CoasterScaleLookup.scaleForGraphHit(level, graphHit, null));
    }

    @ModifyConstant(
            method = PYM$OPEN_END,
            constant = @Constant(doubleValue = 0.019600000000000003D),
            remap = false,
            require = 1
    )
    private static double pym$scaleOpenEndMatchDistanceSq(final double original) {
        final double scale = CoasterCartScaleContext.current();
        return original * scale * scale;
    }

    @Inject(method = PYM$OPEN_END, at = @At("RETURN"), remap = false, require = 1)
    private static void pym$exitOpenEnd(
            final Level level,
            @Coerce final Object graph,
            @Coerce final Object graphHit,
            final List<?> openEnds,
            final CallbackInfoReturnable<Boolean> cir
    ) {
        CoasterCartScaleContext.pop();
    }
}
