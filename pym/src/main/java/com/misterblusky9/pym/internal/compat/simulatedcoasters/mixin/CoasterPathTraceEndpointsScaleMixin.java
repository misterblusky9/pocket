package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterCartScaleContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.graph.CoasterPathTraceEndpoints", remap = false)
public abstract class CoasterPathTraceEndpointsScaleMixin {
    @Unique
    private static final String PYM$OPEN_END =
            "shouldDisengageAtOpenEndHit("
                    + "Lnet/minecraft/world/level/Level;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathGraph;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;"
                    + "Ljava/util/List;D)Z";

    @ModifyConstant(
            method = PYM$OPEN_END,
            constant = @Constant(doubleValue = 0.0484D),
            remap = false,
            require = 1
    )
    private static double pym$scaleOpenEndHandoffJoinDistanceSq(final double original) {
        final double scale = CoasterCartScaleContext.current();
        return original * scale * scale;
    }
}
