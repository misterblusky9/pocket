package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersCartScaleContext;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersScaleLookup;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersPlacementScaleContext;
import com.misterblusky9.pocket.physics.ScaleFrame;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartTrackHitCache", remap = false)
public abstract class CoasterCartTrackHitCacheScaleMixin {

    private static final String POCKET$NEAREST =
            "nearestGraphHit("
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathGraph;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Lnet/minecraft/world/phys/Vec3;D[Z)"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;";

    private static final String POCKET$ENGAGED =
            "engagedTickFrame("
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathGraph;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;ZZ)"
                    + "Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartTrackHitCache$EngagedTickFrame;";

    @Inject(method = POCKET$ENGAGED, at = @At("HEAD"), remap = false, require = 1)
    private static void pocket$enterEngaged(
            final ServerLevel level,
            @Coerce final Object graph,
            final ServerSubLevel cart,
            @Coerce final Object graphHit,
            final boolean negateEdgeTangent,
            final boolean trackHostedOnSubLevel,
            final CallbackInfoReturnable<Object> cir
    ) {
        SimulatedCoastersCartScaleContext.push(
                SimulatedCoastersScaleLookup.scaleForGraphHit(
                        level,
                        graphHit,
                        null,
                        SimulatedCoastersPlacementScaleContext.placementOr(ScaleFrame.scaleOf(cart))
                ));
    }

    @ModifyConstant(
            method = POCKET$ENGAGED,
            constant = @Constant(doubleValue = 0.3125D),
            remap = false,
            require = 1
    )
    private static double pocket$scaleEngagedRailOffset(final double original) {
        return original * SimulatedCoastersCartScaleContext.current();
    }

    @Inject(method = POCKET$ENGAGED, at = @At("RETURN"), remap = false, require = 1)
    private static void pocket$exitEngaged(
            final ServerLevel level,
            @Coerce final Object graph,
            final ServerSubLevel cart,
            @Coerce final Object graphHit,
            final boolean negateEdgeTangent,
            final boolean trackHostedOnSubLevel,
            final CallbackInfoReturnable<Object> cir
    ) {
        SimulatedCoastersCartScaleContext.pop();
    }

    @Inject(
            method = POCKET$NEAREST,
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private static void pocket$trackScaledSnapTolerance(
            final ServerLevel level,
            @Coerce final Object graph,
            final ServerSubLevel cart,
            final Vec3 bearingWorld,
            final double maxDistSq,
            final boolean[] subLevelTrackOut,
            final CallbackInfoReturnable<Object> cir
    ) {
        final Object graphHit = cir.getReturnValue();
        if (graphHit == null || bearingWorld == null) return;

        final Vec3 point = SimulatedCoastersScaleLookup.pointForGraphHit(graphHit);
        if (point == null) return;

        final double trackScale = SimulatedCoastersScaleLookup.scaleForGraphHit(level, graphHit, null, 1.0D);
        final double allowed = maxDistSq * trackScale * trackScale;
        if (bearingWorld.distanceToSqr(point) <= allowed) return;

        if (subLevelTrackOut != null && subLevelTrackOut.length > 0) subLevelTrackOut[0] = false;
        cir.setReturnValue(null);
    }
}
