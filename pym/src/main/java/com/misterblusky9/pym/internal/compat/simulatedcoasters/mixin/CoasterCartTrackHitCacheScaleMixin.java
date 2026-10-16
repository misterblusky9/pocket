package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.misterblusky9.pym.api.Pym;

import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterCartScaleContext;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterScaleLookup;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterPlacementScaleContext;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartTrackHitCache", remap = false)
public abstract class CoasterCartTrackHitCacheScaleMixin {

    @Unique
    private static final String PYM$NEAREST =
            "nearestGraphHit("
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathGraph;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Lnet/minecraft/world/phys/Vec3;D[Z)"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;";

    @Unique
    private static final String PYM$ENGAGED =
            "engagedTickFrame("
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathGraph;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;ZZ)"
                    + "Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartTrackHitCache$EngagedTickFrame;";

    @Inject(method = PYM$ENGAGED, at = @At("HEAD"), remap = false, require = 1)
    private static void pym$enterEngaged(
            ServerLevel level,
            @Coerce Object graph,
            ServerSubLevel cart,
            @Coerce Object graphHit,
            boolean negateEdgeTangent,
            boolean trackHostedOnSubLevel,
            CallbackInfoReturnable<Object> cir
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
            method = PYM$ENGAGED,
            constant = @Constant(doubleValue = 0.3125D),
            remap = false,
            require = 1
    )
    private static double pym$scaleEngagedRailOffset(double original) {
        return original * CoasterCartScaleContext.current();
    }

    @Inject(method = PYM$ENGAGED, at = @At("RETURN"), remap = false, require = 1)
    private static void pym$exitEngaged(
            ServerLevel level,
            @Coerce Object graph,
            ServerSubLevel cart,
            @Coerce Object graphHit,
            boolean negateEdgeTangent,
            boolean trackHostedOnSubLevel,
            CallbackInfoReturnable<Object> cir
    ) {
        CoasterCartScaleContext.pop();
    }

    @Inject(
            method = PYM$NEAREST,
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private static void pym$trackScaledSnapTolerance(
            ServerLevel level,
            @Coerce Object graph,
            ServerSubLevel cart,
            Vec3 bearingWorld,
            double maxDistSq,
            boolean[] subLevelTrackOut,
            CallbackInfoReturnable<Object> cir
    ) {
        Object graphHit = cir.getReturnValue();
        if (graphHit == null || bearingWorld == null) return;

        Vec3 point = CoasterScaleLookup.pointForGraphHit(graphHit);
        if (point == null) return;

        double trackScale = CoasterScaleLookup.scaleForGraphHit(level, graphHit, null, 1.0D);
        double allowed = maxDistSq * trackScale * trackScale;
        if (bearingWorld.distanceToSqr(point) <= allowed) return;

        if (subLevelTrackOut != null && subLevelTrackOut.length > 0) subLevelTrackOut[0] = false;
        cir.setReturnValue(null);
    }
}
