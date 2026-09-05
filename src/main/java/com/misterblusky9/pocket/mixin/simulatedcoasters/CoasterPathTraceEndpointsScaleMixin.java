package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersCartScaleContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.graph.CoasterPathTraceEndpoints", remap = false)
public abstract class CoasterPathTraceEndpointsScaleMixin {
    private static final String POCKET$OPEN_END =
            "shouldDisengageAtOpenEndHit("
                    + "Lnet/minecraft/world/level/Level;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathGraph;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;"
                    + "Ljava/util/List;D)Z";

    @ModifyConstant(
            method = POCKET$OPEN_END,
            constant = @Constant(doubleValue = 0.0484D),
            remap = false,
            require = 1
    )
    private static double pocket$scaleOpenEndHandoffJoinDistanceSq(final double original) {
        final double scale = SimulatedCoastersCartScaleContext.current();
        return original * scale * scale;
    }
}
