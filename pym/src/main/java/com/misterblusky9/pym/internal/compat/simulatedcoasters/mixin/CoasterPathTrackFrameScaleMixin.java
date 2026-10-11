package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterCartScaleContext;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterScaleLookup;
import net.minecraft.world.level.Level;
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
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.graph.CoasterPathTrackFrame", remap = false)
public abstract class CoasterPathTrackFrameScaleMixin {
    @Unique
    private static final String PYM$PATH_ANCHOR =
            "pathAnchorForSnappedBlock("
                    + "Lnet/minecraft/world/level/Level;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;"
                    + "Ljava/lang/Double;)Lnet/minecraft/world/phys/Vec3;";

    @Unique
    private static final String PYM$SPINE_ANCHOR =
            "pathAnchorForSnappedBlockAtSpine("
                    + "Lnet/minecraft/world/level/Level;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;"
                    + "Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;";

    @Inject(method = PYM$PATH_ANCHOR, at = @At("HEAD"), remap = false, require = 1)
    private static void pym$enterPathAnchor(
            final Level level,
            @Coerce final Object graphHit,
            final Double partialTick,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        CoasterCartScaleContext.push(
                CoasterScaleLookup.scaleForGraphHit(level, graphHit, partialTick));
    }

    @ModifyConstant(
            method = PYM$PATH_ANCHOR,
            constant = @Constant(doubleValue = 0.3125D),
            remap = false,
            require = 2
    )
    private static double pym$scaleSnappedPathOffset(final double original) {
        return original * CoasterCartScaleContext.current();
    }

    @Inject(method = PYM$PATH_ANCHOR, at = @At("RETURN"), remap = false, require = 1)
    private static void pym$exitPathAnchor(
            final Level level,
            @Coerce final Object graphHit,
            final Double partialTick,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        CoasterCartScaleContext.pop();
    }

    @Inject(method = PYM$SPINE_ANCHOR, at = @At("HEAD"), remap = false, require = 1)
    private static void pym$enterSpineAnchor(
            final Level level,
            @Coerce final Object graphHit,
            final Vec3 spinePoint,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        CoasterCartScaleContext.push(
                CoasterScaleLookup.scaleForGraphHit(level, graphHit, null));
    }

    @ModifyConstant(
            method = PYM$SPINE_ANCHOR,
            constant = @Constant(doubleValue = 0.3125D),
            remap = false,
            require = 2
    )
    private static double pym$scaleSpinePathOffset(final double original) {
        return original * CoasterCartScaleContext.current();
    }

    @Inject(method = PYM$SPINE_ANCHOR, at = @At("RETURN"), remap = false, require = 1)
    private static void pym$exitSpineAnchor(
            final Level level,
            @Coerce final Object graphHit,
            final Vec3 spinePoint,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        CoasterCartScaleContext.pop();
    }
}
