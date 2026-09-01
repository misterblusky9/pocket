package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersCartScaleContext;
import com.misterblusky9.pocket.physics.ScaleFrame;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
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
        SimulatedCoastersCartScaleContext.push(ScaleFrame.scaleOf(cart));
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
}
