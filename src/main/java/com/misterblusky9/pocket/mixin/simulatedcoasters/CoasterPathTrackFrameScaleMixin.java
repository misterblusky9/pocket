package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersCartScaleContext;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersScaleLookup;
import net.minecraft.world.level.Level;
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
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.graph.CoasterPathTrackFrame", remap = false)
public abstract class CoasterPathTrackFrameScaleMixin {
    private static final String POCKET$PATH_ANCHOR =
            "pathAnchorForSnappedBlock("
                    + "Lnet/minecraft/world/level/Level;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;"
                    + "Ljava/lang/Double;)Lnet/minecraft/world/phys/Vec3;";

    private static final String POCKET$SPINE_ANCHOR =
            "pathAnchorForSnappedBlockAtSpine("
                    + "Lnet/minecraft/world/level/Level;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathTrackFrame$GraphHit;"
                    + "Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;";

    @Inject(method = POCKET$PATH_ANCHOR, at = @At("HEAD"), remap = false, require = 1)
    private static void pocket$enterPathAnchor(
            final Level level,
            @Coerce final Object graphHit,
            final Double partialTick,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        SimulatedCoastersCartScaleContext.push(
                SimulatedCoastersScaleLookup.scaleForGraphHit(level, graphHit, partialTick));
    }

    @ModifyConstant(
            method = POCKET$PATH_ANCHOR,
            constant = @Constant(doubleValue = 0.3125D),
            remap = false,
            require = 2
    )
    private static double pocket$scaleSnappedPathOffset(final double original) {
        return original * SimulatedCoastersCartScaleContext.current();
    }

    @Inject(method = POCKET$PATH_ANCHOR, at = @At("RETURN"), remap = false, require = 1)
    private static void pocket$exitPathAnchor(
            final Level level,
            @Coerce final Object graphHit,
            final Double partialTick,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        SimulatedCoastersCartScaleContext.pop();
    }

    @Inject(method = POCKET$SPINE_ANCHOR, at = @At("HEAD"), remap = false, require = 1)
    private static void pocket$enterSpineAnchor(
            final Level level,
            @Coerce final Object graphHit,
            final Vec3 spinePoint,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        SimulatedCoastersCartScaleContext.push(
                SimulatedCoastersScaleLookup.scaleForGraphHit(level, graphHit, null));
    }

    @ModifyConstant(
            method = POCKET$SPINE_ANCHOR,
            constant = @Constant(doubleValue = 0.3125D),
            remap = false,
            require = 2
    )
    private static double pocket$scaleSpinePathOffset(final double original) {
        return original * SimulatedCoastersCartScaleContext.current();
    }

    @Inject(method = POCKET$SPINE_ANCHOR, at = @At("RETURN"), remap = false, require = 1)
    private static void pocket$exitSpineAnchor(
            final Level level,
            @Coerce final Object graphHit,
            final Vec3 spinePoint,
            final CallbackInfoReturnable<Vec3> cir
    ) {
        SimulatedCoastersCartScaleContext.pop();
    }
}
